package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * Charge (مستحق) على الطالب، مثل "رسوم أيلول" أو "الفصل الأول" أو "رسوم التسجيل".
 * كل دفعة تُسجَّل مقابل charge محدد، فيُعرف دائمًا لأي شيء دُفع كل مبلغ.
 */
@IgnoreExtraProperties
data class Charge(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val discount: Double = 0.0, // الخصم من الـ monthly fee وقت الإنشاء (amount هو بعد الخصم)
    val dueDate: String = "", // اختياري: dd/MM/yyyy
    val period: String = "", // yyyy-MM للـ monthly charges التلقائية، فارغ للـ charges اليدوية
    val note: String = "",
    val createdAt: Long = 0
)
