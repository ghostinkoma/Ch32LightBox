# LightBox Specification Document (SPEC)

**English** · [日本語](SPEC_JP.md) · [← README](../README.md)

Functional specification for the CH32V003 LED Dimmer Controller. Architectural rationale and design decisions can be found in [DESIGN.md](DESIGN.md), and configuration references in [CONFIG_REFERENCE.md](CONFIG_REFERENCE.md).

## 1. Target & Architecture
- **MCU**: CH32V003 (Recommended package: **F4P6 TSSOP20**; SOP8 supports core functionality only)
- **Framework**: ch32fun (self-contained)
- **Clock**: HCLK 48MHz, SysTick = HCLK (48MHz) *Required for WS2812 driver timings*
- **Inputs**: Rotary Encoder (A/B + Push Switch), External Temperature Sensor (NTC, etc.)
- **Outputs**: Main Output PWM 1-channel (LED), Over-temperature Warning GPIO, WS2812/SK6812 Night Light

## 2. Dimming Curve (CIE 1931 $L^*$)
- Step $i \in [0..64]$ is allocated at equal intervals of lightness $L^* \rightarrow$ inverse mapping $L^* \rightarrow \text{luminance } Y \rightarrow \text{duty cycle}$ (1 click = perceptually uniform change).
  - $L^* \le 8: Y = L^* / 903.3$ (linear toe) / $L^* > 8: Y = ((L^* + 16) / 116)^3$
- Uses integer arithmetic only (cubic terms computed using `uint64_t`), without `libm` or floating-point libraries. Shared between the main output and night light (`cie.h`).

## 3. PWM Output
- `TIM2_CH2` (remap 1) = `PC2`, 16-bit counter. Default `PWM_TOP = 4095` ($\approx 12\text{kHz}$). Maximum limit: 65,535.
- **Output pin selectable via `PWM_PIN`** (default `PC2`=TIM2_CH2; also `PC1`=TIM2_CH4 / `PA1`=TIM1_CH2 / `PC4`=TIM1_CH4). `source/pins.h` derives the timer/channel/remap and validates it at compile time. Only `PC2` is hardware-verified; other pins are compile-supported.

## 4. Encoder & Debouncing
- **Full-step State Machine (Ben Buxton)**: Emits $\pm 1$ only upon completing a valid state transition path $\rightarrow$ suppresses single-phase bounce artifacts.
- **Direction Reversal Lockout**: If a step in the opposite direction occurs within `ENC_REVERSAL_LOCK_MS` (default 5ms), it is treated as physical bounce and discarded. Steps in the same direction are unrestricted to allow fast spinning.
- Supports half-step hardware (`ENC_HALF_STEP`) and direction inversion (`ENC_REVERSE`).
- **Fast-spin Snap**: Performing `SNAP_CLICKS` steps in the same direction within `SNAP_WINDOW_MS` snaps brightness immediately to 100% or 0%.
- **Push Switch**: Time-based integration debounce (`SW_DEBOUNCE_MS`). Short press toggles power ON/OFF.

## 5. Non-Volatile Brightness Memory (Wear Leveling)
### Storage Strategy
- Dedicated reservation of `STORE_SIZE_BYTES` (default 2KB) via a 1KB-aligned `const` array. Single-cell append log (last-written-wins).
  - *Note*: Placement is managed by the linker (immediately following code execution space) rather than hardcoded to the flash boundary. Because it is **1KB aligned and sized as a multiple of 1KB**, it exclusively occupies dedicated flash sectors, guaranteeing that erase operations will not affect adjacent code regardless of absolute address.
  - *Note*: Memory address may shift across firmware compilation sizes. Default data is embedded as `0xFF` in the binary payload. Maintaining persistence across firmware re-flashing requires fixing the memory location in the linker script.
- **Save Operation**: Appends 2 bytes to the first empty cell (`0xFFFF`) from the array start (1 save = 1 write). Active value resides immediately before the first empty cell. When full, 2 sectors are erased and rotation resets to the start. Default initial state (`0xFFFF` array) restores as OFF / Brightness 0.
- **Power State Persistence**: Packs both state and level into a single cell (`bit15 = ON`, `bit[0..6] = level`; maximum value `0x8040`, non-conflicting with empty marker `0xFFFF`).
  $\rightarrow$ Remembers power state across power cycles and restores to OFF if powered down while OFF.
- **Write Timing**: Commits once after state (`ON/OFF + level`) remains unchanged for `STORE_COMMIT_MS` (5 seconds).
  *Note: Powering off within 5 seconds of an adjustment leaves the latest change uncommitted to minimize flash wear.*

### Flash Endurance Estimation
- CH32V003 Flash Erase Endurance $\approx \mathbf{10,000 \text{ cycles/sector}}$ (WCH datasheet nominal value).
- $2\text{KB} / 2\text{ bytes} = \mathbf{1024 \text{ records/erase cycles}}$. Therefore:

  $$\text{Estimated Total Write Cycles} \approx 1024 \times 10,000 = \mathbf{\approx 10,240,000 \text{ cycles}}$$

- **Assessment**: Exceeds 100,000 write cycles by approximately 100 times. $\rightarrow$ **The default 2KB allocation is sufficient; no flash expansion required.**
  - Even assuming a conservative sector limit of 1,000 cycles, $1024 \times 1000 \approx 1,024,000$ operations.
  - The 5-second delayed write filter consolidates each adjustment session into a single commit, reducing real-world consumption far below theoretical limits.
- To expand log retention, scale `STORE_SIZE_BYTES` in 1KB increments ($4\text{KB} = 2048$ records).

### Handling Zero Level Edge Cases
- Brightness levels range from $0..64$. **`0x0000` (lights off) is a valid data state**, whereas empty cells read `0xFFFF`. Because these values are distinct, zero values restore accurately. Verified on host test harness across edge cases (single 0, consecutive 0s, terminal cell 0, alternating 0/non-zero, full wraparound 0).

## 6. Thermal Fail-Safe (Forced Cutoff & Thermal Throttling)
> **★ Default Status: OFF (`TEMP_PROTECT_ENABLE 0`)**. Die temperature estimation previously caused false triggers on physical hardware during full output due to mathematical model drift without an physical sensor.
> $\rightarrow$ **Die estimation is discarded**. Thermal protection should only be enabled when using external NTC physical sensing (Section 6.2).
> The following describes the system behavior when enabled.

Selectable temperature source via `TEMP_SOURCE`:

### 6.1 Internal Die Temperature Estimation (Deprecated / Reference)
- ★ **CH32V003 lacks an integrated internal die temperature sensor** (ADC channels only expose `ADC_CH8 = Vrefint` and `ADC_CH9 = Vcalint`). Thermal state is estimated via a thermal rise model:
  - $T_{\text{target}} = T_{\text{ambient}} (28^\circ\text{C}) + \left(\frac{\text{effective\_duty}}{\text{PWM\_TOP}}\right) \times T_{\text{rise\_full}} (60^\circ\text{C})$
  - $T_{\text{est}}$ follows $T_{\text{target}}$ using a first-order lag filter with time constant `DIE_TAU_MS`.
  - Uses effective duty cycle (reflecting active cutoffs/throttling) as input, enabling estimation decay upon shutdown.
- Zero external component requirement. Output values are indicative reference approximations.

### 6.2 External Analog Sensor (Physical Sensing)
- Thermally couples an NTC thermistor to the switching MOSFET and samples via ADC (`TEMP_SENSE_PIN`; the ADC channel is derived automatically). Calibrated via linear approximation coefficients `TEMP_CAL_*`.

### 6.3 Protection Logic (Both Sources)
- Exceeding `WARN_TEMP_C` ($80^\circ\text{C}$) $\rightarrow$ **Forces main output PWM (MOSFET) to 0** to mitigate thermal hazards.
- Resumes operation once temperature drops below hysteresis limit `WARN_TEMP_HYST_C`.
- **Thermal Throttling**: Every `THERMAL_TRIP_N` ($n$) trip/recovery cycles, maximum allowed output duty cumulative drops by `THERMAL_THROTTLE_PCT` ($j\%$), capped at `THERMAL_THROTTLE_MAX`.
  $\rightarrow$ Automatically limits power dissipation under sustained thermal loads until thermal equilibrium is maintained below the threshold.
- Optional indicator output via `WARN_LED_ENABLE` (asserted High during over-temperature condition).

## 5b. Soft Start (Fade-In / Fade-Out)
- Smoothly fades output to target brightness upon power toggle (push switch) or system boot.
- **Perceptual lightness $L^*$ is linearly interpolated over time**, converting each step to PWM duty via CIE 1931 $\rightarrow$ visually linear dimming transitions.
- Independent durations via `SOFT_START_ON` / `SOFT_START_OFF` (in milliseconds; 0 = immediate, max 65,535ms).
- Direct rotary encoder adjustments bypass soft start for immediate response. Rotary or switch inputs during an active fade transition smoothly recalculate target trajectory from current dynamic output level without abrupt jumps.
- Night light sequence initiates only after soft-off fade completely reaches zero brightness.

## 6b. Watchdog Timer (IWDG)
- Automatic system reset recovery in event of firmware execution stall. Timeout configurable via `WDT_TIMEOUT_MS` (default 2000ms, range $\approx 2..\text{8190ms}$).
- Driven by low-speed internal oscillator (LSI $\approx 128\text{kHz}$) divided by 256. Refreshed at top of main loop execution cycle.
- Timeout duration validated to exceed longest blocking operation (flash page erase/write cycles $\approx \text{several ms}$).
- *Note: If `DEBUG_LOG = 1` and SWD host is disconnected, unbuffered `printf` blocking may trigger watchdog resets.*

## 6c. EMI Mitigation (High-Current Load Optimization)
- **Slew Rate Control**: Configures GPIO speed `PWM_SLEW_MHZ` (2, 10, or 30MHz) to soften edge transitions and suppress high-frequency harmonics (2MHz recommended for high-current driving).
- **Spread Spectrum**: `PWM_SPREAD_SPECTRUM = 1` applies micro-dither ($\pm\text{PWM\_SPREAD\_RANGE}$) to auto-reload register `ATRLR` to spread fundamental frequency peaks. Duty ratios dynamically track period adjustments, **maintaining constant light output**. Dither period managed via `PWM_SPREAD_PERIOD_MS`.

## 7. WS2812 / SK6812 Night Light
- Active exclusively when main lighting output is fully extinguished (`!g_on || level == 0`).
- **Operating Cycle**: Executes smooth bright-to-dark pulse over `NIGHTLIGHT_PERIOD_MS` ($n$) using triangular wave mapped through CIE dimming curves.
- Enters complete off state for `NIGHTLIGHT_INTERVAL_MS` ($i$) after each cycle before repeating. Disables immediately upon main light activation.
- Time tracking evaluates on millisecond scale prior to scaling to prevent integer overflow in `Ticks_from_Ms(period) = period \times 48000` ($period \gtrsim 89.5\text{s}$). Cycle/interval configurations constrained to $\le 60,000\text{ms}$ to maintain safety margin against 32-bit SysTick roll-over ($\approx 89.5\text{s}$).
- Configurable maximum color `WS_MAX_COLOR` (RGB format `0xRRGGBB` / RGBW format `0xRRGGBBWW`).
- Selectable color channel ordering via `WS_ORDER` (GRB, RGB, GRBW, RGBW).
- Supports SK6812 RGBW LED protocol. Configured on `WS_DIN_PIN` (GPIO port/number derived automatically) for `WS_COUNT` elements.
- Frame updates emitted at `NIGHTLIGHT_REFRESH_MS` intervals via `ws2812b_simple.h` (requires `SysTick = HCLK`).

## 8. Boot Sequence
1. Execute `SystemInit` $\rightarrow$ Initialize GPIO and encoder hardware peripherals.
2. Generate CIE 1931 lookup tables (LUT).
3. Restore state and level from non-volatile flash storage (default initial state = level 0, `g_on = level > 0`).
4. Initialize PWM peripheral (applying restored state).
5. Initialize Temperature ADC / Night Light control logic.
6. Enter main execution loop (Encoder processing $\rightarrow$ Switch debounce $\rightarrow$ Temperature checking $\rightarrow$ Night Light state engine $\rightarrow$ Flash store commit processing).

## 9. Verification & Status — ✅ Verified on Physical Hardware (In Continuous Operation)
- **★ Hardware Execution Verified (2026-09-16 to present)**: Flashed and operational on target PCB (`LightBox`). 
  - Verified Night Light function (SK6812 / OST45050C1A-W).
  - Verified CIE brightness control, rotary acceleration ($3\times$), and snapping.
  - Verified Soft-Start / Soft-Off transition curves.
  - **12-hour continuous operation in ON and OFF states validated without anomalies** (disabling uncalibrated thermal protection eliminated false shutdowns).
- **Host Unit Tests Passed**: CIE calculations, state machine transitions, flash boundary storage handling, night light curves, channel ordering, acceleration, and thermal modeling.
- **Build Verification**: Zero warnings across configuration permutations. Memory usage footprint: Flash 50%, RAM 3.5%.
- **Unverified Edge Cases**: Long-term flash endurance wear limit testing (design target $\approx 10^7$ writes), physical external NTC thermal protection behavior (default OFF), PlatformIO toolchain integration.
- Hardware specifications, schematics, and BOM detailed in [HARDWARE.md](HARDWARE.md).