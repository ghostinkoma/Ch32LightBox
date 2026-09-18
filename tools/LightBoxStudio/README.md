# LightBox Studio — CH32V003 One-Stop GUI (No VS Code Required)

**English** · [日本語](README_JP.md) · [← Project README](../../README.md)

A Java toolset for executing the workflow of `config.h` editing → compilation → flashing **from a single menu GUI**.
Its goal is to enable complete development of LightBox ([../../README.md](../../README.md)) using only a GUI, without requiring VS Code.

> **Status: ✅ Verified on real hardware (2026-09-18)**. On first-time setup, it automatically downloads the toolchain, deploys it to `toolchain/`, and fills in configuration settings automatically (self-contained, sandbox without scanning the PC).
> **config.h editing → compilation → CH32V003 flashing** was successfully executed on actual hardware using **only minichlink bundled in the downloaded ch32fun (inside dist, self-contained)** (`Detected CH32V003 → Image written`).
> Users do not need to search for installed paths. Detailed troubleshooting logs are recorded in [BUGFIX.md](BUGFIX.md).

## Architecture (5-Component Set)

| Executable | Role | Type |
|---|---|---|
| `LightBoxMenu.jar` | **Menu (Kicker)**. Launches other tools and aggregates logs | GUI |
| `setup.jar` | **Initial Setup**. Downloads/extracts toolchain and auto-fills settings (with progress bar) | GUI |
| `config-editor.jar` | Form-edits `config.h`, validates, and saves | GUI |
| `builder.jar` | Executes `make` to build (and optionally flash) | Background |
| `flasher.jar` | Flashes to CH32V003 using `minichlink` | Background |

The menu GUI and each background functional JAR operate as loosely coupled **separate processes**. Settings are shared via `settings.properties` (located in the same folder as the JARs).

## Requirements

- **JDK / JRE 11 or higher** (JDK for building, JRE sufficient for execution).
- Native toolchains (ch32fun / RISC-V GCC / make / minichlink) are **automatically prepared by [⬇ Initial Setup]**, so pre-installation is not required.
- Only for flashing, the **WinUSB driver for WCH-LinkE** (Zadig on first-time setup only) is required.
  * This is the only manual step that cannot be automated via Java (for distribution on other PCs).
  Instructions are built into the menu under **[🔌 Driver (Zadig)]** (refer to [ZADIG.md](ZADIG.md)).

> If you only edit `config.h`, **setup is not required** (operates in pure Java).

## Initial Setup (Self-Contained)

Pressing **[⬇ Initial Setup]** (`setup.jar`) in the menu **downloads & extracts** the following into `toolchain/` and automatically populates `settings.properties`. Progress is displayed via a progress bar.

| Component | Source | Approx. Size |
|---|---|---|
| RISC-V GCC (xpack riscv-none-elf) | xpack-dev-tools Releases | **~360MB** (takes time) |
| make (xpack windows-build-tools) | xpack-dev-tools Releases | ~2.7MB |
| ch32fun SDK | cnlohr/ch32fun (master zip) | A few MBs |
| minichlink | Automatically detects & copies existing binaries on PC due to lack of official prebuilts (internal process, no user action needed) | — |

- Download sources/versions can be replaced via `dist/toolchain.manifest.properties` (optional).
- Since `toolchain/` is large, it is ignored via `.gitignore` (not included in the repository).
- If minichlink is not found on the PC, the step is skipped; building and editing remain functional, but **flashing requires manually specifying minichlink**.

## Build

```bash
# Windows
build.bat
# Linux/macOS
./build.sh
```
Five JAR files will be generated in `dist/` (pre-generated JARs are also bundled in this repository).

## Usage

1. Launch the menu:
   - **Recommended: Double-click `LightBoxStudio.bat` (Windows) / `LightBoxStudio.sh` (Linux/macOS)**.
     The launcher automatically detects Java (① Bundled portable JRE → ② `JAVA_HOME` → ③ PATH) and starts.
   - Direct execution is also possible: `java -jar dist/LightBoxMenu.jar`
2. First-time only: Press **[⬇ Initial Setup]** to acquire the toolchain (wait until the progress bar completes).
   * If only editing config.h, this step is not needed. If a toolchain already exists, you can manually specify it in **[⚙ Settings]**.
3. **① Edit config.h** → **② Compile** → **③ Flash** (or **②+③ Build & Flash**).

### Java Runtime (When System Installation is Undesirable)

- The launcher **prioritizes detecting and reusing existing Java** (`JAVA_HOME`/PATH). If Java is already present, nothing extra is needed.
- If you **do not wish to install Java onto the system**, select **"☐ Also acquire portable JRE"** during initial setup. This extracts Adoptium Temurin 17 JRE (~44MB, no installer required) **only into `dist/runtime/`**.
- **System PATH, registry, and admin privileges remain untouched** — deleting the folder returns the system to its original state (**sandbox**). Subsequent launcher runs will automatically use this `runtime/`.
- Distributing to a PC without Java: **Run "Also acquire portable JRE" on a PC with Java → Copy the folder whole**. The target PC can run the launcher without installing Java (including `dist/toolchain/` allows self-contained building and flashing as well).

## Settings (settings.properties)

| Key | Meaning |
|---|---|
| `project.dir` | The `source/` folder containing `config.h` / `Makefile` (empty = auto-detect) |
| `ch32fun.dir` | The folder containing `ch32fun.mk` |
| `make.path` | `make` executable path (empty = PATH) |
| `gcc.bin.dir` | `bin` directory containing `riscv-none-elf-gcc` (empty = PATH) |
| `minichlink.path` | `minichlink` executable path (empty = PATH) |
| `flash.artifact` | Target file to flash (default `lightbox.bin`, relative paths based on `project.dir`) |

## Features of config.h Editor

- **Schema-Driven**: Form-edit all 57 settings with type-specific widgets (numeric / checkbox / dropdown / pin / color).
  Definitions correspond to [`documents/CONFIG_REFERENCE.md`](../../documents/CONFIG_REFERENCE.md) and `source/config.h`.
- **Non-Destructive Editing**: Replaces **only value tokens** in `#define KEY VALUE`, preserving comments, enum definitions, empty lines, `u`/`L` suffixes, and `(-300)` formatting (without regenerating full text). Creates `config.h.bak` before saving.
- **Cross-Constraint Validation** (Pre-save validation):
  - When `PWM_SPREAD_SPECTRUM=1`: `PWM_TOP+PWM_SPREAD_RANGE≤65535` and `RANGE<PWM_TOP`
  - `STORE_SIZE_BYTES` must be a multiple of 1KB / `LEVEL_INIT≤LEVELS`
  - `WARN_TEMP_HYST_C<WARN_TEMP_C` / `THERMAL_THROTTLE_MAX<100` / `TEMP_CAL_SLOPE_X100≠0`
  - **PC4 Mutual Exclusion** (Nightlight / External temp sensor / Warning LED cannot share PC4 simultaneously)
  - Links digit counts of `WS_MAX_COLOR` with `WS_ORDER` (RGB = 6 digits / RGBW = 8 digits)
  - Warnings: `DEBUG_LOG=1` (must be 0 for normal operation), Die estimation temperature source (risk of false cutoffs)
- **Advanced Mode**: Fixed hardware wiring items like `ENC_*_PIN` are locked by default to prevent accidental modifications.

## Flashing Notes (Compliant with README / CONFIG_REFERENCE.md)

- SWD printf monitoring (`minichlink -T`) is **mutually exclusive with flashing**. Close monitoring before flashing.
- If no flash artifact exists, run **② Compile** first to generate `lightbox.bin`.
- WCH-LinkE not recognized → Verify WinUSB driver (Zadig), power supply, and SWIO (PD1) wiring.
- **If flashing hangs**: Use **[■ Abort]** in the menu to stop execution (kills child minichlink processes as well).
- **minichlink compatibility varies by build**: When multiple versions exist on a PC, some builds initialize successfully but hang during flashing. As a countermeasure, flashing **tries multiple minichlink candidates sequentially**, with each attempt **timing out after ~25 seconds** before falling back automatically (ensuring execution using a working binary even if a hanging binary is selected initially). Initial setup automatically records candidates in `minichlink.candidates`.
- **For guaranteed reliability**: Specify a proven `minichlink.exe` via **[🔧 Select Flashing Tool]** in the menu (runs with highest priority). This setting persists across setup re-runs.
- **Once successfully flashed, minichlink is recorded in user profile (`~/.lightboxstudio/`)**, and will be **automatically prioritized on subsequent runs** even if the `tools/` folder is deleted and cleanly reinstalled.
  * "Once a working minichlink is configured, it works automatically from then on."
- Candidate evaluation order: ① Local settings → ② Historical successful runs → ③ Auto-detected candidates. If a record exists, unnecessary waiting is avoided.
- Re-running setup skips downloading large files if `toolchain/` is already extracted.

### minichlink Compatibility and Self-Containment (Investigation Results)

- **Source builds are not required**. ch32fun bundles Windows `minichlink.exe` and `libusb-1.0.dll` in its repository (prebuilts built with tcc committed). Extracting ch32fun during initial setup yields `toolchain/ch32fun-master/minichlink/minichlink.exe`.
  → **Flashing automatically tests this minichlink as a self-contained candidate** (no PC-wide search required).
- **However, compatibility exists between minichlink builds and WCH-LinkE drivers/firmware** (Verified on hardware):
  - `Could not initialize` … WinUSB driver not assigned. Assign WCH-Link to WinUSB via **Zadig** (one-time setup).
  - Hangs after `Found WCH Link` … Compatibility issue between minichlink build and LinkE firmware (e.g., firmware 2.17 succeeds with older `bc15212` series but hangs with newer series). **Once a working version is used, it is remembered automatically**.
- Fallback sequence (**No full PC scanning = Sandbox**): ① Local settings → ② Historical successes (`~/.lightboxstudio/`) → ③ **Bundled with ch32fun (inside dist, self-contained)**. Each times out after 25s.
  **Successful versions are recorded permanently** (retained across clean reinstalls).
- If the bundled version fails in your environment: Explicitly specify a working `minichlink.exe` via [🔧 Select Flashing Tool] (default path points to the bundled location in ch32fun). Explicitly set paths gain highest priority and permanent memory.
- If flashing still fails, update WCH-LinkE firmware (using WCH-LinkUtility) to match driver versions.

> Key takeaway: Simply point to or assign a working minichlink **once**, and the process becomes fully automatic afterward. Even for CH32 beginners, the target is to complete flashing using ch32fun-bundled minichlink + Zadig (one-time initial driver setup).

## Roadmap

- **Phase 0 (Completed)**: GUI implementation assuming pre-existing environment. `config-editor` completed as a standalone tool.
- **Phase 1 (Completed / Current)**: `setup.jar` automatically downloads xpack GCC / make / ch32fun into `toolchain/`, auto-detects/copies existing minichlink, and populates configuration settings (self-contained, with progress bar).
- **Phase 2 (In Progress)**: **Portable JRE (sandbox) + Launcher** for complete self-contained Java dependency management.
  Existing Java installations are auto-detected and reused; if missing/unwanted, extracted solely to `dist/runtime/` (no system modifications).
  Remaining: Single `.exe` creation via `jpackage`, integrated WinUSB driver installation (Zadig) guide.

## License

This toolset follows the same non-commercial license (CC BY-NC 4.0 compliant) as LightBox itself ([../../LICENSE](../../LICENSE)).