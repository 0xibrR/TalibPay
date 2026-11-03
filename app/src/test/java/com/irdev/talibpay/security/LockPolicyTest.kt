package com.irdev.talibpay.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LockPolicyTest {

    // ---------------- PBKDF2 (متجهات RFC 7914، القسم 11) ----------------

    @Test
    fun `PBKDF2-HMAC-SHA256 matches RFC 7914 with 1 iteration`() {
        val key = Pbkdf2.deriveKey("passwd".toByteArray(), "salt".toByteArray(), 1, 64)
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            key.toHex()
        )
    }

    @Test
    fun `PBKDF2-HMAC-SHA256 matches RFC 7914 with 80000 iterations`() {
        val key = Pbkdf2.deriveKey("Password".toByteArray(), "NaCl".toByteArray(), 80_000, 64)
        assertEquals(
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
            key.toHex()
        )
    }

    @Test
    fun `hex round trip`() {
        val bytes = byteArrayOf(0, 15, -1, -128, 127)
        assertArrayEquals(bytes, bytes.toHex().hexToBytes())
        assertEquals(null, "abc".hexToBytes())
        assertEquals(null, "zz".hexToBytes())
    }

    // ---------------- قواعد الرمز ----------------

    @Test
    fun `PIN must be exactly 6 digits`() {
        assertEquals(PinRules.Validation.Valid, PinRules.validate("482915"))
        assertEquals(PinRules.Validation.WrongLength, PinRules.validate("48291"))
        assertEquals(PinRules.Validation.WrongLength, PinRules.validate("4829150"))
        assertEquals(PinRules.Validation.NotDigits, PinRules.validate("48291a"))
        assertEquals(PinRules.Validation.NotDigits, PinRules.validate("٤٨٢٩١٥"))
    }

    @Test
    fun `easy to guess PINs are rejected`() {
        listOf("000000", "111111", "123456", "234567", "987654", "654321").forEach {
            assertEquals(it, PinRules.Validation.TooSimple, PinRules.validate(it))
        }
        assertEquals(PinRules.Validation.Valid, PinRules.validate("112233"))
    }

    // ---------------- سياسة الانتظار ----------------

    @Test
    fun `wait doubles after the free attempts and is capped at one hour`() {
        assertEquals(0, LockoutPolicy.delayAfter(4))
        assertEquals(30_000, LockoutPolicy.delayAfter(5))
        assertEquals(60_000, LockoutPolicy.delayAfter(6))
        assertEquals(120_000, LockoutPolicy.delayAfter(7))
        assertEquals(60 * 60_000L, LockoutPolicy.delayAfter(30))
    }

    // ---------------- طرق الفتح ----------------

    @Test
    fun `unlock methods follow the chosen mode`() {
        assertEquals(UnlockMethods(biometric = true, pin = false), unlockMethodsFor(LockMode.BIOMETRIC, true))
        assertEquals(UnlockMethods(biometric = false, pin = true), unlockMethodsFor(LockMode.PIN, true))
        assertEquals(UnlockMethods(biometric = true, pin = true), unlockMethodsFor(LockMode.BOTH, true))
    }

    @Test
    fun `PIN is the fallback when fingerprint is unavailable`() {
        LockMode.entries.forEach { mode ->
            assertEquals(UnlockMethods(biometric = false, pin = true), unlockMethodsFor(mode, biometricAvailable = false))
        }
    }
}
