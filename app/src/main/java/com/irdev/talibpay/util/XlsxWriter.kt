package com.irdev.talibpay.util

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * كاتب ملفات Excel (.xlsx) خفيف بدون مكتبات خارجية (Apache POI يضيف عدة ميجابايت للتطبيق).
 * ملف xlsx هو أرشيف ZIP لملفات XML (معيار Office Open XML)؛ نكتب الحد الأدنى منه:
 * عدة أوراق، صف عنوان عريض، نصوص وأرقام حقيقية (قابلة للجمع في Excel)، واتجاه من اليمين لليسار عند الحاجة.
 */
object XlsxWriter {

    sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell
    }

    /** @param header صف العنوان (يُكتب عريضًا ويُثبَّت أعلى الورقة). */
    data class Sheet(val name: String, val header: List<String>, val rows: List<List<Cell>>)

    fun write(out: OutputStream, sheets: List<Sheet>, rightToLeft: Boolean) {
        require(sheets.isNotEmpty()) { "At least one sheet is required" }
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", contentTypes(sheets.size))
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("xl/workbook.xml", workbook(sheets))
            zip.entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> zip.entry("xl/worksheets/sheet${i + 1}.xml", worksheet(sheet, rightToLeft)) }
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun worksheet(sheet: Sheet, rightToLeft: Boolean): String = buildString {
        append(XML_HEADER)
        append("""<worksheet xmlns="$NS_MAIN" xmlns:r="$NS_REL">""")
        append("""<sheetViews><sheetView workbookViewId="0"""")
        if (rightToLeft) append(""" rightToLeft="1"""")
        append("><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        val columns = maxOf(sheet.header.size, sheet.rows.maxOfOrNull { it.size } ?: 0)
        if (columns > 0) append("""<cols><col min="1" max="$columns" width="20" customWidth="1"/></cols>""")
        append("<sheetData>")
        appendRow(1, sheet.header.map { Cell.Text(it) }, style = STYLE_HEADER)
        sheet.rows.forEachIndexed { i, row -> appendRow(i + 2, row, style = 0) }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.appendRow(index: Int, cells: List<Cell>, style: Int) {
        append("""<row r="$index">""")
        cells.forEachIndexed { col, cell ->
            val ref = columnName(col) + index
            val s = if (style != 0) """ s="$style"""" else ""
            when (cell) {
                is Cell.Text -> append("""<c r="$ref" t="inlineStr"$s><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                is Cell.Number -> append("""<c r="$ref"$s><v>${formatNumber(cell.value)}</v></c>""")
            }
        }
        append("</row>")
    }

    /** A, B, ..., Z, AA, AB, ... */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** يهرّب رموز XML ويحذف محارف التحكم غير المسموحة فيه (قد تأتي من ملاحظات ملصوقة). */
    internal fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    /** أرقام بدون أس علمي، ونقطة عشرية دائمًا (مهما كانت لغة الجهاز). */
    internal fun formatNumber(value: Double): String =
        if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
        else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    /** أسماء الأوراق: 31 حرفًا كحد أقصى وبدون الرموز الممنوعة في Excel. */
    internal fun safeSheetName(name: String): String =
        name.filterNot { it in "[]:*?/\\" }.take(31).ifBlank { "Sheet" }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append(XML_HEADER)
        append("""<workbook xmlns="$NS_MAIN" xmlns:r="$NS_REL"><sheets>""")
        sheets.forEachIndexed { i, sheet ->
            append("""<sheet name="${escape(safeSheetName(sheet.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append(XML_HEADER)
        append("""<Relationships xmlns="$NS_PKG_REL">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="$NS_DOC_REL/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="$NS_DOC_REL/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun contentTypes(count: Int): String = buildString {
        append(XML_HEADER)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val STYLE_HEADER = 1
    private const val XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    private const val ROOT_RELS = XML_HEADER +
        """<Relationships xmlns="$NS_PKG_REL">""" +
        """<Relationship Id="rId1" Type="$NS_DOC_REL/officeDocument" Target="xl/workbook.xml"/>""" +
        "</Relationships>"

    /** نمطان: 0 عادي، 1 عريض بخلفية رمادية فاتحة (صف العنوان). */
    private const val STYLES = XML_HEADER +
        """<styleSheet xmlns="$NS_MAIN">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFE8EEF7"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        "</styleSheet>"
}
