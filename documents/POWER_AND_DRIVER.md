# LightBox — Power, Low-Power & LED-Driver Design Notes

**English** · [日本語](POWER_AND_DRIVER_JP.md) · [← README](../README.md)

Design record for the battery / low-power features, the nightlight variants, and the main-LED
driver selection. This complements [CONFIG_REFERENCE.md](CONFIG_REFERENCE.md) (per-setting
reference) and [DESIGN.md](DESIGN.md) (core firmware rationale). Parts marked **★experimental**
are compile-supported but **not yet hardware-verified**.

---

## 1. Feature ↔ Pin assignment (why it is config-driven)

Motivation is PCB simplification on the SOP8 (J4M6) package, whose datasheet pinout clusters
power on pins 2/4 (VSS/VDD) and GPIO on pins 5–8:

- Grouping the rotary encoder + push switch on **PC1/PC2/PC4** (pins 5–7) keeps the 5-terminal
  encoder connector on one side → simpler routing.
- Putting the output(s) on **PA1/PA2** (pins 1/3, next to power) collects the LED/output section
  near VDD/VSS → the other clean zone.

CH32V003 constrains which pins can do PWM (timer channel/remap) and ADC, so `source/pins.h`
models each SOP8 pin's capability, derives GPIO/ADC/timer info from a single pin macro, and
rejects capability violations and pin conflicts at compile time. Full table and rules are in
[CONFIG_REFERENCE.md](CONFIG_REFERENCE.md) ("Feature ↔ Pin Assignment"). Note: `PWM_PIN=PA1`
(TIM1_CH2) + `WS_DIN_PIN`/nightlight on PA2 + encoder on PC1/PC2/PC4 is fully expressible.

## 2. Auto-off timer (`PWM_ON_TIME_S`)

Turns the main output off (soft fade) N seconds after it was switched on; `0` = disabled.
Any encoder/switch activity resets the countdown. It lowers average on-time (battery) and adds a
"forgot to turn it off" convenience on continuous power. Counted in 1-second steps so it is
independent of the ~89 s SysTick wrap.

## 3. Nightlight variants (`NIGHTLIGHT_TYPE`)

- **① WS2812/SK6812** (`NL_TYPE_WS2812`) — color, effect-priority (existing driver). Not eligible
  for deep sleep (needs tight CPU timing / higher voltage than a coin cell provides).
- **② Single-color LED** (`NL_TYPE_SINGLE`) — a dedicated pin blinks `NL_ON_MS` every
  `NL_PERIOD_S`. `NL_USE_PWM=0` = plain GPIO on/off (lowest CPU/power) or `=1` = soft-PWM
  breathing (effect). **Only ② + `NL_USE_PWM=0`** is eligible for the deep low-power mode.

Nightlight current is set by a **series resistor** (not a driver IC); at 5 V a white LED via ~1 kΩ
≈ 1.8 mA, plenty for a nightlight and current-stable thanks to the headroom.

## 4. Deep low-power mode (Standby + AWU) — ★experimental

Battery mode (`LOW_POWER_MODE=1`, `source/lowpower.h`). Design decisions:

- **Only for the low-power nightlight config**: requires ② single-LED + `NL_USE_PWM=0` +
  `NL_PERIOD_S≤30` + `WDT_ENABLE=0` (enforced by `_Static_assert`). WS2812 / PWM effect keep the
  normal always-on architecture (effect-priority).
- **Sleep structure**: LED on for `NL_ON_MS` in **Sleep** (GPIO holds), then **Standby** (deepest,
  ~µA) for the rest of the period. Both intervals are timed by **AWU (LSI ≈128 kHz, clock-independent)**;
  a push-switch on **EXTI** also wakes it. AWU one-cycle max ≈ 30 s → `NL_PERIOD_S≤30`.
- **Wake model**: the **push switch** wakes the device into normal dimmer operation for
  `LOWPWR_ACTIVE_WINDOW_S`; after that, with the main light off, it returns to sleep. The **rotary
  encoder is deliberately NOT a Standby wake source** (two-phase EXTI is complex and leaves
  pull-ups leaking).
- **Leakage**: before sleeping, encoder A/B are switched to **analog-input** so their pull-ups stop
  leaking (a closed contact through a ~30–50 kΩ pull-up would draw ~100 µA — larger than Standby).
  The push switch keeps a pull-up (normally open → ~0 idle leak, low on press = wake).
- **Reflash safety**: hold the push switch while powering on → the device stays in normal mode and
  never sleeps, so SWD can attach for flashing. This also doubles as reset-tolerance: whether
  Standby wake resets or resumes, `main` re-deriving state (or the resume loop) reaches the same
  decision from the button state.
- **Watchdog**: `WDT_ENABLE=0` is required — IWDG runs on LSI and could reset during Standby
  before the AWU fires.

**Status**: the Standby/AWU/EXTI path is compile-supported but **must be verified on hardware**
(Standby wake behavior, AWU period accuracy, actual currents). `LOW_POWER_MODE=0` (default) is
byte-identical to the previous firmware.

## 5. Battery-life estimates

Model (documented assumptions, ★confirm on hardware): green LED via **1 kΩ** at 3 V ≈ **0.9 mA**;
Standby ≈ 10 µA; active @48 MHz ≈ 8 mA; on-time Sleep @48 MHz ≈ 3 mA; wake overhead ≈ 5 ms;
CR2032 ≈ 220 mAh. Average current dominated by **"how the MCU spends the on-time"**, not the LED.

Coin-cell (3 V) nightlight, 100 ms every 5 s:

| Config | Avg current | CR2032 life (est.) |
|---|---|---|
| Deep low-power (as implemented, Sleep @48 MHz during on-time) | ~90 µA | **~3–4 months** |
| Deep + lowering the clock during on-time (future optimization) | ~45 µA | ~6.5 months |
| No deep low-power (MCU 48 MHz always) | ~8 mA | ~1 day |

- **Resistor bias vs brightness/life**: 510 Ω (~1.8 mA) is bright but roughly halves life; **1 kΩ
  (~0.9 mA) is the brightness/life sweet spot**; beyond ~1–2 kΩ, life approaches the MCU+Standby
  floor, so extend by shortening on-time / lowering clock rather than dimming further.
- **Cell choice**: 2×CR2032 in **series** doubles voltage (6 V) but **not capacity**, and 6 V
  exceeds the CH32V003 5.5 V max (needs regulation, which wastes the extra energy in an LDO) —
  it improves LED-current stability, not mAh life. For life prefer **parallel** cells (~440 mAh)
  or a larger cell (CR2450 ~600 mAh ≈ ~2 years). Add a 10–100 µF buffer cap for CR2032 pulse/IR.

The GUI config editor shows this CR2032 estimate live for the single-LED nightlight (1 kΩ assumption).

## 6. Main-LED driver selection

**Confirmed operating conditions:** 5 V single supply · white-ish main LED · nightlight current set
by a resistor · main PWM limited to **≤2 kHz** (so it can dim a CN5711 via its CE pin).

### CN5711 (Consonance) — chosen candidate
Linear constant-current LED driver: Vin 2.8–6 V, up to **1.5 A**, low dropout **0.37 V @1.5 A**,
±5 %, on-chip MOSFET + sense, current set by **ISET resistor** (≤30 kΩ), **temperature regulation
(foldback)**, SOP8. **PWM dimming via the CE pin at <2 kHz.** Using it replaces the discrete
MOSFET (FDS5680) gate drive: LED anode→5 V, cathode→CN5711 LED pin, CE←MCU PWM.

**Key caveat — it is LINEAR:** it dissipates `(Vin − Vf) × I` in the IC. At 5 V into a ~3.2 V white
LED the drop is ~1.8 V:

| Main LED current | CN5711 dissipation | SOP8 (with thermal pad) |
|---|---|---|
| 100 mA | ~0.18 W | fine |
| 300 mA | ~0.54 W | OK (wide copper) |
| 500 mA | ~0.9 W | marginal |
| **1 A** | **~1.8 W** | too much → thermal foldback (self-dims) |

The current MOSFET+resistor design was "virtually no heat" because the `(Vin−Vf)` loss sat in the
external current-limit element / the LED ran modestly; a linear driver **relocates that loss into
the small SOP8**. So at ~1 A white/5 V, CN5711 will fold back.

### Alternatives
- **AMC7135** — cheaper linear CC, fixed 350 mA (parallel N for more), whole-chip PWM. Cheapest, but
  fixed granularity and more heat.
- **PT4115 / AL8860 / MT7202** — **buck** CC with a DIM pin (PWM, incl. >2 kHz). Efficient when
  Vin≫Vf or for battery, and no `(Vin−Vf)` heat; costs an inductor + Schottky (more BOM/area).

### Decision guide (by main-LED current, 5 V, white)
- **≤ ~300–400 mA → CN5711** is the sweet spot: simple, low dropout, few parts, built-in thermal
  foldback (which also lightens the MCU's own thermal-protection burden).
- **~1 A → prefer a buck driver (PT4115 etc.)** or reduce the LED current, or keep the MOSFET+CC.

### Firmware implication of ≤2 kHz CE dimming
Set the main PWM to ≤2 kHz, e.g. `PWM_TOP=23999, PWM_PSC=0` → 48 MHz/24000 = **2 kHz** (24000-step
resolution, no visible flicker). Alternatively keep the fast MOSFET PWM and don't add a CC IC.

**Sources (CN5711):**
[LCSC C35634](https://www.lcsc.com/product-detail/C35634.html) ·
[datasheetbank preview](https://www.datasheetbank.com/en/preview/CN5711-CONSONANCE) ·
[alldatasheet](https://www.alldatasheet.com/datasheet-pdf/pdf/1133252/CONSONANCE/CN5711.html)
