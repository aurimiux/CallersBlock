package dev.localcalls

import dev.localcalls.PhoneRules.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneRulesTest {
    private val trunks = listOf("0", "8")

    private fun decide(
        number: String?,
        enabled: Boolean = true,
        emergency: Boolean = false,
        contact: Boolean = false,
    ) = PhoneRules.decide(enabled, number, "+370", trunks, { emergency }, { contact })

    @Test fun normalizeStripsFormatting() {
        assertEquals("+37061234567", PhoneRules.normalize(" +370 (612) 34-567 "))
        assertEquals("+4420712345", PhoneRules.normalize("0044 20 712.345"))
        assertEquals("861234567", PhoneRules.normalize("8 612 34567"))
    }

    @Test fun countryCodeNormalization() {
        assertEquals("+370", PhoneRules.normalizeCountryCode("370"))
        assertEquals("+370", PhoneRules.normalizeCountryCode("00370"))
        assertEquals("", PhoneRules.normalizeCountryCode("  "))
    }

    @Test fun trunkParsing() {
        assertEquals(listOf("0", "8"), PhoneRules.parseTrunkPrefixes("0, 8,,8"))
    }

    @Test fun allowsLocal() {
        assertEquals(Decision.ALLOW_LOCAL_PREFIX, decide("+370 612 34567"))
        assertEquals(Decision.ALLOW_LOCAL_PREFIX, decide("00370 612 34567"))
        assertEquals(Decision.ALLOW_LOCAL_PREFIX, decide("8 612 34567"))
    }

    @Test fun blocksForeign() {
        assertEquals(Decision.BLOCK, decide("+44 20 7123 4567"))
        assertEquals(Decision.BLOCK, decide("0044 20 7123 4567")) // 00 is international, not trunk 0
        assertEquals(Decision.BLOCK, decide("+3712345678")) // Latvia, shares the +37 start
    }

    @Test fun allowsShortNumbers() {
        assertEquals(Decision.ALLOW_SHORT_NUMBER, decide("1588"))
        assertEquals(Decision.ALLOW_SHORT_NUMBER, decide("19 800"))
        assertEquals(Decision.BLOCK, decide("+1588")) // '+' means international, not short
        assertEquals(Decision.BLOCK, decide("1234567")) // 7 digits, no trunk prefix
    }

    @Test fun allowExceptions() {
        assertEquals(Decision.ALLOW_DISABLED, decide("+44 20 7123 4567", enabled = false))
        assertEquals(Decision.ALLOW_CONTACT, decide("+44 20 7123 4567", contact = true))
        assertEquals(Decision.ALLOW_EMERGENCY, decide("112", emergency = true))
        assertEquals(Decision.ALLOW_NO_HANDLE, decide(null))
        assertEquals(Decision.ALLOW_NO_HANDLE, decide("  "))
    }
}
