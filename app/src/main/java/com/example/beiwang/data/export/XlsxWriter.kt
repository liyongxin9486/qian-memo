package com.example.beiwang.data.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 极简的 .xlsx 写入工具（不依赖任何第三方库）。
 *
 * xlsx 本质是一个 ZIP 包，内部用 OOXML（一系列 XML 文件）描述工作簿。
 * 这里只实现最小可用子集：多张工作表、字符串（inlineStr）与数值单元格，
 * 不生成 sharedStrings 共享字符串表，足以被 Excel / WPS / Numbers 正确打开。
 */
object XlsxWriter {

    /** 单个单元格：要么是文本，要么是数值（Long/Double/Int）。null 当作空文本。 */
    data class Cell(val value: Any?)

    /** 生成一张工作表：name 为表名，rows 为行（每行是若干单元格）。 */
    fun write(out: OutputStream, sheets: List<Pair<String, List<List<Cell>>>>) {
        val zip = ZipOutputStream(out)
        zip.setLevel(9)

        // 1) 内容类型声明
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(buildContentTypes(sheets.size).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // 2) 包级关系
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(BuildRels.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // 3) 文档属性（可选，提升兼容性）
        zip.putNextEntry(ZipEntry("docProps/core.xml"))
        zip.write(CoreXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("docProps/app.xml"))
        zip.write(AppXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // 4) 工作簿与工作表
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(buildWorkbook(sheets.map { it.first }).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(buildWorkbookRels(sheets.size).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        sheets.forEachIndexed { index, (_, rows) ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
            zip.write(buildSheet(rows).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
    }

    private fun buildContentTypes(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (i in 1..sheetCount) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        sb.append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
        sb.append("</Types>")
        return sb.toString()
    }

    private val BuildRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private val CoreXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:title>七安备忘 导出</dc:title>
<dc:creator>七安备忘</dc:creator>
</cp:coreProperties>"""

    private val AppXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
<Application>七安备忘</Application>
</Properties>"""

    private fun buildWorkbook(sheetNames: List<String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        sb.append("<sheets>")
        sheetNames.forEachIndexed { index, name ->
            val safe = escapeXml(name).take(31) // 工作表名最长 31 字符
            sb.append("<sheet name=\"$safe\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        sb.append("</sheets>")
        sb.append("</workbook>")
        return sb.toString()
    }

    private fun buildWorkbookRels(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..sheetCount) {
            sb.append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildSheet(rows: List<List<Cell>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 1
            sb.append("<row r=\"$r\">")
            row.forEachIndexed { colIndex, cell ->
                val ref = "${colLetter(colIndex)}$r"
                val v = cell.value
                when (v) {
                    null -> { /* 空单元格，跳过 */ }
                    is Number -> sb.append("<c r=\"$ref\"><v>$v</v></c>")
                    else -> {
                        val text = stripInvalidXmlChars(v.toString())
                        sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(text)}</t></is></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    /** 列索引 -> Excel 列字母（0=A, 25=Z, 26=AA）。 */
    private fun colLetter(index: Int): String {
        var n = index + 1
        var s = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            s = ('A' + rem) + s
            n = (n - 1) / 26
        }
        return s
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /** 去除 XML 1.0 不允许的控制字符（\t \n \r 之外）。 */
    private fun stripInvalidXmlChars(s: String): String {
        val buf = StringBuilder(s.length)
        for (c in s) {
            if (c == '\t' || c == '\n' || c == '\r' || c.code >= 32) {
                buf.append(c)
            }
        }
        return buf.toString()
    }
}
