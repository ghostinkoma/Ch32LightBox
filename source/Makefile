# LightBox Makefile (ch32fun)
#
# 萓晏ｭ・ ch32fun (https://github.com/cnlohr/ch32fun) 縺ｨ RISC-V GCC + WCH-LinkE縲・
#       CH32V003 縺ｯ RV32EC 縺ｮ縺溘ａ rv32ec libgcc 繧呈戟縺､繝・・繝ｫ繝√ぉ繧､繝ｳ
#       (xpack riscv-none-elf) 縺悟ｿ・ｦ√４ysGCC(rv64) 蜊倅ｽ薙〒縺ｯ荳榊庄縲・
#
# 驟咲ｽｮ: 縺薙・Makefile縺ｯ source/ 縺ｫ縺ゅｊ縲√た繝ｼ繧ｹ(lightbox.c 遲・縺ｨ蜷悟ｱ・☆繧九・
#       謌先棡迚ｩ(.elf/.bin/...)繧・source/ 縺ｫ逕滓・縺輔ｌ繧・gitignore貂・縲・
#
# 繝薙Ν繝画婿豕・ 縺薙・ source/ 繝輔か繝ｫ繝縺ｧ CH32FUN 繧呈欠螳壹＠縺ｦ螳溯｡・
#   make lightbox.bin CH32FUN=/path/to/ch32fun/ch32fun   # 繝薙Ν繝峨・縺ｿ
#   make              CH32FUN=/path/to/ch32fun/ch32fun   # 繝薙Ν繝俄・譖ｸ霎ｼ
#   萓・ make CH32FUN=D:/hobby/ch32fun/work/ch32fun/ch32fun

TARGET     := lightbox
TARGET_MCU ?= CH32V003
CH32FUN    ?= ../../ch32fun

all : flash

include $(CH32FUN)/ch32fun.mk

flash : cv_flash          # WCH-LinkE 縺ｧ譖ｸ霎ｼ (minichlink)
clean : cv_clean
