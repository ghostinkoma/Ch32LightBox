# LightBox Studio — Bug Fix & Troubleshooting Record (BUGFIX)

> **Summary: ✅ Verified on real hardware (2026-09-18).** `config.h editing → compilation → CH32V003 flashing` was 
> successfully executed on actual hardware using **only minichlink bundled with the downloaded ch32fun (`dist/toolchain/.../minichlink.exe`, self-contained)** 
> (`Detected CH32V003 → Writing image → Image written.`). Features such as a sandbox that does not scan the local PC and permanent recording of proven working builds (resilient against clean reinstalls) have been fully implemented and verified.

Target: `tools/LightBoxStudio/`. Branch: `claude/lightbox-studio-tools`.

---

## 1. Setting LEVELS to 128 or Higher in config.h Caused Build Failures at `_Static_assert`
- **Symptom**: Compilation stopped with `lightbox.c:59: static assertion failed: "LEVELS ... 1..127"`.
- **Cause**: The editor GUI schema range (`LEVELS` 1..255, etc.) conflicted with the firmware's `_Static_assert`.
- **Resolution**: Audited all `_Static_assert` calls in `lightbox.c` / `*.h` and aligned the GUI schema with them.
  - `LEVELS` 1..127 / `LEVEL_INIT` 0..127 / `WDT_TIMEOUT_MS` 100..8190.
  - **Unconditionally validated** `PWM_TOP+PWM_SPREAD_RANGE<=65535` and `PWM_TOP>PWM_SPREAD_RANGE` (firmware asserts even when spread spectrum is OFF).
  - Added validation for `DIE_TAU_MS>=WARN_TEMP_PERIOD_MS`.
  - Added warning dialogues when opening files with out-of-range existing values (automatically corrected upon save).
- **Verification**: Real hardware build `Flash 8200B/16KB`, `lightbox.bin` successfully generated.

## 2. Compilation Passed, but Failed at Pre-Flashing Stage (ldscript Generation)
- **Symptom**: `sh: can't create D:hobbyLightBox...generated__.ld: nonexistent directory`.
- **Cause**: xpack `windows-build-tools` `make` executes recipes via the **bundled `sh`**. The backslash absolute path in `CH32FUN` was consumed by `sh` escape sequences during standard output redirection (`>`), causing path separators to disappear.
- **Resolution**: Pass `CH32FUN=` using **forward slashes** (Windows builds of gcc/make/sh all accept `/`).
- **Verification**: `CH32FUN=D:/hobby/.../ch32fun` successfully builds and produces binaries.

## 3. Flashing Depended on ch32fun's Built-in `make flash`
- **Symptom**: `②+③` invoked `cv_flash` from `ch32fun.mk` (built-in minichlink), failing depending on environment.
- **Resolution**: Centralized flashing logic into `FlashOp`. Both `②+③` (`builder --flash`) and `③` (`flasher`) **directly use the configured minichlink**. Separated build (`make`) from flashing (`minichlink`).

## 4. minichlink Could Connect but Hung During Flashing / Version Incompatibility
- **Symptom**: Hung after `Found WCH Link` at `Read protection: disabled` (no response for several minutes). Multiple builds existed on the PC, and only one build hung.
- **Resolution**:
  - Added `ProcRunner.runTimed`. Each flashing attempt **times out after 25 seconds** (forcefully killing child and sub-child processes).
  - Sequentially **tries multiple minichlink candidates** and accepts the first successful execution.
  - Added an **\[■ Abort\]** button to the menu (allows stopping hung processes from the GUI).
  - Verified via headless tests: "fail → hung 25s → success = exit code 0" and "immediate exit on first-candidate success".

## 5. Proven Working minichlink Forgotten on Clean Reinstall
- **Resolution**: Upon successful flashing, **permanently recorded to user profile `~/.lightboxstudio/settings.properties`**.
  Even if `tools/` is deleted and reinstalled, the remembered working minichlink is prioritized automatically.

## 6. Searching the Entire PC Was Inappropriate for a Sandbox
- **Symptom**: Auto-detection scanned `C:\Users\...` and picked incompatible builds from other projects, breaking sandbox isolation.
- **Resolution**: **Completely removed automatic PC scanning**. Candidates are strictly limited to: **① Local settings → ② Success history (remembered) → ③ ch32fun bundled (inside dist)**.
  Changed `[🔧 Select Flashing Tool]` to a standard file picker (defaulting to the ch32fun bundled directory).

## 7. Self-Contained minichlink (No Source Build Needed)
- **Investigation**: cnlohr/ch32fun **bundles prebuilt Windows `minichlink.exe` + `libusb-1.0.dll`** in its repository (built with tcc). Extracting ch32fun during initial setup automatically provides `toolchain/ch32fun-master/minichlink/minichlink.exe` (**no source compilation required**). The bundled version is `bc15212` (verified identical to the proven working build).
- **Resolution**: Adopted **ch32fun bundled minichlink (self-contained)** as a flashing candidate. Beginners without minichlink can get a working toolchain purely by downloading.
- **Verification**: **Successfully flashed real hardware using only the bundled minichlink** (`Detected CH32V003 → Image written`).

## 8. `nothing connected to linker` Is a Hardware, Not Software, Issue
- **Symptom**: `link error, nothing connected to linker (...)` / `Unknown chip type` / `marchid: ffffffff`. Occurred even with the proven working minichlink.
- **Cause**: **SWD disconnected** between WCH-LinkE ↔ target (CH32V003) (power supply, SWIO=PD1, or shared GND missing).
- **Isolation**: Occurs regardless of minichlink version = cannot be fixed via software. **After fixing wiring/power, flashed on the first try with the same self-contained minichlink** (confirming the diagnosis).

## 9. UI / Screen Transition Improvements
- **On first launch** (unconfigured state: gcc missing or no toolchain), **automatically displays setup screen**.
- **Removed the [Initial Setup] button from the main menu**.
- Setup screen **defaults to progress bar only** (compact mode). **[Details ▼] toggle** shows logs and expands the window; toggling again hides logs and shrinks the window.
- **Enabled portable JRE acquisition by default** (aligned with sandbox policy).

---

## Flashing Tool (minichlink) Fallback Order (Current Spec)
No whole-PC scanning:
1. Local setting `minichlink.path`
2. Past success history `~/.lightboxstudio/` (retained across clean reinstalls)
3. **ch32fun bundled (`dist/toolchain/.../minichlink.exe`, self-contained)**

Each attempt times out after 25 seconds and falls back to the next candidate. Successful versions are permanently remembered.

## Only Remaining External Dependency
Running the bundled minichlink on a fresh PC requires the **WCH-LinkE WinUSB driver (Zadig, one-time initial setup)**, which is external to the software.
Troubleshooting isolation guide: `Could not initialize` = Driver issue; `nothing connected` = Wiring/Power issue; `Hangs after Found WCH Link` = Select a different minichlink build (refer to [README](README.md)).