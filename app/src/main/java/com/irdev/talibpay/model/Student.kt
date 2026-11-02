package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Student(
    val id: String = "",
    val name: String = "",
    val birthYear: String = "",
    val registrationDate: String = "", // تاريخ التسجيل dd/MM/yyyy
    val paidCash: Double = 0.0, // مجموع ما دفعه الطالب نقدًا
    val paidBank: Double = 0.0, // مجموع ما دفعه الطالب بنكيًا
    val paid: Double = 0.0, // مجموع ما دفعه الطالب
    val total: Double = 0.0, // الرسوم المطلوبة من الطالب
    val remaining: Double = 0.0, // المتبقي على الطالب
    val phoneNumber: String = "",
    val notes: String = "",
    val addedAt: Long = 0, // وقت الإضافة (epoch millis)
    val updatedAt: Long = 0, // وقت آخر تعديل (epoch millis)
    // حقول نصية من النسخ القديمة، تُقرأ فقط عند غياب الطوابع الزمنية
    val lastModified: String = "",
    val addedIn: String = ""
)
