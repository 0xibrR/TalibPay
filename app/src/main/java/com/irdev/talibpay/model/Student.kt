package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Student(
    val id: String = "",
    val name: String = "",
    val birthYear: String = "",
    val registrationDate: String = "", // dd/MM/yyyy
    val attendanceDays: String = "", // أيام الدوام مثل "SAT,TUE,THU" (انظر AttendanceDays)؛ فارغ = غير محددة
    val monthlyFee: Double = 0.0, // 0 = لا monthly charges تلقائية
    val discountType: String = "", // PERCENT أو AMOUNT؛ فارغ = بدون خصم
    val discountValue: Double = 0.0, // النسبة (0-100) أو المبلغ المخصوم كل شهر
    val discountReason: String = "", // سبب الخصم (مثل: أخ، منحة)
    val billingStart: String = "", // أول تاريخ تُحسب منه الـ monthly fee، dd/MM/yyyy
    val lastBilledPeriod: String = "", // آخر شهر أُنشئ له charge، yyyy-MM (يمنع التكرار)
    val nextBillingDate: String = "", // موعد الـ billing القادم yyyy-MM-dd، index للـ query؛ فارغ = لا رسوم تلقائية
    val paidCash: Double = 0.0, // مجموع النقدي (computed)
    val paidBank: Double = 0.0, // مجموع البنكي (computed)
    val paid: Double = 0.0, // مجموع المدفوع (computed)
    val total: Double = 0.0, // مجموع الـ charges (computed)
    val remaining: Double = 0.0, // المتبقي على الطالب (computed)
    val walletBalance: Double = 0.0, // رصيد الـ wallet (copy للعرض؛ المصدر هو حركات الـ wallet)
    val phoneNumber: String = "",
    val phoneCountryCode: String = "", // رمز الدولة (مثل 970)؛ فارغ = الافتراضي من الإعدادات
    val notes: String = "",
    val archived: Boolean = false, // مخفي من القائمة الرئيسية ولا تُنشأ له monthly charges جديدة
    val archivedAt: Long = 0, // epoch millis
    val addedAt: Long = 0, // epoch millis
    val updatedAt: Long = 0, // epoch millis
    // Legacy: حقول نصية من النسخ القديمة، تُقرأ فقط عند غياب الـ timestamps
    val lastModified: String = "",
    val addedIn: String = ""
)
