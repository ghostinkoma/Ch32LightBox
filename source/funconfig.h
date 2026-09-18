#ifndef _FUNCONFIG_H
#define _FUNCONFIG_H

#define CH32V003                1     /* 対象: CH32V003 */
#define FUNCONF_USE_DEBUGPRINTF 1     /* printf を SWD(WCH-LinkE) 経由で出す */
#define FUNCONF_USE_CLK_SEC     0
#define FUNCONF_SYSTICK_USE_HCLK 1    /* SysTick=48MHz。WS2812(ws2812b_simple.h)が要求。
                                       * 時間ロジックは全て Ticks_from_Ms 経由で自動追従。 */

#endif
