package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AttendanceDaysTest {

    @Test
    fun `days are saved in week order starting Saturday, whatever order they were picked`() {
        val days = setOf(Weekday.THURSDAY, Weekday.SUNDAY, Weekday.TUESDAY)
        assertEquals("SUN,TUE,THU", AttendanceDays.encode(days))
        assertEquals("SAT,FRI", AttendanceDays.encode(setOf(Weekday.FRIDAY, Weekday.SATURDAY)))
    }

    @Test
    fun `saved days are read back`() {
        assertEquals(setOf(Weekday.SUNDAY, Weekday.TUESDAY, Weekday.THURSDAY), AttendanceDays.decode("SUN,TUE,THU"))
        assertEquals(setOf(Weekday.SUNDAY, Weekday.TUESDAY), Student(attendanceDays = "SUN,TUE").attendanceDaySet)
    }

    @Test
    fun `no days set for older students or empty input`() {
        assertTrue(AttendanceDays.decode("").isEmpty())
        assertTrue(AttendanceDays.decode(null).isEmpty())
        assertTrue(Student().attendanceDaySet.isEmpty())
        assertEquals("", AttendanceDays.encode(emptySet()))
    }

    @Test
    fun `unknown or messy values are ignored instead of failing`() {
        assertEquals(setOf(Weekday.MONDAY, Weekday.FRIDAY), AttendanceDays.decode(" MON , XYZ,FRI,"))
    }

    @Test
    fun `all seven days means every day`() {
        assertTrue(AttendanceDays.isDaily(Weekday.entries.toSet()))
        assertFalse(AttendanceDays.isDaily(Weekday.entries.toSet() - Weekday.FRIDAY))
        assertEquals("SAT,SUN,MON,TUE,WED,THU,FRI", AttendanceDays.encode(Weekday.entries.toSet()))
    }

    @Test
    fun `encoded value fits the database rule`() {
        // القاعدة: نص ≤ 27 حرفًا من رموز الأيام مفصولة بفواصل
        val rule = Regex("^((SAT|SUN|MON|TUE|WED|THU|FRI)(,(SAT|SUN|MON|TUE|WED|THU|FRI))*)?$")
        val all = AttendanceDays.encode(Weekday.entries.toSet())
        assertTrue(all.length <= 27)
        assertTrue(rule.matches(all))
        assertTrue(rule.matches(""))
        assertTrue(rule.matches(AttendanceDays.encode(setOf(Weekday.WEDNESDAY))))
    }

    @Test
    fun `days map to the calendar for attendance later`() {
        assertEquals(Weekday.SATURDAY, Weekday.fromCalendarDay(Calendar.SATURDAY))
        assertEquals(Weekday.FRIDAY, Weekday.fromCalendarDay(Calendar.FRIDAY))
    }
}
