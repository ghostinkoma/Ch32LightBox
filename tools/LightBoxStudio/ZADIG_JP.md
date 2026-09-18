# WCH-LinkE ドライバ設定ガイド（Zadig）

[English](ZADIG.md) · **日本語** · [← Studio README](README_JP.md)

CH32V003 へ書き込む minichlink は、WCH-LinkE に **WinUSB ドライバ**が割り当たっている必要があります。
新規PCでは初回1回だけこの設定を行います（メニューの **［🔌 ドライバ(Zadig)］** からも同じ案内が出ます）。

## 手順

1. **Zadig を入手**：<https://zadig.akeo.ie/> から `zadig.exe` をダウンロードし、**管理者として実行**。
   - 本ツールに同梱したい場合は `dist/toolchain/zadig/zadig.exe` に置くと、メニューから起動できます。
2. **WCH-LinkE を USB 接続**。
3. Zadig の **Options → List All Devices** にチェック。
4. 上部のリストから **"WCH-Link"**（CH32 書込用インターフェース）を選択。
   - 表示名は環境により `WCHLink` / `WinUSB (...)` 等。CH32V003 書込に使うインターフェースを選ぶ。
5. ドライバ欄で **"WinUSB"** を選び、**[Replace Driver]**（初回は Install Driver）。
6. 完了したら、本ツールで **［③ 書込のみ］** を再試行。`Detected CH32V003 → Image written` になれば成功。

## エラー別の切り分け

| minichlink の表示 | 意味 | 対処 |
|---|---|---|
| `Could not initialize` | WinUSB ドライバ未割当 | 本ガイド（Zadig）で WinUSB を割当 |
| `nothing connected to linker` / `Unknown chip type` | SWD 未接続 | 配線（SWIO=PD1）・ターゲット電源・GND共通を確認 |
| `Found WCH Link` 後にハング | minichlink ビルドと LinkE ファームの相性 | ［🔧書込ツール選択］で別の minichlink を選ぶ（成功版は自動記憶） |

> 補足: WCH-LinkE のファームが古く相性が出る場合は、WCH 公式の **WCH-LinkUtility** でファーム更新も選択肢。
