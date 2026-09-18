# Building LightBox with PlatformIO

> **Status**: This configuration is **untested** in this environment (PlatformIO is not installed).
> **The verified standard build is `source/Makefile` ([README](README.md) ①)**.
> PlatformIO support is provided purely as a convenient scaffold.

## Background

`ch32fun` is not an official PlatformIO framework. Therefore, this `platformio.ini` uses the **community platform-ch32v toolchain** to set up a "bare build without a framework" for `ch32fun` (`ch32fun.c` provides the startup code, interrupt vectors, and `SystemInit`).

Since the CH32V003 uses **RV32EC** (`-march=rv32ec -mabi=ilp32e`), a **toolchain with rv32ec `libgcc` support** (such as xPack `riscv-none-elf`) is required. SysGCC (rv64) alone will not work.

## Setup

1. **Place `ch32fun`** (in the project root directory `./ch32fun`):
   ```bash
   cd LightBox
   git clone https://github.com/cnlohr/ch32fun ch32fun
   ```
2. **Linker Script**: Use the included `ldscript/lightbox_ch32v003.ld` (preprocessed `ch32fun.ld` targeting CH32V003). If you update `ch32fun`, regenerate it with:
   ```bash
   riscv-none-elf-gcc -E -P -x c -DTARGET_MCU=CH32V003 -DMCU_PACKAGE=1 \
     -DTARGET_MCU_LD=0 -DTARGET_MCU_MEMORY_SPLIT= \
     ch32fun/ch32fun/ch32fun.ld > ldscript/lightbox_ch32v003.ld
   ```
3. **Toolchain**: If the toolchain bundled with `platform-ch32v` lacks `rv32ec` multilib support, override it with xPack in `platformio.ini`:
   ```ini
   platform_packages =
       toolchain-riscv @ file:///C:/Users/<you>/AppData/Local/xpack-riscv-none-elf-gcc/14.2.0-3
   ```
4. **Build**:
   ```bash
   pio run
   ```

## Flashing

For the CH32V003, using WCH-LinkE + `minichlink` is the most reliable method:
```bash
# Building and flashing via Makefile is the most reliable method
cd source && make CH32FUN=../ch32fun/ch32fun
```
PlatformIO's `pio run -t upload` depends on your environment (requires protocol support for WCH-Link).
If it fails, flash the generated `.bin` or `.hex` file using `minichlink`.

## Troubleshooting

| Symptom | Solution |
|---|---|
| `undefined reference to __muldi3`, etc. | Missing `rv32ec` `libgcc` → Point to the xPack toolchain (Step 3). |
| Linker exceeds FLASH/RAM limits | Verify that the linker script targets CH32V003 (16K/2K) and regenerate it. |
| Fails to boot or hangs | Verify the bare build configuration (includes `ch32fun.c`, uses `-nostdlib` and the proper `ldscript`). |
| Unresolved build issues | **Use the verified Makefile build.** PlatformIO is optional. |