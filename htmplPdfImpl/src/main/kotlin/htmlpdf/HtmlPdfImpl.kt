package htmlpdf

import calc.DefaultCalculationProvider
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import spec.ReportInterface
import java.io.ByteArrayOutputStream

/**
 * Zajednička implementacija HTML/PDF renderera.
 * Nije direktno registrovana kao SPI provider, već je koriste HtmlImpl i PdfImpl.
 */
open class HtmlPdfImpl(
    private val format: OutputFormat
) : ReportInterface {

    enum class OutputFormat { HTML, PDF }

    override val implName: String =
        when (format) {
            OutputFormat.HTML -> "html"
            OutputFormat.PDF  -> "pdf"
        }

    override val contentType: String =
        when (format) {
            OutputFormat.HTML -> "text/html"
            OutputFormat.PDF  -> "application/pdf"
        }

    override val defaultFileExtension: String =
        when (format) {
            OutputFormat.HTML -> ".html"
            OutputFormat.PDF  -> ".pdf"
        }

    override val supportsFormatting: Boolean = true

    override val calculationProvider: ReportInterface.CalculationProvider =
        DefaultCalculationProvider()

    override fun generateReport(sections: List<ReportInterface.Section>): ByteArray {
        val sb = StringBuilder()

        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"en\">")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset=\"UTF-8\"/>")
        sb.appendLine("  <title>Report</title>")
        sb.appendLine("  <style>")
        sb.appendLine("    body { font-family: sans-serif; font-size: 14px; }")
        sb.appendLine("    h1, h2 { margin: 0 0 0.5em 0; }")
        sb.appendLine("    table { border-collapse: collapse; margin-bottom: 1.5em; }")
        // th, td bez fiksnog border-a – border ide preko SectionStyle.borderWidth
        sb.appendLine("    th, td { padding: 4px 8px; }")
        sb.appendLine("    ul.summary { list-style-type: disc; margin: 0 0 1.5em 1.5em; padding: 0; }")
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")

        sections.forEachIndexed { index, section ->
            // 1) primeni izračunate kolone
            val dataWithCalculated = applyCalculatedColumns(
                data = section.data,
                calculatedColumns = section.calculatedColumns
            )

            // 2) validiraj podatke + summary + calculated
            val valid = validate(
                data = dataWithCalculated,
                summaryItems = section.summaryItems,
                calculatedColumns = section.calculatedColumns
            )
            if (!valid) {
                throw IllegalArgumentException("Nevalidni podaci ili summary u sekciji '${section.title ?: "bez naslova"}'.")
            }

            // 3) render title
            renderTitle(sb, section.title, section.style)

            // 4) tabela
            renderTable(
                sb = sb,
                data = dataWithCalculated,
                showRowNumbers = section.showRowNumbers,
                style = section.style,
                showHeader = section.showHeader
            )

            // 5) summary
            renderSummary(
                sb = sb,
                summaryItems = section.summaryItems,
                data = dataWithCalculated
            )

            // razdvajanje sekcija
            if (index != sections.lastIndex) {
                sb.appendLine("<hr/>")
            }
        }

        sb.appendLine("</body>")
        sb.appendLine("</html>")

        val html = sb.toString()

        return when (format) {
            OutputFormat.HTML -> html.toByteArray(Charsets.UTF_8)
            OutputFormat.PDF  -> htmlToPdfBytes(html)
        }
    }

    // ========== TITLE ==========

    override fun renderTitle(
        sb: StringBuilder,
        title: String?,
        style: ReportInterface.SectionStyle
    ) {
        if (title.isNullOrBlank()) return

        var openTags = ""
        var closeTags = ""

        if (style.titleBold) {
            openTags += "<b>"
            closeTags = "</b>$closeTags"
        }
        if (style.titleItalic) {
            openTags += "<i>"
            closeTags = "</i>$closeTags"
        }
        if (style.underline) {
            openTags += "<u>"
            closeTags = "</u>$closeTags"
        }

        sb.append("<h2>")
        sb.append(openTags)
        sb.append(escapeHtml(title))
        sb.append(closeTags)
        sb.appendLine("</h2>")
    }

    // ========== TABLE ==========

    override fun renderTable(
        sb: StringBuilder,
        data: Map<String, List<String>>,
        showRowNumbers: Boolean,
        style: ReportInterface.SectionStyle,
        showHeader: Boolean
    ) {
        if (data.isEmpty()) {
            sb.appendLine("<p><em>Nema podataka</em></p>")
            return
        }

        val columnNames = data.keys.toList()
        val rowCount = data.values.first().size

        val borderWidth = style.borderWidth.coerceAtLeast(0)

        val tableCss = buildString {
            append("border-collapse:collapse;")
            if (borderWidth > 0) {
                append("border:${borderWidth}px solid #333;")
            }
        }

        // ivica za svaku ćeliju (th/td)
        val cellBorderCss =
            if (borderWidth > 0) "border:${borderWidth}px solid #333;" else ""

        sb.append("<table style=\"$tableCss\">").appendLine()

        // HEADER
        if (showHeader) {
            sb.append("  <tr>")
            if (showRowNumbers) {
                val thStyle = cellBorderCss
                sb.append("<th style=\"$thStyle\">#</th>")
            }
            for (colName in columnNames) {
                val thStyle = buildString {
                    append(cellBorderCss)
                    if (style.headerBold) append("font-weight:bold;")
                    if (style.underline) append("text-decoration:underline;")
                }
                sb.append("<th style=\"$thStyle\">")
                sb.append(escapeHtml(colName))
                sb.append("</th>")
            }
            sb.appendLine("</tr>")
        }

        // PODACI
        for (rowIndex in 0 until rowCount) {
            sb.append("  <tr>")
            if (showRowNumbers) {
                sb.append("<td style=\"$cellBorderCss\">")
                sb.append(rowIndex + 1)
                sb.append("</td>")
            }
            for (colName in columnNames) {
                val value = data[colName]!![rowIndex]
                sb.append("<td style=\"$cellBorderCss\">")
                sb.append(escapeHtml(value))
                sb.append("</td>")
            }
            sb.appendLine("</tr>")
        }

        sb.appendLine("</table>")
    }

    // ========== SUMMARY ==========

    override fun renderSummary(
        sb: StringBuilder,
        summaryItems: List<ReportInterface.SummaryItem>,
        data: Map<String, List<String>>
    ) {
        if (summaryItems.isEmpty()) return

        sb.appendLine("<ul class=\"summary\">")

        for (item in summaryItems) {
            val valueText: String = when (item.calcType) {
                ReportInterface.SummaryCalcType.MANUAL -> item.manualValue ?: ""

                ReportInterface.SummaryCalcType.SUM -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.sum(values).toString()
                    }
                }

                ReportInterface.SummaryCalcType.AVG -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.average(values).toString()
                    }
                }

                ReportInterface.SummaryCalcType.MIN -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.min(values).toString()
                    }
                }

                ReportInterface.SummaryCalcType.MAX -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.max(values).toString()
                    }
                }

                ReportInterface.SummaryCalcType.COUNT -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.count(values).toString()
                    }
                }

                ReportInterface.SummaryCalcType.COUNT_IF -> {
                    val col = item.columnName
                    val cond = item.conditionValue
                    if (col == null || cond == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.countIf(values, cond).toString()
                    }
                }
            }

            sb.append("  <li><b>")
            sb.append(escapeHtml(item.label))
            sb.append(":</b> ")
            sb.append(escapeHtml(valueText))
            sb.appendLine("</li>")
        }

        sb.appendLine("</ul>")
    }

    // ========== POMOĆNE ==========

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun htmlToPdfBytes(html: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val builder = PdfRendererBuilder()

        builder.withHtmlContent(html, null)
        builder.toStream(outputStream)
        builder.run()

        return outputStream.toByteArray()
    }
}
