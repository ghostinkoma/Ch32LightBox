/*
 * cie.h — CIE 1931 L*(明度) カーブ (人間の知覚を再現)  ※本体LEDと常夜灯で共用
 *
 *   L* <= 8 : Y = L* / 903.3            (真っ暗付近の直線トウ)
 *   L* >  8 : Y = ((L* + 16) / 116)^3   (べき乗則)
 *   出力 = maxval * Y     (整数演算のみ, 立方は uint64)
 *
 * Lx = L* を 1/100 単位で渡す(0..10000)。maxval は任意(本体=PWM_TOP, 常夜灯=各chの最大値)。
 */
#ifndef CIE_H
#define CIE_H

#include <stdint.h>

static uint16_t cie_scale(uint32_t Lx /*L* x100, 0..10000*/, uint32_t maxval)
{
    uint32_t d;
    if (Lx > 10000u) Lx = 10000u;
    if (Lx <= 800u) {                                    /* L* <= 8.00 : 直線トウ */
        d = (uint32_t)((uint64_t)maxval * Lx / 90330u);  /* /903.3 (x100) */
    } else {                                             /* べき乗則 */
        uint64_t t = (uint64_t)(Lx + 1600u);             /* (L*+16) x100 */
        d = (uint32_t)((uint64_t)maxval * t * t * t / 1560896000000ull); /* (116*100)^3 */
    }
    if (d > maxval) d = maxval;
    return (uint16_t)d;
}

/* レベル i (0..levels) → L*(x100) 等間隔 → duty(0..maxval) */
static inline uint16_t cie_level(uint32_t i, uint32_t levels, uint32_t maxval)
{
    return cie_scale(10000u * i / levels, maxval);
}

#endif /* CIE_H */
