# LightBox Hardware (Board / Circuit / BOM)

**English** · [日本語](HARDWARE_JP.md) · [← README](../README.md)

Physical PCB for the CH32V003 LED Dimmer Controller. Complete KiCad project located at:
`PCB/LightBox/` (Schematic: `LightBox.kicad_sch` / PCB: `LightBox.kicad_pcb`).
Artwork image: `PCB/AertWork.png`. MOSFET symbol: `PCB/KiCad/FDS5680.*`.

> PCB design, schematic design, and artwork (including pin headers) are complete. **Wiring has been verified to match the active firmware identically.**

## System Overview

* **U1 CH32V003JxMx** (SOP8 / J4M6) — Main MCU

* **Q1 FDS5680** (N-channel MOSFET, SO-8) — **Low-side drive** for the main LED

* **D1 SK6812** (RGBW addressable = OptoSupply **OST45050C1A-W**) — Night light, data line on `PC4`

* **D2 LED** — **On-board pilot (status) LED**. Synchronized with Q1's `LED OUT` (drain) side

* **R1 100$\Omega$** — Series resistor from `PC2` to MOSFET gate

* **R2 10k$\Omega$** — Gate-to-GND pull-down (**fail-safe ensuring LED is OFF during reset or un-driven state**)

* **R3 100$\Omega$** — **Series current-limiting resistor for pilot LED D2** (connected to D2 cathode)

* **Main (Power) LED** — External to PCB. Connected via pin header from `LED OUT` (`J1-1`) (external current limiting required)

* **J1 Conn_01x09** (9-pin header) — Breakout for power, programming, encoder, and dual LED signals

* **SW2 RotaryEncoder_Switch** — Rotary encoder with push button switch

## Pin Assignment (U1 = CH32V003 SOP8)

| U1 Pin | Signal (Net) | Destination | Firmware `config.h` | 
 | -----: | ----- | ----- | ----- | 
| 1 PA1 | ENC A | SW2-A / J1-7 | `ENC_A_PIN=PA1` | 
| 2 VSS | GND | GND | — | 
| 3 PA2 | SW (Encoder Push) | SW2-S1 / J1-8 | `ENC_SW_PIN=PA2` | 
| 4 VDD | VDD | Power / J1-9 | — | 
| 5 PC1 | ENC B | SW2-B / J1-6 | `ENC_B_PIN=PC1` | 
| 6 PC2 | PWM $\rightarrow$ R1 $\rightarrow$ Q1 Gate | R1 / U1-6 | PWM=`PC2` (Fixed) | 
| 7 PC4 | LED2810 (SK6812 DIN) | D1-2 / J1-2 | `WS_DIN_PIN=PC4` | 
| 8 PD1 | SW IO (SWIO Flash/debug) | J1-3 | Preserved | 

$\rightarrow$ **PCB pinout matches the flashed firmware identically** (all features verified on real hardware).

## J1 9-Pin Header Pinout

| J1 | Signal | Description | 
 | ---: | ----- | ----- | 
| 1 | LED OUT | Main LED Cathode side (Q1 Drain). Connect external LED + current limiting here | 
| 2 | LED2810 / DIN | Night light SK6812 / OST45050C1A-W data input (`PC4`) | 
| 3 | SW IO | SWIO (WCH-LinkE Flashing / `debugprintf`, `PD1`) | 
| 4 | GND | Power / Signal Ground | 
| 5 | GND | Power / Signal Ground | 
| 6 | ENC B | Encoder Phase B (`PC1`) | 
| 7 | ENC A | Encoder Phase A (`PA1`) | 
| 8 | SW | Encoder Push Switch (`PA2`) | 
| 9 | VDD | Power Supply (**5V Input**. CH32V003 operates from 2.7V to 5.5V) | 

## Main LED Drive Stage (Low-Side Switch)

```
VDD ──▶ [External Main LED (≈1A)] ──▶ J1-1 (LED OUT) = Q1 (FDS5680) Drain
VDD ──▶ [Pilot LED D2] ─[R3 100Ω]──▶ (LED OUT)                      ← Status Indicator
PC2 ─[R1 100Ω]─┬─ Q1 Gate
               └─[R2 10kΩ]─ GND    (Pull-down: LED OFF during reset)
Q1 Source ── GND
```

* PWM (`PC2`) High $\rightarrow$ MOSFET ON $\rightarrow$ Drain pulled to GND $\rightarrow$ External LED and Pilot D2 illuminate.

* **R2 actively pulls the gate Low during boot/reset, keeping the LED OFF** (passive fail-safe).

* **External current limiting** (resistor or constant-current driver) must be provided for the main LED. Pilot LED D2 is limited by R3.

* FDS5680 is a 30V logic-level N-channel MOSFET. **Generates virtually no heat when driving a $\approx 1\text{A}$ LED** (heatsink is purely preventive).

## Power Supply

* **VDD = 5V Input**. Since the CH32V003 supports 5V operation, GPIO High levels reach $\approx 5\text{V}$, easily exceeding the SK6812 data input high threshold ($\approx 0.7 \times \text{VDD} = 3.5\text{V}$). This makes 5V preferable to 3.3V.

## LED Configuration Details

* **Main (Power) LED is External**: Connected via pin header at `LED OUT` (`J1-1`, Q1 drain). Current limiting is handled externally (resistor or CC driver). Verified operational with a **$\approx 1\text{A}$ LED**.

* **On-board Pilot LED D2**: Illuminates in sync with `LED OUT`, with **current limited by R3 (100$\Omega$)** (connected to D2 cathode). Acts as a physical status indicator for the output.

* SK6812 data line (`PC4` $\rightarrow$ DIN) is directly connected. For noisy environments, a series resistor of 100–470$\Omega$ and a decoupling capacitor ($0.1\mu\text{F}$ to a few $\mu\text{F}$) near the power pins can optionally be added.

> *Note:* Earlier references mentioning "R3 unconnected" were based on an outdated `LightBox.dsn` export prior to routing R3. The physical hardware has R3 wired in series with D2 as specified.

## Physical Hardware Verification — ✅ Verified (In active use, operating normally)

* **★ Flashed successfully on 2026-09-16** (WCH-LinkE / minichlink, CH32V003 UUID `a5-69-ab-cd-0d-91-bc-5b`). Running continuously without issues since.

* **★ Functional Verification Complete**: SK6812 / OST45050C1A-W night light breathing, brightness scaling (3x rotary acceleration), soft start / soft off transitions, and **12-hour continuous testing in both ON and OFF states passed without anomalies** (false over-temperature cutoffs resolved by setting thermal protection to OFF).

* **★ Prototyped on 1.27mm pitch perfboard with hand-wired circuit $\rightarrow$ Operational success confirmed**.

* **★ Integrated into custom 3D-printed enclosure designed in Fusion 360** (STL files located in `3D_Models/`).

* **Thermal Performance**: Although a heatsink was attached as a precaution, **virtually no heat generation was observed under a $\approx 1\text{A}$ LED load**. This confirms that previous false trips under duty-cycle-based die estimation were caused by math model discrepancies rather than physical heat, validating the decision to set `TEMP_PROTECT_ENABLE` to `0` (disabled). If thermal protection is required in the future, a physical external NTC sensor should be used.