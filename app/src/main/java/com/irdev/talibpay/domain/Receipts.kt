package com.irdev.talibpay.domain

/** رقم الإيصال: اختصار ثابت من id الدفعة، فإعادة إرسال إيصال نفس الدفعة تعطي نفس الرقم. */
object Receipts {
    private const val LENGTH = 6

    fun number(paymentId: String): String {
        val code = paymentId.filter { it.isLetterOrDigit() }.takeLast(LENGTH).uppercase()
        return "R-" + code.padStart(LENGTH, '0')
    }
}
