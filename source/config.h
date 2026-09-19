/*
 * config.h — LightBox 設定 (ユーザが触るのはこのファイル)
 *
 *  ここに全チューニング値を集約。lightbox.c は本ヘッダを include するだけ。
 *  (ch32fun 本体の設定は別ファイル funconfig.h)
 */
#ifndef CONFIG_H
#define CONFIG_H

/* ===================== ピン割当 (SOP8 / J4M6) =====================
 * 「1機能=1ピンマクロ」で割当てる。port/ピン番号・ADCチャネル・PWMのタイマ/チャネル/remap は
 * pins.h が自動導出し、ハード能力違反(PWM/ADC不可ピン)とピン衝突(2機能が同一ピン)を
 * コンパイル時に _Static_assert で弾く。SOP8 実在ピン: PA1/PA2/PC1/PC2/PC4/PD1(SWIO予約)。
 *
 *   PWM_PIN 選択肢(正出力): PC2=TIM2_CH2(remap1,既定) / PC1=TIM2_CH4(remap1)
 *                          / PA1=TIM1_CH2 / PC4=TIM1_CH4
 *   ADC対応ピン(外付け温度): PA2/PA1/PC4 のみ
 *   ※PC2 以外の PWM は「コンパイル対応・実機未検証」。既定(PC2)は従来と挙動不変。 */
#define PWM_PIN      PC2     /* 本体LED PWM 出力 (既定 PC2=TIM2_CH2 remap1) */
#define ENC_A_PIN    PA1     /* エンコーダ A 相 (内部プルアップ, 共通=GND) */
#define ENC_B_PIN    PC1     /* エンコーダ B 相 (内部プルアップ, 共通=GND) */
#define ENC_SW_PIN   PA2     /* 押しSW (内部プルアップ, 押下=Low, 短押し=ON/OFF) */

/* ===================== PWM (16bit タイマ TIM2) =====================
 * TIM2 は 16bit カウンタ。PWM_TOP は最大 65535 まで設定可。
 *   周波数 = 48MHz / (PWM_TOP+1) / (PWM_PSC+1)
 *   分解能 = PWM_TOP+1 段
 * 既定 4095(12bit) → 約 11.7kHz: フリッカ/可聴音なし かつ 低輝度も細かい。
 * さらに滑らかにしたい場合 8191/16383 等へ上げる (周波数は下がる)。 */
#define PWM_TOP      4095u
#define PWM_PSC      0u

/* --- EMI 対策 (大電流LED時のノイズ低減) ---
 * PWM_SLEW_MHZ: PWM出力ピンのスルーレート(GPIO速度)。低いほどエッジが鈍り高調波が減る。
 *   2 / 10 / 30 のいずれか。大電流LEDは 2 推奨(ただし極端に低いと立上りが甘くなる)。
 * PWM_SPREAD_SPECTRUM: 1 で周期を微小ディザしてスペクトラム拡散(基本波のピークを分散)。
 *   duty比は保つので明るさは不変。PWM_SPREAD_RANGE は ATRLR の振り幅(±カウント)。
 *   PWM_SPREAD_PERIOD_MS 間隔で更新。 */
#define PWM_SLEW_MHZ         2
#define PWM_SPREAD_SPECTRUM  0
#define PWM_SPREAD_RANGE     192u    /* ±この分 ATRLR を振る(≈±%スパンで周波数拡散) */
#define PWM_SPREAD_PERIOD_MS 2u      /* ディザ更新間隔 */

/* ===================== 調光段数 ===================== */
#define LEVELS       64u     /* 0(消灯) .. LEVELS(最大) の段数 */
#define LEVEL_INIT   32u     /* 起動時レベル */

/* ===================== 調光カーブ (CIE 1931 L*: 人間の知覚を最優先で再現) =====
 * 目的: 「1クリック=知覚的に等量の明るさ変化」。人間の明るさ知覚(明度)は輝度に対し
 *       べき乗則(≒立方根)で、暗部は緩やか・明部は急峻。これを標準化したのが CIE L*。
 *   段 i を明度 L*(0..100) に等間隔で割り当て、L* → 相対輝度 Y → duty へ逆算:
 *       L* <= 8 : Y = L* / 903.3                (真っ暗付近の直線トウ)
 *       L* >  8 : Y = ((L* + 16) / 116)^3       (べき乗則)
 *       duty = PWM_TOP * Y
 *   → 暗い時はゆっくり、明るい時は急激に、が"目にとって均等な"配分で実現。
 *   純 log/指数テーパより実際の知覚(特に暗部)に忠実。曲線は固定(パラメータ不要)。
 * ※整数演算のみ(立方は起動時に uint64 で計算)。libm/float 不使用。
 *   実行中は uint16 テーブル参照のみ。 */
/* (この節に調整パラメータは無い ─ CIE L* は知覚均等になるよう定義された曲線そのもの) */

/* ===================== エンコーダ (CW/CCW 2タイプ対応) =====================
 * 品種・配線で回転方向が逆になる。ENC_REVERSE で入れ替え可 (半田付けし直し不要)。
 *   0: 時計回り(CW)で明るく / 反時計回り(CCW)で暗く
 *   1: 反時計回り(CCW)で明るく / 時計回り(CW)で暗く
 * ENC_HALF_STEP: ディテントの数え方。
 *   0: フルステップ = 1クリック(4遷移)で1段 … 一般的なディテント付きエンコーダ
 *   1: ハーフステップ = 半クリック(2遷移)で1段 … 1周に倍のディテントを持つ品/半段品 */
#define ENC_REVERSE     0
#define ENC_HALF_STEP   0

/* エンコーダ加速: 同方向を速く回すと 1クリックのステップを ×ENC_ACCEL_FACTOR 段に。
 * 前クリックから ENC_ACCEL_WINDOW_MS 未満なら加速。0 相当にしたい場合は ENABLE=0。
 * (さらに速い"勢い回し"は SNAP で 100%/0% になる。加速はその手前の中速域。) */
#define ENC_ACCEL_ENABLE    1
#define ENC_ACCEL_FACTOR    3       /* 速回し時の1クリックのステップ倍率(=現在の3倍) */
#define ENC_ACCEL_WINDOW_MS 50u     /* 前クリックからこの時間未満なら加速 */

/* ===================== チャタリング(バウンス)吸収 =====================
 * 単純 delay ではなく「状態機械 + 時間ロックアウト」で構造的に吸収する。
 *   (1) フルステップ状態機械(Ben Buxton方式): ディテント間の正しい遷移経路を
 *       完走したときだけ 1 ステップ発火。途中のバウンスは中間状態と静止位置を
 *       往復するだけで発火しない → 単相バウンスを構造的に無視。
 *   (2) 方向反転ロックアウト: 直前ステップと"逆方向"のステップが ENC_REVERSAL_LOCK_MS
 *       以内に来たら、物理的にあり得ない=バウンスとして破棄。
 *       (人が実際にディテントを逆回しするには数十 ms 以上かかる。5ms は安全に短い。)
 *       同方向は制限しない → 高速回転(スナップ)を妨げない。
 * ※詳細と代替案(定周期サンプリング等)の検討は documents/DESIGN.md 参照。 */
#define ENC_REVERSAL_LOCK_MS  5u    /* この時間以内の"逆方向"ステップはバウンス扱いで無視 */

/* ===================== 高速回しスナップ =====================
 * SNAP_WINDOW_MS(=n ms) 以内に同方向へ SNAP_CLICKS(=j回) 以上のクリックが
 * 起きたら、明るい方向なら duty=100%(全開)、暗い方向なら duty=0%(消灯) に即セット。
 *   「勢いよく回す」= 一気に最大/最小、のショートカット。 */
#define SNAP_WINDOW_MS   500u    /* n ms (0.5秒) */
#define SNAP_CLICKS      6u      /* j 回 (2以上) */

/* ===================== 押しSW デバウンス (時間ベース積分) =====================
 * ループ回数依存の粗いカウンタではなく、SysTick 実時間で「安定してこの時間 Low を
 * 維持したら押下確定」。放し側も同様に安定を要求してチャタを吸収する。 */
#define SW_DEBOUNCE_MS   20u

/* ===================== 動作オプション =====================
 * WAKE_ON_TURN: 消灯(OFF)中にエンコーダを回したら自動で点灯(ON)に復帰する。
 *   ただし"明るさが出る"操作(結果 level>0)のときだけ点灯する。level=0 のまま暗方向へ
 *   回した場合は消灯のまま(g_on を立てない)＝「回したのに点かず on だけ立つ」不整合を防ぐ。
 *   0 にすると OFF 中の回転は隠れた level のみ変え、点灯は押しSW でのみ行う。
 * DEBUG_LOG: 1 で SWD(WCH-LinkE) 経由の printf ログを出す(開発時のみ)。
 *   ★通常運用は 0 を厳守。printf はブロッキングで、ホスト未接続だとバッファ満杯で
 *     処理が停滞し、高速回転時のエンコーダ遷移取りこぼし/スナップ不発を招く。 */
#define WAKE_ON_TURN     1
#define DEBUG_LOG        0

/* ===================== PWM ON 時間 (自動消灯タイマ) =====================
 * PWM_ON_TIME_S: 点灯(PWM出力ON)してからこの秒数が経過したら自動で消灯(ソフトオフ)する。
 *   単位=秒。0 で無効(自動消灯しない=従来どおり点けっぱなし)。
 *   操作(エンコーダ回転/押しSW ON)があるたびにカウントはリセット(=延長)される。
 *   自動消灯後は常夜灯が有効なら常夜灯へ移行する。
 * ★狙い: 常時電源でも「切り忘れ防止/自動消灯」の付加価値。バッテリ駆動では平均点灯時間を
 *   下げて電池寿命を延ばす(バッテリモードの有無に関係なく効く)。SysTick ラップ非依存で計時。 */
#define PWM_ON_TIME_S    0u        /* 0=無効 / 例: 1800 で 30 分後に自動消灯 */

/* ===================== ソフトスタート (フェードイン/アウト) =====================
 * 電源(押しSW)ON/OFF 時に、設定輝度まで滑らかに点灯/消灯させる演出。
 * 明るさの変化は本体と同じ CIE L*(人間の知覚)基準で補間するので、目で見て均等に変化する。
 *   SOFT_START_ON : 点灯(0→設定輝度)にかける時間(ms)。0=無効(即時)。最大 65535(≒65.5秒)。
 *   SOFT_START_OFF: 消灯(設定輝度→0)にかける時間(ms)。0=無効(即時)。最大 65535。
 * ※起動時(電源投入)も、復元した輝度へ SOFT_START_ON でフェードインする。
 * ※フェード中に再トグル/回転しても、現在の明るさから滑らかに目標へ追従(ジャンプしない)。
 * ※押しSWのチャタリングは時間ベースデバウンス(SW_DEBOUNCE_MS)で吸収済み。 */
#define SOFT_START_ON    800u      /* n ms (点灯) */
#define SOFT_START_OFF   600u      /* n ms (消灯) */

/* ===================== 明るさ不揮発ストア (ウェアレベリング) =====================
 * 電源再投入後に最後の明るさを復元する。フラッシュには消去回数寿命があるため、
 * 「同じ番地を毎回消去→書込」ではなく、消去済み領域へ 2バイトずつ"追記"し、
 * 満杯になったときだけ消去する(循環)ことで書込を分散し寿命を延ばす。
 *   ・末尾 STORE_SIZE_BYTES を専用領域として予約(1KB整列の const 配列で確保)。
 *   ・1レコード=2バイト → 2KB で 1024 レコード。1消去あたり 1024 回保存できる
 *     = 消去回数を約 1/1024 に削減 → フラッシュ寿命 約1024倍。
 *   ・保存タイミング: 明るさが「変わらなくなってから STORE_COMMIT_MS 後」に1回だけ。
 *     回すたびには書かない(操作1セッション=1書込) → 実書込も激減。
 *   ・初回(領域が空=全0xFFFF): 明るさ 0 で起動。
 * 方式の詳細は documents/DESIGN.md 参照。 */
#define STORE_ENABLE      1
#define STORE_SIZE_BYTES  2048u    /* 予約サイズ。1KBの倍数。2KB=1024レコード */
#define STORE_COMMIT_MS   5000u    /* 値が落ち着いてからこの時間後に書込 (5秒) */

/* ===================== 温度保護 (過熱フェイルセーフ) =====================
 * 過熱時に本体PWM(MOSFET)を強制的に 0 にして火災リスクを回避し、下がれば再開する。
 * 温度源は 2 種から選択(TEMP_SOURCE):
 *
 *  (A) TEMP_SOURCE_DIE  … ダイ温度「推定」(外付けピン不要, SOP8向き)
 *      ★CH32V003 に内蔵ダイ温度センサは無いため、実測ではなく熱モデルで推定する。
 *        無負荷時を DIE_AMBIENT_C(既定28℃)とし、PWMデューティ由来の自己発熱で
 *        どれだけ上がるかを1次熱モデルで足す:
 *          T_target = AMBIENT + (duty/PWM_TOP) * DIE_RISE_AT_FULL_C
 *          T_est    は時定数 DIE_TAU_MS で T_target に追従
 *        「おおよその値でよい/28℃からの上昇分」という用途に合致。値は目安。
 *
 *  (B) TEMP_SOURCE_EXTERNAL … 外付けアナログ温度センサ(NTC等)を MOSFET に熱結合し
 *      ADC(TEMP_SENSE_PIN=ADC対応ピン。chは自動導出)で実測。SOP8ではピンが要る(常夜灯/警告灯と排他)。
 *      近似1次校正 TEMP_CAL_*(要実機校正)。
 *
 *  共通: WARN_TEMP_C 超で遮断、WARN_TEMP_HYST_C 下がって再開。
 *  サーマルスロットル: 遮断→再開を THERMAL_TRIP_N 回繰り返したら、以後 THERMAL_THROTTLE_PCT %
 *    ずつ最大出力を下げて運転(N回ごとに累積, 上限あり)。n,j を config 指定。 */
/* ★ダイ温度(推定)は誤過熱カットの元なので既定オフ。実測NTCで本当に保護したい場合のみ
 *   TEMP_PROTECT_ENABLE=1 かつ TEMP_SOURCE=TEMP_SOURCE_EXTERNAL にする。 */
#define TEMP_PROTECT_ENABLE   0

#define TEMP_SOURCE_DIE       0
#define TEMP_SOURCE_EXTERNAL  1
#define TEMP_SOURCE           TEMP_SOURCE_EXTERNAL /* 有効化するなら実測NTCを推奨(ダイ推定は不採用) */

/* --- (A) ダイ温度推定モデル --- */
#define DIE_AMBIENT_C         28       /* 無負荷時の基準温度(℃) */
/* ★DIE推定は「実測でなくデューティ由来の推定」。全開時の推定温度 = AMBIENT + RISE_AT_FULL。
 *   これが WARN_TEMP_C を超えると、実際に熱くなくても点けっぱなしで自動遮断(誤動作)になる。
 *   既定は 28+45=73℃ < 80℃ で通常運用は遮断しない。実機の発熱に合わせて調整し、
 *   本当の過熱保護が要るなら TEMP_SOURCE_EXTERNAL(実測NTC)を使うこと。 */
#define DIE_RISE_AT_FULL_C    45       /* デューティ100%連続時の推定上昇(℃)。28+45=73<80で誤遮断せず */
#define DIE_TAU_MS            30000u   /* 熱時定数(ms) 大きいほどゆっくり上下 */

/* --- (B) 外付けセンサ --- ADCチャネルは TEMP_SENSE_PIN から pins.h が自動導出。
 *   ADC対応ピン: PA2(ch0)/PA1(ch1)/PC4(ch2)。SOP8で使うなら常夜灯/警告灯と排他(衝突検出あり)。 */
#define TEMP_SENSE_PIN        PC4      /* 外付けNTC等のアナログ入力ピン (ADC対応ピンのみ) */
#define TEMP_CAL_T0_C         25
#define TEMP_CAL_ADC0         512      /* T0_C 時の生ADC(10bit) ※要校正 */
#define TEMP_CAL_SLOPE_X100  (-300)    /* ADC/℃ ×100 (符号付, 0不可) ※要校正 */

/* --- 共通しきい値・保護 --- */
#define WARN_TEMP_C           80       /* 遮断オン閾値(℃) */
#define WARN_TEMP_HYST_C      8        /* この分下がると再開 */
#define WARN_TEMP_PERIOD_MS   200u     /* サンプリング/更新周期 */
#define THERMAL_TRIP_N        3u       /* 遮断→再開をこの回数繰り返したらスロットル発動(n) */
#define THERMAL_THROTTLE_PCT  20u      /* スロットル時の出力ダウン量(%)(j, N回ごとに累積) */
#define THERMAL_THROTTLE_MAX  80u      /* ダウンの上限(%)。これ以上は下げない */

/* --- 警告灯(任意) --- SOP8では空きピンが PC4 のみ。常夜灯や外付けセンサと排他。 */
#define WARN_LED_ENABLE       0        /* 1 で過熱中に WARN_LED_PIN を High */
#define WARN_LED_PIN          PC4

/* ===================== WS2812 / SK6812 常夜灯 =====================
 * 本体が「完全に暗い(明るさ0)」ときだけ、常夜灯を n 秒周期で じわっと明→暗を
 * 繰り返す(明るさ変化は本体と同じ CIE L* 基準)。1周期の後、i 秒 完全オフの
 * インターバルを置いてから次の周期へ。本体を明るくすると常夜灯は消える。
 *   ・NIGHTLIGHT_PERIOD_MS(n): 明→暗 1往復の時間。
 *   ・NIGHTLIGHT_INTERVAL_MS(i): 周期の後に完全オフする時間。
 *   ・WS_MAX_COLOR: 最大時の色。RGB品は 0xRRGGBB / RGBW品(SK6812)は 0xRRGGBBWW。
 *   ・WS_ORDER: チップの実バイト並び(製品で異なる)。GRB/RGB/GRBW/RGBW から選ぶ。
 *   ・WS_DIN_PIN: データ線のGPIO(1ピンマクロ。port/番号は pins.h が自動導出)。 */
#define NIGHTLIGHT_ENABLE     1
#define WS_DIN_PIN            PC4    /* データ線GPIO (SOP8既定: PC4)。任意のSOP8ピン可 */
#define WS_COUNT              1      /* LED 個数 */
#define NIGHTLIGHT_PERIOD_MS  4000u  /* n: 明→暗 1往復(ms) */
#define NIGHTLIGHT_INTERVAL_MS 3000u /* i: 周期後の完全オフ(ms) */
#define NIGHTLIGHT_REFRESH_MS 30u    /* 送信リフレッシュ間隔(滑らかさ) */
/* 起動セルフテスト: 電源投入直後、常夜灯LEDを WS_MAX_COLOR で NIGHTLIGHT_BOOT_TEST_MS だけ点灯。
 * 配線/タイミングの切り分け用(0=無効)。常夜灯は普段「本体が完全に暗い時」だけ点くので、
 * これが点けば配線OK=あとは本体を消灯すれば呼吸する。点かなければ配線/電圧/タイミングを疑う。 */
#define NIGHTLIGHT_BOOT_TEST_MS 1500u

/* チップのバイト並び。RGBW系(末尾W)は SK6812RGBW など。 */
#define WS_ORDER_GRB   0
#define WS_ORDER_RGB   1
#define WS_ORDER_GRBW  2
#define WS_ORDER_RGBW  3
/* 接続LED: OptoSupply OST45050C1A-W (インテリジェント制御RGBW, 1データ線/NZR, RGBW-8888)。
 * データシート送出順 = R7..R0 → G → B → W7..W0 = RGBW順(各バイトMSB先行)。 */
#define WS_ORDER       WS_ORDER_RGBW

/* 最大時の色。WS_ORDER が RGBW系(GRBW/RGBW)なら 0xRRGGBBWW、そうでなければ 0xRRGGBB。
 * ※桁数を WS_ORDER に合わせること(RGB=6桁 / RGBW=8桁)。RGBW順なので8桁。 */
#define WS_MAX_COLOR   0x40300810u   /* R=0x40 G=0x30 B=0x08 W=0x10 (電球色寄り, 常夜灯) ※好みで調整 */

/* ===================== ウォッチドッグ (IWDG) =====================
 * 独立ウォッチドッグ。ファームが固まったら自動リセットして復帰する安全装置。
 * メインループで自動的に定期リフレッシュ。タイムアウトは最悪ブロック(フラッシュ書込 数ms 等)
 * より十分長くすること。LSI(≈128kHz)駆動で誤差あり=概算。 */
#define WDT_ENABLE       1
#define WDT_TIMEOUT_MS   2000u   /* この時間リフレッシュされないとリセット */

#endif /* CONFIG_H */
