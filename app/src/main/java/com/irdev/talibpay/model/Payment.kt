package com.irdev.talibpay.model

data class Payment(
    val id: String = "",
    val cash: Double = 0.0, // كم دفع الطالب نقدي
    val bank: Double = 0.0, // كم دفع الطالب بنكي
    val paid: Double = 0.0, //كم دافع الطالب
    val total: Double = 0.0, // كم لازم الطالب يدفع
    val remaining: Double = 0.0, // المتبقي للطالب
    val paymentMethod: String = "",
    val paymentDate: String = "",
    val note: String = ""
)

