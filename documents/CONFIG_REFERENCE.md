# LightBox Configuration Reference Guide (config.h)

**English** · [日本語](CONFIG_REFERENCE_JP.md) · [← README](../README.md)

Full reference for all parameters in `source/config.h`. Rebuild after changing any values (refer to [README](../README.md)).
Pins are named using ch32fun pin nomenclature (`PA1`, `PC2`, `PD0`, etc.).

## Pin Assignments (one macro per feature)

Each feature is assigned with a **single pin macro**. `source/pins.h` derives the GPIO
port/number, ADC channel, and PWM timer/channel/remap automatically, and rejects capability
violations (non-PWM / non-ADC pins) and pin conflicts (two features on one pin) at **compile
time** (`_Static_assert`). SOP8 (J4M6) pins: `PA1 PA2 PC1 PC2 PC4 PD1(SWIO)`.

| Item | Default | Description |
|------|---------|-------------|
| `PWM_PIN` | `PC2` | Main LED PWM output. PWM-capable pins only: `PC2`(TIM2_CH2) / `PC1`(TIM2_CH4) / `PA1`(TIM1_CH2) / `PC4`(TIM1_CH4). **`PC2` is the hardware-verified default; other pins are compile-supported but not hardware-verified** |
| `ENC_A_PIN` | `PA1` | Encoder Phase A (Internal Pull-Up) |
| `ENC_B_PIN` | `PC1` | Encoder Phase B (Internal Pull-Up) |
| `ENC_SW_PIN` | `PA2` | Push Button Switch (Internal Pull-Up, Active Low) |

## PWM (16-bit TIM2)

| Item | Default | Description |
|------|---------|-------------|
| `PWM_TOP` | `4095` | Resolution - 1. Frequency = `48MHz / (TOP + 1) / (PSC + 1)`. Max 65535. Increasing it results in smoother dimming at a lower frequency. |
| `PWM_PSC` | `0` | Prescaler |

## Dimming Levels

| Item | Default | Description |
|------|---------|-------------|
| `LEVELS` | `64` | Number of steps (0 = Off to LEVELS = Max) |
| `LEVEL_INIT` | `32` | Startup brightness level when persistent store is disabled (only active when `STORE_ENABLE=0`) |

> The dimming curve (CIE L*) is fixed and parameterless (it directly uses the formula defined for perceptually linear light adjustment).

## Encoder / Debounce

| Item | Default | Description |
|------|---------|-------------|
| `ENC_REVERSE` | `0` | Set to `1` to swap CW/CCW direction (accommodates hardware/wiring variations) |
| `ENC_HALF_STEP` | `0` | `0` = Full-step (4 transitions = 1 step) / `1` = Half-step (2 transitions = 1 step) |
| `ENC_ACCEL_ENABLE` | `1` | Acceleration: Turning quickly multiplies step count |
| `ENC_ACCEL_FACTOR` | `3` | Step multiplier per click during fast turns (3x) |
| `ENC_ACCEL_WINDOW_MS` | `50` | If time since last click in the same direction is less than this value (ms), enable acceleration |
| `ENC_REVERSAL_LOCK_MS` | `5` | Reverse steps occurring within this time window are ignored as bounce. Shorten if fast inputs drop clicks, lengthen if chatter remains |
| `SW_DEBOUNCE_MS` | `20` | Debounce stabilization time for push switch |

## Fast Turn Snap

| Item | Default | Description |
|------|---------|-------------|
| `SNAP_WINDOW_MS` | `500` | Detection time window (ms) |
| `SNAP_CLICKS` | `6` | Number of clicks in the same direction required within the window to trigger snap ($\ge 2$) |

## Operational Options

| Item | Default | Description |
|------|---------|-------------|
| `WAKE_ON_TURN` | `1` | Turning the encoder while off automatically turns the light on (only if resulting level > 0; turning towards dark keeps level at 0 and stays off). Set to `0` to require button push to turn on |
| `PWM_ON_TIME_S` | `0` | **Auto-off timer (seconds)**: turns the main output off (soft fade) this many seconds after it was switched on. `0` = disabled (stays on). Any encoder/switch activity resets (extends) the countdown. Lowers average on-time → saves battery, and adds an auto-off convenience on continuous power |
| `SOFT_START_ON` | `800` | Turn-on fade-in time (ms). `0` = Instantaneous, Max = 65535 ($\approx 65.5\text{s}$). Perceptually linear based on CIE |
| `SOFT_START_OFF` | `600` | Turn-off fade-out time (ms). `0` = Instantaneous, Max = 65535 |
| `DEBUG_LOG` | `0` | Set to `1` to enable SWD printf (development only). **Must be set to `0` for normal operation** (blocking printf can cause missed events) |

## Non-Volatile Brightness Store

| Item | Default | Description |
|------|---------|-------------|
| `STORE_ENABLE` | `1` | Brightness memory using wear-leveling. Set to `0` to disable |
| `STORE_SIZE_BYTES` | `2048` | Reserved Flash memory size (**must be a multiple of 1KB**). 2KB = 1024 records. Larger size increases Flash longevity |
| `STORE_COMMIT_MS` | `5000` | Delay time after level stabilizes before committing to Flash (ms) |

> Lifespan $\approx (\text{STORE\_SIZE\_BYTES} / 2) \times \text{Erase cycles}$. 2KB yields approximately 10 million saves (SPEC.md Section 5).

## EMI Mitigation (PWM)

| Item | Default | Description |
|------|---------|-------------|
| `PWM_SLEW_MHZ` | `2` | PWM output pin slew rate (GPIO speed): 2 / 10 / 30 MHz. Lower speeds soften edges and reduce harmonics (2 MHz recommended for high-current LEDs) |
| `PWM_SPREAD_SPECTRUM` | `0` | Set to `1` to dither PWM period for spread spectrum (brightness remains unaffected) |
| `PWM_SPREAD_RANGE` | `192` | ATRLR sweep range ($\pm\text{counts}$). **Must satisfy `PWM_TOP + RANGE` $\le 65535$ and `RANGE < PWM_TOP`** (ATRLR is 16-bit; violations are caught by compile-time assertions and clamped to $[1, 65535]$ at runtime) |
| `PWM_SPREAD_PERIOD_MS` | `2` | Dither update interval |

## Overheat Failsafe

| Item | Default | Description |
|------|---------|-------------|
| `TEMP_PROTECT_ENABLE` | **`0`** | Thermal protection (cutoff + throttling). **Default OFF** (die estimation omitted to prevent false cutoffs). Enable only when using physical NTC |
| `TEMP_SOURCE` | `TEMP_SOURCE_EXTERNAL` | Temperature source when enabled. **Physical NTC (`EXTERNAL`) is recommended**. `DIE` estimation is not recommended due to potential false cutoffs |
| **[DIE]** `DIE_AMBIENT_C` | `28` | Baseline ambient temperature at zero load ($^\circ\text{C}$) |
| **[DIE]** `DIE_RISE_AT_FULL_C` | `60` | Steady-state temperature rise at 100% duty cycle ($^\circ\text{C}$). *Adjust based on real hardware measurement* |
| **[DIE]** `DIE_TAU_MS` | `30000` | Thermal time constant (ms). Larger values smooth out temperature changes |
| **[EXT]** `TEMP_SENSE_PIN` | `PC4` | Analog sensor input pin. **ADC-capable pins only: `PA2`(ch0) / `PA1`(ch1) / `PC4`(ch2)** (the ADC channel is derived automatically) |
| **[EXT]** `TEMP_CAL_T0_C` | `25` | Calibration reference temperature |
| **[EXT]** `TEMP_CAL_ADC0` | `512` | Raw 10-bit ADC value at $T_{0\text{C}}$ (*Requires calibration*) |
| **[EXT]** `TEMP_CAL_SLOPE_X100` | `-300` | $\text{ADC counts} / ^\circ\text{C} \times 100$ (Signed, non-zero) (*Requires calibration*) |
| `WARN_TEMP_C` | `80` | Cutoff trigger threshold ($^\circ\text{C}$) |
| `WARN_TEMP_HYST_C` | `8` | Recovery temperature margin ($^\circ\text{C}$ drop required to restart; $0 < \text{HYST} < \text{WARN}$) |
| `WARN_TEMP_PERIOD_MS` | `200` | Sampling / update interval |
| `THERMAL_TRIP_N` | `3` | Number of cutoff $\rightarrow$ restart cycles before throttling engages ($n$) |
| `THERMAL_THROTTLE_PCT` | `20` | Output power reduction step during throttling (%) (accumulates every $N$ trips) |
| `THERMAL_THROTTLE_MAX` | `80` | Maximum power reduction limit (%) ($< 100$) |
| `WARN_LED_ENABLE` | `0` | Set to `1` to drive `WARN_LED_PIN` High during overheat conditions |
| `WARN_LED_PIN` | `PC4` | Warning LED pin (any SOP8 GPIO; conflicts with other features are caught at compile time) |

## Watchdog Timer

| Item | Default | Description |
|------|---------|-------------|
| `WDT_ENABLE` | `1` | Enable IWDG. Automatically resets MCU on hang |
| `WDT_TIMEOUT_MS` | `2000` | Reset threshold if watchdog is not refreshed within this duration ($\approx 2\text{ to }8190\text{ ms}$) |

> *Note:* If `DEBUG_LOG=1` and no SWD host is connected, `printf` blocking may trigger unintended Watchdog resets during development.

### External Sensor (EXTERNAL) Calibration Procedure (Linear Approximation)
$$\text{temp} = \text{TEMP\_CAL\_T0\_C} + \frac{(\text{adc} - \text{TEMP\_CAL\_ADC0}) \times 100}{\text{TEMP\_CAL\_SLOPE\_X100}}$$

1. Build and flash with `DEBUG_LOG=1`. (If you need to view raw ADC readings, temporarily add output for `adc` in `temp_update` inside `temp.h`).
2. Record raw ADC value at a known baseline temperature $T_1$ (e.g., room temperature $25^\circ\text{C}$) $\rightarrow$ Assign to `TEMP_CAL_ADC0` and `TEMP_CAL_T0_C`.
3. Record raw ADC value at a second temperature $T_2$ (e.g., $60^\circ\text{C}$ using a heat gun) $\rightarrow$ Calculate slope:
   $$\text{TEMP\_CAL\_SLOPE\_X100} = \frac{(\text{adc}_2 - \text{adc}_1) \times 100}{T_2 - T_1}$$
4. Set `WARN_TEMP_C` to target limit ($80^\circ\text{C}$). Although NTCs are non-linear, a 2-point calibration near the operating limit ($80^\circ\text{C}$) provides sufficient accuracy for over-temperature warnings.
5. Revert `DEBUG_LOG=0` for production release.

## Nightlight

Two nightlight types are selectable with `NIGHTLIGHT_TYPE`:
①`NL_TYPE_WS2812` (color, effect-priority — the existing WS2812/SK6812 driver) and
②`NL_TYPE_SINGLE` (single-color LED on a dedicated pin, blinked `NL_ON_MS` every `NL_PERIOD_S`).
Only **② single LED with `NL_USE_PWM=0`** is eligible for the deep low-power (Standby) mode below.

| Item | Default | Description |
|------|---------|-------------|
| `NIGHTLIGHT_ENABLE` | `1` | Enables nightlight feature |
| `NIGHTLIGHT_TYPE` | `NL_TYPE_WS2812` | ①`NL_TYPE_WS2812` (color/effect) or ②`NL_TYPE_SINGLE` (single-color LED, low-power) |
| **②** `NL_LED_PIN` | `PC4` | Single-LED dedicated pin (any SOP8 GPIO) |
| **②** `NL_PERIOD_S` | `5` | Blink period in **seconds** (`1..60`; `1..30` when deep low-power is on) |
| **②** `NL_ON_MS` | `100` | On-time per blink in ms (must be `< NL_PERIOD_S*1000`) |
| **②** `NL_USE_PWM` | `0` | `0` = GPIO on/off (lowest power, deep-sleep eligible) / `1` = soft-PWM breathing (effect; not deep-sleep) |
| **①** `WS_DIN_PIN` | `PC4` | WS2812 data line GPIO (any SOP8 pin; GPIO port/number derived automatically) |
| `WS_COUNT` | `1` | Number of addressable LEDs |
| `NIGHTLIGHT_PERIOD_MS` | `4000` | Duration for 1 full bright $\rightarrow$ dim breathing cycle in ms (**2 to 60000**). Upper limit provides safety margin against 32-bit SysTick wrap ($\approx 89.5\text{s}$) |
| `NIGHTLIGHT_INTERVAL_MS` | `3000` | Complete OFF delay following breathing cycle in ms (**$\le 60000$**) |
| `NIGHTLIGHT_REFRESH_MS` | `30` | Refresh rate for data transmission (smoothness) |
| `NIGHTLIGHT_BOOT_TEST_MS` | `1500` | Illuminates WS LED with configured color briefly at startup (for wiring diagnostics; `0` = disabled) |
| `WS_ORDER` | `WS_ORDER_RGBW` | Chip color byte order: `WS_ORDER_GRB`, `RGB`, `GRBW`, or `RGBW` (default matches the bundled SK6812 RGBW) |
| `WS_MAX_COLOR` | `0x40300810` | Maximum color intensity. RGB = **`0xRRGGBB` (6 hex digits)** / RGBW = **`0xRRGGBBWW` (8 hex digits)** |

### Color Order & RGBW Configuration Examples
- Standard WS2812 (GRB): `#define WS_ORDER WS_ORDER_GRB` / `#define WS_MAX_COLOR 0x403008`
- RGB-ordered variants: `#define WS_ORDER WS_ORDER_RGB`
- **SK6812 RGBW**: `#define WS_ORDER WS_ORDER_GRBW` / `#define WS_MAX_COLOR 0x40300810` ($R=0\text{x}40, G=0\text{x}30, B=0\text{x}08, W=0\text{x}10$)
- *Note:* The hex length of `WS_MAX_COLOR` must strictly match the selected `WS_ORDER` (with or without White channel).

## Feature ↔ Pin Assignment (config.h only, SOP8 / J4M6)

Assign each feature with its single pin macro in `config.h`. `source/pins.h` validates every
assignment at compile time. SOP8 pin capabilities:

| Pin | GPIO | ADC ch | PWM route (positive output) | Default use |
|-----|------|--------|------------------------------|-------------|
| `PA1` | GPIOA/1 | ch1 | TIM1_CH2 | ENC_A |
| `PA2` | GPIOA/2 | ch0 | — (CH2N complementary only) | ENC_SW |
| `PC1` | GPIOC/1 | — | TIM2_CH4 | ENC_B |
| `PC2` | GPIOC/2 | — | **TIM2_CH2** ★default PWM | PWM |
| `PC4` | GPIOC/4 | ch2 | TIM1_CH4 | Nightlight / Temp / Warn LED |
| `PD1` | GPIOD/1 | — | — | **SWIO (flash/printf reserved)** |

Rules enforced at compile time (and mirrored in the GUI config editor):
- **`PWM_PIN`** must be a PWM-capable pin (`PC2`/`PC1`/`PA1`/`PC4`). `PC2` is hardware-verified; others are compile-supported only.
- **`TEMP_SENSE_PIN`** (external temp) must be ADC-capable (`PA2`/`PA1`/`PC4`); the ADC channel is derived automatically.
- **No two enabled features may share a pin** (conflict → `_Static_assert` error). This subsumes the old "PC4 mutual exclusion" of Nightlight / External Sensor / Warning LED.
- Assigning **`PD1`** to a feature emits a `#warning` (it collides with SWIO flashing/`debugprintf`).

*Note:* Software-only features (die-estimated temperature, thermal cutoff/throttling, watchdog, EMI spread spectrum) **need no GPIO pin**. To run more pin-bound features simultaneously than SOP8 allows, switch to a larger package such as TSSOP20 / QFN (CH32V003F4P6) — that package's pin set is out of scope for this table.

## Deep Low-Power Mode (Standby + AWU) — experimental, hardware-unverified

For battery use, `LOW_POWER_MODE=1` puts the MCU into Standby between single-LED nightlight blinks
and wakes periodically via AWU; a push-switch (EXTI) wakes it to normal dimmer operation for a while.
Design rationale and the LED-driver discussion are in [POWER_AND_DRIVER.md](POWER_AND_DRIVER.md).

| Item | Default | Description |
|------|---------|-------------|
| `LOW_POWER_MODE` | `0` | `1` = deep-sleep nightlight. **Requires** `NIGHTLIGHT_TYPE=NL_TYPE_SINGLE`, `NL_USE_PWM=0`, `NL_PERIOD_S≤30`, `WDT_ENABLE=0` (enforced at compile time and in the GUI) |
| `LOWPWR_ACTIVE_WINDOW_S` | `30` | Seconds of normal operation kept after a push-switch wake before returning to sleep |

- **Reflash safety**: hold the push-switch while powering on → the device stays in normal mode (does not sleep), so SWD can attach for flashing.
- **`LOW_POWER_MODE=0` (default) is byte-identical to the previous firmware.** The Standby/AWU/EXTI path is experimental and **must be verified on hardware** (Standby wake behavior, AWU period, actual current).

## Battery Operation (supplementary — design estimates)

LightBox is primarily designed for **continuous power**; battery use is feasible for a low-duty
nightlight. The figures below are **design estimates** (confirm on hardware):

- **Nightlight LED**: a green LED via a **~1 kΩ** series resistor at 3 V ≈ **0.9 mA** (clearly
  visible for a nightlight). A ~2 mA option (510 Ω) is brighter but roughly **halves** battery life;
  1 kΩ is a good brightness/life balance.
- **Duty**: e.g. 100 ms on every 5 s.
- **`PWM_ON_TIME_S` (shipped)**: the auto-off timer lowers average on-time and thus battery drain,
  independent of any sleep mode — the concrete lever available today.
- **Deep low-power (`LOW_POWER_MODE`, experimental — now implemented)**: Standby + AWU periodic
  wake, push-switch (EXTI) wake, encoder pull-ups released to analog-input during sleep. For
  1 kΩ / 100 ms-per-5 s, a single **CR2032 (~220 mAh)** is estimated at **~3–4 months as implemented**
  (the MCU sleeps at 48 MHz during the on-time). Lowering the clock during the on-time (future)
  would extend this toward ~6.5 months. **Without** deep low-power (MCU at 48 MHz always) a coin
  cell lasts only ~a day. The GUI config editor shows this CR2032 estimate live (1 kΩ assumption).
- **Cell choice**: 2×CR2032 in *series* raises voltage (6 V) but **not capacity**, and 6 V exceeds
  the CH32V003 5.5 V maximum (needs regulation). For longer life prefer *parallel* cells (~440 mAh)
  or a larger cell (CR2450 ~600 mAh ≈ ~2 years est.). Add a 10–100 µF buffer cap to handle CR2032
  pulse load / internal resistance.