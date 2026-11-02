package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * مستحق على الطالب (مثل "رسوم أيلول" أو "الفصل الأول" أو "رسوم التسجيل").
 * الدفعات تُسجَّل مقابل مستحق محدد، فيُعرف دائمًا لأي مستحق دُفع كل مبلغ.
 */
@IgnoreExtraProperties
data class Charge(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val dueDate: String = "", // اختياري: dd/MM/yyyy
    val period: String = "", // yyyy-MM للرسوم الشهرية التلقائية، فارغ للمستحقات اليدوية
    val note: String = "",
    val createdAt: Long = 0
)
