# Retro Phone

Android retro-game emulator/front-end project.

## Restarted multi-emulator architecture

Retro Phone is being rebuilt around a **pluggable emulator-core architecture** instead of tying the app to a single NES implementation. The architecture is based on the systems and open-source emulator projects cataloged by [alnacle/awesome-emulators](https://github.com/alnacle/awesome-emulators).

The current registry is [`docs/emulator-registry.json`](docs/emulator-registry.json), with the native routing model in [`EmulatorRegistry.kt`](app/src/main/java/com/dokcmonika90/retrophone/EmulatorRegistry.kt).

### Core status

- **LaiNES / NES:** integrated native core
- **Snes9x / SNES:** planned adapter
- **mGBA / Game Boy family:** planned adapter
- **SameBoy / Game Boy family:** planned adapter
- **Genesis Plus GX / Sega 8/16-bit:** planned adapter
- **Mednafen / multi-system:** planned adapter
- **melonDS / Nintendo DS:** planned adapter
- **Dolphin / GameCube + Wii:** planned adapter
- **Mupen64Plus / Nintendo 64:** planned adapter
- **Flycast / Dreamcast + Naomi + Atomiswave:** planned adapter
- **PCSX-ReARMed / PlayStation:** planned adapter
- **PPSSPP / PSP:** planned adapter
- **MAME / arcade:** planned adapter
- **FinalBurn Neo / arcade:** planned adapter
- **Stella / Atari 2600:** planned adapter
- **VICE / Commodore:** planned adapter

`INTEGRATED` means the core is actually linked and playable in the Android build. `PLANNED` means the core is registered and its ROM extensions are recognized by the architecture, but its native emulator code has not yet been linked into Retro Phone.

## Current Android build

- Android ARM64 native C++ core
- NES iNES/NES 2.0 ROM loading foundation
- Mapper support in the native NES core
- Native 6502 execution core
- Android ROM picker
- ROM library with search/favorites/recent sorting
- Reset/run controls
- Touch controls
- Fullscreen gameplay
- Audio output
- GitHub Actions debug APK build
- Expandable multi-emulator registry

## Roadmap

1. Keep NES stable while the new core interface is introduced.
2. Add SNES through Snes9x.
3. Add Game Boy/Game Boy Color/Game Boy Advance through mGBA or SameBoy.
4. Add Sega systems through Genesis Plus GX.
5. Continue with N64, PlayStation, Dreamcast, DS, PSP and arcade cores.
6. Add per-core settings, save states, controller profiles and automatic core selection.

Only use ROMs, BIOS files, and other game data that you own or are legally permitted to use. Retro Phone does not provide copyrighted game files.
