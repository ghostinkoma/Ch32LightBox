# WCH-LinkE Driver Setup Guide (Zadig)

minichlink, which writes to the CH32V003, requires the **WinUSB driver** to be assigned to the WCH-LinkE.
On a new PC, you only need to perform this setup once at the beginning (the same guide appears from **[🔌 Driver (Zadig)]** in the menu).

## Steps

1. **Get Zadig**: Download `zadig.exe` from <https://zadig.akeo.ie/> and **Run as administrator**.
   - If you want to bundle it with this tool, place it in `dist/toolchain/zadig/zadig.exe` to launch it directly from the menu.
2. **Connect WCH-LinkE via USB**.
3. In Zadig, check **Options → List All Devices**.
4. Select **"WCH-Link"** (the interface for writing to CH32) from the top list.
   - The displayed name may vary depending on the environment (e.g., `WCHLink`, `WinUSB (...)`). Select the interface used for CH32V003 flashing.
5. In the driver column, select **"WinUSB"** and click **[Replace Driver]** (or Install Driver if first time).
6. Once completed, retry **[③ Flash Only]** in this tool. Success is indicated when `Detected CH32V003 → Image written` appears.

## Troubleshooting by Error Message

| minichlink Output | Meaning | Action |
|---|---|---|
| `Could not initialize` | WinUSB driver not assigned | Assign WinUSB using this guide (Zadig) |
| `nothing connected to linker` / `Unknown chip type` | SWD not connected | Check wiring (SWIO=PD1), target power supply, and common GND |
| Hangs after `Found WCH Link` | Compatibility issue between minichlink build and LinkE firmware | Select a different minichlink via **[🔧 Select Flashing Tool]** (successful versions are automatically remembered) |

> Note: If the WCH-LinkE firmware is outdated and causing compatibility issues, updating the firmware via WCH's official **WCH-LinkUtility** is also an option.