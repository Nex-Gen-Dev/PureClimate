package com.midea.pureclimate

object MideaProtocol {
    private const val HEADER_MARK = 4400
    private const val HEADER_SPACE = 4400
    private const val BIT_MARK = 540
    private const val ZERO_SPACE = 540
    private const val ONE_SPACE = 1620
    private const val GAP_SPACE = 5200

    val TEMP_MAP = mapOf(
        17 to 0x0, 18 to 0x1, 19 to 0x3, 20 to 0x2,
        21 to 0x6, 22 to 0x7, 23 to 0x5, 24 to 0x4,
        25 to 0xC, 26 to 0xD, 27 to 0x9, 28 to 0x8,
        29 to 0xA, 30 to 0xB
    )

    enum class Mode(val label: String, val code: Int) {
        COOL("COOL", 0x00),
        DRY("DRY", 0x04),
        AUTO("AUTO", 0x08),
        HEAT("HEAT", 0x0C),
        FAN("FAN", 0x04)
    }

    enum class FanSpeed(val label: String, val code: Int) {
        AUTO("AUTO", 0xB),
        LOW("LOW", 0x9),
        MED("MED", 0x5),
        HIGH("HIGH", 0x3)
    }

    fun buildPacket(
        powerOn: Boolean,
        temp: Int,
        mode: Mode,
        fan: FanSpeed,
        swing: Boolean = false
    ): IntArray {
        val b0 = 0xB2
        val b1: Int
        val b2: Int

        if (!powerOn) {
            b1 = 0x7B
            b2 = 0xE0
        } else if (swing) {
            b1 = 0x6B
            b2 = 0xE0
        } else {
            b1 = (fan.code shl 4) or 0x0F
            val tempCode = if (mode == Mode.FAN) 0xE else (TEMP_MAP[temp.coerceIn(17, 30)] ?: 0x4)
            b2 = (tempCode shl 4) or mode.code
        }

        val frame = intArrayOf(
            b0, b0 xor 0xFF,
            b1, b1 xor 0xFF,
            b2, b2 xor 0xFF
        )

        val pattern = mutableListOf<Int>()
        repeat(2) {
            pattern.add(HEADER_MARK)
            pattern.add(HEADER_SPACE)
            for (byte in frame) {
                for (bit in 0..7) {
                    val isOne = ((byte shr bit) and 1) == 1
                    pattern.add(BIT_MARK)
                    pattern.add(if (isOne) ONE_SPACE else ZERO_SPACE)
                }
            }
            pattern.add(BIT_MARK)
            pattern.add(GAP_SPACE)
        }
        return pattern.toIntArray()
    }
}
