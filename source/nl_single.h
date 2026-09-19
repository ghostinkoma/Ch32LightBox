/*
 * nl_single.h — 単色LED 常夜灯 (NIGHTLIGHT_TYPE=NL_TYPE_SINGLE, 非スリープ経路)
 *
 *  本体が「完全に暗い」ときだけ、NL_LED_PIN を NL_PERIOD_S 秒周期で NL_ON_MS だけ点灯する。
 *   NL_USE_PWM=0: 点灯中は GPIO 単純 ON(最小演算)。
 *   NL_USE_PWM=1: 点灯中は CIE 三角波でソフトPWM明滅(演出。主ループ協調・非ブロッキング)。
 *
 *  ※深い低電力(Standby)経路は lowpower.h。本ファイルは「通常アクティブ時」の単色LED常夜灯。
 *   時間は SysTick 差分(≤~89秒)で判定するため NL_PERIOD_S は ~60秒までを想定。
 */
#ifndef NL_SINGLE_H
#define NL_SINGLE_H

#include "ch32fun.h"
#include "config.h"
#include "pins.h"
#if NL_USE_PWM
#include "cie.h"
#endif

_Static_assert((uint32_t)NL_ON_MS >= 1u && (uint32_t)NL_ON_MS < (uint32_t)NL_PERIOD_S * 1000u,
               "NL_ON_MS は 1..(NL_PERIOD_S*1000-1) ms");
_Static_assert((uint32_t)NL_PERIOD_S <= 60u,
               "非スリープ単色常夜灯の NL_PERIOD_S は 60秒以下(SysTick差分の安全域)");

static uint8_t  g_snlDark = 0;        /* 直前が暗状態だったか */
static uint8_t  g_snlOnPhase = 0;     /* 1=点灯フェーズ / 0=消灯フェーズ */
static uint32_t g_snlStart = 0;       /* 現フェーズ開始 tick */

static void nl_single_init(void)
{
    funPinMode(NL_LED_PIN, GPIO_CFGLR_OUT_2Mhz_PP);
    funDigitalWrite(NL_LED_PIN, FUN_LOW);
    g_snlDark = 0; g_snlOnPhase = 0;
}

/* 毎ループ呼ぶ。dark=1(本体が完全に暗い)なら周期点灯、0なら消灯。 */
static void nl_single_update(uint8_t dark)
{
    uint32_t now = SysTick->CNT;

    if (!dark) {                                   /* 明るい→常夜灯オフ */
        if (g_snlDark) funDigitalWrite(NL_LED_PIN, FUN_LOW);
        g_snlDark = 0; g_snlOnPhase = 0;
        return;
    }
    if (!g_snlDark) {                              /* 暗へ遷移: 点灯フェーズ先頭から */
        g_snlDark = 1; g_snlOnPhase = 1; g_snlStart = now;
    }

    uint32_t el = (uint32_t)(now - g_snlStart);

    if (g_snlOnPhase) {                            /* 点灯フェーズ (NL_ON_MS) */
        if (el >= Ticks_from_Ms((uint32_t)NL_ON_MS)) {
            g_snlOnPhase = 0; g_snlStart = now;
            funDigitalWrite(NL_LED_PIN, FUN_LOW);
            return;
        }
#if NL_USE_PWM
        /* CIE 三角波でソフトPWM(明→暗→) 。位相は SysTick 下位ビットで生成(≈3kHz)。 */
        uint32_t on_ms = (uint32_t)NL_ON_MS, half = on_ms / 2u; if (half == 0) half = 1u;
        uint32_t el_ms = el / Ticks_from_Ms(1);
        uint32_t Lx = (el_ms < half) ? (10000u * el_ms / half)
                                     : (10000u * (on_ms - el_ms) / half);
        uint8_t duty  = (uint8_t)cie_scale((uint16_t)Lx, 255);
        uint8_t phase = (uint8_t)((now >> 6) & 0xFFu);
        funDigitalWrite(NL_LED_PIN, (phase < duty) ? FUN_HIGH : FUN_LOW);
#else
        funDigitalWrite(NL_LED_PIN, FUN_HIGH);     /* 単純ON */
#endif
    } else {                                       /* 消灯フェーズ (残り時間) */
        if (el >= Ticks_from_Ms((uint32_t)NL_PERIOD_S * 1000u - (uint32_t)NL_ON_MS)) {
            g_snlOnPhase = 1; g_snlStart = now;    /* 次の点灯へ */
        }
    }
}

#endif /* NL_SINGLE_H */
