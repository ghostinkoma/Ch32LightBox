/*
 * nightlight.h — WS2812 / SK6812(RGBW) 常夜灯
 *
 *  本体が「完全に暗い(明るさ0)」ときだけ動作。NIGHTLIGHT_PERIOD_MS(n) 周期で
 *  CIE L*(人間の知覚)基準に じわっと 明→暗 を1往復し、そのあと NIGHTLIGHT_INTERVAL_MS(i)
 *  だけ完全オフ、を繰り返す。本体を明るくすると即オフ。
 *
 *  色順(WS_ORDER)と RGB/RGBW(SK6812) を config で選択。最大色は WS_MAX_COLOR。
 *
 *  ※ ws2812b_simple.h は FUNCONF_SYSTICK_USE_HCLK=1 を要求(funconfig.h で設定済)。
 *    実機でのタイミング/色は未検証。
 */
#ifndef NIGHTLIGHT_H
#define NIGHTLIGHT_H

#include "ch32fun.h"
#include "config.h"
#include "cie.h"
#include "ws2812b_simple.h"

/* --- 色チャンネル分解 (RGBW系は末尾Wあり) --- */
#if (WS_ORDER == WS_ORDER_GRBW) || (WS_ORDER == WS_ORDER_RGBW)
  #define WS_HAS_W 1
  #define WS_BPP   4
  #define WS_MAX_R ((WS_MAX_COLOR >> 24) & 0xFFu)
  #define WS_MAX_G ((WS_MAX_COLOR >> 16) & 0xFFu)
  #define WS_MAX_B ((WS_MAX_COLOR >>  8) & 0xFFu)
  #define WS_MAX_W ((WS_MAX_COLOR      ) & 0xFFu)
#else
  #define WS_HAS_W 0
  #define WS_BPP   3
  #define WS_MAX_R ((WS_MAX_COLOR >> 16) & 0xFFu)
  #define WS_MAX_G ((WS_MAX_COLOR >>  8) & 0xFFu)
  #define WS_MAX_B ((WS_MAX_COLOR      ) & 0xFFu)
  #define WS_MAX_W 0u
#endif

/* 1 LED 分のバイトをチップの並びで buf に詰める */
static void ws_pack(uint8_t r, uint8_t g, uint8_t b, uint8_t w, uint8_t *buf)
{
#if   WS_ORDER == WS_ORDER_GRB
    buf[0] = g; buf[1] = r; buf[2] = b;
#elif WS_ORDER == WS_ORDER_RGB
    buf[0] = r; buf[1] = g; buf[2] = b;
#elif WS_ORDER == WS_ORDER_GRBW
    buf[0] = g; buf[1] = r; buf[2] = b; buf[3] = w;
#elif WS_ORDER == WS_ORDER_RGBW
    buf[0] = r; buf[1] = g; buf[2] = b; buf[3] = w;
#else
  #error "WS_ORDER が不正 (WS_ORDER_GRB/RGB/GRBW/RGBW のいずれか)"
#endif
    (void)w;
}

_Static_assert(NIGHTLIGHT_PERIOD_MS >= 2u, "NIGHTLIGHT_PERIOD_MS は 2ms 以上(half=0除算防止)");
/* 32bit SysTick@48MHz は約89.5秒でラップ。周期/間隔はそれ未満に十分な余裕(<=60秒)で制約。
 * これで el_ms(経過ms)も 10000*el_ms も uint32 に収まりオーバーフローしない。 */
_Static_assert(NIGHTLIGHT_PERIOD_MS   <= 60000u, "NIGHTLIGHT_PERIOD_MS は 60000ms 以下");
_Static_assert(NIGHTLIGHT_INTERVAL_MS <= 60000u, "NIGHTLIGHT_INTERVAL_MS は 60000ms 以下");
_Static_assert(WS_COUNT >= 1u, "WS_COUNT は 1 以上");

static uint8_t  g_nlBuf[WS_COUNT * WS_BPP];
static uint8_t  g_nlPhaseOff  = 0;      /* 0=呼吸中, 1=インターバル(オフ) */
static uint8_t  g_nlActive    = 0;      /* 直前が暗状態だったか */
static uint32_t g_nlStart     = 0;      /* 現フェーズ開始 tick */
static uint32_t g_nlRefresh   = 0;      /* 最終送信 tick */

/* 全LEDを1色でセットして送信 */
static void ws_send_rgbw(uint8_t r, uint8_t g, uint8_t b, uint8_t w)
{
    for (uint32_t k = 0; k < WS_COUNT; k++) ws_pack(r, g, b, w, &g_nlBuf[k * WS_BPP]);
    WS2812BSimpleSend(WS_PORT, WS_PINNUM, g_nlBuf, (int)sizeof(g_nlBuf));
}

static inline void ws_off(void) { ws_send_rgbw(0, 0, 0, 0); }

static void nightlight_init(void)
{
    ws_off();                            /* 起動時に消灯 */
    g_nlActive = 0; g_nlPhaseOff = 0;
}

/* 起動セルフテスト用: 常夜灯LEDを WS_MAX_COLOR で点灯(配線/タイミング切り分け) */
static inline void nightlight_test_on(void)
{
    ws_send_rgbw((uint8_t)WS_MAX_R, (uint8_t)WS_MAX_G, (uint8_t)WS_MAX_B, (uint8_t)WS_MAX_W);
}

/* 毎ループ呼ぶ。dark=1(本体が完全に暗い)なら常夜灯を進め、0なら消して待機。 */
static void nightlight_update(uint8_t dark)
{
    uint32_t now = SysTick->CNT;

    if (!dark) {                         /* 明るい→常夜灯オフ(1回だけ送信) */
        if (g_nlActive) { ws_off(); g_nlActive = 0; }
        g_nlPhaseOff = 0; g_nlStart = now; g_nlRefresh = now;
        return;
    }

    if (!g_nlActive) {                    /* 暗へ遷移: 呼吸フェーズ先頭から開始 */
        g_nlActive = 1; g_nlPhaseOff = 0; g_nlStart = now; g_nlRefresh = now - Ticks_from_Ms(NIGHTLIGHT_REFRESH_MS);
    }

    /* 経過を先に ms へ(tick/48000)。以降は ms で比較し、Ticks_from_Ms(大きな周期)の
     * uint32 オーバーフロー(period×48000 が 2^32 超)を避ける。
     * el_ms は 32bit SysTick のラップ(~89s @48MHz)未満で有効 → 周期/間隔は静的に上限制約。 */
    uint32_t el_ms = (uint32_t)(now - g_nlStart) / Ticks_from_Ms(1);

    if (g_nlPhaseOff) {                   /* インターバル(完全オフ) */
        if (el_ms >= (uint32_t)NIGHTLIGHT_INTERVAL_MS) {
            g_nlPhaseOff = 0; g_nlStart = now;   /* 次の呼吸へ */
        }
        return;                          /* オフ中は送信不要 */
    }

    /* 呼吸フェーズ: 明→暗 1往復 (三角波) を CIE で */
    if (el_ms >= (uint32_t)NIGHTLIGHT_PERIOD_MS) {
        g_nlPhaseOff = 1; g_nlStart = now; ws_off();  /* 往復完了→オフへ */
        return;
    }
    /* リフレッシュ間隔でのみ送信(REFRESHは小さいので tick 比較で可) */
    if ((uint32_t)(now - g_nlRefresh) < Ticks_from_Ms(NIGHTLIGHT_REFRESH_MS)) return;
    g_nlRefresh = now;

    uint32_t half_ms = (uint32_t)NIGHTLIGHT_PERIOD_MS / 2u;
    uint32_t Lx;                                            /* L* x100 (0..10000) */
    if (el_ms < half_ms) Lx = 10000u * el_ms / half_ms;                         /* 明るく */
    else                 Lx = 10000u * ((uint32_t)NIGHTLIGHT_PERIOD_MS - el_ms) / half_ms; /* 暗く */

    uint8_t r = (uint8_t)cie_scale(Lx, WS_MAX_R);
    uint8_t g = (uint8_t)cie_scale(Lx, WS_MAX_G);
    uint8_t b = (uint8_t)cie_scale(Lx, WS_MAX_B);
    uint8_t w = (uint8_t)cie_scale(Lx, WS_MAX_W);
    ws_send_rgbw(r, g, b, w);
}

#endif /* NIGHTLIGHT_H */
