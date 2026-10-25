package com.midea.pureclimate.data

/** Order matches the codebook's mode index (and the MODE button cycle). */
enum class AcMode(val label: String) { COOL("COOL"), DRY("DRY"), AUTO("AUTO"), HEAT("HEAT"), FAN("FAN") }

/** Order matches the codebook's fan index. */
enum class FanSpeed(val label: String) { AUTO("AUTO"), LOW("LOW"), MED("MED"), HIGH("HIGH") }

enum class AppTheme(val label: String) {
    CLASSIC_LCD("Classic Dark LCD"),
    DARK_MINIMAL("Dark Minimal"),
    LIGHT_CLEAN("Light Clean"),
}

/** A raw signal recorded from the original remote through the IR receiver. */
data class LearnedButton(
    val label: String,
    val pattern: IntArray,
    val carrierHz: Int = 38000,
) {
    override fun equals(other: Any?): Boolean =
        other is LearnedButton && label == other.label && carrierHz == other.carrierHz &&
            pattern.contentEquals(other.pattern)

    override fun hashCode(): Int = (label.hashCode() * 31 + pattern.contentHashCode()) * 31 + carrierHz
}

/**
 * One air conditioner. It is driven either by a codebook protocol (full state
 * control) or, when the protocol is unknown, by buttons learned from its remote.
 */
data class Room(
    val id: String,
    val name: String,
    /** Codebook variant id, e.g. "COOLIX". Null for a learned-only remote. */
    val protocolId: String? = DEFAULT_PROTOCOL,
    val learned: List<LearnedButton> = emptyList(),
    val power: Boolean = false,
    val temp: Int = 24,
    val mode: AcMode = AcMode.COOL,
    val fan: FanSpeed = FanSpeed.AUTO,
    val swing: Boolean = false,
) {
    val isLearnedOnly: Boolean get() = protocolId == null

    fun statusLine(): String = when {
        isLearnedOnly -> "${learned.size} learned buttons"
        !power -> "OFF"
        else -> "$temp°C • ${mode.label}"
    }

    companion object {
        const val DEFAULT_PROTOCOL = "COOLIX"
        const val MIN_TEMP = 17
        const val MAX_TEMP = 30
    }
}
