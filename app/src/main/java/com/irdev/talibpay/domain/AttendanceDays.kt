package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student
import java.util.Calendar

/**
 * أيام الأسبوع بترتيب يبدأ بالسبت (كما في فلسطين).
 * [calendarDay] لربطها بـ Calendar عند حساب "يداوم اليوم" والحضور لاحقًا.
 */
enum class Weekday(val code: String, val calendarDay: Int) {
    SATURDAY("SAT", Calendar.SATURDAY),
    SUNDAY("SUN", Calendar.SUNDAY),
    MONDAY("MON", Calendar.MONDAY),
    TUESDAY("TUE", Calendar.TUESDAY),
    WEDNESDAY("WED", Calendar.WEDNESDAY),
    THURSDAY("THU", Calendar.THURSDAY),
    FRIDAY("FRI", Calendar.FRIDAY);

    companion object {
        fun fromCode(code: String): Weekday? = entries.firstOrNull { it.code == code }
        fun fromCalendarDay(day: Int): Weekday? = entries.firstOrNull { it.calendarDay == day }
    }
}

/**
 * أيام دوام الطالب، تُحفظ نصًا مختصرًا مرتبًا مثل "SAT,TUE,THU".
 * فارغة = لم تُحدَّد (الطلاب القدامى، أو لم يُدخلها المستخدم).
 */
object AttendanceDays {

    fun encode(days: Set<Weekday>): String = Weekday.entries.filter { it in days }.joinToString(",") { it.code }

    /** القيم غير المعروفة تُتجاهل، فلا تكسر قراءة الطالب. */
    fun decode(text: String?): Set<Weekday> =
        text.orEmpty().split(",").mapNotNull { Weekday.fromCode(it.trim()) }.toSet()

    fun isDaily(days: Set<Weekday>): Boolean = days.size == Weekday.entries.size
}

val Student.attendanceDaySet: Set<Weekday> get() = AttendanceDays.decode(attendanceDays)
