package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * دفعة دفعها الطالب، مرتبطة بمستحق واحد ([chargeId]).
 * الحقول paid و total و remaining محسوبة (تُكتب كنسخة للعرض والتوافق) ومصدرها الحقيقي المستحق ودفعاته.
 */
@IgnoreExtraProperties
data class Payment(
    val id: String = "",
    val chargeId: String = "", // المستحق الذي تُسدَّد له هذه الدفعة
    val cash: Double = 0.0, // المبلغ النقدي
    val bank: Double = 0.0, // المبلغ البنكي
    val paid: Double = 0.0, // نقدي + بنكي
    val credited: Double = 0.0, // الزيادة عن المتبقي على المستحق، أُضيفت لمحفظة الطالب
    val total: Double = 0.0, // قيمة المستحق وقت الدفعة
    val remaining: Double = 0.0, // المتبقي من المستحق بعد هذه الدفعة
    val paymentMethod: String = "",
    val paymentDate: String = "",
    val note: String = ""
)
