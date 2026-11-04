package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * حركة في الـ wallet (رصيد دائن). Append-only: الحركات لا تُعدَّل ولا تُحذف،
 * والرصيد دائمًا مجموعها.
 * - DEPOSIT: زيادة دفعة عن المتبقي على الـ charge الخاص بها ([paymentId]).
 * - USE: استخدام الرصيد لتسديد charge جديد ([chargeId])، وليس دفعة نقدية.
 * - REFUND: إرجاع الرصيد للطالب.
 */
@IgnoreExtraProperties
data class WalletEntry(
    val id: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val chargeId: String = "",
    val paymentId: String = "",
    val note: String = "",
    val createdAt: Long = 0
)
