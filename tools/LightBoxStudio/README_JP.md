# LightBox Studio — CH32V003 ワンストップGUI（VS Code不要）

[English](README.md) · **日本語** · [← プロジェクト README](../../README_JP.md)

`config.h` 編集 → コンパイル → 書込 を **1本のメニューGUIから** 実行するための Java ツール群。
LightBox（[../../README_JP.md](../../README_JP.md)）の開発に VS Code を使わず、GUI だけで完結させることを目的とする。

> **状態: ✅ 実機確認済み（2026-09-18）**。初回セットアップでツールチェーンを自動DLして
> `toolchain/` に配備し設定まで自動記入（自己完結・PC非走査のサンドボックス）。
> **config.h編集 → コンパイル → CH32V003書込** を、**ダウンロードした ch32fun 同梱の
> minichlink（dist内・self-contained）だけ**で実機書込成功（`Detected CH32V003 → Image written`）。
> ユーザーはインストール済みパスを探す必要がない。詳細な不具合対処の記録は [BUGFIX_JP.md](BUGFIX_JP.md)。

## 構成（5本立て）

| 実行物 | 役割 | 種別 |
|---|---|---|
| `LightBoxMenu.jar` | **メニュー（キッカー）**。他を起動しログを集約 | GUI |
| `setup.jar` | **初回セットアップ**。ツールチェーンをDL＆展開し設定を自動記入（進捗バー付き） | GUI |
| `config-editor.jar` | `config.h` をフォーム編集し検証して保存 | GUI |
| `builder.jar` | `make` を実行してビルド（＋任意で書込） | バックグラウンド |
| `flasher.jar` | `minichlink` で CH32V003 へ書込 | バックグラウンド |

メニューGUIと各機能JAR（バックグラウンド）は**別プロセス**として疎結合。設定は
`settings.properties`（JARと同じフォルダ）で共有する。

## 必要環境

- **JDK / JRE 11 以上**（ビルドは JDK、実行は JRE 可）
- ネイティブなツールチェーン（ch32fun / RISC-V GCC / make / minichlink）は
  **［⬇ 初回セットアップ］が自動で用意**するので、事前インストールは不要。
- 書込機能のみ、**WCH-LinkE の WinUSBドライバ**（初回のみ Zadig）が必要。
  ※これは Java から自動化できない唯一の手動ステップ（別PC配布時のみ）。
  メニューの **［🔌 ドライバ(Zadig)］** に手順を内蔵（[ZADIG_JP.md](ZADIG_JP.md) 参照）。

> `config.h` 編集だけなら**セットアップも不要**（純Javaで完結）。

## 初回セットアップ（自己完結）

メニューの **［⬇ 初回セットアップ］**（＝`setup.jar`）を押すと、`toolchain/` へ以下を
**ダウンロード＆展開**し、`settings.properties` を自動記入する。進捗はプログレスバー表示。

| 部品 | 入手元 | 目安 |
|---|---|---|
| RISC-V GCC (xpack riscv-none-elf) | xpack-dev-tools Releases | **約360MB**（時間がかかる） |
| make (xpack windows-build-tools) | xpack-dev-tools Releases | 約2.7MB |
| ch32fun SDK | cnlohr/ch32fun (master zip) | 数MB |
| minichlink | 公式プリビルドが無いため**PC内の既存を自動検出しコピー**（内部処理・ユーザー操作不要） | — |

- ダウンロード元/版は `dist/toolchain.manifest.properties`（任意）で差し替え可能。
- `toolchain/` は巨大なので `.gitignore` 済み（リポジトリには含めない）。
- minichlink が PC 内に無い場合はスキップされ、ビルド/編集は可能・**書込のみ**別途 minichlink 指定が必要。

## ビルド

```bash
# Windows
build.bat
# Linux/macOS
./build.sh
```
`dist/` に5本のJARが生成される（本リポジトリには生成済みJARも同梱）。

## 使い方

1. メニューを起動:
   - **推奨: `LightBoxStudio.bat`（Windows）/ `LightBoxStudio.sh`（Linux/macOS）をダブルクリック**。
     ランチャが Java を自動検出（①同梱ポータブルJRE → ②`JAVA_HOME` → ③PATH）して起動する。
   - 直接でも可: `java -jar dist/LightBoxMenu.jar`
2. 初回だけ **［⬇ 初回セットアップ］** を押してツールチェーン取得（進捗バーが完了するまで待つ）。
   ※config.h 編集だけならこの手順は不要。既にツールチェーンがある場合は **［⚙ 設定］** で手動指定も可。
3. **① config.h 編集** → **② コンパイル** → **③ 書込**（または **②+③ ビルド→書込**）。

### Java ランタイム（システムに入れたくない場合）

- ランチャは**既存の Java を優先的に検出・再利用**する（`JAVA_HOME`/PATH）。既に Java があれば何も要らない。
- Java を**システムへインストールしたくない**場合は、初回セットアップの
  **「☐ ポータブルJRE も取得」** を選ぶと、Adoptium Temurin 17 JRE（約44MB, インストーラ不要）を
  **`dist/runtime/` にのみ展開**する。**PATH・レジストリ・管理者権限は一切触らない**＝
  フォルダを消せば元通りの**サンドボックス**。以後ランチャはこの `runtime/` を自動使用する。
- Java の無い別PCへ配る場合: **Java のある PC で「ポータブルJRE も取得」を実行 → フォルダごとコピー**すれば、
  配布先は Java 不要でランチャから起動できる（`dist/toolchain/` も同梱すればビルド/書込も自己完結）。

## 設定項目（settings.properties）

| キー | 意味 |
|---|---|
| `project.dir` | `config.h` / `Makefile` のある `source/` フォルダ（空=自動検出） |
| `ch32fun.dir` | `ch32fun.mk` があるフォルダ |
| `make.path` | make 実行ファイル（空=PATH） |
| `gcc.bin.dir` | `riscv-none-elf-gcc` のある bin（空=PATH） |
| `minichlink.path` | minichlink 実行ファイル（空=PATH） |
| `flash.artifact` | 書込対象（既定 `lightbox.bin`, 相対は project.dir 基準） |

## config.h エディタの特長

- **スキーマ駆動**：全57項目を型別ウィジェット（数値/チェック/ドロップダウン/ピン/色）で編集。
  定義は [`documents/CONFIG_REFERENCE_JP.md`](../../documents/CONFIG_REFERENCE_JP.md) と `source/config.h` に対応。
- **機能↔ピン割当**：ピンのドロップダウンは能力で絞り込み（`PWM_PIN` は PWM可能ピンのみ、`TEMP_SENSE_PIN` は ADC対応ピンのみ）。ピン衝突（2機能が同一ピン）・能力違反・SWIO(PD1)使用を保存前に検出し、ファーム `source/pins.h` のコンパイル時チェックと一致。
- **非破壊編集**：`#define KEY VALUE` の**値トークンだけ**を差し替え、コメント・enum定義行・
  空行・`u`/`L`サフィックス・`(-300)` 様式を保全（全文再生成しない）。保存前に `config.h.bak` を作成。
- **相互制約バリデーション**（保存前チェック）：
  - `PWM_SPREAD_SPECTRUM=1` 時 `PWM_TOP+PWM_SPREAD_RANGE≤65535` かつ `RANGE<PWM_TOP`
  - `STORE_SIZE_BYTES` は1KBの倍数 / `LEVEL_INIT≤LEVELS`
  - `WARN_TEMP_HYST_C<WARN_TEMP_C` / `THERMAL_THROTTLE_MAX<100` / `TEMP_CAL_SLOPE_X100≠0`
  - **PC4排他**（常夜灯 / 外付け温度センサ / 警告灯 は同時に1つ）
  - `WS_MAX_COLOR` の桁を `WS_ORDER`（RGB=6桁 / RGBW=8桁）に連動
  - 警告: `DEBUG_LOG=1`（通常運用は0）, ダイ推定温度源（誤遮断のおそれ）
- **上級者モード**：`ENC_*_PIN` 等のHW結線固定項目は既定ロック（誤変更防止）。

## 書込の注意（README/CONFIG_REFERENCE.md 準拠）

- SWD printf 監視（`minichlink -T`）は**書込と排他**。観測中は閉じてから書き込む。
- 書込対象が無い場合は先に **② コンパイル** で `lightbox.bin` を生成。
- WCH-LinkE が認識されない → WinUSBドライバ（Zadig）/ 電源 / SWIO(PD1)配線を確認。
- **書込がハングしたら**：メニューの **［■ 中断］** で停止できる（子の minichlink まで含めて kill）。
- **minichlink はビルド差で相性がある**：PC内に複数バージョンがあると、init はできても書込で
  固まる個体がある。対策として書込は**複数の minichlink 候補を順に試し**、各試行は
  **約25秒でタイムアウト**して次へ自動フォールバックする（ハング個体を掴んでも最終的に
  動く個体で書ける）。初回セットアップが候補を `minichlink.candidates` に自動記録する。
- **確実にしたいなら**：メニューの **［🔧 書込ツール選択］** で動作実績のある `minichlink.exe` を指定
  （＝最優先で試行）。この設定は初回セットアップを再実行しても維持される。
- **一度書込に成功した minichlink は、ユーザープロファイル（`~/.lightboxstudio/`）に記録**され、
  `tools/` フォルダを削除してクリーン再導入しても**次回から最優先で自動使用**される。
  ＝「動く minichlink を一度当てれば、その後はずっと自動で通る」。
- 試行順は ①ローカル設定 → ②過去の成功実績 → ③自動検出候補。実績があれば無駄な待ちは無い。
- 再セットアップは、`toolchain/` が既に展開済みなら**大容量DLを省略**する。

### minichlink 互換性と自己完結（調査結果）

- **ソースビルドは不要**。ch32fun は Windows 版 `minichlink.exe` と `libusb-1.0.dll` を
  リポジトリに同梱している（tcc でビルド済みの prebuilt をコミット）。初回セットアップで
  ch32fun を展開すると `toolchain/ch32fun-master/minichlink/minichlink.exe` が手に入る。
  → **書込は本 minichlink を自己完結の候補として自動で試す**（PC内を漁らずに済む）。
- **ただし minichlink のビルドと WCH-LinkE のドライバ/ファームには相性がある**（実測）:
  - `Could not initialize` … WinUSBドライバ未設定。**Zadig** で WCH-Link を WinUSB に割当（初回1回）。
  - `Found WCH Link` 後にハング … minichlinkビルドと LinkE ファームの相性（例: ファーム 2.17 では
    古い `bc15212` 系は成功、新しめ系はハング）。**動く版を一度当てれば自動記憶**され以後最優先。
- 試行順（**PC全体の走査はしない＝サンドボックス**）: ①ローカル設定 → ②過去の成功実績
  (`~/.lightboxstudio/`) → ③**ch32fun 同梱(dist内・self-contained)**。各25秒でタイムアウトし次へ。
  **成功した版は恒久記憶**（クリーン再導入でも維持）。
- 同梱版が環境で動かない場合: ［🔧書込ツール選択］で手元の動く `minichlink.exe` を**明示指定**
  （既定フォルダは ch32fun 同梱の場所）。指定した版は最優先＋恒久記憶される。
- それでも通らないときは WCH-LinkE のファーム更新（WCH-LinkUtility）でドライバを合わせる。

> 要点: 「動く minichlink を **一度** 当てる／指定する」だけで、その後は自動。CH32 未経験者でも、
> ch32fun 同梱 minichlink ＋ Zadig（初回のドライバ設定1回）で書込できることを目標にしている。

## ロードマップ

- **Phase 0（完了）**: 既存環境前提でGUI化。`config-editor` は単独で完成品。
- **Phase 1（完了・現状）**: `setup.jar` が `toolchain/` へ xpack GCC / make / ch32fun を自動DL、
  minichlink は既存を自動検出コピー、設定を自動記入（自己完結・進捗バー付き）。
- **Phase 2（進行中）**: **ポータブルJRE（サンドボックス）＋ランチャ**で Java 依存も自己完結化。
  既存Javaは自動検出・再利用、無い/入れたくない場合は `dist/runtime/` にのみ展開（システム非変更）。
  残: `jpackage` による単一 `.exe` 化、WinUSBドライバ導入（Zadig）ガイド内蔵。

## ライセンス

本ツール群は LightBox 本体と同じ非商用ライセンス（CC BY-NC 4.0 準拠）に従う（[../../LICENSE](../../LICENSE)）。
