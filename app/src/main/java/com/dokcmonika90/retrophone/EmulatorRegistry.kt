package com.dokcmonika90.retrophone

/**
 * Pluggable emulator-core registry.
 *
 * The registry deliberately separates console detection from a concrete core.
 * This lets Retro Phone add multiple emulator backends without rewriting the
 * ROM library, touch controls, save-state layer, or launcher.
 */
data class EmulatorCore(
    val id: String,
    val name: String,
    val systems: Set<String>,
    val extensions: Set<String>,
    val status: Status,
    val source: String,
    val license: String
) {
    enum class Status { INTEGRATED, PLANNED }
}

object EmulatorRegistry {
    val cores = listOf(
        EmulatorCore("laines-nes", "LaiNES", setOf("nes"), setOf("nes", "fds"), EmulatorCore.Status.INTEGRATED, "https://github.com/AndreaOrru/LaiNES", "BSD-2-Clause"),
        EmulatorCore("snes9x", "Snes9x", setOf("snes"), setOf("sfc", "smc", "fig"), EmulatorCore.Status.PLANNED, "https://github.com/snes9xgit/snes9x", "GPL-2.0"),
        EmulatorCore("mgba", "mGBA", setOf("game-boy", "game-boy-color", "game-boy-advance"), setOf("gb", "gbc", "gba"), EmulatorCore.Status.PLANNED, "https://github.com/mgba-emu/mgba", "MPL-2.0"),
        EmulatorCore("sameboy", "SameBoy", setOf("game-boy", "game-boy-color"), setOf("gb", "gbc"), EmulatorCore.Status.PLANNED, "https://github.com/LIJI32/SameBoy", "MIT"),
        EmulatorCore("genesis-plus-gx", "Genesis Plus GX", setOf("sg-1000", "master-system", "game-gear", "genesis", "sega-cd"), setOf("sms", "gg", "md", "gen", "bin", "cue"), EmulatorCore.Status.PLANNED, "https://github.com/ekeeke/Genesis-Plus-GX", "Non-commercial"),
        EmulatorCore("mednafen", "Mednafen", setOf("pc-engine", "playstation", "game-boy", "game-boy-color", "game-boy-advance", "sega-cd", "saturn"), setOf("pce", "cue", "bin", "iso", "m3u"), EmulatorCore.Status.PLANNED, "https://github.com/mednafen/mednafen", "GPL-2.0"),
        EmulatorCore("melonds", "melonDS", setOf("ds"), setOf("nds"), EmulatorCore.Status.PLANNED, "https://github.com/melonDS-emu/melonDS", "GPL-2.0"),
        EmulatorCore("dolphin", "Dolphin", setOf("gamecube", "wii"), setOf("iso", "gcm", "wbfs", "rvz"), EmulatorCore.Status.PLANNED, "https://github.com/dolphin-emu/dolphin", "GPL-2.0"),
        EmulatorCore("mupen64plus", "Mupen64Plus", setOf("n64"), setOf("n64", "z64", "v64"), EmulatorCore.Status.PLANNED, "https://github.com/mupen64plus", "GPL-2.0"),
        EmulatorCore("flycast", "Flycast", setOf("dreamcast", "naomi", "atomiswave"), setOf("gdi", "cdi", "chd"), EmulatorCore.Status.PLANNED, "https://github.com/flyinghead/flycast", "GPL-2.0"),
        EmulatorCore("pcsx-rearmed", "PCSX-ReARMed", setOf("playstation"), setOf("cue", "bin", "iso", "chd"), EmulatorCore.Status.PLANNED, "https://github.com/libretro/pcsx_rearmed", "GPL-2.0"),
        EmulatorCore("ppsspp", "PPSSPP", setOf("psp"), setOf("iso", "cso", "pbp"), EmulatorCore.Status.PLANNED, "https://github.com/hrydgard/ppsspp", "GPL-2.0"),
        EmulatorCore("mame", "MAME", setOf("arcade-mame"), setOf("zip", "7z"), EmulatorCore.Status.PLANNED, "https://github.com/mamedev/mame", "BSD-3-Clause"),
        EmulatorCore("finalburn-neo", "FinalBurn Neo", setOf("arcade-mame", "cps1", "cps2", "cps3", "neo-geo"), setOf("zip"), EmulatorCore.Status.PLANNED, "https://github.com/finalburnneo/FBNeo", "GPL-2.0"),
        EmulatorCore("stella", "Stella", setOf("atari-2600"), setOf("a26", "bin"), EmulatorCore.Status.PLANNED, "https://github.com/stella-emu/stella", "GPL-2.0"),
        EmulatorCore("vice", "VICE", setOf("c64", "vic-20", "plus4"), setOf("d64", "t64", "prg", "crt"), EmulatorCore.Status.PLANNED, "https://github.com/VICE-Team/svn-mirror", "GPL-2.0")
    )

    fun coresForExtension(extension: String): List<EmulatorCore> = cores.filter { extension.lowercase() in it.extensions }
    fun coresForSystem(system: String): List<EmulatorCore> = cores.filter { system in it.systems }
    fun integratedForExtension(extension: String): List<EmulatorCore> = coresForExtension(extension).filter { it.status == EmulatorCore.Status.INTEGRATED }
}
