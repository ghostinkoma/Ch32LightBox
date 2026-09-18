# LightBox Studio — 不具合対処記録（BUGFIX）

[English](BUGFIX.md) · **日本語** · [← Studio README](README_JP.md)

> **総括: ✅ 実機確認済み（2026-09-18）。** `config.h編集 → コンパイル → CH32V003書込` を、
> **ダウンロードした ch32fun 同梱 minichlink（`dist/toolchain/.../minichlink.exe`・self-contained）だけ**で
> 実機書込成功（`Detected CH32V003 → Writing image → Image written.`）。PC内を走査しないサンドボックス、
> 成功実績の恒久記憶（クリーン再導入耐性）まで実装・検証済み。

対象: `tools/LightBoxStudio/`（`main` に統合済み）。

---

## 1. config.h 編集で LEVELS を 128 以上にでき、ビルドが `_Static_assert` で失敗
- **症状**: `lightbox.c:59: static assertion failed: "LEVELS ... 1..127"` でコンパイル停止。
- **原因**: 編集GUIのスキーマ範囲（`LEVELS` 1..255 等）がファームの `_Static_assert` と不一致。
- **対処**: `lightbox.c` / `*.h` の全 `_Static_assert` を洗い出しスキーマへ整合。
  - `LEVELS` 1..127 / `LEVEL_INIT` 0..127 / `WDT_TIMEOUT_MS` 100..8190。
  - `PWM_TOP+PWM_SPREAD_RANGE<=65535` と `PWM_TOP>PWM_SPREAD_RANGE` を**無条件検証**（拡散OFFでもファームはassert）。
  - `DIE_TAU_MS>=WARN_TEMP_PERIOD_MS` 検証を追加。
  - 読込時に範囲外の既存値を警告表示（保存で自動修正）。
- **確認**: 実機ビルド `Flash 8200B/16KB`、`lightbox.bin` 生成。

## 2. コンパイルは通るが書込前段（ldscript生成）で失敗
- **症状**: `sh: can't create D:hobbyLightBox...generated__.ld: nonexistent directory`。
- **原因**: xpack `windows-build-tools` の `make` は**同梱 `sh` 経由**でレシピを実行。`CH32FUN` の
  バックスラッシュ絶対パスが `>` リダイレクトで sh のエスケープに食われ、区切りが消失。
- **対処**: `CH32FUN=` を**フォワードスラッシュ**で渡す（Windowsでも gcc/make/sh は `/` を受理）。
- **確認**: `CH32FUN=D:/hobby/.../ch32fun` でビルド〜生成物まで通過。

## 3. 書込が ch32fun 内蔵 `make flash` に依存していた
- **症状**: `②+③` が ch32fun.mk の `cv_flash`（内蔵minichlink）を呼び、環境により失敗。
- **対処**: 書込ロジックを `FlashOp` に集約。`②+③`（builder --flash）も `③`（flasher）も
  **設定の minichlink を直接使用**。ビルドは make、書込は minichlink と分離。

## 4. minichlink が「接続はできるが書込でハング」／版の相性
- **症状**: `Found WCH Link` 後、`Read protection: disabled` で停止（数分応答なし）。PC内に複数版があり、
  片方だけハング。
- **対処**:
  - `ProcRunner.runTimed` を追加。各書込試行を**25秒でタイムアウト**（子孫プロセスごと強制停止）。
  - 複数の minichlink 候補を**順に試行**し、最初の成功を採用。
  - メニューに **［■ 中断］** を追加（ハング時にGUIから停止）。
  - headlessテストで「fail→ハング25s→成功=コード0」「成功先頭で即時」を検証。

## 5. 成功した minichlink を忘れてしまう（クリーン再導入で振り出し）
- **対処**: 書込成功時に**ユーザープロファイル `~/.lightboxstudio/settings.properties` へ恒久記憶**。
  `tools/` を削除して再導入しても、記憶した実績 minichlink を最優先で自動使用。

## 6. 「PC全体を検索」はサンドボックスとして不適切
- **症状**: 自動検出が `C:\Users\...` 等を走査し、相性の悪い別ビルドを掴む／サンドボックス違反。
- **対処**: **自動走査を全廃**。候補は **①ローカル設定 → ②成功実績（記憶）→ ③ch32fun同梱（dist内）** のみ。
  ［🔧書込ツール選択］は素のファイル選択（既定フォルダ＝ch32fun同梱の場所）に変更。

## 7. minichlink の自己完結（ソースビルド不要）
- **調査**: cnlohr/ch32fun は **Windows版 `minichlink.exe` + `libusb-1.0.dll` を prebuilt でリポジトリ同梱**
  （tccビルド済み）。初回セットアップの ch32fun 展開で `toolchain/ch32fun-master/minichlink/minichlink.exe`
  が入手できる（＝**ソースビルド不要**）。同梱版のバージョンは `bc15212`（実績ビルドと同一と実測）。
- **対処**: 書込候補に **ch32fun 同梱 minichlink（self-contained）** を採用。PC内にminichlinkが無い
  初心者でも、ダウンロードだけで書込ツールが揃う。
- **確認**: **同梱minichlink単独で実機書込成功**（`Detected CH32V003 → Image written`）。

## 8. `nothing connected to linker` はソフトではなくハード
- **症状**: `link error, nothing connected to linker (...)` / `Unknown chip type` / `marchid: ffffffff`。
  同一の実績 minichlink でも失敗。
- **原因**: WCH-LinkE ↔ ターゲット(CH32V003) の **SWD 未接続**（電源・SWIO=PD1・GND共通のいずれか）。
- **切り分け**: minichlink の版に依存せず発生＝ソフトでは解決不可。**配線/電源を修正後、同じ自己完結
  minichlink で一発成功**（当該切り分けの正しさも確定）。

## 9. UI/画面遷移
- **初回起動時**（未セットアップ＝gcc未設定 or toolchain無し）に**セットアップ画面を自動表示**。
- **メイン画面から［初回セットアップ］ボタンを撤去**。
- セットアップ画面は**既定でプログレスバーのみ**（コンパクト）。**［詳細 ▼］トグル**でログ表示＋
  ウィンドウ拡大、再トグルで非表示・縮小。
- **ポータブルJRE取得を既定ON**（サンドボックス方針）。

---

## 書込ツール（minichlink）の試行順（現行仕様）
PC全体の走査はしない：
1. ローカル設定 `minichlink.path`
2. 過去の成功実績 `~/.lightboxstudio/`（クリーン再導入でも維持）
3. **ch32fun 同梱（`dist/toolchain/.../minichlink.exe`・self-contained）**

各試行は 25 秒でタイムアウトし次へ。成功した版は恒久記憶。

## 残る唯一の外部要素
新規PCで同梱 minichlink を動かす **WCH-LinkE の WinUSB ドライバ（Zadig, 初回1回）** のみソフト外。
`Could not initialize` はドライバ、`nothing connected` は配線/電源、`Found WCH Link後にハング` は
別ビルド選択、で切り分ける（[README_JP.md](README_JP.md) 参照）。
