/*
 * store.h — 明るさ不揮発ストア (CH32V003 内蔵フラッシュ・ウェアレベリング)
 *
 *  方式: マーカ無しの「単一セル追記ログ (last-written-wins)」。
 *    ・STORE_SIZE_BYTES を専用領域として予約(1KB整列の const 配列)。初期値 0xFFFF=消去状態。
 *      ※配置は「Flash末尾」ではなく"リンカ任せ"(コード直後)。ただし 1KB整列かつサイズ=
 *        1KBの倍数なので専用セクタを丸ごと占有し、消去が他データに波及しないことは保証される
 *        (位置に依存しない)。const オブジェクトなのでリンカが排他確保し重複もしない。
 *      ※注意: 配置はファーム容量で動くため、アドレスはビルドごとに変わりうる。保存データは
 *        本バイナリに0xFF初期値として含まれる=再書込で消える(ファーム更新をまたいで保存を
 *        残したい場合は、別途リンカスクリプトで固定領域を確保する必要がある)。
 *    ・保存 = 先頭から最初の空(0xFFFF)セルへ 2バイト書込(追記)。1保存=1書込。
 *    ・現在値 = 先頭から見て最初の空セルの「手前」= 最後に書いた値。
 *    ・満杯なら 2 セクタ(1KB×n)を消去して先頭から再利用(循環)。
 *  → 消去は 1024 保存に1回だけ。「同番地を毎回消去」に比べ消去回数 約1/1024、
 *     フラッシュ寿命 約1024倍。
 *
 *  フラッシュ物理則: ビットは 1→0 のみ書込可(0→1 は消去でのみ)。消去単位=1KB。
 *  領域は 1KB 整列・サイズ=1KBの倍数なので専用セクタ群を丸ごと占有し、
 *  消去が他データ(コード/定数)へ波及しない。
 *
 *  ※標準モード(PG=2バイト書込 / PER=1KB消去)を使用。実機での書込/消去/摩耗は
 *    未検証(このプロジェクト全体が実機未検証)。
 */
#ifndef STORE_H
#define STORE_H

#include "ch32fun.h"
#include "config.h"

#define STORE_SECTOR_BYTES  1024u
#define STORE_CELLS         (STORE_SIZE_BYTES / 2u)

/* 予約領域は 1KB(消去単位)の倍数、値は空(0xFFFF)と衝突しないこと */
_Static_assert((STORE_SIZE_BYTES % STORE_SECTOR_BYTES) == 0u,
               "STORE_SIZE_BYTES は 1KB の倍数にすること");
_Static_assert(LEVELS < 0xFFFFu, "LEVELS が 0xFFFF(空セル)と衝突");

/* 恒久領域を linker に確保させる。1KB整列 & サイズ=1KBの倍数 → 専用セクタを占有。
 * const だが書込はフラッシュコントローラ経由で行い、読出しは volatile ポインタ経由。 */
static const uint16_t g_store[STORE_CELLS]
    __attribute__((aligned(STORE_SECTOR_BYTES), used))
    = { [0 ... STORE_CELLS - 1] = 0xFFFFu };

/* フラッシュコントローラ(FLASH->ADDR)と書込ポインタは 0x08000000 ベースの物理
 * アドレスを要求する(ch32fun flashtest 参照)。リンクは ORIGIN=0 起点なので、
 * g_store のオフセットを 0x08000000 に載せ替えて物理アドレスにする。 */
#define FLASH_PHYS_BASE   0x08000000u
#define STORE_BASE_ADDR   (FLASH_PHYS_BASE | ((uint32_t)(uintptr_t)g_store & 0x1FFFFu))
#define STORE_MEM         ((const volatile uint16_t *)STORE_BASE_ADDR)

static inline void flash_wait(void)   { while (FLASH->STATR & FLASH_STATR_BSY); }
static inline void flash_unlock(void) { FLASH->KEYR = FLASH_KEY1; FLASH->KEYR = FLASH_KEY2; }
static inline void flash_lock(void)   { FLASH->CTLR |= FLASH_CTLR_LOCK; }

/* 1KB ページ消去 (標準 PER) */
static void flash_erase_1k(uint32_t addr)
{
    flash_wait();
    FLASH->CTLR |= FLASH_CTLR_PER;
    FLASH->ADDR  = addr;
    FLASH->CTLR |= FLASH_CTLR_STRT;
    flash_wait();
    FLASH->CTLR &= ~FLASH_CTLR_PER;
}

/* 2バイト(ハーフワード)書込 (標準 PG) */
static void flash_prog_hw(uint32_t addr, uint16_t data)
{
    flash_wait();
    FLASH->CTLR |= FLASH_CTLR_PG;
    *(volatile uint16_t *)addr = data;
    flash_wait();
    FLASH->CTLR &= ~FLASH_CTLR_PG;
}

/* 先頭から最初の空(0xFFFF)セルの index を返す(=末尾なら STORE_CELLS) */
static uint32_t store_first_empty(void)
{
    const volatile uint16_t *c = STORE_MEM;
    uint32_t i = 0;
    while (i < STORE_CELLS && c[i] != 0xFFFFu) i++;
    return i;
}

/* 保存する状態(16bit): bit15=ON, bit0..6=level(0..LEVELS)。
 *   ON &  level → 0x8000|level (0x8000..0x8040)
 *   OFF & level → level        (0x0000..0x0040)
 * いずれも空セル 0xFFFF とは衝突しない(最大 0x8040)。 */
#define STORE_PACK(on, level)  ((uint16_t)(((on) ? 0x8000u : 0u) | ((level) & 0x7Fu)))
#define STORE_ON(v)            (((v) & 0x8000u) ? 1u : 0u)
#define STORE_LEVEL(v)         ((uint8_t)((v) & 0x7Fu))

/* 現在の状態(パック値)をロード。空(初回)なら 0(=OFF/level0)。 */
static uint16_t store_load(void)
{
    const volatile uint16_t *c = STORE_MEM;
    uint32_t i = store_first_empty();
    if (i == 0) return 0;                       /* 全部空 = 初回起動 → OFF/0 */
    return c[i - 1];                             /* 最後に書いた値(呼び側で分解/クランプ) */
}

/* 状態(パック値)を保存(追記)。満杯なら全セクタ消去してから先頭へ。 */
static void store_save(uint16_t state)
{
    uint32_t i = store_first_empty();
    flash_unlock();
    if (i >= STORE_CELLS) {                      /* 満杯 → 循環: 全セクタ消去 */
        for (uint32_t a = STORE_BASE_ADDR; a < STORE_BASE_ADDR + STORE_SIZE_BYTES;
             a += STORE_SECTOR_BYTES) {
            flash_erase_1k(a);
        }
        i = 0;
    }
    flash_prog_hw(STORE_BASE_ADDR + i * 2u, state);
    flash_lock();
}

#endif /* STORE_H */
