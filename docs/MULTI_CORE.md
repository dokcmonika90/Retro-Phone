# Retro Phone multi-core architecture

Retro Phone is being rebuilt around a core registry instead of hard-coding NES emulation into the launcher.

## Current status

| System | Core | ROM extensions | Status |
|---|---|---|---|
| NES | LaiNES | `.nes`, `.fds` | Integrated |
| SNES | Snes9x | `.sfc`, `.smc`, `.fig` | Source added; Android backend next |
| Game Boy / Color | SameBoy | `.gb`, `.gbc` | Registered; backend next |
| Game Boy Advance | mGBA | `.gba` | Registered; backend next |
| Genesis / Mega Drive | Genesis Plus GX | `.md`, `.gen`, `.smd`, `.bin` | Registered; backend next |
| Master System / Game Gear | Gearsystem | `.sms`, `.gg` | Registered; backend next |
| Nintendo 64 | Mupen64Plus | `.n64`, `.z64`, `.v64` | Registered; backend next |
| Nintendo DS | melonDS | `.nds` | Registered; backend next |
| Dreamcast / Naomi | Flycast | `.cdi`, `.gdi`, `.chd` | Registered; backend next |
| PlayStation | PCSX-ReARMed | `.cue`, `.iso`, `.img`, `.bin`, `.pbp`, `.chd` | Registered; backend next |
| PSP | PPSSPP | `.iso`, `.cso`, `.chd` | Registered; backend next |
| Atari 2600 | Stella | `.a26` | Registered; backend next |
| Arcade | MAME | `.zip`, `.7z` | Registered; backend next |
| Arcade | FinalBurn Neo | `.zip`, `.7z` | Registered; backend next |

The registry deliberately separates **supported formats** from **installed playable cores**. This prevents a ROM from being reported as playable when only its metadata entry exists.

## Why this structure

The project reference list at https://github.com/alnacle/awesome-emulators contains many open-source emulators across Atari, Nintendo, Sega, Sony and multi-system categories. Retro Phone uses that list as a source for candidate cores, but each core still has to be evaluated for Android, licensing, build system, input, video, audio and save-state integration before it is marked installed.

The next native integration target is Snes9x. Its upstream repository contains a `libretro` implementation and documents Android/ARM64 libretro builds. Once that backend is connected, the same bridge can be reused for additional libretro cores.

## ROM legality

Retro Phone does not ship commercial ROMs, BIOS files, or copyrighted game assets. Users must provide ROMs and required firmware they are legally entitled to use.
