/*
 * pins.h — 機能↔ピン割当の能力モデル・導出・コンパイル時検証 (CH32V003 SOP8 / J4M6)
 *
 *  config.h の「1機能=1ピンマクロ」から、各機能に必要な低レベル情報
 *  (GPIOポート/ピン番号 / ADCチャネル / PWMのタイマ・チャネル・remap) を導出し、
 *  ハード能力違反(PWM/ADC不可ピン)とピン衝突(2機能が同一ピン)をコンパイル時に弾く。
 *
 *  ── SOP8 (J4M6) の実在ピンと能力 ──  (出典: CH32V003 データシート / ch32fun ch32v003hw.h)
 *   pin   GPIO      ADCch   PWM経路(正出力)            既定用途
 *   PA1   GPIOA/1   1       TIM1_CH2 (no remap)        ENC_A
 *   PA2   GPIOA/2   0       (CH2N=相補のみ→PWM非対応)   ENC_SW
 *   PC1   GPIOC/1   -       TIM2_CH4 (remap1)          ENC_B
 *   PC2   GPIOC/2   -       TIM2_CH2 (remap1) ★既定PWM
 *   PC4   GPIOC/4   2       TIM1_CH4 (no remap)        常夜灯/温度/警告灯
 *   PD1   GPIOD/1   -       (CH3N=相補のみ) ※SWIO=書込/printf 予約
 *
 *  ※既定構成(PWM=PC2)は従来と同一のレジスタ操作を生成する(挙動不変)。
 *    PC2 以外の PWM ピンは「コンパイル対応・実機未検証」。
 */
#ifndef PINS_H
#define PINS_H

#include "ch32fun.h"   /* ピンマクロ(PA1=1,PC2=34,…) / GpioOf / TIM/RCC/AFIO 定義 */
#include "config.h"    /* 機能↔ピンの割当・有効フラグ */

/* ch32fun のピンマクロは整数値: 汎用の導出はビット演算で行える。
 *   GPIOポート = GpioOf(pin)  (= GPIOA_BASE + 0x400*(pin>>4))
 *   ピン番号   = (pin & 0x7)   (SOP8 は全て 0..7) */
#define LB_GPIO_OF(pin)   GpioOf(pin)
#define LB_PINNUM_OF(pin) ((pin) & 0x7)

/* SOP8 実在ピンか (それ以外は物理的に存在しない) */
#define LB_PIN_IS_SOP8(p) \
    ((p)==PA1 || (p)==PA2 || (p)==PC1 || (p)==PC2 || (p)==PC4 || (p)==PD1)

/* =========================================================================
 *  PWM 出力ピン → タイマ / チャネル / remap の導出
 *  正出力(単出力)チャネルのみ対応。相補(PA2=CH2N/PD1=CH3N)は既定除外。
 * ========================================================================= */
#if   PWM_PIN == PC2
  #define PWM_USES_TIM1    0
  #define PWM_CH           2
  #define PWM_REMAP_CLEAR  AFIO_PCFR1_TIM2_REMAP
  #define PWM_REMAP_SET    AFIO_PCFR1_TIM2_REMAP_PARTIALREMAP1
  #define PWM_RCC_GPIO_BIT RCC_APB2Periph_GPIOC
#elif PWM_PIN == PC1
  #define PWM_USES_TIM1    0
  #define PWM_CH           4
  #define PWM_REMAP_CLEAR  AFIO_PCFR1_TIM2_REMAP
  #define PWM_REMAP_SET    AFIO_PCFR1_TIM2_REMAP_PARTIALREMAP1
  #define PWM_RCC_GPIO_BIT RCC_APB2Periph_GPIOC
#elif PWM_PIN == PA1
  #define PWM_USES_TIM1    1
  #define PWM_CH           2
  #define PWM_REMAP_CLEAR  AFIO_PCFR1_TIM1_REMAP
  #define PWM_REMAP_SET    AFIO_PCFR1_TIM1_REMAP_NOREMAP
  #define PWM_RCC_GPIO_BIT RCC_APB2Periph_GPIOA
#elif PWM_PIN == PC4
  #define PWM_USES_TIM1    1
  #define PWM_CH           4
  #define PWM_REMAP_CLEAR  AFIO_PCFR1_TIM1_REMAP
  #define PWM_REMAP_SET    AFIO_PCFR1_TIM1_REMAP_NOREMAP
  #define PWM_RCC_GPIO_BIT RCC_APB2Periph_GPIOC
#else
  #error "PWM_PIN は PWM 可能な SOP8 ピンにしてください: PC2(TIM2_CH2)/PC1(TIM2_CH4)/PA1(TIM1_CH2)/PC4(TIM1_CH4)"
#endif

#if PWM_USES_TIM1
  #define PWM_TIMREG TIM1
#else
  #define PWM_TIMREG TIM2
#endif

#define PWM_GPIO_PORT  LB_GPIO_OF(PWM_PIN)
#define PWM_PINNUM     LB_PINNUM_OF(PWM_PIN)

/* チャネル番号(1..4)からレジスタ/ビットを合成する2段展開マクロ */
#define LB_CVR_(t,c)   ((t)->CH##c##CVR)
#define LB_CVR(t,c)    LB_CVR_(t,c)
#define PWM_CVR        LB_CVR(PWM_TIMREG, PWM_CH)                 /* 比較値レジスタ */

#define LB_OCM_(c)     (TIM_OC##c##M_2 | TIM_OC##c##M_1 | TIM_OC##c##PE) /* PWM mode1 + preload */
#define LB_OCM(c)      LB_OCM_(c)
#define PWM_OCM        LB_OCM(PWM_CH)

#define LB_CCE_(c)     (TIM_CC##c##E)                            /* 出力有効 */
#define LB_CCE(c)      LB_CCE_(c)
#define PWM_CCE        LB_CCE(PWM_CH)

/* CH1/CH2 は CHCTLR1、CH3/CH4 は CHCTLR2 に OCxM を書く */
#if PWM_CH <= 2
  #define PWM_CHCTLR   PWM_TIMREG->CHCTLR1
#else
  #define PWM_CHCTLR   PWM_TIMREG->CHCTLR2
#endif

/* =========================================================================
 *  外付け温度センサ(ADC) ピン → ADCチャネルの導出 (external 使用時のみ)
 * ========================================================================= */
#if TEMP_PROTECT_ENABLE && (TEMP_SOURCE == TEMP_SOURCE_EXTERNAL)
  #if   TEMP_SENSE_PIN == PA2
    #define TEMP_ADC_CH 0
  #elif TEMP_SENSE_PIN == PA1
    #define TEMP_ADC_CH 1
  #elif TEMP_SENSE_PIN == PC4
    #define TEMP_ADC_CH 2
  #else
    #error "TEMP_SENSE_PIN は ADC 対応の SOP8 ピンにしてください: PA2(ch0)/PA1(ch1)/PC4(ch2)"
  #endif
#endif

/* =========================================================================
 *  常夜灯 WS2812 データピン → GPIOポート/ピン番号の導出
 * ========================================================================= */
#define WS_GPIO_PORT   LB_GPIO_OF(WS_DIN_PIN)
#define WS_GPIO_PINNUM LB_PINNUM_OF(WS_DIN_PIN)

/* =========================================================================
 *  ピン衝突検出 (全機能横断・コンパイル時)
 *    各 SOP8 ピンに一意ビットを割当て、有効な機能だけが自分のピンビットを寄与する
 *    「加算(SUM)」と「論理和(OR)」を作る。重複があれば加算で桁上がりして SUM!=OR に
 *    なる → _Static_assert で検出。ビットは数値ピンIDで引く(マクロは整数に展開される)。
 * ========================================================================= */
#define PIN_BIT_1   (1u << 0)   /* PA1 */
#define PIN_BIT_2   (1u << 1)   /* PA2 */
#define PIN_BIT_33  (1u << 2)   /* PC1 */
#define PIN_BIT_34  (1u << 3)   /* PC2 */
#define PIN_BIT_36  (1u << 4)   /* PC4 */
#define PIN_BIT_49  (1u << 5)   /* PD1 */
#define LB_PINBIT_(p) PIN_BIT_##p
#define LB_PINBIT(p)  LB_PINBIT_(p)   /* p は数値に展開されてから貼付 (PA1→1→PIN_BIT_1) */

/* 常に使うピン: エンコーダ A/B/SW と 本体 PWM */
#define LB_BITS_ALWAYS_SUM \
    (LB_PINBIT(ENC_A_PIN) + LB_PINBIT(ENC_B_PIN) + LB_PINBIT(ENC_SW_PIN) + LB_PINBIT(PWM_PIN))
#define LB_BITS_ALWAYS_OR  \
    (LB_PINBIT(ENC_A_PIN) | LB_PINBIT(ENC_B_PIN) | LB_PINBIT(ENC_SW_PIN) | LB_PINBIT(PWM_PIN))

/* 任意機能: 有効なときだけピンビットを寄与 */
#if NIGHTLIGHT_ENABLE
  #define LB_BIT_NL LB_PINBIT(WS_DIN_PIN)
#else
  #define LB_BIT_NL 0u
#endif
#if TEMP_PROTECT_ENABLE && (TEMP_SOURCE == TEMP_SOURCE_EXTERNAL)
  #define LB_BIT_TEMP LB_PINBIT(TEMP_SENSE_PIN)
#else
  #define LB_BIT_TEMP 0u
#endif
#if WARN_LED_ENABLE
  #define LB_BIT_WARN LB_PINBIT(WARN_LED_PIN)
#else
  #define LB_BIT_WARN 0u
#endif

#define LB_USED_SUM (LB_BITS_ALWAYS_SUM + LB_BIT_NL + LB_BIT_TEMP + LB_BIT_WARN)
#define LB_USED_OR  (LB_BITS_ALWAYS_OR  | LB_BIT_NL | LB_BIT_TEMP | LB_BIT_WARN)

/* 能力・存在チェック(分かりやすいメッセージを先に出す) */
_Static_assert(LB_PIN_IS_SOP8(ENC_A_PIN),  "ENC_A_PIN は SOP8 ピン(PA1/PA2/PC1/PC2/PC4/PD1)にしてください");
_Static_assert(LB_PIN_IS_SOP8(ENC_B_PIN),  "ENC_B_PIN は SOP8 ピンにしてください");
_Static_assert(LB_PIN_IS_SOP8(ENC_SW_PIN), "ENC_SW_PIN は SOP8 ピンにしてください");
_Static_assert(LB_PIN_IS_SOP8(PWM_PIN),    "PWM_PIN は SOP8 ピンにしてください");
#if NIGHTLIGHT_ENABLE
_Static_assert(LB_PIN_IS_SOP8(WS_DIN_PIN), "WS_DIN_PIN は SOP8 ピンにしてください");
#endif
#if WARN_LED_ENABLE
_Static_assert(LB_PIN_IS_SOP8(WARN_LED_PIN), "WARN_LED_PIN は SOP8 ピンにしてください");
#endif

/* 衝突検出: 2機能が同一ピンなら SUM!=OR
 * (メッセージは ASCII 先頭。日本語は gcc が8進エスケープ表示するため識別語を前置) */
_Static_assert(LB_USED_SUM == LB_USED_OR,
    "LightBox PIN CONFLICT: two features are assigned to the same pin. "
    "Fix the pin assignments in config.h (PWM_PIN/ENC_*_PIN/WS_DIN_PIN/TEMP_SENSE_PIN/WARN_LED_PIN).");

/* SWIO(PD1) は書込/printf 用の予約ピン。機能割当は非推奨(警告のみ) */
#if (ENC_A_PIN==PD1)||(ENC_B_PIN==PD1)||(ENC_SW_PIN==PD1)||(PWM_PIN==PD1)|| \
    (NIGHTLIGHT_ENABLE && (WS_DIN_PIN==PD1))|| \
    (WARN_LED_ENABLE && (WARN_LED_PIN==PD1))|| \
    (TEMP_PROTECT_ENABLE && (TEMP_SOURCE==TEMP_SOURCE_EXTERNAL) && (TEMP_SENSE_PIN==PD1))
  #warning "PD1 は SWIO(書込/debugprintf)予約ピンです。機能割当は書込/デバッグと競合します"
#endif

#endif /* PINS_H */
