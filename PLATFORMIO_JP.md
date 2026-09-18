# LightBox を PlatformIO でビルドする

[English](PLATFORMIO.md) · **日本語** · [← README](README_JP.md)

> **状態**: この構成はこの環境では**未検証**です（PlatformIO 未インストール）。
> **検証済みの標準ビルドは `source/Makefile`（[README](README_JP.md) ①）** です。
> PlatformIO は利便性のための scaffold として提供します。

## 背景

ch32fun は PlatformIO の公式フレームワークではありません。そこで本 `platformio.ini` は
**コミュニティ platform-ch32v のツールチェイン**を使い、ch32fun を「フレームワーク無しで
ベアビルド」する構成にしています（`ch32fun.c` が起動コード・割込みベクタ・`SystemInit` を提供）。

CH32V003 は **RV32EC**（`-march=rv32ec -mabi=ilp32e`）なので、**rv32ec の libgcc を持つ
ツールチェイン**（xpack `riscv-none-elf`）が必要です。SysGCC(rv64)単体では不可。

## セットアップ

1. **ch32fun を配置**（プロジェクト直下 `./ch32fun`）:
   ```bash
   cd LightBox
   git clone https://github.com/cnlohr/ch32fun ch32fun
   ```
2. **リンカスクリプト**は同梱の `ldscript/lightbox_ch32v003.ld`（ch32fun.ld を CH32V003 向けに
   前処理済み）を使用。ch32fun を更新した場合は再生成:
   ```bash
   riscv-none-elf-gcc -E -P -x c -DTARGET_MCU=CH32V003 -DMCU_PACKAGE=1 \
     -DTARGET_MCU_LD=0 -DTARGET_MCU_MEMORY_SPLIT= \
     ch32fun/ch32fun/ch32fun.ld > ldscript/lightbox_ch32v003.ld
   ```
3. **ツールチェイン**: platform-ch32v 同梱の toolchain に rv32ec マルチリブが無い場合、
   `platformio.ini` に xpack を差し込む:
   ```ini
   platform_packages =
       toolchain-riscv @ file:///C:/Users/<you>/AppData/Local/xpack-riscv-none-elf-gcc/14.2.0-3
   ```
4. ビルド:
   ```bash
   pio run
   ```

## 書込

CH32V003 は WCH-LinkE + `minichlink` が確実です:
```bash
# Makefile 経由が最も確実
cd source && make CH32FUN=../ch32fun/ch32fun
```
PlatformIO の `pio run -t upload` は環境依存（WCH-Link のプロトコル対応が必要）。
うまくいかない場合は生成された `.bin`/`.hex` を minichlink で書き込んでください。

## トラブルシューティング

| 症状 | 対処 |
|------|------|
| `undefined reference to __muldi3` 等 | rv32ec libgcc 不足 → xpack ツールチェインを差し込む(手順3) |
| リンカが FLASH/RAM を超える | ldscript が CH32V003(16K/2K)向けか確認、再生成 |
| 起動しない/ハングする | ベアビルド設定(`ch32fun.c` を含む・`-nostdlib`・ldscript)を確認 |
| どうしても通らない | **Makefile ビルド（検証済み）を使用**。PlatformIO は任意 |
