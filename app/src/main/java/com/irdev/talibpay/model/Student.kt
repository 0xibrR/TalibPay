package com.irdev.talibpay.model

data class Student(
    val id: String = "",
    val name: String = "",
    val birthYear: String = "",
    val paidCash: Double = 0.0, // محموع ما تم دفعه الطالب نقدي
    val paidBank: Double = 0.0, // مجموع ما تم دفعه الطالب بنكي
    val paid: Double = 0.0, //كم دافع الطالب
    val total: Double = 0.0,// كم لازم الطالب يدفع
    val remaining: Double = 0.0, // محموع ما تبقى على طالب
    val phoneNumber: String = "",
    val notes: String = "",
    val lastModified: String = "",
    val addedIn: String = ""
)
