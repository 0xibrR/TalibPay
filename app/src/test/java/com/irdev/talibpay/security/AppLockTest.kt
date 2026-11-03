package com.irdev.talibpay.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AppLockTest {

    /** بديل Keystore في الاختبار: مفتاح HMAC ثابت يمكن "حذفه". */
    private class FakeMac : KeyedMac {
        var present = false
        override fun exists() = present
        override fun mac(data: ByteArray): ByteArray {
            present = true
            return Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec("device-secret".toByteArray(), "HmacSHA256"))
                doFinal(data)
            }
        }
        override fun destroy() {
            present = false
        }
    }

    private class FakeStore : LockStore {
        val values = mutableMapOf<String, String>()
        override fun read() = values.toMap()
        override fun write(values: Map<String, String>) {
            this.values.clear()
            this.values.putAll(values)
        }
        override fun clear() = values.clear()
    }

    private class FakeClock : LockClock {
        var now = 1_000_000L
        var boot = 7
        override fun elapsedRealtime() = now
        override fun bootCount() = boot
    }

    private val store = FakeStore()
    private val mac = FakeMac()
    private val clock = FakeClock()
    private fun newLock() = AppLock(store, mac, clock, iterations = 1_000)
    private val lock = newLock()

    private val pin = "482915"
    private val uid = "user-1"

    private fun enabled(mode: LockMode = LockMode.PIN) = lock.apply { enable(uid, pin, mode) }

    // ---------------- التفعيل والحالة ----------------

    @Test
    fun `lock is off until enabled`() {
        assertEquals(LockState.NotEnabled, lock.state(uid))
        enabled(LockMode.BOTH)
        assertEquals(LockState.Enabled(LockMode.BOTH, biometricEnrolled = false), lock.state(uid))
    }

    @Test
    fun `no lock before sign in`() {
        enabled()
        assertEquals(LockState.NotEnabled, lock.state(null))
        // ولا يُلغى قفل المستخدم بمجرد ظهور شاشة الدخول
        assertTrue(lock.state(uid) is LockState.Enabled)
    }

    @Test
    fun `another account signing in clears the previous user's lock`() {
        enabled()
        assertEquals(LockState.NotEnabled, lock.state("someone-else"))
        assertEquals(LockState.NotEnabled, lock.state(uid))
        assertFalse(mac.present)
    }

    @Test
    fun `mode and biometric flag are saved`() {
        enabled(LockMode.PIN)
        lock.setMode(LockMode.BIOMETRIC)
        lock.setBiometricEnrolled(true)
        assertEquals(LockState.Enabled(LockMode.BIOMETRIC, biometricEnrolled = true), lock.state(uid))
    }

    @Test
    fun `settings survive an app restart`() {
        enabled(LockMode.BOTH)
        assertEquals(LockState.Enabled(LockMode.BOTH, false), newLock().state(uid))
        assertEquals(PinResult.Success, newLock().verifyPin(pin))
    }

    // ---------------- الرمز ----------------

    @Test
    fun `correct PIN unlocks, wrong PIN does not`() {
        enabled()
        assertEquals(PinResult.Success, lock.verifyPin(pin))
        assertTrue(lock.verifyPin("000001") is PinResult.Wrong)
    }

    @Test
    fun `PIN is never stored in plain text`() {
        enabled()
        val saved = store.values.values.joinToString("|")
        assertFalse(saved.contains(pin))
        assertFalse(saved.contains(pin.toByteArray().toHex()))
    }

    @Test
    fun `same PIN gives a different verifier each time (random salt)`() {
        enabled()
        val first = store.values["verifier"]
        enabled()
        assertTrue(first != store.values["verifier"])
        assertEquals(PinResult.Success, lock.verifyPin(pin))
    }

    @Test
    fun `stolen settings are useless without the device key`() {
        enabled()
        val copied = store.values.toMap()
        // جهاز آخر: مفتاح مختلف، فالتوقيع لا يطابق
        val otherDevice = AppLock(FakeStore().apply { write(copied) }, object : KeyedMac {
            override fun exists() = true
            override fun mac(data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec("other-device".toByteArray(), "HmacSHA256"))
                doFinal(data)
            }
            override fun destroy() = Unit
        }, clock, iterations = 1_000)
        assertEquals(LockState.Tampered, otherDevice.state(uid))
        assertEquals(PinResult.Tampered, otherDevice.verifyPin(pin))
    }

    @Test
    fun `change PIN requires the current PIN`() {
        enabled()
        assertTrue(lock.changePin("111112", "730518") is PinResult.Wrong)
        assertEquals(PinResult.Success, lock.verifyPin(pin))
        assertEquals(PinResult.Success, lock.changePin(pin, "730518"))
        assertTrue(lock.verifyPin(pin) is PinResult.Wrong)
        assertEquals(PinResult.Success, lock.verifyPin("730518"))
    }

    @Test
    fun `disabling removes settings and the key`() {
        enabled()
        lock.reset()
        assertTrue(store.values.isEmpty())
        assertFalse(mac.present)
        assertEquals(LockState.NotEnabled, lock.state(uid))
    }

    // ---------------- المحاولات والانتظار ----------------

    @Test
    fun `first wrong attempts have no wait`() {
        enabled()
        repeat(4) { i ->
            assertEquals(PinResult.Wrong(attemptsBeforeLockout = 4 - i, lockoutMs = 0), lock.verifyPin("000001"))
        }
    }

    @Test
    fun `wait grows after repeated failures and blocks even the right PIN`() {
        enabled()
        repeat(4) { lock.verifyPin("000001") }
        assertEquals(PinResult.Wrong(0, 30_000), lock.verifyPin("000001"))
        // خلال الانتظار لا يُفحص الرمز حتى لو كان صحيحًا
        assertEquals(PinResult.LockedOut(30_000), lock.verifyPin(pin))
        clock.now += 10_000
        assertEquals(PinResult.LockedOut(20_000), lock.verifyPin(pin))
        clock.now += 20_000
        assertEquals(PinResult.Wrong(0, 60_000), lock.verifyPin("000001"))
        clock.now += 60_000
        assertEquals(PinResult.Wrong(0, 120_000), lock.verifyPin("000001"))
    }

    @Test
    fun `correct PIN after waiting resets the counter`() {
        enabled()
        repeat(5) { lock.verifyPin("000001") }
        clock.now += 30_000
        assertEquals(PinResult.Success, lock.verifyPin(pin))
        assertEquals(0, lock.failedAttempts())
        assertEquals(PinResult.Wrong(4, 0), lock.verifyPin("000001"))
    }

    @Test
    fun `too many failures removes the lock and requires signing in again`() {
        enabled()
        repeat(LockoutPolicy.MAX_ATTEMPTS - 1) {
            lock.verifyPin("000001")
            clock.now += LockoutPolicy.delayAfter(lock.failedAttempts())
        }
        assertEquals(PinResult.TooManyAttempts, lock.verifyPin("000001"))
        assertTrue(store.values.isEmpty())
        assertFalse(mac.present)
    }

    @Test
    fun `wait and counter survive closing the app`() {
        enabled()
        repeat(5) { lock.verifyPin("000001") }
        val restarted = newLock()
        assertEquals(5, restarted.failedAttempts())
        assertEquals(PinResult.LockedOut(30_000), restarted.verifyPin(pin))
    }

    @Test
    fun `changing the device clock does not shorten the wait`() {
        // الانتظار يُحسب بساعة الإقلاع؛ ساعة الجهاز غير مستخدمة أصلًا
        enabled()
        repeat(5) { lock.verifyPin("000001") }
        assertEquals(30_000, lock.remainingLockoutMs())
    }

    @Test
    fun `rebooting the device restarts the wait instead of skipping it`() {
        enabled()
        repeat(5) { lock.verifyPin("000001") }
        clock.now += 25_000
        // إعادة تشغيل: ساعة الإقلاع تبدأ من قرب الصفر ورقم الإقلاع يزيد
        clock.boot += 1
        clock.now = 5_000
        assertEquals(30_000, lock.remainingLockoutMs())
        clock.now += 30_000
        assertEquals(PinResult.Success, lock.verifyPin(pin))
    }

    @Test
    fun `biometric success clears failed PIN attempts`() {
        enabled(LockMode.BOTH)
        repeat(3) { lock.verifyPin("000001") }
        lock.onBiometricSuccess()
        assertEquals(0, lock.failedAttempts())
    }

    // ---------------- التلاعب ----------------

    @Test
    fun `editing the failed attempts counter is detected`() {
        enabled()
        repeat(5) { lock.verifyPin("000001") }
        store.values["failed"] = "0"
        store.values["lockDuration"] = "0"
        assertEquals(LockState.Tampered, lock.state(uid))
        assertEquals(PinResult.Tampered, lock.verifyPin(pin))
    }

    @Test
    fun `switching the mode by editing the file is detected`() {
        enabled(LockMode.PIN)
        store.values["mode"] = LockMode.BIOMETRIC.name
        assertEquals(LockState.Tampered, lock.state(uid))
    }

    @Test
    fun `replacing the PIN verifier is detected`() {
        enabled()
        val other = newLock()
        val stolen = FakeStore().also { s -> AppLock(s, mac, clock, iterations = 1_000).enable(uid, "730518", LockMode.PIN) }
        store.values["verifier"] = stolen.values.getValue("verifier")
        store.values["salt"] = stolen.values.getValue("salt")
        assertEquals(LockState.Tampered, other.state(uid))
    }

    @Test
    fun `deleting the settings file does not disable the lock`() {
        enabled()
        store.values.clear()
        assertEquals(LockState.Tampered, lock.state(uid))
    }

    @Test
    fun `missing or malformed fields are detected`() {
        enabled()
        store.values.remove("salt")
        assertEquals(LockState.Tampered, lock.state(uid))
        enabled()
        store.values["mac"] = "zz"
        assertEquals(LockState.Tampered, lock.state(uid))
    }
}
