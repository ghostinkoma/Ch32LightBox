# LightBox 設定リファレンスガイド (config.h)

`source/config.h` の全項目リファレンス。値を変えたら再ビルド（[README](../README.md) 参照）。
ピンは ch32fun のピン名（`PA1`, `PC2`, `PD0` …）。

## ピン割当

| 項目 | 既定 | 説明 |
|------|------|------|
| `ENC_A_PIN` | `PA1` | エンコーダ A 相（内部プルアップ） |
| `ENC_B_PIN` | `PC1` | エンコーダ B 相（内部プルアップ） |
| `ENC_SW_PIN` | `PA2` | 押しSW（内部プルアップ, 押下=Low） |
| （PWM出力） | `PC2` | 本体 LED = TIM2_CH2(remap1) 固定 |

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
| **[EXT]** `TEMP_SENSE_ANALOG` | `2` | センサ ADC チャンネル（2=PC4, 7=PD4, 0=PA2, 1=PA1） |
| **[EXT]** `TEMP_SENSE_PIN` | `PC4` | センサ入力ピン（`TEMP_SENSE_ANALOG` と同じ物理ピン） |
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
| `WARN_LED_PIN` | `PC4` | 警告灯ピン（SOP8では常夜灯/外付けセンサと排他） |

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

## WS2812 / SK6812 常夜灯

| 項目 | 既定 | 説明 |
|------|------|------|
| `NIGHTLIGHT_ENABLE` | `1` | 常夜灯機能 |
| `WS_PORT` | `GPIOD` | データ線ポート |
| `WS_PINNUM` | `0` | データ線ピン番号（0=PD0） |
| `WS_COUNT` | `1` | LED 個数 |
| `NIGHTLIGHT_PERIOD_MS` | `4000` | n: 明→暗 1往復(ms, **2〜60000**)。上限は32bit SysTick(~89.5s)ラップ回避の安全マージン |
| `NIGHTLIGHT_INTERVAL_MS` | `3000` | i: 周期後の完全オフ(ms, **≤60000**) |
| `NIGHTLIGHT_REFRESH_MS` | `30` | 送信リフレッシュ間隔（滑らかさ） |
| `NIGHTLIGHT_BOOT_TEST_MS` | `1500` | 起動直後にWS LEDを設定色で点灯（配線/切り分け用, 0=無効） |
| `WS_ORDER` | `WS_ORDER_GRB` | チップのバイト並び。`WS_ORDER_GRB/RGB/GRBW/RGBW` |
| `WS_MAX_COLOR` | `0x403008` | 最大色。RGB系=**0xRRGGBB(6桁)** / RGBW系=**0xRRGGBBWW(8桁)** |

### 色順・RGBW の設定例
- 一般的 WS2812（GRB）: `#define WS_ORDER WS_ORDER_GRB` / `#define WS_MAX_COLOR 0x403008`
- RGB 順の製品: `#define WS_ORDER WS_ORDER_RGB`
- **SK6812 RGBW**: `#define WS_ORDER WS_ORDER_GRBW` / `#define WS_MAX_COLOR 0x40300810`
  （R=0x40 G=0x30 B=0x08 W=0x10）
- ※`WS_MAX_COLOR` の桁数は `WS_ORDER`（W有無）に必ず合わせること。

## ピン早見（既定, **SOP8 (J4M6) で全機能**）
`PA1`=ENC_A / `PC1`=ENC_B / `PA2`=ENC_SW / `PC2`=PWM / `PD1`=SWIO。
残る **`PC4` は 常夜灯WS2812 / 外付け温度センサ / 警告灯 のいずれか1つ（排他）**。
温度=ダイ推定、過熱遮断・スロットル・ウォッチドッグ・EMI対策は**すべてピン不要**。
複数を同時に使いたい場合は TSSOP20/QFN(F4P6)で各ピンを分ける。
