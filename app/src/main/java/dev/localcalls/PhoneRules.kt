package dev.localcalls

/** Pure, Android-free call rules so they can be unit tested on the JVM. */
object PhoneRules {

    enum class Decision(val allow: Boolean) {
        ALLOW_DISABLED(true),
        ALLOW_NO_HANDLE(true),
        ALLOW_EMERGENCY(true),
        ALLOW_LOCAL_PREFIX(true),
        ALLOW_SHORT_NUMBER(true),
        ALLOW_CONTACT(true),
        BLOCK(false),
    }

    /**
     * Strips spaces, hyphens, parentheses, dots and any other non-digit characters.
     * A leading '+' is kept, and the "00" international access prefix becomes '+'.
     */
    fun normalize(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when {
                c in '0'..'9' -> sb.append(c)
                c == '+' && sb.isEmpty() -> sb.append(c)
            }
        }
        val n = sb.toString()
        return if (n.startsWith("00")) "+" + n.substring(2) else n
    }

    /** "370", "+370", "00370" and " +370 " all become "+370". Empty if no digits. */
    fun normalizeCountryCode(code: String): String {
        val digits = normalize(code).removePrefix("+")
        return if (digits.isEmpty()) "" else "+$digits"
    }

    /** "+370, 371 00 44" -> ["+370", "+371", "+44"]. Invalid entries are dropped. */
    fun parseAllowedPrefixes(csv: String): List<String> =
        csv.split(',', ';', '\n')
            .map { normalizeCountryCode(it) }
            .filter { it.length in 2..MAX_PREFIX_LENGTH }
            .distinct()

    /** "+" plus up to 7 digits — enough for a country code plus an area/operator code. */
    const val MAX_PREFIX_LENGTH = 8

    /** "0, 8" -> ["0", "8"]. */
    fun parseTrunkPrefixes(csv: String): List<String> =
        csv.split(',', ';', ' ')
            .map { it.filter { c -> c in '0'..'9' } }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Longest number still treated as a domestic short/service number (banks, carriers, 19xx lines). */
    const val MAX_SHORT_NUMBER_LENGTH = 6

    /** Short service numbers like 1588 or 19800: no '+', at most [MAX_SHORT_NUMBER_LENGTH] digits. */
    fun isShortNumber(normalized: String): Boolean =
        !normalized.startsWith("+") && normalized.length in 1..MAX_SHORT_NUMBER_LENGTH

    fun isLocal(normalized: String, allowedPrefixes: List<String>, trunkPrefixes: List<String>): Boolean {
        if (allowedPrefixes.any { p ->
                val cc = normalizeCountryCode(p)
                cc.isNotEmpty() && normalized.startsWith(cc)
            }
        ) return true
        // National format (no '+') starting with a trunk prefix, e.g. 8 6xx xxxxx in Lithuania.
        if (!normalized.startsWith("+")) {
            return trunkPrefixes.any { it.isNotEmpty() && normalized.startsWith(it) }
        }
        return false
    }

    /**
     * Cheap checks run first; the contacts lookup only happens for numbers that
     * would otherwise be blocked.
     */
    fun decide(
        enabled: Boolean,
        rawNumber: String?,
        allowedPrefixes: List<String>,
        trunkPrefixes: List<String>,
        isEmergency: (String) -> Boolean,
        isContact: (String) -> Boolean,
    ): Decision {
        if (!enabled) return Decision.ALLOW_DISABLED
        if (rawNumber.isNullOrBlank()) return Decision.ALLOW_NO_HANDLE
        val normalized = normalize(rawNumber)
        if (normalized.isEmpty() || normalized == "+") return Decision.ALLOW_NO_HANDLE
        if (isEmergency(rawNumber) || isEmergency(normalized)) return Decision.ALLOW_EMERGENCY
        if (isLocal(normalized, allowedPrefixes, trunkPrefixes)) return Decision.ALLOW_LOCAL_PREFIX
        if (isShortNumber(normalized)) return Decision.ALLOW_SHORT_NUMBER
        if (isContact(rawNumber) || isContact(normalized)) return Decision.ALLOW_CONTACT
        return Decision.BLOCK
    }
}
