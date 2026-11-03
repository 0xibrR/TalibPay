package com.irdev.talibpay.util

import android.content.Context
import com.google.android.material.chip.Chip
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ViewAttendanceDaysBinding
import com.irdev.talibpay.domain.AttendanceDays
import com.irdev.talibpay.domain.Weekday

private fun ViewAttendanceDaysBinding.dayChips(): Map<Weekday, Chip> = mapOf(
    Weekday.SATURDAY to chipSaturday,
    Weekday.SUNDAY to chipSunday,
    Weekday.MONDAY to chipMonday,
    Weekday.TUESDAY to chipTuesday,
    Weekday.WEDNESDAY to chipWednesday,
    Weekday.THURSDAY to chipThursday,
    Weekday.FRIDAY to chipFriday
)

/**
 * أزرار أيام الدوام: "يوميًا" يحدد كل الأيام أو يلغيها،
 * ويُعلَّم تلقائيًا عند اختيار الأيام السبعة يدويًا.
 */
fun ViewAttendanceDaysBinding.setup(initial: Set<Weekday>) {
    val chips = dayChips()
    var syncing = false
    chips.forEach { (day, chip) -> chip.isChecked = day in initial }
    chipDaily.isChecked = AttendanceDays.isDaily(initial)

    chipDaily.setOnCheckedChangeListener { _, checked ->
        if (syncing) return@setOnCheckedChangeListener
        syncing = true
        chips.values.forEach { it.isChecked = checked }
        syncing = false
    }
    chips.values.forEach { chip ->
        chip.setOnCheckedChangeListener { _, _ ->
            if (syncing) return@setOnCheckedChangeListener
            syncing = true
            chipDaily.isChecked = chips.values.all { it.isChecked }
            syncing = false
        }
    }
}

fun ViewAttendanceDaysBinding.selectedDays(): Set<Weekday> =
    dayChips().filterValues { it.isChecked }.keys

fun ViewAttendanceDaysBinding.clearDays() {
    chipDaily.isChecked = false
    dayChips().values.forEach { it.isChecked = false }
}

/** أيام الدوام للعرض: "يوميًا"، أو "الأحد، الثلاثاء، الخميس"، أو "غير محددة". */
fun Context.attendanceDaysText(days: Set<Weekday>): String = when {
    days.isEmpty() -> getString(R.string.attendance_days_not_set)
    AttendanceDays.isDaily(days) -> getString(R.string.attendance_daily)
    else -> Weekday.entries.filter { it in days }.joinToString(getString(R.string.list_separator)) { getString(dayName(it)) }
}

private fun dayName(day: Weekday): Int = when (day) {
    Weekday.SATURDAY -> R.string.day_saturday
    Weekday.SUNDAY -> R.string.day_sunday
    Weekday.MONDAY -> R.string.day_monday
    Weekday.TUESDAY -> R.string.day_tuesday
    Weekday.WEDNESDAY -> R.string.day_wednesday
    Weekday.THURSDAY -> R.string.day_thursday
    Weekday.FRIDAY -> R.string.day_friday
}
