# LightBox Configuration Reference Guide (config.h)

**English** · [日本語](CONFIG_REFERENCE_JP.md) · [← README](../README.md)

Full reference for all parameters in `source/config.h`. Rebuild after changing any values (refer to [README](../README.md)).
Pins are named using ch32fun pin nomenclature (`PA1`, `PC2`, `PD0`, etc.).

## Pin Assignments

| Item | Default | Description |
|------|---------|-------------|
| `ENC_A_PIN` | `PA1` | Encoder Phase A (Internal Pull-Up) |
| `ENC_B_PIN` | `PC1` | Encoder Phase B (Internal Pull-Up) |
| `ENC_SW_PIN` | `PA2` | Push Button Switch (Internal Pull-Up, Active Low) |
| (PWM Output) | `PC2` | Main LED = Fixed to TIM2_CH2 (remap1) |

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
| **[EXT]** `TEMP_SENSE_ANALOG` | `2` | Sensor ADC Channel (`2` = PC4, `7` = PD4, `0` = PA2, `1` = PA1) |
| **[EXT]** `TEMP_SENSE_PIN` | `PC4` | Sensor input pin (Physical pin corresponding to `TEMP_SENSE_ANALOG`) |
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
| `WARN_LED_PIN` | `PC4` | Warning LED pin (Mutually exclusive with Nightlight/External Sensor on SOP8 packages) |

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

## WS2812 / SK6812 Nightlight

| Item | Default | Description |
|------|---------|-------------|
| `NIGHTLIGHT_ENABLE` | `1` | Enables nightlight feature |
| `WS_PORT` | `GPIOD` | Data line GPIO port |
| `WS_PINNUM` | `0` | Data line pin number (`0` = PD0) |
| `WS_COUNT` | `1` | Number of addressable LEDs |
| `NIGHTLIGHT_PERIOD_MS` | `4000` | Duration for 1 full bright $\rightarrow$ dim breathing cycle in ms (**2 to 60000**). Upper limit provides safety margin against 32-bit SysTick wrap ($\approx 89.5\text{s}$) |
| `NIGHTLIGHT_INTERVAL_MS` | `3000` | Complete OFF delay following breathing cycle in ms (**$\le 60000$**) |
| `NIGHTLIGHT_REFRESH_MS` | `30` | Refresh rate for data transmission (smoothness) |
| `NIGHTLIGHT_BOOT_TEST_MS` | `1500` | Illuminates WS LED with configured color briefly at startup (for wiring diagnostics; `0` = disabled) |
| `WS_ORDER` | `WS_ORDER_GRB` | Chip color byte order: `WS_ORDER_GRB`, `RGB`, `GRBW`, or `RGBW` |
| `WS_MAX_COLOR` | `0x403008` | Maximum color intensity. RGB = **`0xRRGGBB` (6 hex digits)** / RGBW = **`0xRRGGBBWW` (8 hex digits)** |

### Color Order & RGBW Configuration Examples
- Standard WS2812 (GRB): `#define WS_ORDER WS_ORDER_GRB` / `#define WS_MAX_COLOR 0x403008`
- RGB-ordered variants: `#define WS_ORDER WS_ORDER_RGB`
- **SK6812 RGBW**: `#define WS_ORDER WS_ORDER_GRBW` / `#define WS_MAX_COLOR 0x40300810` ($R=0\text{x}40, G=0\text{x}30, B=0\text{x}08, W=0\text{x}10$)
- *Note:* The hex length of `WS_MAX_COLOR` must strictly match the selected `WS_ORDER` (with or without White channel).

## Quick Pin Map (Default, **Full functionality on SOP8 / J4M6**)
`PA1` = ENC_A / `PC1` = ENC_B / `PA2` = ENC_SW / `PC2` = PWM / `PD1` = SWIO.  
The remaining pin **`PC4` can be assigned to ONLY ONE of the following (mutually exclusive)**:
- WS2812 Nightlight
- External Temperature Sensor
- Warning LED

*Note:* Software-based features such as die-estimated temperature, thermal cutoff, thermal throttling, watchdog timer, and EMI spread spectrum **do not require any dedicated GPIO pins**. If you require multiple pin-bound features simultaneously, switch to a package with more pins such as TSSOP20 or QFN (e.g., CH32V003F4P6).