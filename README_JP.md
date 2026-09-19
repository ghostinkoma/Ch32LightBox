# LightBox — CH32V003 LED 調光コントローラ

[English](README.md) · **日本語**

> **ステータス: ✅ 実機検証済み・稼働中（2026-09 時点、問題なし）** — 実基板(KiCad)／ユニバーサル基板の
> 両方で全機能を確認。常夜灯・明るさ調整・ソフトスタート/オフ・12h耐久・≒1A LEDで発熱ほぼ無し。
> **Fusion 360 で設計・3Dプリントした筐体に組み込み、実運用中。**
>
> **ライセンス: 非商用（CC BY-NC 4.0 準拠 / クレジット必須・改変自由(非商用)・無保証無責任）** → [LICENSE](LICENSE)

ロータリーエンコーダで LED の明るさを調整する、シングル PWM 出力の調光器。
マイコンは **CH32V003**、ファームは [ch32fun](https://github.com/cnlohr/ch32fun) 自己完結。

**人間の目が感じる明るさ**を最優先に再現するため、PWM duty を **CIE 1931 L\*(明度)カーブ**で
生成する。1クリックが「知覚的に等量」の明るさ変化になり、暗い時はゆっくり・明るい時は急激に、
が目にとって均等な配分で実現する。

## 主な機能

| 機能 | 概要 |
|------|------|
| **CIE L\* 調光** | 知覚均等な明るさ。全 64 段、起動時 LUT 生成、実行中は整数テーブル参照のみ |
| **16bit PWM** | TIM2、既定 4095段/約12kHz。`config.h` で分解能・周波数可変 |
| **チャタリング吸収** | フルステップ状態機械(Ben Buxton) + 方向反転ロックアウト(既定5ms)。単純delay不使用 |
| **高速回しスナップ** | n ms 以内に同方向 j 回以上で duty=100%/0% へ即セット |
| **CW/CCW 両対応** | 回転方向を `config.h` 1行で反転 |
| **明るさ記憶** | フラッシュ・ウェアレベリング（専用2KB領域、5秒後保存、寿命 約1024倍、ON/OFFも保存） |
| **過熱フェイルセーフ**（既定OFF） | 過熱で本体PWM(MOSFET)を強制遮断→冷えたら再開。n回繰返しでj%サーマルスロットル。**ダイ推定は誤遮断のため不採用（`TEMP_PROTECT_ENABLE 0`）**、外付けNTC実測で使う場合のみ有効化 |
| **ウォッチドッグ** | IWDG。ハング時に自動リセットで復帰 |
| **EMI対策** | PWMスルーレート(GPIO速度)＋スペクトラム拡散(周期ディザ,輝度不変) |
| **常夜灯（WS2812 or 単色LED）** | 完全に暗い時: ①WS2812/SK6812 が CIE基準で 明↔暗 呼吸（RGB/RGBW・色順・最大色）/ ②単色LEDを n秒おきに x ms 点灯 |
| **深い低電力（実験的）** | 任意のバッテリモード(`LOW_POWER_MODE`): 単色LED常夜灯で点灯間は MCU を Standby（AWU周期起床・押しSW EXTI起水）。既定OFF。詳細は CONFIG_REFERENCE |
| **ソフトスタート** | 電源ON/OFF時に設定輝度まで CIE基準で滑らかにフェードイン/アウト（時間を config 指定、0で無効、最大65535ms） |
| **押しSW** | 短押しで ON/OFF トグル（時間ベースデバウンス。フェード中の再操作も滑らかに追従） |

全設定は **`source/config.h`** に集約（詳細は [CONFIG_REFERENCE_JP.md](documents/CONFIG_REFERENCE_JP.md)）。
仕様の詳細は [SPEC_JP.md](documents/SPEC_JP.md)、設計判断は [DESIGN_JP.md](documents/DESIGN_JP.md)。

## ピン割当（**SOP8 (J4M6) で全機能が載る**）

| ピン | 信号 | 用途 |
|------|------|------|
| PA1 | ENC_A | エンコーダ A 相（内部プルアップ, 共通=GND） |
| PC1 | ENC_B | エンコーダ B 相（内部プルアップ, 共通=GND） |
| PA2 | ENC_SW | 押しSW（内部プルアップ, 押下=Low） |
| PC2 | PWM | **本体 LED 出力**（既定 `PWM_PIN`=PC2 → TIM2_CH2 remap1） |
| PC4 | （選択） | **常夜灯WS2812** / 外付け温度センサ / 警告灯 のいずれか1つ（排他） |
| PD1 | SWIO | 書込 / debugprintf（温存） |

> **機能↔ピンの割当は `config.h` だけで完結**（1機能=1ピンマクロ: `PWM_PIN`・`ENC_*_PIN`・`WS_DIN_PIN`・`TEMP_SENSE_PIN`・`WARN_LED_PIN`）。`source/pins.h` が能力（PWM可/ADC可）とピン衝突をコンパイル時に検証し、LightBox Studio の設定エディタも同じ検証を行う。詳細は [CONFIG_REFERENCE_JP.md](documents/CONFIG_REFERENCE_JP.md) の「機能↔ピン割当」節。

> **温度保護は既定OFF**（ダイ推定は誤遮断のため不採用）。ウォッチドッグ・
> EMI対策も**すべてピン不要**なので、SOP8 の唯一の空きピン PC4 を常夜灯に使える。
> 外付け温度センサ(実測)や警告灯を使う場合は PC4 をそれに割当（常夜灯とは排他）。
> より多機能を同時に使うなら TSSOP20/QFN(F4P6) で各ピンを `config.h` で分ける。

## LED の接続

- **推奨**: PC2 → MOSFET（例 AO3400/2N7002）ゲート、ドレインで LED をスイッチ。
- **簡易**: 小電流 LED は `PC2 →[抵抗]→ LED → GND` 直結も可（1ピン Imax≈8mA 厳守）。
- **過熱保護（既定OFF）**: CH32V003 に内蔵ダイ温度センサは無く、当初のデューティ由来の
  「ダイ温度推定」は実測でないため**誤遮断（点けっぱなしで急に消灯）**を招いた。→ **不採用
  （`TEMP_PROTECT_ENABLE 0`）**。本当に発熱保護したい場合のみ、MOSFET に熱結合した NTC を
  ADC に付けて `TEMP_PROTECT_ENABLE=1` + `TEMP_SOURCE=EXTERNAL` で実測運用する。詳細 SPEC.md。

## 操作

- エンコーダを回す → 1クリックごとに ±1 段（CIE L* で知覚的に等間隔）
- **勢いよく回す**（既定 0.5秒に6クリック以上）→ 明方向で全開(100%)、暗方向で消灯(0%)
- 押しSW 短押し → ON/OFF トグル（レベルは保持）
- 明るさは**自動記憶**：初回起動は 0、設定して 5 秒手を止めると保存、次回復元
- 電源ON/OFF・起動時は設定輝度まで**ソフトスタート**（フェード、時間は config）
- 完全に暗くすると **常夜灯**が呼吸を開始（本体を明るくすると消灯）

## ビルド & 書込

### ① ch32fun + Makefile（検証済みの標準ビルド）

依存: [ch32fun](https://github.com/cnlohr/ch32fun)、RISC-V GCC（RV32EC の rv32ec libgcc を持つ
xpack riscv-none-elf 推奨）、WCH-LinkE。

```bash
cd source
make lightbox.bin CH32FUN=/path/to/ch32fun/ch32fun   # ビルドのみ
make            CH32FUN=/path/to/ch32fun/ch32fun      # ビルド→書込
```

ビルド実績（既定 config, 全機能ON, `DEBUG_LOG=0`）: **Flash 8200 B / 16 KB (50%)、RAM 184 B (9%)**。
機能を切ると: コアのみ約3KB、+常夜灯で約3.6KB。`DEBUG_LOG=1` で +約1KB。

> `DEBUG_LOG` は開発時のみ 1 に。printf はブロッキングで、SWD ホスト未接続だと停滞し
> ホットループの取りこぼしを招く。既定 0 で printf 機構ごと非リンク。

### ② PlatformIO

[PLATFORMIO_JP.md](PLATFORMIO_JP.md) 参照（`platformio.ini` 同梱）。
※PlatformIO ビルドはこの環境では未検証。検証済みは上記 Makefile。

### ③ LightBox Studio（GUI・VS Code 不要）

`config.h` 編集 → コンパイル → 書込 を **1本のメニュー GUI から** 完結させたい場合は、
[tools/LightBoxStudio/](tools/LightBoxStudio/README_JP.md) の Java ツール群を使う。初回起動で
自己完結ツールチェーンを自動DLするため、コマンドラインも VS Code も不要。
詳細は [LightBox Studio README](tools/LightBoxStudio/README_JP.md)。

## ディレクトリ

```
LightBox/
├─ source/        ファーム
│   ├─ lightbox.c      本体(main/エンコーダ/PWM)
│   ├─ config.h        全設定(ここを触る)
│   ├─ cie.h           CIE L* カーブ(本体/常夜灯 共用)
│   ├─ store.h         明るさ不揮発ストア(ウェアレベリング)
│   ├─ temp.h          温度取得(外付けNTC。既定OFF)
│   ├─ wdt.h           ウォッチドッグ(IWDG)
│   ├─ nightlight.h    WS2812/SK6812 常夜灯
│   ├─ funconfig.h     ch32fun 設定
│   └─ Makefile
├─ documents/     SPEC.md / CONFIG_REFERENCE.md / HARDWARE.md / DESIGN.md (+ *_JP.md)
├─ tools/         LightBoxStudio/ = GUIツール群 (config編集→ビルド→書込, VS Code不要)
├─ ldscript/      PlatformIO 用リンカスクリプト (lightbox_ch32v003.ld)
├─ PCB/           KiCad 基板一式 (LightBox/ = 回路図/PCB; AertWork.png; KiCad/ = FDS5680 lib)
├─ platformio.ini / PLATFORMIO.md
├─ 3D_Models/     筐体 (Fusion 360 設計 / STL, 3Dプリント済)
└─ LICENSE        非商用ライセンス (CC BY-NC 4.0 準拠)
```

## 筐体

**Fusion 360 で設計 → 3Dプリント済み**。本基板を組み込み、**実運用中**（`3D_Models/` に STL）。

## ハードウェア（基板）

基板設計・回路図・アートワーク（ピンヘッダ付き）完了 → `PCB/LightBox/`（KiCad）。
**配線はファームと完全一致**。詳細・BOM・J1ピンアウトは [HARDWARE_JP.md](documents/HARDWARE_JP.md)。

- U1 CH32V003(SOP8) / Q1 FDS5680(N-MOSFET, メインLED低側駆動) / D1 SK6812(OST45050C1A-W 常夜灯)
- R1 100Ω(ゲート直列) / R2 10K(ゲートプルダウン=リセット時LED OFF) / **D2 パイロットLED + R3 100Ω(直列)**
- **メイン(パワー)LEDは基板外**（LED OUT→J1）。**≒1A LEDで発熱ほぼ無し**（ヒートシンクは予防的）
- J1 9ピンヘッダ: 電源/SWIO/エンコーダ/両LED信号を引き出し。**VDD=5V 給電**
- ユニバーサル基板(1.27mmピッチ)でも同等回路を製作・動作確認済

## 検証状況 — ✅ 実機検証済み（継続稼働中・問題なし）

- **★実機動作 確認済（2026-09-16〜、以降 問題なく稼働中）**: 実基板/配線一致、フラッシュ成功。
  - **常夜灯**（SK6812 / OST45050C1A-W, RGBW順）
  - **明るさ調整**（CIE L*、加速×3、スナップ）
  - **ON/OFF ソフトスタート・ソフトオフ**
  - **消灯・点灯 各約12時間 異常なし**（温度保護OFFで誤遮断解消、長時間安定）
- ビルド: 全機能ON/OFF・RGBW・ハーフステップの各組合せで警告ゼロ（Makefile, ch32fun）。
- ホスト検証: CIE LUT、エンコーダ状態機械、ストア境界、常夜灯カーブ、色順、加速、熱モデル。
- 未確認: フラッシュ長期摩耗（設計上~1000万回）、外付けNTC温度保護（既定OFF）、PlatformIO ビルド。

## ライセンス

本プロジェクト（ファーム/基板/筐体/ドキュメントの ghostinkoma オリジナル部分）は
**非商用ライセンス（Creative Commons 表示-非営利 4.0 / CC BY-NC 4.0 準拠）**です。詳細は [LICENSE](LICENSE)。

- **非商用限定**（商用利用は作者へ個別許諾を）
- **クレジット必須**（原作者 **ghostinkoma** と "LightBox" を明記）
- **改変自由**（非商用に限り、派生物も同条件を継承）
- **無保証・無責任**（AS IS。使用による損害等について作者は一切責任を負わない）

※同梱・依存の第三者コード（[ch32fun](https://github.com/cnlohr/ch32fun) 等）は各自の原ライセンス（MIT-x11/NewBSD）に従います。
