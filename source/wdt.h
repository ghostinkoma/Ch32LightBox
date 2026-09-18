/*
 * wdt.h — 独立ウォッチドッグ (IWDG)
 *
 *  ファームが固まったら自動リセットして復帰させる安全装置。
 *  IWDG は LSI(≈128kHz) 駆動。プリスケーラ /256 で 500Hz(=2ms/tick)、
 *  reload(12bit,最大4095) = TIMEOUT_MS/2。→ 最大約 8.19 秒まで。
 *  ※LSI は誤差があるためタイムアウトは概算。
 */
#ifndef WDT_H
#define WDT_H

#include "ch32fun.h"
#include "config.h"

#define WDT_LSI_HZ        128000u
#define WDT_PRESCALER     6u          /* /256 */
#define WDT_TICK_HZ       (WDT_LSI_HZ / 256u)               /* 500 Hz */
#define WDT_RELOAD        ((WDT_TIMEOUT_MS) * WDT_TICK_HZ / 1000u)

_Static_assert(WDT_RELOAD >= 1u && WDT_RELOAD <= 0x0FFFu,
               "WDT_TIMEOUT_MS が範囲外(約2〜8190ms)。/256時 reload=ms*0.5, 最大4095");
/* 最悪ブロック(フラッシュ書込 数〜十数ms 等)より十分長くする。100ms 以上を要求。 */
_Static_assert(WDT_TIMEOUT_MS >= 100u,
               "WDT_TIMEOUT_MS は 100ms 以上に(フラッシュ書込等の最悪ブロックより長く)");

static inline void wdt_feed(void)
{
    IWDG->CTLR = 0xAAAA;              /* リロード */
}

static void wdt_init(void)
{
    IWDG->CTLR = 0x5555;             /* レジスタ書込許可 */
    IWDG->PSCR = WDT_PRESCALER;
    IWDG->CTLR = 0x5555;
    IWDG->RLDR = WDT_RELOAD & 0x0FFFu;
    wdt_feed();
    IWDG->CTLR = 0xCCCC;             /* 起動(以後停止不可) */
}

#endif /* WDT_H */
