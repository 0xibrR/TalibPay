package com.irdev.talibpay.util

import com.irdev.talibpay.util.XlsxWriter.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { files[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return files
    }

    private fun build(vararg sheets: XlsxWriter.Sheet, rtl: Boolean = true): Map<String, String> {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(out, sheets.toList(), rtl)
        return unzip(out.toByteArray())
    }

    private val students = XlsxWriter.Sheet(
        "الطلاب",
        listOf("الاسم", "المتبقي"),
        listOf(
            listOf(Cell.Text("Lana & \"Sami\" <test>"), Cell.Number(150.0)),
            listOf(Cell.Text("رهف"), Cell.Number(12.5))
        )
    )

    @Test
    fun `workbook has all required parts`() {
        val files = build(students, XlsxWriter.Sheet("الدفعات", listOf("التاريخ"), emptyList()))
        listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
            "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"
        ).forEach { assertTrue(it, it in files) }
    }

    @Test
    fun `every part is well-formed XML`() {
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        build(students).forEach { (name, xml) ->
            parser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            assertTrue(name, xml.startsWith("<?xml"))
        }
    }

    @Test
    fun `text is escaped and numbers are real numbers`() {
        val sheet = build(students).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Lana &amp; &quot;Sami&quot; &lt;test&gt;"))
        assertTrue(sheet.contains("""<c r="B2"><v>150</v></c>"""))
        assertTrue(sheet.contains("""<c r="B3"><v>12.5</v></c>"""))
        assertTrue(sheet.contains("رهف"))
    }

    @Test
    fun `header row is bold and frozen`() {
        val sheet = build(students).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<c r="A1" t="inlineStr" s="1">"""))
        assertTrue(sheet.contains("state=\"frozen\""))
    }

    @Test
    fun `sheet direction follows the app language`() {
        assertTrue(build(students, rtl = true).getValue("xl/worksheets/sheet1.xml").contains("rightToLeft=\"1\""))
        assertFalse(build(students, rtl = false).getValue("xl/worksheets/sheet1.xml").contains("rightToLeft"))
    }

    @Test
    fun `column names`() {
        assertEquals(listOf("A", "B", "Z", "AA", "AZ", "BA"), listOf(0, 1, 25, 26, 51, 52).map(XlsxWriter::columnName))
    }

    @Test
    fun `numbers never use scientific notation or a locale comma`() {
        assertEquals("1000000", XlsxWriter.formatNumber(1_000_000.0))
        assertEquals("0.05", XlsxWriter.formatNumber(0.05))
        assertEquals("1234.5", XlsxWriter.formatNumber(1234.50))
    }

    @Test
    fun `control characters are dropped and sheet names are made safe`() {
        assertEquals("ab\ncd", XlsxWriter.escape("a\u0001b\ncd\u0000"))
        assertEquals("Fees 202611", XlsxWriter.safeSheetName("Fees: 2026/11?"))
        assertEquals(31, XlsxWriter.safeSheetName("x".repeat(50)).length)
        assertEquals("Sheet", XlsxWriter.safeSheetName("[]*"))
    }
}
