package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * دفعة من الطالب، مرتبطة بـ charge واحد ([chargeId]).
 * paid و total و remaining قيم محسوبة (copy للعرض والتوافق)؛ المصدر الحقيقي هو الـ charge ودفعاته.
 */
@IgnoreExtraProperties
data class Payment(
    val id: String = "",
    val chargeId: String = "", // الـ charge الذي تُسدَّد له الدفعة
    val cash: Double = 0.0, // المبلغ النقدي
    val bank: Double = 0.0, // المبلغ البنكي
    val paid: Double = 0.0, // cash + bank
    val credited: Double = 0.0, // الزيادة عن المتبقي على الـ charge، أُضيفت للـ wallet
    val total: Double = 0.0, // قيمة الـ charge وقت الدفعة
    val remaining: Double = 0.0, // المتبقي من الـ charge بعد هذه الدفعة
    val paymentMethod: String = "",
    val paymentDate: String = "",
    val note: String = ""
)
