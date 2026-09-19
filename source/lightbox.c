/*
 * lightbox.c — CH32V003J4M6 (SOP8) LED 調光コントローラ
 *
 *  ・入力 : ローカル接続のロータリーエンコーダ (A/B 直交 + 押しSW)
 *  ・出力 : シングル PWM (LED 1ch)  — 16bit タイマ TIM2_CH2 / PC2
 *  ・調光 : CIE 1931 L*(明度) → 1クリック=知覚的に等量の明るさ変化 (人間の目を再現)
 *  ・機能 : 高速回しで duty=100%/0% スナップ / CW・CCW 両対応
 *
 *  全チューニング値は config.h に集約。整数演算のみ (CH32V003 は FPU 無し。
 *  LUT の立方項は起動時に uint64 で計算。libm/float には依存しない)。
 *
 *  ── SOP8 (J4M6) ピン割当 ── ※PD1 は SWIO(書込/printf) のため温存
 *   pin1 PA1 : ENC_A        内部プルアップ, 共通=GND
 *   pin2 VSS : GND
 *   pin3 PA2 : ENC_SW       内部プルアップ, 押下=Low (短押し=ON/OFFトグル)
 *   pin4 VDD : 3.3V
 *   pin5 PC1 : ENC_B        内部プルアップ, 共通=GND
 *   pin6 PC2 : PWM 出力(LED) TIM2_CH2 remap1
 *   pin7 PC4 : WS2812/SK6812 常夜灯データ (SOP8で唯一の空きピン。用途は排他で選択)
 *   pin8 PD1 : SWIO
 *
 *  SOP8 でも全機能が載る構成:
 *   ・温度=ダイ推定(熱モデル,ピン不要) / 過熱遮断・スロットル(PWMに作用,ピン不要)
 *   ・ウォッチドッグ(IWDG,ピン不要) / EMI対策(スルーレート・スペクトラム拡散,ピン不要)
 *   ・PC4 は 常夜灯 / 外付け温度センサ / 警告灯 のいずれか1つに割当(排他)。
 */
#include "ch32fun.h"
#include "config.h"
#include "pins.h"      /* 機能↔ピンの導出・能力/衝突のコンパイル時検証 */
#include "cie.h"
#if STORE_ENABLE
#include "store.h"
#endif
#if TEMP_PROTECT_ENABLE
#include "temp.h"
#endif
#if WDT_ENABLE
#include "wdt.h"
#endif
#if NIGHTLIGHT_ENABLE
  #if NIGHTLIGHT_TYPE == NL_TYPE_WS2812
    #define WS2812BSIMPLE_IMPLEMENTATION
    #include "nightlight.h"          /* ①WS2812/SK6812(従来・演出優先) */
  #else
    #include "nl_single.h"           /* ②単色LED(専用ピン, n秒/x ms/PWM有無) */
  #endif
#endif
#if LOW_POWER_MODE
  #include "lowpower.h"              /* 深い低電力(Standby+AWU)常夜灯 ★実験的 */
#endif

/* デバッグログ: 既定OFF。printf はブロッキングでホスト未接続だと停滞し、
 * ホットループ内では高速回転の遷移取りこぼし/スナップ不発を招くため通常は無効。 */
#if DEBUG_LOG
#include <stdio.h>
#define DBG(...)  printf(__VA_ARGS__)
#else
#define DBG(...)  ((void)0)
#endif

/* config の別名 (ピンは funPinMode 等へそのまま渡す) */
#define ENC_A   ENC_A_PIN
#define ENC_B   ENC_B_PIN
#define ENC_SW  ENC_SW_PIN

/* ---- 設定値の範囲チェック(堅牢性) ---- */
_Static_assert(LEVELS >= 1u && LEVELS <= 127u,
               "LEVELS は 1..127 (0=除算不能, 127=ストアの7bit level上限)");
_Static_assert(PWM_TOP >= 1u && PWM_TOP <= 65535u, "PWM_TOP は 1..65535 (16bit)");
_Static_assert(LEVEL_INIT <= LEVELS, "LEVEL_INIT は LEVELS 以下");
_Static_assert(SNAP_CLICKS >= 2u, "SNAP_CLICKS は 2 以上");

static uint8_t  g_level = LEVEL_INIT;   /* 現在の調光レベル 0..LEVELS */
static uint8_t  g_on    = 1;            /* ソフト ON/OFF */

/* PWM出力の実効デューティ(PWM_TOP基準)。スペクトラム拡散で ATRLR が動くため保持。 */
static uint16_t g_curDuty = 0;

#if TEMP_PROTECT_ENABLE
static uint8_t  g_thermCut  = 0;        /* 過熱で出力遮断中か */
static uint8_t  g_tripCount = 0;        /* 遮断→再開の回数 */
static uint8_t  g_deratePct = 0;        /* サーマルスロットルの累積ダウン率(%) */
#endif

/* PWM_SLEW_MHZ(2/10/30) → GPIO速度マクロ */
#if   PWM_SLEW_MHZ == 2
  #define PWM_GPIO_SPEED  GPIO_Speed_2MHz
#elif PWM_SLEW_MHZ == 30
  #define PWM_GPIO_SPEED  GPIO_Speed_30MHz
#else
  #define PWM_GPIO_SPEED  GPIO_Speed_10MHz
#endif

/* g_curDuty(PWM_TOP基準) を現在の周期(ATRLR)にスケールして選択チャネルの CVR に書く。
 * タイマ/チャネルは PWM_PIN から pins.h が導出 (既定 PC2=TIM2_CH2)。 */
static inline void pwm_write(void)
{
    uint32_t arr = PWM_TIMREG->ATRLR;         /* スペクトラム拡散で PWM_TOP から変動しうる */
    PWM_CVR = (uint32_t)g_curDuty * (arr + 1u) / (PWM_TOP + 1u);
}

/* ---- 表示輝度 (L*空間) とソフトスタート(フェード)状態 ----
 * g_dispLx = 実際に表示中の明度 L*(x100, 0..10000)。ON/OFF はこれをフェードさせる。
 * duty は cie_scale(g_dispLx) で算出 → フェードも人間の知覚で均等。 */
static uint16_t g_dispLx  = 0;            /* 現在表示中の L* */
static uint16_t g_fadeFrom = 0, g_fadeTo = 0;
static uint32_t g_fadeStart = 0;
static uint16_t g_fadeDurMs = 0;          /* 0 = フェードなし(停止) */

#if PWM_ON_TIME_S > 0
/* 自動消灯タイマ: 点灯からの経過を1秒刻みで数える(SysTick ラップ非依存)。
 * 操作のたびに arm() でリセット(=延長)。PWM_ON_TIME_S 秒到達で g_on=0 → ソフトオフ。 */
static uint32_t g_onSecTick = 0;          /* 1秒刻みの基準 tick */
static uint32_t g_onSeconds = 0;          /* 点灯開始/最終操作からの経過秒 */
static inline void autooff_arm(void) { g_onSeconds = 0; g_onSecTick = SysTick->CNT; }
#else
static inline void autooff_arm(void) {}   /* 無効時は何もしない */
#endif

/* 目標 L*: ON なら現在レベルの L*, OFF なら 0 */
static inline uint16_t target_lx(void)
{
    return g_on ? (uint16_t)(10000u * g_level / LEVELS) : 0u;
}

/* g_dispLx から duty を算出し、保護を適用して PWM へ書く */
static void output_refresh(void)
{
    uint16_t duty = cie_scale(g_dispLx, PWM_TOP);
#if TEMP_PROTECT_ENABLE
    if (g_thermCut) {
        duty = 0;                             /* 過熱: 強制遮断(火災リスク回避) */
    } else if (g_deratePct) {
        duty = (uint16_t)((uint32_t)duty * (100u - g_deratePct) / 100u); /* スロットル */
    }
#endif
    g_curDuty = duty;
    pwm_write();
}

/* 即時に目標輝度へ(フェードなし) */
static void output_immediate(void)
{
    g_fadeDurMs = 0;
    g_dispLx = target_lx();
    output_refresh();
}

/* 目標変更(レベル変更/スナップ時): フェード中なら目標だけ差し替え、非フェード中は即時 */
static void output_retarget(void)
{
    if (g_fadeDurMs) { g_fadeTo = target_lx(); g_fadeFrom = g_dispLx; g_fadeStart = SysTick->CNT; }
    else             { output_immediate(); }
}

/* ソフトスタート開始(ON/OFFトグル/起動時)。dur_ms=0 なら即時。 */
static void output_begin_fade(uint16_t dur_ms)
{
    uint16_t tgt = target_lx();
    if (dur_ms == 0 || tgt == g_dispLx) { output_immediate(); return; }
    g_fadeFrom  = g_dispLx;
    g_fadeTo    = tgt;
    g_fadeDurMs = dur_ms;
    g_fadeStart = SysTick->CNT;
    output_refresh();
}

/* メインループで毎回呼ぶ: フェード進行(L*線形補間→CIE) */
static void fade_tick(void)
{
    if (!g_fadeDurMs) return;
    uint32_t el_ms = (uint32_t)(SysTick->CNT - g_fadeStart) / Ticks_from_Ms(1);
    uint16_t nl;
    if (el_ms >= g_fadeDurMs) { nl = g_fadeTo; g_fadeDurMs = 0; }  /* 完了 */
    else {
        int32_t span = (int32_t)g_fadeTo - (int32_t)g_fadeFrom;
        nl = (uint16_t)((int32_t)g_fadeFrom + span * (int32_t)el_ms / (int32_t)g_fadeDurMs);
    }
    if (nl != g_dispLx) { g_dispLx = nl; output_refresh(); }
}

/* PWM_PIN を 16bit PWM 出力に設定 (config: PWM_TOP/PWM_PSC)。
 * タイマ/チャネル/remap/GPIO は PWM_PIN から pins.h が導出。
 * 既定(PWM_PIN=PC2=TIM2_CH2 remap1)は従来と同一のレジスタ操作を生成する(挙動不変)。
 * ★PC2 以外(TIM1/別チャネル)はコンパイル対応・実機未検証。 */
static void pwm_init(void)
{
    RCC->APB2PCENR |= PWM_RCC_GPIO_BIT | RCC_APB2Periph_AFIO;
#if PWM_USES_TIM1
    RCC->APB2PCENR |= RCC_APB2Periph_TIM1;
#else
    RCC->APB1PCENR |= RCC_APB1Periph_TIM2;
#endif

    /* PWM_PIN が出るように remap を設定 (対象タイマの remap ビットのみ更新) */
    AFIO->PCFR1 &= ~PWM_REMAP_CLEAR;
    AFIO->PCFR1 |=  PWM_REMAP_SET;

    /* PWM_PIN = 代替機能プッシュプル出力。EMI対策でスルーレート(GPIO速度)を config 化 */
    PWM_GPIO_PORT->CFGLR &= ~(0xf << (4 * PWM_PINNUM));
    PWM_GPIO_PORT->CFGLR |=  (PWM_GPIO_SPEED | GPIO_CNF_OUT_PP_AF) << (4 * PWM_PINNUM);

    /* タイマ リセット */
#if PWM_USES_TIM1
    RCC->APB2PRSTR |=  RCC_APB2Periph_TIM1;
    RCC->APB2PRSTR &= ~RCC_APB2Periph_TIM1;
#else
    RCC->APB1PRSTR |=  RCC_APB1Periph_TIM2;
    RCC->APB1PRSTR &= ~RCC_APB1Periph_TIM2;
#endif

    PWM_TIMREG->PSC   = PWM_PSC;
    PWM_TIMREG->ATRLR = PWM_TOP;                          /* 16bit カウンタ (最大65535) */
    PWM_CHCTLR |= PWM_OCM;                                /* 選択CH=PWM mode1 + preload */
    PWM_TIMREG->CCER |= PWM_CCE;                          /* 選択CH 出力有効 (正論理) */
    /* ARPE: ARR(周期)もプリロード化。動作中に ATRLR を変えても更新イベント境界で
     * 反映されるため、スペクトラム拡散のディザで周期が途中で化ける/グリッチを防ぐ。 */
    PWM_TIMREG->CTLR1 |= TIM_ARPE;
#if PWM_USES_TIM1
    PWM_TIMREG->BDTR  |= TIM_MOE;                         /* 高機能タイマ(TIM1)は主出力許可が必須 */
#endif
    PWM_TIMREG->SWEVGR = TIM_UG;                          /* シャドウ即反映(ARR/CVR ロード) */
    PWM_TIMREG->CTLR1 |= TIM_CEN;

    output_refresh();          /* 起動直後は g_dispLx=0(消灯) */
}

/* エンコーダ / SW を内部プルアップ入力に */
static void encoder_init(void)
{
    funGpioInitAll();
    funPinMode(ENC_A,  GPIO_CFGLR_IN_PUPD); funDigitalWrite(ENC_A,  FUN_HIGH);
    funPinMode(ENC_B,  GPIO_CFGLR_IN_PUPD); funDigitalWrite(ENC_B,  FUN_HIGH);
    funPinMode(ENC_SW, GPIO_CFGLR_IN_PUPD); funDigitalWrite(ENC_SW, FUN_HIGH);
}

/* ===================== エンコーダ: フルステップ状態機械 (Ben Buxton方式) =====
 * 単純 delay に頼らず、ディテント間の"正しい遷移経路"を完走したときだけ 1 ステップを
 * 発火する状態機械でチャタリングを構造的に吸収する。途中でバウンスしても中間状態と
 * 静止位置(11)を往復するだけで DIR は出ず、単相のバタつきを無視できる。
 *   ・列 index = 現在の2相 (A<<1)|B  … 静止=0b11
 *   ・戻り値下位: 次状態、bit4=DIR_CW / bit5=DIR_CCW を含む */
#define DIR_CW   0x10
#define DIR_CCW  0x20

#if ENC_HALF_STEP
/* ハーフステップ: 半クリック(2遷移)で1回発火 */
enum { H_START, H_CCW_BEGIN, H_CW_BEGIN, H_START_M, H_CW_BEGIN_M, H_CCW_BEGIN_M };
static const uint8_t ENC_TT[6][4] = {
    /* H_START      */ { H_START_M,           H_CW_BEGIN,    H_CCW_BEGIN,   H_START           },
    /* H_CCW_BEGIN  */ { H_START_M | DIR_CCW, H_START,       H_CCW_BEGIN,   H_START           },
    /* H_CW_BEGIN   */ { H_START_M | DIR_CW,  H_CW_BEGIN,    H_START,       H_START           },
    /* H_START_M    */ { H_START_M,           H_CCW_BEGIN_M, H_CW_BEGIN_M,  H_START           },
    /* H_CW_BEGIN_M */ { H_START_M,           H_START_M,     H_CW_BEGIN_M,  H_START | DIR_CW  },
    /* H_CCW_BEGIN_M*/ { H_START_M,           H_CCW_BEGIN_M, H_START_M,     H_START | DIR_CCW },
};
#else
/* フルステップ: 1クリック(4遷移)で1回発火 */
enum { F_START, F_CW_FINAL, F_CW_BEGIN, F_CW_NEXT, F_CCW_BEGIN, F_CCW_FINAL, F_CCW_NEXT };
static const uint8_t ENC_TT[7][4] = {
    /* F_START     */ { F_START,    F_CW_BEGIN,  F_CCW_BEGIN, F_START            },
    /* F_CW_FINAL  */ { F_CW_NEXT,  F_START,     F_CW_FINAL,  F_START | DIR_CW   },
    /* F_CW_BEGIN  */ { F_CW_NEXT,  F_CW_BEGIN,  F_START,     F_START            },
    /* F_CW_NEXT   */ { F_CW_NEXT,  F_CW_BEGIN,  F_CW_FINAL,  F_START            },
    /* F_CCW_BEGIN */ { F_CCW_NEXT, F_START,     F_CCW_BEGIN, F_START            },
    /* F_CCW_FINAL */ { F_CCW_NEXT, F_CCW_FINAL, F_START,     F_START | DIR_CCW  },
    /* F_CCW_NEXT  */ { F_CCW_NEXT, F_CCW_FINAL, F_CCW_BEGIN, F_START            },
};
#endif

static uint8_t g_encState = 0;   /* 状態機械の現在状態 (START = 0) */

/* A/B を1回読み、状態機械を1段進める。ディテント完成時のみ +1(CW)/-1(CCW) を返す(他は0)。 */
static int8_t enc_poll(void)
{
    uint8_t ab = (uint8_t)((funDigitalRead(ENC_A) << 1) | funDigitalRead(ENC_B));
    g_encState = ENC_TT[g_encState & 0x0f][ab];
    if (g_encState & DIR_CW)  return +1;
    if (g_encState & DIR_CCW) return -1;
    return 0;
}

/* ---- 高速回しスナップ: 同方向の直近 SNAP_CLICKS 回の時刻を保持 ---- */
static uint32_t g_snapTick[SNAP_CLICKS];
static uint8_t  g_snapIdx = 0;      /* 次に書く位置 = 最古の位置(満杯時) */
static uint8_t  g_snapCnt = 0;
static int8_t   g_snapDir = 0;

/* クリック(ディテント)1回を記録し、窓内 j 回以上なら 1 を返す */
static uint8_t snap_hit(int8_t dir, uint32_t now)
{
    if (dir != g_snapDir) { g_snapDir = dir; g_snapCnt = 0; g_snapIdx = 0; }

    g_snapTick[g_snapIdx] = now;
    uint8_t oldestIdx = (uint8_t)((g_snapIdx + 1u) % SNAP_CLICKS);
    g_snapIdx = oldestIdx;
    if (g_snapCnt < SNAP_CLICKS) g_snapCnt++;

    if (g_snapCnt >= SNAP_CLICKS) {
        uint32_t span = now - g_snapTick[oldestIdx];   /* 直近 j 回の所要時間(符号なし=ラップ安全) */
        if (span <= Ticks_from_Ms(SNAP_WINDOW_MS)) {
            g_snapCnt = 0; g_snapIdx = 0;               /* 連続スナップ抑止 */
            return 1;
        }
    }
    return 0;
}

#if PWM_SPREAD_SPECTRUM
/* スペクトラム拡散: ATRLR を PWM_TOP±PWM_SPREAD_RANGE で微小ディザ(周波数を分散)。
 * duty比は pwm_write が周期に追従させるので明るさは不変。
 * ★ATRLR は TIM2 の16bitレジスタ(最大65535)。PWM_TOP+j が 65536 以上になると
 *   切り詰めで 0 等に化けるので [1,65535] にクランプする。
 *   (設定段階の保証として下の _Static_assert も参照) */
static uint32_t g_lcg = 0x1234567u;
static void spread_tick(void)
{
    g_lcg = g_lcg * 1664525u + 1013904223u;              /* 簡易LCG */
    int32_t j   = (int32_t)(g_lcg % (2u * PWM_SPREAD_RANGE + 1u)) - (int32_t)PWM_SPREAD_RANGE;
    int32_t arr = (int32_t)PWM_TOP + j;
    if (arr > 65535) arr = 65535;                        /* 16bit ARR 上限 */
    if (arr < 1)     arr = 1;                            /* 下限(0除算/0周期回避) */
    PWM_TIMREG->ATRLR = (uint32_t)arr;
    pwm_write();                                          /* 新周期に CVR を再スケール */
}
/* 設定ミス防止: 拡散レンジを含めて 16bit(<=65535)に収め、下側も正に保つこと */
_Static_assert((uint32_t)PWM_TOP + PWM_SPREAD_RANGE <= 65535u,
               "PWM_TOP+PWM_SPREAD_RANGE が 65535 超(16bit ARRを超える)。PWM_TOP か RANGE を下げる");
_Static_assert(PWM_TOP > PWM_SPREAD_RANGE,
               "PWM_SPREAD_RANGE が PWM_TOP 以上(周期が0以下になりうる)");
#endif

#if TEMP_PROTECT_ENABLE
/* 過熱保護の状態機械: 遮断/再開/トリップ計数/サーマルスロットル */
static void thermal_protect(int16_t tC)
{
    if (!g_thermCut) {
        if (tC >= WARN_TEMP_C) {                          /* 過熱 → 強制遮断 */
            g_thermCut = 1;
            if (g_tripCount < 0xFF) g_tripCount++;
            /* THERMAL_TRIP_N 回ごとに j% 累積ダウン(上限 THERMAL_THROTTLE_MAX) */
            if (THERMAL_TRIP_N > 0 && (g_tripCount % THERMAL_TRIP_N) == 0) {
                uint16_t d = (uint16_t)g_deratePct + THERMAL_THROTTLE_PCT;
                if (d > THERMAL_THROTTLE_MAX) d = THERMAL_THROTTLE_MAX;
                g_deratePct = (uint8_t)d;
            }
            output_refresh();
            DBG("THERMAL CUT t=%dC trips=%u derate=%u%%\n", tC, g_tripCount, g_deratePct);
        }
    } else {
        if (tC <= (WARN_TEMP_C - WARN_TEMP_HYST_C)) {     /* 冷えた → 再開(スロットル適用) */
            g_thermCut = 0;
            output_refresh();
            DBG("THERMAL RESUME t=%dC derate=%u%%\n", tC, g_deratePct);
        }
    }
    temp_warn_led(g_thermCut);
}
#endif

int main(void)
{
    SystemInit();
    encoder_init();        /* funGpioInitAll を含む → 先に */
#if STORE_ENABLE
    {                                  /* 前回の ON/OFF + 明るさを復元(空=初回なら OFF/0) */
        uint16_t st = store_load();
        g_level = STORE_LEVEL(st);
        if (g_level > LEVELS) g_level = LEVELS;   /* 破損ガード */
        g_on = STORE_ON(st);
    }
#endif
    pwm_init();            /* g_dispLx=0(消灯)で起動 (PWM_PIN から導出) */
#if TEMP_PROTECT_ENABLE
    temp_init();
#endif
#if NIGHTLIGHT_ENABLE
  #if NIGHTLIGHT_TYPE == NL_TYPE_WS2812
    nightlight_init();
  #else
    nl_single_init();
  #endif
#endif
#if WDT_ENABLE
    wdt_init();            /* 全初期化後に開始 */
#endif

#if NIGHTLIGHT_ENABLE && (NIGHTLIGHT_TYPE == NL_TYPE_WS2812) && (NIGHTLIGHT_BOOT_TEST_MS > 0)
    /* 起動セルフテスト: 常夜灯LEDを一定時間点灯して配線/タイミングを切り分け */
    nightlight_test_on();
    {
        uint32_t t0 = SysTick->CNT;
        while ((uint32_t)(SysTick->CNT - t0) < Ticks_from_Ms(NIGHTLIGHT_BOOT_TEST_MS)) {
#if WDT_ENABLE
            wdt_feed();                    /* テスト中も WDT を維持 */
#endif
        }
    }
#endif

    /* 起動(電源投入): 復元輝度へソフトスタート・フェードイン */
    output_begin_fade(SOFT_START_ON);
    if (g_on) autooff_arm();               /* 点灯状態で起動したら自動消灯カウント開始 */

#if LOW_POWER_MODE
    /* 深い低電力: 起動時に押しSW非押下なら常夜灯スリープサイクルへ。
     * 押下(=起動時ホールド or 押しSW起水)なら通常アクティブモードに留まる(reflash-safe)。 */
    lp_init();
    if (!lp_button_down()) {
        lowpower_nightlight_cycle();       /* 押しSW起水で戻る(reset復帰時は main 再実行で同判断) */
        encoder_init();                    /* アナログ化したエンコーダピンを通常入力へ戻す */
        output_begin_fade(SOFT_START_ON);
    }
    uint32_t lpActivityTick = SysTick->CNT; /* アクティブ窓のアクティビティ計時 */
#endif

    DBG("LightBox CH32V003: CIE-L* dimmer  level=%u/%u top=%u rev=%u half=%u lock=%ums snap=%u/%ums\n",
        g_level, LEVELS, (unsigned)PWM_TOP,
        ENC_REVERSE, ENC_HALF_STEP, ENC_REVERSAL_LOCK_MS, SNAP_CLICKS, SNAP_WINDOW_MS);

    /* 方向反転ロックアウト用 */
    int8_t   lastDir      = 0;              /* 直前に"確定"した方向 (+1/-1) */
    uint32_t lastStepTick = SysTick->CNT;

    /* 押しSW 時間ベース・デバウンス */
    uint8_t  swStable   = 1;                /* 確定状態 (1=放し, 0=押下) */
    uint8_t  swCand     = 1;                /* 候補(生読み)状態 */
    uint32_t swCandTick = SysTick->CNT;     /* 候補が変わった時刻 */

#if STORE_ENABLE
    /* 不揮発ストア: 状態(ON/OFF+level)が STORE_COMMIT_MS 変化しなくなったら1回だけ書込 */
    uint16_t savedState = STORE_PACK(g_on, g_level);  /* 既にフラッシュにある状態 */
    uint8_t  dirty      = 0;                /* 未保存の変更あり */
    uint32_t dirtyTick  = 0;                /* 最後に変更した時刻 */
#endif
#if TEMP_PROTECT_ENABLE
    uint32_t tempTick   = SysTick->CNT;     /* 温度サンプリング周期用 */
#endif
#if PWM_SPREAD_SPECTRUM
    uint32_t spreadTick = SysTick->CNT;     /* スペクトラム拡散ディザ周期用 */
#endif

    for (;;) {
#if WDT_ENABLE
        wdt_feed();                         /* ループが回っている限りリセットしない */
#endif
        fade_tick();                        /* ソフトスタート進行 */
        /* --- ロータリー: 状態機械 → ディテント完成時のみ dir --- */
        int8_t dir = enc_poll();
        if (dir) {
#if ENC_REVERSE
            dir = (int8_t)-dir;                             /* CW/CCW 反転 (config) */
#endif
            uint32_t now = SysTick->CNT;

            /* 方向反転ロックアウト: 直前と逆方向が極短時間(既定5ms)に来たら
             * 物理的にあり得ない=バウンスとして破棄。同方向は制限しない。 */
            if (dir == -lastDir &&
                (uint32_t)(now - lastStepTick) < Ticks_from_Ms(ENC_REVERSAL_LOCK_MS)) {
                /* バウンス反転 → 無視 (状態は据置、lastStepTick も更新しない) */
            } else {
                uint32_t sinceLast = (uint32_t)(now - lastStepTick); /* 前クリックからの間隔 */
                int8_t   prevDir   = lastDir;
                lastDir = dir; lastStepTick = now;

                /* 加速: 同方向を速く連続で回したら 1クリック=±ENC_ACCEL_FACTOR 段 */
                uint8_t step = 1;
#if ENC_ACCEL_ENABLE
                if (dir == prevDir && sinceLast < Ticks_from_Ms(ENC_ACCEL_WINDOW_MS))
                    step = ENC_ACCEL_FACTOR;
#else
                (void)prevDir; (void)sinceLast;
#endif
                /* 先に新レベルを確定 */
                if (snap_hit(dir, now)) {
                    if (dir > 0) { g_level = LEVELS; g_on = 1; }  /* 全開 */
                    else         { g_level = 0;      }            /* 消灯(0%) */
                    DBG("SNAP %s -> level=%u duty=%u\n",
                        dir > 0 ? "MAX" : "MIN", g_level, cie_level(g_level, LEVELS, PWM_TOP));
                } else {
                    /* 通常: 1 クリック = ±step 段 (加速時 ×ENC_ACCEL_FACTOR)。エンコーダは即応 */
                    if (dir > 0) g_level = (g_level + step > LEVELS) ? (uint8_t)LEVELS
                                                                     : (uint8_t)(g_level + step);
                    else         g_level = (g_level > step)         ? (uint8_t)(g_level - step) : 0u;
                    DBG("level=%u/%u step=%u duty=%u\n", g_level, LEVELS, step,
                        cie_level(g_level, LEVELS, PWM_TOP));
                }
#if WAKE_ON_TURN
                /* OFF中に回したら点灯復帰。ただし"明るさが出る"操作(level>0)のときだけ。
                 * level==0 のまま(例: 消灯状態で更に暗方向)は g_on を立てない
                 * → 「回したのに点灯せず on だけ立つ」不整合を防ぐ。 */
                if (g_level > 0) g_on = 1;
#endif
                output_retarget();
                if (g_on) autooff_arm();           /* 操作があったら自動消灯カウントをリセット(延長) */
#if LOW_POWER_MODE
                lpActivityTick = now;              /* 操作 → アクティブ窓を延長 */
#endif
#if STORE_ENABLE
                dirty = 1; dirtyTick = now;        /* 変更 → 5秒後にコミット予約 */
#endif
            }
        }

        /* --- 押しSW: 時間ベース積分デバウンス (安定 SW_DEBOUNCE_MS で確定) --- */
        uint8_t swRaw = funDigitalRead(ENC_SW);        /* 押下=Low(0) */
        uint32_t nowSw = SysTick->CNT;
        if (swRaw != swCand) {                          /* 生読みが動いた→候補更新+計時 */
            swCand = swRaw; swCandTick = nowSw;
        } else if (swCand != swStable &&
                   (uint32_t)(nowSw - swCandTick) >= Ticks_from_Ms(SW_DEBOUNCE_MS)) {
            swStable = swCand;                          /* 安定継続→確定 */
            if (swStable == 0) {                        /* 確定した押下(立下り)でトグル */
                g_on = !g_on;
                output_begin_fade(g_on ? SOFT_START_ON : SOFT_START_OFF);  /* ソフトスタート */
                if (g_on) autooff_arm();                /* ONトグルで自動消灯カウント開始 */
#if LOW_POWER_MODE
                lpActivityTick = SysTick->CNT;          /* 押しSW操作 → アクティブ窓を延長 */
#endif
                DBG("output %s\n", g_on ? "ON" : "OFF");
#if STORE_ENABLE
                dirty = 1; dirtyTick = SysTick->CNT;    /* ON/OFF も保存対象 → コミット予約 */
#endif
            }
        }

#if STORE_ENABLE
        /* --- 不揮発コミット: 状態が STORE_COMMIT_MS 変化しなければ1回だけ書込 --- */
        if (dirty) {
            uint32_t nc = SysTick->CNT;
            if ((uint32_t)(nc - dirtyTick) >= Ticks_from_Ms(STORE_COMMIT_MS)) {
                uint16_t st = STORE_PACK(g_on, g_level);
                if (st != savedState) { store_save(st); savedState = st; }
                dirty = 0;
            }
        }
#endif

#if PWM_ON_TIME_S > 0
        /* --- 自動消灯タイマ: 点灯からの経過秒が PWM_ON_TIME_S に達したらソフトオフ ---
         * 1秒刻みで数えるので SysTick(32bit@48MHz, ~89秒周期)のラップに依存しない。 */
        if (g_on) {
            if ((uint32_t)(SysTick->CNT - g_onSecTick) >= Ticks_from_Ms(1000u)) {
                g_onSecTick += Ticks_from_Ms(1000u);        /* 1秒進める(ドリフト無し) */
                if (++g_onSeconds >= (uint32_t)PWM_ON_TIME_S) {
                    g_on = 0;
                    output_begin_fade(SOFT_START_OFF);      /* 自動消灯(ソフトオフ) */
                    DBG("AUTO-OFF after %us\n", (unsigned)PWM_ON_TIME_S);
#if STORE_ENABLE
                    dirty = 1; dirtyTick = SysTick->CNT;    /* OFF状態を保存予約 */
#endif
                }
            }
        }
#endif

#if TEMP_PROTECT_ENABLE
        /* --- 過熱保護 (周期サンプリング → 遮断/再開/スロットル) --- */
        if ((uint32_t)(SysTick->CNT - tempTick) >= Ticks_from_Ms(WARN_TEMP_PERIOD_MS)) {
            tempTick = SysTick->CNT;
            int16_t tc = temp_sample(g_curDuty);    /* DIEモデルは実効デューティで自己発熱推定 */
            thermal_protect(tc);
            DBG("temp=%dC cut=%u trips=%u derate=%u%%\n",
                tc, g_thermCut, g_tripCount, g_deratePct);
        }
#endif

#if PWM_SPREAD_SPECTRUM
        /* --- スペクトラム拡散: 周期ディザ --- */
        if ((uint32_t)(SysTick->CNT - spreadTick) >= Ticks_from_Ms(PWM_SPREAD_PERIOD_MS)) {
            spreadTick = SysTick->CNT;
            spread_tick();
        }
#endif

#if NIGHTLIGHT_ENABLE
        /* --- 常夜灯: 本体が完全に暗い(消灯意図かつフェード完了で輝度0)ときだけ動作 --- */
        {
            uint8_t nl_dark = ((!g_on || g_level == 0) && g_dispLx == 0) ? 1u : 0u;
  #if NIGHTLIGHT_TYPE == NL_TYPE_WS2812
            nightlight_update(nl_dark);      /* ①WS2812: 呼吸 */
  #else
            nl_single_update(nl_dark);       /* ②単色LED: 周期点灯 */
  #endif
        }
#endif

#if LOW_POWER_MODE
        /* --- アクティブ窓: 無操作が LOWPWR_ACTIVE_WINDOW_S 続き、本体消灯なら再びスリープへ --- */
        if (!g_on &&
            (uint32_t)(SysTick->CNT - lpActivityTick) >= Ticks_from_Ms((uint32_t)LOWPWR_ACTIVE_WINDOW_S * 1000u)) {
            lowpower_nightlight_cycle();     /* 押しSW起水で戻る */
            encoder_init();
            output_begin_fade(SOFT_START_ON);
            lpActivityTick = SysTick->CNT;
        }
#endif
    }
}
