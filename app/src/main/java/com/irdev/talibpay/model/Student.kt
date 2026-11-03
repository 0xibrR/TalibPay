package com.irdev.talibpay.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Student(
    val id: String = "",
    val name: String = "",
    val birthYear: String = "",
    val registrationDate: String = "", // تاريخ التسجيل dd/MM/yyyy
    val attendanceDays: String = "", // أيام الدوام مثل "SAT,TUE,THU" (انظر AttendanceDays)؛ فارغ = غير محددة
    val monthlyFee: Double = 0.0, // الرسوم الشهرية؛ 0 = لا رسوم تلقائية
    val discountType: String = "", // PERCENT أو AMOUNT؛ فارغ = بدون خصم
    val discountValue: Double = 0.0, // النسبة (0-100) أو المبلغ المخصوم من كل شهر
    val discountReason: String = "", // سبب الخصم (مثل: أخ، منحة)
    val billingStart: String = "", // أول تاريخ تُحسب منه الرسوم الشهرية dd/MM/yyyy
    val lastBilledPeriod: String = "", // آخر شهر أُنشئت رسومه yyyy-MM (يمنع التكرار)
    val paidCash: Double = 0.0, // مجموع ما دفعه الطالب نقدًا
    val paidBank: Double = 0.0, // مجموع ما دفعه الطالب بنكيًا
    val paid: Double = 0.0, // مجموع ما دفعه الطالب
    val total: Double = 0.0, // الرسوم المطلوبة من الطالب
    val remaining: Double = 0.0, // المتبقي على الطالب
    val walletBalance: Double = 0.0, // رصيد المحفظة (نسخة للعرض؛ المصدر حركات المحفظة)
    val phoneNumber: String = "",
    val phoneCountryCode: String = "", // رمز الدولة لرقم الهاتف (مثل 970)؛ فارغ = الرمز الافتراضي من الإعدادات
    val notes: String = "",
    val archived: Boolean = false, // مؤرشف: مخفي من القائمة الرئيسية ولا تُنشأ له رسوم شهرية جديدة
    val archivedAt: Long = 0, // وقت الأرشفة (epoch millis)
    val addedAt: Long = 0, // وقت الإضافة (epoch millis)
    val updatedAt: Long = 0, // وقت آخر تعديل (epoch millis)
    // حقول نصية من النسخ القديمة، تُقرأ فقط عند غياب الطوابع الزمنية
    val lastModified: String = "",
    val addedIn: String = ""
)
