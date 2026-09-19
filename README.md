# LightBox — CH32V003 LED Dimmer Controller

**English** · [日本語](README_JP.md)

> **Status: ✅ Hardware Verified & Operational (as of 2026-09, no issues)** — All functions confirmed on both custom PCB (KiCad) and universal breadboard/perfboard. Verified nightlight, brightness control, soft start/off, 12-hour endurance test, and virtually zero heat generation with a ~1A LED.
> **Enclosed and currently in actual use inside a 3D-printed case designed in Fusion 360.**
>
> **License: Non-Commercial (CC BY-NC 4.0 compliant / Attribution required, modification allowed for non-commercial use, provided AS-IS without warranty)** → [LICENSE](LICENSE)

A single-channel PWM LED dimmer controlled via a rotary encoder.
Powered by the **CH32V003** microcontroller with a self-contained firmware based on [ch32fun](https://github.com/cnlohr/ch32fun).

To prioritize reproducing brightness as **perceived by the human eye**, the PWM duty cycle is generated using the **CIE 1931 L\* (lightness) curve**. One click produces a "perceptually equal" shift in brightness—slower changes at low levels and sharper changes at high levels—achieving a balanced response for human vision.

## Key Features

| Feature | Description |
|---|---|
| **CIE L\* Dimming** | Perceptually uniform brightness. 64 total steps, LUT generated at startup, runtime lookup via integer table. |
| **16-bit PWM** | TIM2, default 4095 steps / ~12 kHz. Adjustable resolution and frequency in `config.h`. |
| **Debouncing** | Full-step state machine (Ben Buxton) + direction reversal lockout (default 5 ms). No simple delays used. |
| **Fast-turn Snap** | Immediately sets duty to 100% or 0% if turned in the same direction $j$ times within $n$ ms. |
| **CW/CCW Support** | Swap rotation direction with a single line in `config.h`. |
| **Brightness Memory** | Flash wear leveling (dedicated 2 KB area, saved after 5 sec, ~1024x lifespan extension, saves ON/OFF state). |
| **Overheat Failsafe** (Default OFF) | Forcefully shuts off main PWM (MOSFET) when overheated → resumes when cooled down. Thermal throttles to $j$\% after $n$ cycles. **Die estimation disabled due to false triggers (`TEMP_PROTECT_ENABLE 0`)**; enable only when using an external NTC sensor. |
| **Watchdog** | IWDG. Automatically resets on system freeze for self-recovery. |
| **EMI Mitigation** | Configurable PWM slew rate (GPIO speed) + spread spectrum (period dithering, brightness invariant). |
| **Nightlight (WS2812 or single LED)** | When completely dark: ①WS2812/SK6812 breathes light↔dark on the CIE curve (RGB/RGBW, color order, max color), or ②a single-color LED blinks `x` ms every `n` s. |
| **Deep Low-Power (experimental)** | Optional battery mode (`LOW_POWER_MODE`): single-LED nightlight with the MCU in Standby between blinks (AWU periodic wake, push-switch EXTI wake). Default off; see CONFIG_REFERENCE. |
| **Soft Start** | Smoothly fades in/out to target brightness on power ON/OFF based on CIE curve (duration set in config, disabled if 0, max 65535 ms). |
| **Push Switch** | Short press toggles ON/OFF (time-based debouncing; smoothly tracks re-triggering during active fades). |

All configuration options are centralized in **`source/config.h`** (see [CONFIG_REFERENCE.md](documents/CONFIG_REFERENCE.md) for details).
Detailed specifications are in [SPEC.md](documents/SPEC.md), and design decisions are in [DESIGN.md](documents/DESIGN.md).

## Pinout (**All features fit on SOP8 / J4M6**)

| Pin | Signal | Function |
|---|---|---|
| PA1 | ENC_A | Encoder Phase A (Internal pull-up, Common=GND) |
| PC1 | ENC_B | Encoder Phase B (Internal pull-up, Common=GND) |
| PA2 | ENC_SW | Push Switch (Internal pull-up, Active Low) |
| PC2 | PWM | **Main LED Output** (default `PWM_PIN`=PC2 → TIM2_CH2 remap1) |
| PC4 | (Selectable) | **WS2812 Nightlight** / External Temp Sensor / Warning LED (Mutually exclusive, choose one) |
| PD1 | SWIO | Programming / debugprintf (Reserved) |

> **Feature ↔ pin assignment is done entirely in `config.h`** (one pin macro per feature: `PWM_PIN`, `ENC_*_PIN`, `WS_DIN_PIN`, `TEMP_SENSE_PIN`, `WARN_LED_PIN`). `source/pins.h` validates capabilities (PWM-/ADC-capable pins) and pin conflicts at compile time, and the LightBox Studio config editor mirrors the same checks. See the “Feature ↔ Pin Assignment” section of [CONFIG_REFERENCE.md](documents/CONFIG_REFERENCE.md).

> **Temperature protection is OFF by default** (die estimation rejected due to false shutdowns). Watchdog and EMI mitigations **require no external pins**, allowing PC4—the only remaining free pin on SOP8—to be used for the nightlight.
> If an external physical temperature sensor or warning LED is needed, allocate PC4 accordingly (mutually exclusive with the nightlight). To use more features simultaneously, switch to TSSOP20/QFN (F4P6) and assign individual pins in `config.h`.

## Connecting the LED

- **Recommended**: PC2 → MOSFET gate (e.g., AO3400 / 2N7002), driving the LED on the low side via the drain.
- **Simplified**: Low-current LEDs can be connected directly via `PC2 → [Resistor] → LED → GND` (Strictly respect $I_{\max} \approx 8\text{ mA}$ per pin).
- **Overheat Protection (Default OFF)**: The CH32V003 lacks an internal die temperature sensor. The initial duty-based "die temperature estimation" caused **false shutdowns (sudden turn-offs during continuous use)** because it was not a physical measurement. It has been **rejected (`TEMP_PROTECT_ENABLE 0`)**. If physical thermal protection is strictly required, attach a thermal-coupled NTC on the MOSFET to an ADC pin and run with `TEMP_PROTECT_ENABLE=1` + `TEMP_SOURCE=EXTERNAL`. See SPEC.md for details.

## Operation

- **Rotate Encoder**: $\pm 1$ step per click (perceptually equal spacing using CIE L*).
- **Fast Turn** (Default: 6+ clicks within 0.5s): Snaps to full brightness (100%) in the bright direction, or turns off (0%) in the dark direction.
- **Push Switch Short Press**: Toggles ON/OFF (retains last brightness level).
- **Auto Memory**: Defaults to 0 on first boot. Setting a brightness and leaving it untouched for 5 seconds automatically saves it to flash for next startup restoration.
- **Soft Start**: Fades in/out smoothly to target brightness on power ON/OFF or boot up (fade duration configured in config).
- **Nightlight**: Breathes automatically when turned completely dark (turns off when main LED turns on/brightens).

## Build & Flash

### ① ch32fun + Makefile (Verified Standard Build)

Dependencies: [ch32fun](https://github.com/cnlohr/ch32fun), RISC-V GCC (xpack riscv-none-elf recommended for `rv32ec libgcc`), WCH-LinkE.

```bash
cd source
make lightbox.bin CH32FUN=/path/to/ch32fun/ch32fun    # Build only
make             CH32FUN=/path/to/ch32fun/ch32fun    # Build -> Flash
```

Build memory footprints (Default config, all features ON, `DEBUG_LOG=0`): **Flash 8200 B / 16 KB (50%), RAM 184 B (9%)**.
Stripped footprint: Core only ~3 KB, + Nightlight ~3.6 KB. `DEBUG_LOG=1` adds ~1 KB.

> Set `DEBUG_LOG` to 1 only during development. `printf` is blocking and can stall if an SWD host is disconnected, causing dropped inputs in hot loops. Defaults to 0, which unlinks the printf infrastructure entirely.

### ② PlatformIO

See [PLATFORMIO.md](PLATFORMIO.md) (`platformio.ini` included).
*Note: The PlatformIO build has not been verified in this environment. The verified build method is the Makefile above.*

### ③ LightBox Studio (GUI — no VS Code required)

For a GUI-only workflow (`config.h` editing → compile → flash from a single menu), use the Java toolset in [tools/LightBoxStudio/](tools/LightBoxStudio/README.md). It downloads a self-contained toolchain on first run, so no command line or VS Code is required. See the [LightBox Studio README](tools/LightBoxStudio/README.md) for details.

## Directory Structure

```
LightBox/
├─ source/         Firmware source files
│  ├─ lightbox.c      Main application (main / encoder / PWM)
│  ├─ config.h        Centralized configuration (edit parameters here)
│  ├─ cie.h           CIE L* curve library (shared by main PWM & nightlight)
│  ├─ store.h         Non-volatile brightness storage (wear leveling)
│  ├─ temp.h          Temperature reading (external NTC, default OFF)
│  ├─ wdt.h           Watchdog timer (IWDG)
│  ├─ nightlight.h    WS2812/SK6812 nightlight driver
│  ├─ funconfig.h     ch32fun environment configuration
│  └─ Makefile
├─ documents/      SPEC.md / CONFIG_REFERENCE.md / HARDWARE.md / DESIGN.md (+ *_JP.md)
├─ tools/          LightBoxStudio/ = GUI toolset (config edit → build → flash, no VS Code)
├─ ldscript/       Linker script for PlatformIO (lightbox_ch32v003.ld)
├─ PCB/            KiCad board files (LightBox/ = Schematic/PCB; AertWork.png; KiCad/ = FDS5680 lib)
├─ platformio.ini / PLATFORMIO.md
├─ 3D_Models/      Enclosure (Fusion 360 design / STL files, 3D printed)
└─ LICENSE         Non-commercial license (CC BY-NC 4.0 compliant)
```

## Enclosure

**Designed in Fusion 360 → 3D printed**. The board is assembled inside and **currently in operational use** (STL files available in `3D_Models/`).

## Hardware (PCB)

PCB layout, schematic, and artwork (with pin headers) completed → `PCB/LightBox/` (KiCad).
**Pin routing matches firmware exactly.** Details, BOM, and J1 pinout are in [HARDWARE.md](documents/HARDWARE.md).

- U1 CH32V003 (SOP8) / Q1 FDS5680 (N-MOSFET, low-side drive for main LED) / D1 SK6812 (OST45050C1A-W nightlight)
- R1 $100\ \Omega$ (Gate series resistor) / R2 $10\text{ k}\Omega$ (Gate pull-down = LED OFF during reset) / **D2 Pilot LED + R3 $100\ \Omega$ (series)**
- **Main (Power) LED is off-board** (Connected via LED OUT → J1). **Near-zero heat generation with ~1A LED** (heatsink added only as a precaution).
- J1 9-pin header: Breaks out Power / SWIO / Encoder / Signals for both LEDs. **Powered by $\text{VDD} = 5\text{ V}$**.
- Equivalent circuit constructed on a 1.27 mm pitch universal perfboard and verified working.

## Verification Status — ✅ Hardware Verified (Running Continuously)

- **★ Physical Board Operation Confirmed (2026-09-16 ~ present, running without issues)**: Board schematic/routing verified, flashing successful.
  - **Nightlight** (SK6812 / OST45050C1A-W, RGBW sequence)
  - **Brightness adjustment** (CIE L*, acceleration x3, fast-turn snap)
  - **ON/OFF soft start & soft off**
  - **12-hour ON / 12-hour OFF endurance test passed** (Disabling thermal protection eliminated false cutoffs, ensuring long-term stability)
- Build checks: Zero compiler warnings across all feature ON/OFF, RGBW, and half-step combinations (Makefile, ch32fun).
- Host simulation tests: CIE LUT, encoder state machine, flash wear store boundaries, nightlight curves, color ordering, acceleration, thermal model.
- Unverified: Long-term Flash wear (~10 million cycles by design), external NTC thermal protection (disabled by default), PlatformIO build environment.

## License

This project (original parts of firmware, PCB, enclosure, and documentation created by ghostinkoma) is licensed under a **Non-Commercial License (Creative Commons Attribution-NonCommercial 4.0 International / CC BY-NC 4.0 compliant)**. See [LICENSE](LICENSE) for full details.

- **Non-Commercial Use Only** (Contact the author for commercial licensing).
- **Attribution Required** (Must credit the original author **ghostinkoma** and "LightBox").
- **Modifications Allowed** (Free to adapt for non-commercial purposes; derivative works must inherit the same license conditions).
- **No Warranty / No Liability** (Provided "AS IS". The author accepts no responsibility for damages resulting from use).

*Note: Bundled/dependent third-party code (e.g., [ch32fun](https://github.com/cnlohr/ch32fun)) remains under its respective original licenses (MIT-x11 / NewBSD).*