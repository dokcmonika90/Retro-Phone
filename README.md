# Retro Phone

Android retro-game emulator/recompiler project.

## Current build
- Android ARM64 native C++ core
- NES iNES/NES 2.0 ROM loading foundation
- Mapper 0 (NROM) foundation
- Native 6502 execution core
- Android ROM picker
- Reset/run controls
- GitHub Actions debug APK build

The current release is an early playable-core foundation; graphics, audio, input mapping, and the ARM64 dynamic recompiler are being built incrementally.

## Multi-console repository

Retro Phone now has an expanded console catalog in [`docs/console-catalog.json`](docs/console-catalog.json). It is designed so additional systems can be added without changing the catalog format.

The catalog includes Nintendo, Sega, Atari, Sony, Microsoft, SNK, NEC, Bandai, Commodore, Sinclair, Capcom arcade hardware, Sega arcade hardware, and other classic systems. It also includes handheld, arcade, computer, CD, and fantasy-console categories.

The catalog is **metadata only**. It does not contain ROMs or copyrighted game files. Only use game files you own or are legally permitted to use.

### Planned library features
- Console/system filtering
- Game search
- Favorites
- Recently played games
- Per-game save-state support
- Console-specific settings
- Automatic detection of supported file formats
- Expandable emulator cores as systems are implemented

Only use ROMs you own or are legally permitted to use.
