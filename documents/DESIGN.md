# LightBox Design Notes

**English** · [日本語](DESIGN_JP.md) · [← README](../README.md)

CH32V003 LED Dimmer. Documenting design decisions and rationale to ensure third-party reproducibility.
Battery / low-power and LED-driver decisions are recorded separately in [POWER_AND_DRIVER.md](POWER_AND_DRIVER.md).

## Requirements
- Input: Locally connected rotary encoder
- Output: Single PWM (1ch LED)
- MCU: CH32V003
- Uses a 16-bit timer
- **Perceptual Brightness Priority**: Recreates human visual perception as top priority → adopts CIE 1931 L*
- Snap shortcut: Sets duty to 100% or 0% immediately if clicked $j+$ times in the same direction within $n\text{ ms}$ ($n, j$ configured via config)
- Encoder Direction: Supports both CW/CCW encoder types (configurable)
- Non-Volatile Brightness Memory: Stores brightness level upon power cycle with wear-leveling (initial 0, saves after 5 seconds)
- Over-temperature Fail-Safe: Thermal protection light (fail-safe with configurable thresholds)
- WS2812/SK6812 Night Light: "Breathing" illumination based on CIE standard when dark (configurable color, byte order, period)
- All settings consolidated in **config.h**

## Pin Assignment Rationale
Designed for the smallest package, **SOP8 (J4M6)**. Available GPIO pins are 5 in total: PA1, PA2, PC1, PC2, PC4 (excluding VDD/VSS, and preserving PD1 for SWIO debugging/flashing).

- PWM Output: **TIM2_CH2 (remap1) = PC2**. Tested and proven in the sister project FanConCH32V003, presenting the lowest risk.
- Encoder Inputs: A=PA1, B=PC1, SW=PA2 assigned from remaining pins, all configured as internal pull-ups.
- Reserve Pin: PC4 reserved (for a future 2nd channel or status LED).

Since larger packages maintain pin compatibility with this layout, SOP8 serves as the baseline design.

## Encoder Decoding and Debouncing
Mechanical rotary encoders inherently suffer from contact bounce. **Simple delay-based debouncing was rejected** as it blocks the main execution loop, worsening dropped events and failing to suppress bounce occurring during the delay itself. Instead, a **State Machine + Time Lockout** architecture is implemented.

### Evaluated Debounce Strategies
| # | Strategy | Effect | Adoption |
|---|---|---|---|
| 1 | Reject invalid transitions (2-bit simultaneous change) | Suppresses glitch noise | Embedded in State Machine |
| 2 | **Full-Step State Machine (Ben Buxton Method)** | Triggers 1 step only upon completing a valid path between detents. Intermediate bounce bounces back/forth without firing, ignoring single-phase chatter. | **Adopted (Core)** |
| 3 | **Direction Reversal Lockout (Default 5ms)** | Ignores reverse steps occurring within an extremely short time window as physical impossibilities (bounce). Same-direction steps are unrestricted. | **Adopted** |
| 4 | Fixed Period Sampling (e.g., 1ms) | Hides fast bounce between samples | Rejected: Coarse sampling drops fast turns. #2 + #3 suffice without loss. |
| 5 | Majority Voting / Integration per Phase | Phase-independent debouncing | Rejected: Redundant with #2. |
| 6 | Inter-step Refractory Period | General step throttling | Reduced into #3 (reversal only). Global throttling hampers fast spins. |
| 7 | Simple `delay()` | - | **Rejected** (Degrades responsiveness, drops steps, fails to resolve bounce). |
| 8 | Pushbutton Time-Based Integration Debounce | Replaces loop-count-dependent counters | **Adopted** |

### (2) Full-Step State Machine
Column index = Current 2-phase state `(A<<1)|B` (idle = `0b11`). States start at START and must traverse BEGIN $\rightarrow$ NEXT $\rightarrow$ FINAL in the **correct order** for either CW or CCW to emit `DIR_CW` or `DIR_CCW`. Phase bounce simply oscillates between BEGIN $\leftrightarrow$ START without triggering invalid steps. Half-step lookup tables are also available via `ENC_HALF_STEP` (unused tables excluded via `#if`).
- Host Verification: Clean CW×3 $\rightarrow$ [1,1,1] / CCW×3 $\rightarrow$ [-1,-1,-1]. Single detent with injected bounce $\rightarrow$ [1]. Immediate reverse chatter following CW $\rightarrow$ [1] (zero false reversals).

### (3) Direction Reversal Lockout
Implements a strict threshold where opposite directional steps within 5ms are discarded. Even if state transitions near mechanical thresholds produce a reverse step, if it occurs in the opposite direction of the last **confirmed** step within `ENC_REVERSAL_LOCK_MS` (default 5ms), it is dropped as contact bounce. Manual reverse turning takes tens of ms; thus, reversals under 5ms are guaranteed chatter. **Same-direction steps are unrestricted**, maintaining responsiveness for rapid spins. Timing uses SysTick (unsigned subtraction, overflow-safe).

### (8) Pushbutton Debounce
When raw state changes, candidate value and timestamp update. Once the value remains stable for `SW_DEBOUNCE_MS` (default 20ms), state updates. Validated press (falling edge) toggles state. Replaces inaccurate loop-counter methods.

Polling Choice: Manual encoder rotation achieves at most a few ms per quarter-step. Polling in the main loop captures all events without missing, avoids interrupt complexity, and preserves SOP8 EXTI resources.

## Dimming Curve — CIE 1931 L* (Perceptual Brightness Priority)
The primary design goal is **matching human brightness perception**. Human visual perception follows a **power law (~cubic root, Stevens's Law)** rather than a pure logarithmic response, featuring high sensitivity at low light levels and lower sensitivity at high light levels. **CIE 1931 L\*** is used as the standardized perceptually uniform scale. Logarithmic/exponential tapers approximate this curve but suffer from accuracy loss at lower brightness levels.

Level index $i$ is mapped linearly to lightness $L^*$ (0..100) (meaning 1 click = equal perceived step change), and mapped back to relative luminance $Y$ and duty cycle:

$$\text{For } L^* \le 8: Y = \frac{L^*}{903.3} \quad (\text{linear toe near complete darkness})$$

$$\text{For } L^* > 8: Y = \left(\frac{L^* + 16}{116}\right)^3 \quad (\text{power law})$$

$$\text{Duty} = \text{PWM\_TOP} \times Y$$

- **Integer-Only Math**: CH32V003 lacks an FPU. $L^*$ is represented in units of $1/100$, and the cubic term is calculated during initialization using 64-bit unsigned integers (`uint64_t`): $\text{PWM\_TOP} \times (L^* + 16)_{x100}^3 / (11600)^3$. Maximum value $65535 \times 11600^3 < 2^{63}$, preventing overflow without `libm` or `float` dependencies.
- Linear Toe ($L^* \le 8$): Handles low-light conditions, avoiding the extreme dim-crushing associated with pure logarithmic functions.
- Lookup Table (LUT): Computed once at boot. Main execution relies purely on `uint16_t` array indexing. Host verified: 0 $\rightarrow$ PWM_TOP boundaries, strictly monotonic, no duplicate steps, uniform low-end resolution (`duty = 0, 7, 14, 21, 28, ...`).
- The curve is **fixed and parameterless** (as CIE $L^*$ defines standardized human vision). Gamut adjusters can be added upstream if custom scaling is needed in the future.

## PWM Resolution and Frequency (16-bit Timer)
- TIM2 is a **16-bit counter**. `PWM_TOP` supports values up to 65535 and is configurable via `config.h`.
- Default: `PWM_TOP = 4095` (12-bit resolution), `PSC = 0` $\rightarrow 48\text{MHz} / 4096 \approx \mathbf{11.7\text{kHz}}$. Operates outside audible range without visible flicker. Preserves fine step resolution at low duty cycles (e.g., duty = a few counts), whereas 8-bit resolution (256 steps) leads to rounding collisions at the low end. Resolution can be increased to 8191 or 16383 for smoother dimming.

## High-Speed Rotation Snap (Duty 100% / 0% Shortcut)
Rapidly setting maximum or minimum brightness is achieved through fast rotation gestures.

- Circular buffer tracks timestamps of recent `SNAP_CLICKS` clicks in the same direction.
- On a new click, if elapsed time `now - oldest` across $j$ clicks is within `SNAP_WINDOW_MS` ($n\text{ ms}$), shortcut triggers: sets `level = LEVELS` (100%) for clockwise/brightening, or `level = 0` (0%) for counter-clockwise/dimming.
- Driven by SysTick (`SysTick->CNT` / `Ticks_from_Ms`). Unsigned subtraction avoids wrap-around issues ($0.5\text{s window} \ll 32\text{-bit wrap period} \approx 715\text{s}$).
- Clears click history post-trigger to prevent repeated snapping. Configurable via `SNAP_WINDOW_MS` and `SNAP_CLICKS`.

## Dual CW/CCW Encoder Support
Variations in encoder hardware or wiring order can invert rotation direction. Invert signal decoding digitally via `ENC_REVERSE` (0 or 1 in `config.h`) without modifying hardware wiring (flips decoded `dir` sign, applying consistently across dimming and snap gestures).

## LED Drive Circuitry
- Recommended: **Low-side MOSFET switch** (e.g., 2N7002, AO3400). Avoids MCU GPIO drive limits ($I_{max} \approx 8\text{mA}$), enabling drive of high-power LEDs, series strings, or constant-current drivers.
- Small signal LEDs may connect directly to PC2 via current-limiting resistor within rating boundaries.

## Soft Power Toggle and Operating Options
Short press on encoder switch toggles output state (ON/OFF). Preserves active dimming level during OFF state, restoring prior brightness upon turning back ON.

- **`WAKE_ON_TURN` (Default: 1)**: Turning encoder while OFF automatically restores output. Prevents hidden level adjustments while output remains dark.
- **`DEBUG_LOG` (Default: 0)**: Restricts UART/SWD `printf` to development mode.

## Hot-Loop I/O Optimization (`DEBUG_LOG`)
Enabling `printf` calls within event loops introduces blocking behavior: (a) If SWD host is disconnected, buffer saturation halts CPU execution; (b) Each call incurs hundreds of microseconds to milliseconds, missing intermediate encoder transitions and dropping detents. Setting `DEBUG_LOG = 0` compiles out logging mechanics entirely, running polling loops at maximum rate. Reduces flash consumption from 4380 B to 2968 B.

## Non-Volatile Brightness Storage (Wear-Leveling)
Restores last selected brightness level across power cycles. To accommodate flash write/erase limits, the driver appends updates sequentially to erased space rather than overwriting fixed addresses, performing sector erasures only when full.

### Flash Physics (CH32V003 Standard Mode)
- Bit transitions are limited to **$1 \rightarrow 0$**. Resetting $0 \rightarrow 1$ requires sector erase.
- **Erase Block = 1KB** (`PER`). **Write Unit = 2 Bytes** (`PG`, half-word).
- Erased State = `0xFFFF`.

### Appending Single-Cell Log (Last-Written-Wins)
- Reserves the final `STORE_SIZE_BYTES` (2KB) as a dedicated **1KB-aligned `const` array** (initialized to `0xFFFF`). Being 1KB-aligned and a multiple of 1KB, this memory occupies dedicated sectors without interfering with code or constants (verified: `g_store @ 0x1400`, occupying sectors 5–6 while application code remains separated below `0x1400`).
- **Save Operation**: Appends 2-byte entry to the first available `0xFFFF` cell. **1 save = 1 write**.
- **Read Operation**: Reads cell immediately preceding first empty (`0xFFFF`) slot. Defaults to 0 on uninitialized storage.
- **Full Storage**: Erases reserved 2KB sectors and wraps execution to start.
- Levels (0..64) do not collide with `0xFFFF`, allowing raw values to reside in single cells.
- Flash controller operations translate array references into physical memory offsets based on $0x08000000$.

### Commit Delay Optimization
Defers flash writes until level remains unchanged for `STORE_COMMIT_MS` (5 seconds), grouping adjustments into a single flash commit.

### Flash Lifespan Estimation
- $2\text{KB} / 2\text{ Bytes} = \mathbf{1024\text{ records}}$. Sector erase occurs once every 1024 updates, reducing erase cycles to **~1/1024th** and extending flash endurance by ~1024x.
- Example: For flash erase endurance $E$, naive storage permits $E$ saves; this implementation permits $\approx 1024 \times E$ saves. Assuming $E = 10,000$, total write cycles reach **~10,000,000 saves**.
- Host Verification: Verified initial boot state 0, save/restore integrity, 1024-cell wraparound erase, and edge case handling.

### Alternative Storage Approaches
An alternative 2-cell structure using marker markers (`0x0000` + value) updates former markers to invalid state upon writing. The single-cell log structure simplifies this to a single write per save (reducing write ops to 1/3) while doubling capacity to 1024 records per 2KB.

### Power Interruption Behavior
Power loss during sector erase (~6ms per 1024 saves) may reset unsaved level changes to default (acceptable for non-critical lighting levels).

## Thermal Fail-Safe (Hardware Considerations)
CH32V003 lacks an internal junction temperature sensor (ADC ch8 = Vrefint, ch9 = Vcalint). Thermal monitoring utilizes external NTC thermistors thermally coupled to output MOSFETs via ADC channels.

- Utilizes `funAnalogInit()` / `funAnalogRead(ch)` with ADCCLK divided to HCLK/8 (6MHz) for stability ($\le 14\text{MHz}$).
- First-order linear approximation computes operational thermal thresholds, driving alert GPIO outputs with hysteresis.
- Optional safety feature: Clamps main PWM output to 0 during over-temperature events (`WARN_TEMP_CUT_OUTPUT`).

## WS2812/SK6812 Night Light Integration
When main lighting is inactive (`!g_on || level == 0`), the MCU drives a low-power background night light using a triangular breathing cycle scaled by CIE $L^*$ curve, followed by an off interval. Color, channel order, and RGBW parameters are fully configurable.

- Depends on `ws2812b_simple.h` (ch32fun), requiring **SysTick = HCLK (48MHz)** via `FUNCONF_SYSTICK_USE_HCLK 1` in `funconfig.h`. Timing references scale automatically via `Ticks_from_Ms`.
- Color byte ordering (GRB/RGB/GRBW/RGBW) rearranges packed output bytes during transmission. RGBW modes apply CIE scaling to W channel concurrently.

## Verification & Test Summary
- Resource Utilization: Flash: 8200 B / 16KB (50%), RAM: 184 B (9%) with all features enabled. Core implementation occupies ~3KB (Night light: +3.6KB, `DEBUG_LOG=1`: +1KB). Clean compilation across all feature combinations.
- Math & Logic Verification: Monotonicity and boundary checks confirmed via host testing. State machine and storage log logic verified under simulated chatter and wraparound conditions.
- **Hardware Verified (In Operation since 2026-09-16 on LightBox Custom PCB)**: Operational test covering SK6812 RGBW night light, rotary acceleration (3x), soft start/off, and continuous 12-hour ON/OFF cycles. Junction temperature estimation disabled (`TEMP_PROTECT_ENABLE 0`) to prevent false tripping.
- Hardware specifications and pinout details available in `documents/HARDWARE.md`.