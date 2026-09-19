/*
 * temp.h — 温度取得 (ダイ推定 or 外付けセンサ)  ※過熱保護の判定は lightbox.c
 *
 *  TEMP_SOURCE_DIE      : CH32V003 に内蔵温度センサは無いので、無負荷 DIE_AMBIENT_C を基準に
 *                         PWMデューティ由来の自己発熱を1次熱モデルで推定(ピン不要, SOP8向き)。
 *  TEMP_SOURCE_EXTERNAL : 外付けアナログ温度センサ(NTC等)を ADC で実測(要ピン, 要校正)。
 *
 *  temp_sample(applied_duty) が現在の推定/実測 ℃ を返す。DIE は applied_duty を使う。
 */
#ifndef TEMP_H
#define TEMP_H

#include "ch32fun.h"
#include "config.h"
#include "pins.h"      /* TEMP_SENSE_PIN → TEMP_ADC_CH (ADCチャネル自動導出) */

#if TEMP_SOURCE == TEMP_SOURCE_EXTERNAL
_Static_assert(TEMP_CAL_SLOPE_X100 != 0, "TEMP_CAL_SLOPE_X100 は 0 不可(0除算)");
#endif
_Static_assert(DIE_TAU_MS >= WARN_TEMP_PERIOD_MS, "DIE_TAU_MS は周期以上に");
_Static_assert(WARN_TEMP_HYST_C > 0 && WARN_TEMP_HYST_C < WARN_TEMP_C, "HYST は 0<HYST<WARN");
_Static_assert(THERMAL_THROTTLE_MAX < 100u, "THROTTLE_MAX<100 (出力を完全に0にしない)");

/* --- ダイ推定モデル状態 (centi-℃) --- */
static int32_t g_dieC100 = (int32_t)DIE_AMBIENT_C * 100;

static void temp_init(void)
{
#if WARN_LED_ENABLE
    funPinMode(WARN_LED_PIN, GPIO_CFGLR_OUT_10Mhz_PP);
    funDigitalWrite(WARN_LED_PIN, FUN_LOW);
#endif
#if TEMP_SOURCE == TEMP_SOURCE_EXTERNAL
    funPinMode(TEMP_SENSE_PIN, GPIO_CFGLR_IN_ANALOG);
    RCC->CFGR0 = (RCC->CFGR0 & ~RCC_ADCPRE) | RCC_ADCPRE_DIV8_2;  /* ADCCLK<=14MHz */
    funAnalogInit();
#endif
    g_dieC100 = (int32_t)DIE_AMBIENT_C * 100;
}

#if TEMP_SOURCE == TEMP_SOURCE_EXTERNAL
static int16_t temp_sample(uint16_t applied_duty)
{
    (void)applied_duty;
    int32_t adc = funAnalogRead(TEMP_ADC_CH);                  /* 10bit (chは TEMP_SENSE_PIN から導出) */
    int32_t c = TEMP_CAL_T0_C +
                (adc - (int32_t)TEMP_CAL_ADC0) * 100 / (int32_t)TEMP_CAL_SLOPE_X100;
    if (c < -40) c = -40;
    if (c > 200) c = 200;
    return (int16_t)c;
}
#else  /* TEMP_SOURCE_DIE : 熱モデル推定 */
static int16_t temp_sample(uint16_t applied_duty)
{
    /* 定常目標: 無負荷=AMBIENT, 全開で +DIE_RISE_AT_FULL_C */
    int32_t target = (int32_t)DIE_AMBIENT_C * 100
                   + (int32_t)applied_duty * DIE_RISE_AT_FULL_C * 100 / (int32_t)PWM_TOP;
    /* 時定数 DIE_TAU_MS で target へ一次追従 (周期 WARN_TEMP_PERIOD_MS) */
    int32_t step = (target - g_dieC100) * (int32_t)WARN_TEMP_PERIOD_MS / (int32_t)DIE_TAU_MS;
    if (step == 0 && target != g_dieC100) step = (target > g_dieC100) ? 1 : -1; /* 停滞防止 */
    g_dieC100 += step;
    return (int16_t)(g_dieC100 / 100);
}
#endif

static inline void temp_warn_led(uint8_t on)
{
#if WARN_LED_ENABLE
    funDigitalWrite(WARN_LED_PIN, on ? FUN_HIGH : FUN_LOW);
#else
    (void)on;
#endif
}

#endif /* TEMP_H */
