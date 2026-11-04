package com.irdev.talibpay.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 (RFC 8018).
 * مكتوب يدويًا لأن "PBKDF2WithHmacSHA256" غير متوفر في SecretKeyFactory قبل API 26،
 * والتطبيق يدعم من API 24. يُختبر مقابل الـ test vectors المنشورة في RFC 7914.
 */
object Pbkdf2 {

    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_LENGTH = 32

    fun deriveKey(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        require(keyLength > 0) { "keyLength must be positive" }
        val mac = Mac.getInstance(ALGORITHM)
        // SecretKeySpec لا يقبل password فارغة؛ والـ PIN لا يكون فارغًا أصلًا
        mac.init(SecretKeySpec(password, ALGORITHM))

        val blocks = (keyLength + HASH_LENGTH - 1) / HASH_LENGTH
        val output = ByteArray(keyLength)
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val offset = (block - 1) * HASH_LENGTH
            t.copyInto(output, offset, 0, minOf(HASH_LENGTH, keyLength - offset))
            t.fill(0)
            u.fill(0)
        }
        return output
    }
}
