/*
 * lowpower.h — 深い低電力(Standby + AWU)常夜灯  ★実験的・実機未検証
 *
 *  バッテリ駆動向け。単色LED常夜灯を NL_PERIOD_S 秒おきに NL_ON_MS だけ点け、
 *  その間 MCU は極力眠る。押しSW(EXTI)で起水すると呼び出し側が通常ディマーに戻る。
 *
 *  採用条件(下の _Static_assert): NIGHTLIGHT_ENABLE=1 かつ NIGHTLIGHT_TYPE=NL_TYPE_SINGLE
 *  かつ NL_USE_PWM=0。WS2812 や PWM 明滅は演出優先のため deep sleep 非対象。
 *
 *  ── 設計方針 ──
 *   ・点灯(NL_ON_MS)中: LED を GPIO で ON にし、AWU で計時して Sleep(WFI)。GPIO は Sleep で保持される。
 *   ・消灯(残り時間)中: Standby(PWR_CTLR_PDDS) + AWU。最深(µA)。GPIO は高Z化しうるが LED は消灯中なので可。
 *   ・起水源: AWU(周期) と 押しSW EXTI(立下り)。ロータリは起水源にしない(リーク/複雑回避)。
 *   ・エンコーダ A/B はスリープ前にアナログ入力化してプルアップのリークを止める。
 *   ・リセット耐性: Standby 復帰が reset/resume どちらでも成立するよう、呼び出し側 main が
 *     「起動時に押しSW押下→通常モード / 非押下→本サイクル」で分岐する(reset なら main 再実行で同じ判断)。
 *   ・AWU は LSI(≈128kHz)駆動で HCLK 非依存。1サイクル上限 ~30s(PSC/61440×窓63)。
 *
 *  ★Standby 復帰挙動・AWU 周期・実消費電流は実機で要確認。既定 LOW_POWER_MODE=0 では本ファイルは空。
 */
#ifndef LOWPOWER_H
#define LOWPOWER_H

#include "ch32fun.h"
#include "config.h"
#include "pins.h"

#if LOW_POWER_MODE

/* ---- 採用条件チェック ---- */
_Static_assert(NIGHTLIGHT_ENABLE, "LOW_POWER_MODE は NIGHTLIGHT_ENABLE=1 が必要");
_Static_assert(NIGHTLIGHT_TYPE == NL_TYPE_SINGLE,
               "LOW_POWER_MODE は NIGHTLIGHT_TYPE=NL_TYPE_SINGLE が必要(WS2812は演出優先で非対象)");
_Static_assert(NL_USE_PWM == 0,
               "LOW_POWER_MODE は NL_USE_PWM=0 が必要(PWM明滅は演出優先で非対象)");
_Static_assert(NL_PERIOD_S >= 1u && NL_PERIOD_S <= 30u,
               "深い低電力の NL_PERIOD_S は 1..30 秒(AWU 1サイクル上限)");
_Static_assert(NL_ON_MS >= 1u && (uint32_t)NL_ON_MS < (uint32_t)NL_PERIOD_S * 1000u,
               "NL_ON_MS は 1..(NL_PERIOD_S*1000-1) ms");
_Static_assert(!WDT_ENABLE,
               "LOW_POWER_MODE は WDT_ENABLE=0 が必要(IWDGはStandby中も動作しAWU前にリセットしうる)");

/* LSI 公称 128kHz。AWU: 待ち[s] ≈ 窓(1..63) × PSC / 128000。 */
#define LP_LSI_HZ 128000u

/* PSC 分周値と対応レジスタコード(PWR_AWUPSC_*) */
static const uint16_t LP_PSC_DIV[] = {2,4,8,16,32,64,128,256,512,1024,2048,4096,10240,61440};
static const uint8_t  LP_PSC_REG[] = {0x2,0x3,0x4,0x5,0x6,0x7,0x8,0x9,0xA,0xB,0xC,0xD,0xE,0xF};

/* AWU を「約 target_ms 後に起水」に設定して有効化。
 * 窓 = target_ms × (LSI/1000) / PSC を [1,63] に収める最小 PSC(=最良分解能)を選ぶ。 */
static void lp_awu_set_ms(uint32_t target_ms)
{
    uint8_t  reg = 0xF;      /* fallback: 最大分周 */
    uint32_t win = 63;
    for (unsigned i = 0; i < sizeof(LP_PSC_DIV)/sizeof(LP_PSC_DIV[0]); i++) {
        uint32_t w = target_ms * (LP_LSI_HZ / 1000u) / LP_PSC_DIV[i];  /* = ms*128/div */
        if (w >= 1u && w <= 63u) { reg = LP_PSC_REG[i]; win = w; break; }
    }
    if (win < 1u) win = 1u;
    if (win > 63u) win = 63u;
    PWR->AWUPSC = reg;
    PWR->AWUWR  = (uint32_t)win;
    PWR->AWUCSR = PWR_AWUCSR_AWUEN;      /* AWU 有効 */
}

/* 押しSW(ENC_SW_PIN) を EXTI 立下り起水に設定(押下=Low)。 */
static void lp_button_exti_arm(void)
{
    uint32_t line = (uint32_t)(ENC_SW_PIN) & 0x7u;          /* EXTI ライン = ピン番号 */
    uint32_t port = (uint32_t)(ENC_SW_PIN) >> 4;            /* 0=A,2=C,3=D */
    /* AFIO EXTICR: 4bit/線 でポート選択(A=0,C=2,D=3 は port 値と一致) */
    AFIO->EXTICR = (AFIO->EXTICR & ~(0xFu << (line * 4u))) | (port << (line * 4u));
    EXTI->INTENR |= (1u << line);        /* 割込み許可(WFI 起水に必要) */
    EXTI->FTENR  |= (1u << line);        /* 立下りトリガ */
    NVIC_EnableIRQ(EXTI7_0_IRQn);
}

/* スリープ前: エンコーダ A/B をアナログ入力化(プルアップのリーク停止)。押しSWは起水用に温存。 */
static inline void lp_pins_lowleak(void)
{
    funPinMode(ENC_A_PIN, GPIO_CFGLR_IN_ANALOG);
    funPinMode(ENC_B_PIN, GPIO_CFGLR_IN_ANALOG);
    /* ENC_SW_PIN は内部プルアップ入力のまま(常開なので待機リークほぼ0、押下で起水) */
    funPinMode(ENC_SW_PIN, GPIO_CFGLR_IN_PUPD); funDigitalWrite(ENC_SW_PIN, FUN_HIGH);
}

/* WFI で Sleep(deep=0) / Standby(deep=1) に入る。 */
static inline void lp_wfi(int deep)
{
    if (deep) { PWR->CTLR |= PWR_CTLR_PDDS;  PFIC->SCTLR |=  (1u << 2); }  /* PDDS + SLEEPDEEP */
    else      { PWR->CTLR &= ~PWR_CTLR_PDDS; PFIC->SCTLR &= ~(1u << 2); }
    __asm volatile ("wfi");
}

/* 単色LED を GPIO で ON/OFF(押しSW等と排他は pins.h が保証)。 */
static inline void lp_led(int on)
{
    funPinMode(NL_LED_PIN, GPIO_CFGLR_OUT_2Mhz_PP);
    funDigitalWrite(NL_LED_PIN, on ? FUN_HIGH : FUN_LOW);
}

/* 押しSWが今押されているか(押下=Low)。 */
static inline int lp_button_down(void)
{
    funPinMode(ENC_SW_PIN, GPIO_CFGLR_IN_PUPD); funDigitalWrite(ENC_SW_PIN, FUN_HIGH);
    return funDigitalRead(ENC_SW_PIN) == 0;
}

/* PWR/LSI/AWU の初期化(1回)。 */
static void lp_init(void)
{
    RCC->APB1PCENR |= RCC_APB1Periph_PWR;      /* PWR クロック */
    RCC->RSTSCKR   |= RCC_LSION;               /* LSI 起動 */
    while (!(RCC->RSTSCKR & RCC_LSIRDY)) { }
    NVIC_EnableIRQ(AWU_IRQn);
}

/* 深い低電力の常夜灯サイクル。押しSW起水で戻る(呼び出し側が通常モードへ)。
 * AWU起水では点灯→スリープを繰り返す。reset復帰時は main が再度呼ぶ(同じ挙動)。 */
static void lowpower_nightlight_cycle(void)
{
    lp_pins_lowleak();
    lp_button_exti_arm();
    for (;;) {
        /* --- 点灯(NL_ON_MS): LED ON のまま Sleep(GPIO保持) --- */
        lp_led(1);
        lp_awu_set_ms((uint32_t)NL_ON_MS);
        lp_wfi(0);                              /* Sleep: AWU or 押しSW で起水 */
        lp_led(0);
        if (lp_button_down()) return;           /* 押しSW → 通常モードへ */

        /* --- 消灯(残り時間): Standby で最深スリープ --- */
        lp_awu_set_ms((uint32_t)NL_PERIOD_S * 1000u - (uint32_t)NL_ON_MS);
        lp_wfi(1);                              /* Standby: AWU or 押しSW で起水 */
        if (lp_button_down()) return;           /* 押しSW → 通常モードへ */
        /* AWU 起水 → ループ先頭(次の点灯)へ */
    }
}

/* WFI 起水用の最小割込みハンドラ(フラグをクリアして戻るだけ)。 */
void AWU_IRQHandler(void)     __attribute__((interrupt));
void EXTI7_0_IRQHandler(void) __attribute__((interrupt));
void AWU_IRQHandler(void)     { PWR->AWUCSR &= ~PWR_AWUCSR_AWUEN; }
void EXTI7_0_IRQHandler(void) { EXTI->INTFR = EXTI->INTFR; }   /* pending 全クリア */

#endif /* LOW_POWER_MODE */
#endif /* LOWPOWER_H */
