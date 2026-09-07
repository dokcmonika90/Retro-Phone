package com.dokcmonika90.retrophone

/**
 * Multi-core registry for Retro Phone.
 *
 * A core is considered playable only when its native backend is installed.
 * Keeping registration separate from availability prevents the UI from
 * pretending that a source-only core is already integrated.
 */
data class EmulatorCore(
    val id: String,
    val system: String,
    val displayName: String,
    val extensions: Set<String>,
    val installed: Boolean,
    val nativeBackend: String? = null
)

object EmulatorCoreRegistry {
    private val cores = listOf(
        EmulatorCore("laines", "NES", "LaiNES", setOf("nes", "fds"), true, "retro_recompiler"),
        EmulatorCore("snes9x", "SNES", "Snes9x", setOf("sfc", "smc", "fig"), false, "snes9x_libretro"),
        EmulatorCore("sameboy", "Game Boy / Color", "SameBoy", setOf("gb", "gbc"), false, "sameboy_libretro"),
        EmulatorCore("mgba", "Game Boy Advance", "mGBA", setOf("gba"), false, "mgba_libretro"),
        EmulatorCore("genesis-plus-gx", "Mega Drive / Genesis", "Genesis Plus GX", setOf("md", "gen", "smd", "bin"), false, "genesis_plus_gx_libretro"),
        EmulatorCore("gearsystem", "Master System / Game Gear", "Gearsystem", setOf("sms", "gg"), false, "gearsystem_libretro"),
        EmulatorCore("mupen64plus", "Nintendo 64", "Mupen64Plus", setOf("n64", "z64", "v64"), false, "mupen64plus_libretro"),
        EmulatorCore("melonds", "Nintendo DS", "melonDS", setOf("nds"), false, "melonds_libretro"),
        EmulatorCore("flycast", "Dreamcast / Naomi", "Flycast", setOf("cdi", "gdi", "chd"), false, "flycast_libretro"),
        EmulatorCore("pcsx-rearmed", "PlayStation", "PCSX-ReARMed", setOf("cue", "iso", "img", "bin", "pbp", "chd"), false, "pcsx_rearmed_libretro"),
        EmulatorCore("ppsspp", "PlayStation Portable", "PPSSPP", setOf("iso", "cso", "chd"), false, "ppsspp_libretro"),
        EmulatorCore("stella", "Atari 2600", "Stella", setOf("a26"), false, "stella_libretro"),
        EmulatorCore("mame", "Arcade", "MAME", setOf("zip", "7z"), false, "mame_libretro"),
        EmulatorCore("fbalpha", "Arcade", "FinalBurn Neo", setOf("zip", "7z"), false, "fbneo_libretro")
    )

    fun all(): List<EmulatorCore> = cores

    fun installed(): List<EmulatorCore> = cores.filter { it.installed }

    fun findForExtension(extension: String): List<EmulatorCore> =
        cores.filter { extension.lowercase().removePrefix(".") in it.extensions }

    fun chooseForFile(filename: String): EmulatorCore? {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return findForExtension(extension).firstOrNull { it.installed }
    }
}
