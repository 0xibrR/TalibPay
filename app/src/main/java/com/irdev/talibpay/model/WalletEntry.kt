package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * حركة في محفظة الطالب (رصيد دائن). السجل إضافي فقط: لا تُعدَّل الحركات ولا تُحذف،
 * والرصيد يُحسب دائمًا من مجموعها.
 * - DEPOSIT: زيادة دفعة عن المتبقي على مستحقها ([paymentId]).
 * - USE: استخدام الرصيد لتسديد مستحق جديد ([chargeId])، وليس دفعة نقدية.
 * - REFUND: إعادة الرصيد للطالب.
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
