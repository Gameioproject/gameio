package com.nendo.argosy.libretro

/**
 * Canonical on-disk naming for built-in libretro save-state slots. Live states are flat
 * ({statesDir}/rom.state.X) -- mirroring SRAM and external emulators -- with channels held
 * only in the cache. This is the single source of truth for slot <-> filename, shared by
 * SaveStateManager (live engine) and StateCacheManager (cache/restore).
 */
object LibretroStateSlots {
    const val AUTO_SLOT = -1
    const val RESUME_SLOT = -2
    const val MAX_SLOT = 9
    const val QUICK_SLOT_BASE = 100
    const val QUICK_RING_SIZE = 10

    /** Every slot the built-in core can write, for callers that mean "all states" rather than a range. */
    val ALL_SLOTS: List<Int> =
        listOf(RESUME_SLOT, AUTO_SLOT) +
            (0..MAX_SLOT) +
            (QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE)

    private val NUMBERED_SUFFIX = Regex("""\.state(\d+)""", RegexOption.IGNORE_CASE)

    fun fileName(romBaseName: String, slotNumber: Int): String = when (slotNumber) {
        AUTO_SLOT -> "$romBaseName.state.auto"
        RESUME_SLOT -> "$romBaseName.state.resume"
        in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE ->
            "$romBaseName.state.q${slotNumber - QUICK_SLOT_BASE}"
        0 -> "$romBaseName.state"
        else -> "$romBaseName.state$slotNumber"
    }

    private val QUICK_SUFFIX = Regex("""\.state\.q(\d)""", RegexOption.IGNORE_CASE)

    /** Whether [slotNumber] is one of the quick-save ring. */
    fun isQuickSlot(slotNumber: Int): Boolean =
        slotNumber in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE

    /**
     * Inverse of [fileName] for the cache/sync-eligible slots: the auto slot, the numbered slots
     * (0..N) and the quick-save ring ("$romBaseName.state.qN", reported as [QUICK_SLOT_BASE] + N).
     * A quick save is the way most sessions end on a handheld, so it travels with the account like
     * any other slot; leaving it live-only meant signing out threw it away and a second device never
     * saw it. Returns null for the one-shot resume state, which is consumed by the next launch and
     * must never be cached. Keeping this next to [fileName] makes the write and read codecs one
     * source of truth, so a change to the flat naming can't silently diverge from the parser.
     */
    fun parseSlotNumber(romBaseName: String, fileName: String): Int? {
        if (!fileName.startsWith(romBaseName, ignoreCase = true)) return null
        val suffix = fileName.substring(romBaseName.length)
        return when {
            suffix.equals(".state", ignoreCase = true) -> 0
            suffix.equals(".state.auto", ignoreCase = true) -> AUTO_SLOT
            else -> NUMBERED_SUFFIX.matchEntire(suffix)?.groupValues?.get(1)?.toIntOrNull()
                ?: QUICK_SUFFIX.matchEntire(suffix)?.groupValues?.get(1)?.toIntOrNull()?.let { QUICK_SLOT_BASE + it }
        }
    }
}
