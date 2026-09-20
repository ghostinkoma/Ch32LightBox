# LightBox 設定リファレンスガイド (config.h)

[English](CONFIG_REFERENCE.md) · **日本語** · [← README](../README_JP.md)

`source/config.h` の全項目リファレンス。値を変えたら再ビルド（[README](../README_JP.md) 参照）。
ピンは ch32fun のピン名（`PA1`, `PC2`, `PD0` …）。

## ピン割当（1機能=1ピンマクロ）

各機能は**単一のピンマクロ**で割当てる。`source/pins.h` が GPIOポート/番号・ADCチャネル・
PWMのタイマ/チャネル/remap を自動導出し、能力違反（PWM/ADC不可ピン）とピン衝突（2機能が
同一ピン）を**コンパイル時**に `_Static_assert` で弾く。SOP8(J4M6)実在ピン: `PA1 PA2 PC1 PC2 PC4 PD1(SWIO)`。

| 項目 | 既定 | 説明 |
|------|------|------|
| `PWM_PIN` | `PC2` | 本体LED PWM出力。PWM可能ピンのみ: `PC2`(TIM2_CH2)/`PC1`(TIM2_CH4)/`PA1`(TIM1_CH2)/`PC4`(TIM1_CH4)。**PC2=実機検証済の既定。他はコンパイル対応・実機未検証** |
| `ENC_A_PIN` | `PA1` | エンコーダ A 相（内部プルアップ） |
| `ENC_B_PIN` | `PC1` | エンコーダ B 相（内部プルアップ） |
| `ENC_SW_PIN` | `PA2` | 押しSW（内部プルアップ, 押下=Low） |

## PWM（16bit TIM2）

| 項目 | 既定 | 説明 |
|------|------|------|
| `PWM_TOP` | `4095` | 分解能-1。周波数=`48MHz/(TOP+1)/(PSC+1)`。最大 65535。上げると滑らか/低周波 |
| `PWM_PSC` | `0` | プリスケーラ |

## 調光段

| 項目 | 既定 | 説明 |
|------|------|------|
| `LEVELS` | `64` | 段数（0=消灯〜LEVELS=最大） |
| `LEVEL_INIT` | `32` | ストア無効時の起動レベル（`STORE_ENABLE=0` のときのみ有効） |

> 調光カーブ（CIE L*）は固定・パラメータ無し（知覚均等になるよう定義された曲線そのもの）。

## エンコーダ / チャタリング

| 項目 | 既定 | 説明 |
|------|------|------|
| `ENC_REVERSE` | `0` | 1 で CW/CCW 反転（品種・配線差を吸収） |
| `ENC_HALF_STEP` | `0` | 0=フルステップ(4遷移で1段) / 1=ハーフステップ(2遷移で1段) |
| `ENC_ACCEL_ENABLE` | `1` | 加速: 速く回すとステップ倍化 |
| `ENC_ACCEL_FACTOR` | `3` | 速回し時の1クリックのステップ倍率（=3倍） |
| `ENC_ACCEL_WINDOW_MS` | `50` | 前クリックからこの時間未満(同方向)なら加速 |
| `ENC_REVERSAL_LOCK_MS` | `5` | この時間内の「逆方向」ステップはバウンス扱いで無視。速い操作で取りこぼすなら短く、チャタが残るなら長く |
| `SW_DEBOUNCE_MS` | `20` | 押しSW 安定確定時間 |

## 高速回しスナップ

| 項目 | 既定 | 説明 |
|------|------|------|
| `SNAP_WINDOW_MS` | `500` | 判定の窓（n ms） |
| `SNAP_CLICKS` | `6` | 窓内で発火する同方向クリック数（j, 2以上） |

## 動作オプション

| 項目 | 既定 | 説明 |
|------|------|------|
| `WAKE_ON_TURN` | `1` | 消灯中に回したら自動点灯（結果 level>0 のときのみ。level=0のまま暗方向は消灯維持）。0 で点灯は押しSWのみ |
| `PWM_ON_TIME_S` | `0` | **自動消灯タイマ(秒)**: 点灯からこの秒数で本体出力を自動消灯(ソフトオフ)。`0`=無効(点けっぱなし)。エンコーダ/押しSW 操作でカウントはリセット(延長)。平均点灯時間↓＝バッテリ節約＋常時電源でも自動消灯の付加価値 |
| `SOFT_START_ON` | `800` | 点灯フェードイン時間(ms)。0=即時、最大65535(≒65.5s)。CIE基準で均等 |
| `SOFT_START_OFF` | `600` | 消灯フェードアウト時間(ms)。0=即時、最大65535 |
| `DEBUG_LOG` | `0` | 1 で SWD printf（開発時のみ）。**通常運用は0厳守**（ブロッキングで取りこぼし要因） |

## 明るさ不揮発ストア

| 項目 | 既定 | 説明 |
|------|------|------|
| `STORE_ENABLE` | `1` | 明るさ記憶（ウェアレベリング）。0 で無効 |
| `STORE_SIZE_BYTES` | `2048` | 予約する恒久フラッシュ量（**1KBの倍数**）。2KB=1024レコード。増やすと寿命↑ |
| `STORE_COMMIT_MS` | `5000` | 値が落ち着いてから保存するまでの待ち（n 秒） |

> 寿命 ≈ (STORE_SIZE_BYTES/2) × 消去寿命。2KBで約1000万回（SPEC.md 5章）。

## EMI 対策（PWM）

| 項目 | 既定 | 説明 |
|------|------|------|
| `PWM_SLEW_MHZ` | `2` | PWM出力ピンのスルーレート(GPIO速度) 2/10/30。低いほどエッジが鈍り高調波↓（大電流LEDは2推奨） |
| `PWM_SPREAD_SPECTRUM` | `0` | 1 で周期を微小ディザしスペクトラム拡散（明るさ不変） |
| `PWM_SPREAD_RANGE` | `192` | ATRLR の振り幅(±カウント)。**`PWM_TOP+RANGE≤65535` かつ `RANGE<PWM_TOP`** 必須（ATRLRは16bit。違反はコンパイル時アサートで検出、実行時も[1,65535]にクランプ） |
| `PWM_SPREAD_PERIOD_MS` | `2` | ディザ更新間隔 |

## 過熱フェイルセーフ

| 項目 | 既定 | 説明 |
|------|------|------|
| `TEMP_PROTECT_ENABLE` | **`0`** | 過熱保護（遮断＋スロットル）。**既定OFF**（ダイ推定は誤遮断のため不採用）。実測NTCで守る時のみ1 |
| `TEMP_SOURCE` | `TEMP_SOURCE_EXTERNAL` | 有効化時の温度源。**実測NTC(EXTERNAL)推奨**。DIE(推定)は誤遮断のため非推奨 |
| **[DIE]** `DIE_AMBIENT_C` | `28` | 無負荷時の基準温度(℃) |
| **[DIE]** `DIE_RISE_AT_FULL_C` | `60` | duty100%連続時の定常上昇(℃) ※目安, 実機の発熱に合わせ調整 |
| **[DIE]** `DIE_TAU_MS` | `30000` | 熱時定数(ms)。大きいほどゆっくり上下 |
| **[EXT]** `TEMP_SENSE_PIN` | `PC4` | アナログセンサ入力ピン。**ADC対応ピンのみ: `PA2`(ch0)/`PA1`(ch1)/`PC4`(ch2)**（ADCチャネルは自動導出） |
| **[EXT]** `TEMP_CAL_T0_C` | `25` | 校正基準温度 |
| **[EXT]** `TEMP_CAL_ADC0` | `512` | T0_C 時の生ADC（10bit）※要校正 |
| **[EXT]** `TEMP_CAL_SLOPE_X100` | `-300` | ADCカウント/℃ ×100（符号付, 0不可）※要校正 |
| `WARN_TEMP_C` | `80` | 遮断オン閾値(℃) |
| `WARN_TEMP_HYST_C` | `8` | この分下がると再開（0<HYST<WARN） |
| `WARN_TEMP_PERIOD_MS` | `200` | サンプリング/更新周期 |
| `THERMAL_TRIP_N` | `3` | 遮断→再開をこの回数繰り返したらスロットル発動(n) |
| `THERMAL_THROTTLE_PCT` | `20` | スロットル時の出力ダウン量(%)(j, N回ごと累積) |
| `THERMAL_THROTTLE_MAX` | `80` | ダウンの上限(%)（<100） |
| `WARN_LED_ENABLE` | `0` | 1 で過熱中に `WARN_LED_PIN` を High |
| `WARN_LED_PIN` | `PC4` | 警告灯ピン（任意のSOP8 GPIO。他機能との衝突はコンパイル時に検出） |

## ウォッチドッグ

| 項目 | 既定 | 説明 |
|------|------|------|
| `WDT_ENABLE` | `1` | IWDG 有効。ハング時に自動リセット |
| `WDT_TIMEOUT_MS` | `2000` | この時間リフレッシュ無しでリセット（約2〜8190ms） |

> ※`DEBUG_LOG=1` かつ SWD ホスト未接続だと printf 停滞で WDT リセットしうる（開発時の注意）。

### 外付けセンサ(EXTERNAL)の校正手順（近似1次）
`temp = TEMP_CAL_T0_C + (adc - TEMP_CAL_ADC0)*100 / TEMP_CAL_SLOPE_X100`
1. `DEBUG_LOG=1` でビルド・書込。`temp=..C` と一緒に…（生ADCを見たい場合は temp.h の
   `temp_update` で `adc` も出力するよう一時追記）。
2. 既知温度T1（例 室温25℃）で生ADCを記録 → `TEMP_CAL_ADC0`, `TEMP_CAL_T0_C` に。
3. 別温度T2（例 ドライヤ等で60℃）で生ADCを記録 → 傾き
   `TEMP_CAL_SLOPE_X100 = (adc2 - adc1)*100 / (T2 - T1)`。
4. `WARN_TEMP_C` を目標(80℃)に。NTCは非線形だが、動作点(80℃)付近で2点校正すれば警告用途に十分。
5. `DEBUG_LOG=0` に戻して本運用。

## 常夜灯

`NIGHTLIGHT_TYPE` で2種から選択: ①`NL_TYPE_WS2812`(カラー・演出優先=従来WS2812/SK6812) と
②`NL_TYPE_SINGLE`(専用ピンの単色LEDを `NL_PERIOD_S` 秒おきに `NL_ON_MS` 点灯)。
**②単色LED＋`NL_USE_PWM=0` のときだけ**下記の深い低電力(Standby)対象。

| 項目 | 既定 | 説明 |
|------|------|------|
| `NIGHTLIGHT_ENABLE` | `1` | 常夜灯機能 |
| `NIGHTLIGHT_TYPE` | `NL_TYPE_WS2812` | ①`NL_TYPE_WS2812`(カラー/演出) or ②`NL_TYPE_SINGLE`(単色LED/低電力) |
| **②** `NL_LED_PIN` | `PC4` | 単色LED専用ピン(任意SOP8 GPIO) |
| **②** `NL_PERIOD_S` | `5` | 点灯周期**秒**(`1..60`。深い低電力時 `1..30`) |
| **②** `NL_ON_MS` | `100` | 1回の点灯時間ms(`< NL_PERIOD_S*1000`) |
| **②** `NL_USE_PWM` | `0` | `0`=GPIO ON/OFF(最小電力/deep sleep可) / `1`=ソフトPWM明滅(演出/不可) |
| **①** `WS_DIN_PIN` | `PC4` | WS2812 データ線GPIO（任意のSOP8ピン。port/番号は自動導出） |
| `WS_COUNT` | `1` | LED 個数 |
| `NIGHTLIGHT_PERIOD_MS` | `4000` | n: 明→暗 1往復(ms, **2〜60000**)。上限は32bit SysTick(~89.5s)ラップ回避の安全マージン |
| `NIGHTLIGHT_INTERVAL_MS` | `3000` | i: 周期後の完全オフ(ms, **≤60000**) |
| `NIGHTLIGHT_REFRESH_MS` | `30` | 送信リフレッシュ間隔（滑らかさ） |
| `NIGHTLIGHT_BOOT_TEST_MS` | `1500` | 起動直後にWS LEDを設定色で点灯（配線/切り分け用, 0=無効） |
| `WS_ORDER` | `WS_ORDER_RGBW` | チップのバイト並び。`WS_ORDER_GRB/RGB/GRBW/RGBW`（既定は同梱SK6812 RGBWに一致） |
| `WS_MAX_COLOR` | `0x40300810` | 最大色。RGB系=**0xRRGGBB(6桁)** / RGBW系=**0xRRGGBBWW(8桁)** |

### 色順・RGBW の設定例
- 一般的 WS2812（GRB）: `#define WS_ORDER WS_ORDER_GRB` / `#define WS_MAX_COLOR 0x403008`
- RGB 順の製品: `#define WS_ORDER WS_ORDER_RGB`
- **SK6812 RGBW**: `#define WS_ORDER WS_ORDER_GRBW` / `#define WS_MAX_COLOR 0x40300810`
  （R=0x40 G=0x30 B=0x08 W=0x10）
- ※`WS_MAX_COLOR` の桁数は `WS_ORDER`（W有無）に必ず合わせること。

## 機能↔ピン割当（config.h のみ, SOP8 / J4M6）

各機能を config.h の単一ピンマクロで割当てる。`source/pins.h` が全割当をコンパイル時に検証する。
SOP8 のピン能力:

| ピン | GPIO | ADC ch | PWM経路(正出力) | 既定用途 |
|------|------|--------|------------------|----------|
| `PA1` | GPIOA/1 | ch1 | TIM1_CH2 | ENC_A |
| `PA2` | GPIOA/2 | ch0 | —（CH2N相補のみ） | ENC_SW |
| `PC1` | GPIOC/1 | — | TIM2_CH4 | ENC_B |
| `PC2` | GPIOC/2 | — | **TIM2_CH2** ★既定PWM | PWM |
| `PC4` | GPIOC/4 | ch2 | TIM1_CH4 | 常夜灯 / 温度 / 警告灯 |
| `PD1` | GPIOD/1 | — | — | **SWIO（書込/printf 予約）** |

コンパイル時に強制されるルール（GUI 設定エディタでも同様に検証）:
- **`PWM_PIN`** は PWM可能ピン（`PC2`/`PC1`/`PA1`/`PC4`）のみ。`PC2` は実機検証済、他はコンパイル対応のみ。
- **`TEMP_SENSE_PIN`**（外付け温度）は ADC対応ピン（`PA2`/`PA1`/`PC4`）のみ。ADCチャネルは自動導出。
- **有効な2機能が同一ピンを共有できない**（衝突→`_Static_assert` エラー）。従来の「PC4 排他（常夜灯/外付けセンサ/警告灯）」はこれに包含。
- **`PD1`** を機能に割当てると `#warning`（SWIO 書込/`debugprintf` と競合）。

※ソフトのみの機能（ダイ推定温度・過熱遮断/スロットル・ウォッチドッグ・EMIスペクトラム拡散）は**ピン不要**。
SOP8 で足りないほど多機能を同時に使う場合は TSSOP20/QFN(F4P6) 等へ（そのピン集合は本表の対象外）。

## 深い低電力モード (Standby + AWU) ★実験的・実機未検証

バッテリ向け。`LOW_POWER_MODE=1` で、単色LED常夜灯の点灯間は MCU を Standby にして AWU で周期起床。
押しSW(EXTI)を押すと一定時間 通常ディマーとして動作する。
設計根拠と LED ドライバの検討は [POWER_AND_DRIVER_JP.md](POWER_AND_DRIVER_JP.md)。

| 項目 | 既定 | 説明 |
|------|------|------|
| `LOW_POWER_MODE` | `0` | `1`=deep sleep 常夜灯。**要件**: `NIGHTLIGHT_TYPE=NL_TYPE_SINGLE`, `NL_USE_PWM=0`, `NL_PERIOD_S≤30`, `WDT_ENABLE=0`(コンパイル時＆GUIで強制) |
| `LOWPWR_ACTIVE_WINDOW_S` | `30` | 押しSW起水後に通常動作を維持する秒数 |

- **再書込みセーフティ**: 起動時に押しSWを押していれば通常モードに留まりスリープしない(SWD 接続可)。
- **`LOW_POWER_MODE=0`(既定)は従来ファームと byte 同一。** Standby/AWU/EXTI 経路は実験的で、
  実機での確認（Standby 復帰・AWU 周期・実消費）が必須。

## バッテリ駆動（補足・設計見積り）

LightBox は基本 **常時電源**向けだが、低デューティの常夜灯ならバッテリ駆動も可能。以下は
**設計上の見積り**（実機で要確認）:

- **常夜灯LED**: 緑LEDを **~1kΩ** 直列・3V で ≈ **0.9mA**（常夜灯として十分視認可）。
  ~2mA(510Ω)は明るいが電池寿命はおよそ**半分**。**1kΩ が明るさ/寿命のバランス良好**。
- **デューティ**: 例）5秒おきに 100ms 点灯。
- **`PWM_ON_TIME_S`（実装済）**: 自動消灯タイマは平均点灯時間を下げて消費を削減。スリープ実装の
  有無に関わらず効く「今すぐ使えるレバー」。
- **深い低電力（`LOW_POWER_MODE`・実験的=実装済）**: Standby＋AWU 周期起床、押しSW(EXTI)起水、
  スリープ中はエンコーダのプルアップをアナログ入力化してリーク停止。1kΩ・100ms/5s で単セル
  **CR2032(~220mAh)** は **実装形では約3〜4ヶ月**（点灯中は 48MHz のまま Sleep）。点灯中にクロックを
  落とせば(将来)~6.5ヶ月方向へ延伸。**深い低電力なし(48MHz常時)では約1日**しかもたない。
  GUI 設定エディタはこの CR2032 目安を 1kΩ 前提でライブ表示する。
- **セル選択**: CR2032×2 の*直列*は電圧(6V)は上がるが**容量は増えず**、6V は CH32V003 の 5.5V 上限
  超過（要降圧）。寿命重視なら*並列*(~440mAh)か大容量セル（CR2450 ~600mAh ≈ 約2年見積り）。
  CR2032 のパルス負荷/内部抵抗対策に 10–100µF のバッファコンデンサ推奨。
