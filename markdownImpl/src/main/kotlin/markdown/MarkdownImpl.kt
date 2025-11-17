package markdown

import calc.DefaultCalculationProvider
import spec.ReportInterface
import spec.ReportInterface.Section
import spec.ReportInterface.SectionStyle
import spec.ReportInterface.SummaryItem
import spec.ReportInterface.SummaryCalcType

/**
 * Markdown implementacija ReportInterface-a.
 *
 * - Podržava formatiranje naslova (bold/italic/underline).
 * - Tabela se iscrtava kao Markdown tabela sa header redom i separatorom.
 * - Summary se prikazuje kao bullet lista sa podebljanim label-ima.
 */
class MarkdownImpl : ReportInterface {

    override val implName: String = "markdown"
    override val defaultFileExtension: String = ".md"

    // Markdown podržava formatiranje teksta.
    override val supportsFormatting: Boolean = true

    override val calculationProvider: ReportInterface.CalculationProvider =
        DefaultCalculationProvider()

    override fun generateReport(sections: List<Section>): ByteArray {
        val sb = StringBuilder()

        sections.forEachIndexed { index, section ->
            if (index > 0) {
                // razdvoji sekcije horizontalnom linijom
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }

            // primeni izračunate kolone
            val dataWithCalculated = applyCalculatedColumns(
                data = section.data,
                calculatedColumns = section.calculatedColumns
            )

            // naslov
            renderTitle(sb, section.title, section.style)
            if (!section.title.isNullOrBlank()) {
                sb.appendLine()
            }

            // tabela
            renderTable(
                sb = sb,
                data = dataWithCalculated,
                showRowNumbers = section.showRowNumbers,
                style = section.style,
                showHeader = section.showHeader
            )

            // summary
            if (section.summaryItems.isNotEmpty()) {
                sb.appendLine()
                renderSummary(
                    sb = sb,
                    summaryItems = section.summaryItems,
                    data = dataWithCalculated
                )
            }
        }

        return sb.toString().toByteArray(Charsets.UTF_8)
    }



    override fun renderTitle(sb: StringBuilder, title: String?, style: SectionStyle) {
        if (title.isNullOrBlank()) return

        var text: String = title

        // bold/italic kao Markdown, underline kao HTML <u>
        text = when {
            style.titleBold && style.titleItalic -> "***$text***"   // bold+italic
            style.titleBold -> "**$text**"
            style.titleItalic -> "_${text}_"
            else -> text
        }

        if (style.underline) {
            text = "<u>$text</u>"
        }


        sb.append("## ")
        sb.appendLine(text)
    }

    // Markdown tabela

    override fun renderTable(
        sb: StringBuilder,
        data: Map<String, List<String>>,
        showRowNumbers: Boolean,
        style: SectionStyle,
        showHeader: Boolean
    ) {
        if (data.isEmpty()) {
            sb.appendLine("_Nema podataka_")
            return
        }

        val columnNames = data.keys.toList()
        val rowCount = data.values.first().size


        fun escapeCell(text: String): String =
            text.replace("|", "\\|").replace("\n", " ")

        // HEADER
        val headerCells = mutableListOf<String>()
        if (showRowNumbers) {
            headerCells.add("#")
        }
        headerCells.addAll(columnNames)

        if (showHeader) {
            val renderedHeaderCells = headerCells.map { header ->
                val base = escapeCell(header)
                if (style.headerBold) "**$base**" else base
            }

            sb.append("| ")
            sb.append(renderedHeaderCells.joinToString(" | "))
            sb.appendLine(" |")

            // separator red: za svaku kolonu "---"
            val separator = headerCells.map { "---" }
            sb.append("| ")
            sb.append(separator.joinToString(" | "))
            sb.appendLine(" |")
        } else {
            val emptyHeader = headerCells.map { "" }
            sb.append("| ")
            sb.append(emptyHeader.joinToString(" | "))
            sb.appendLine(" |")

            val separator = headerCells.map { "---" }
            sb.append("| ")
            sb.append(separator.joinToString(" | "))
            sb.appendLine(" |")
        }

        // PODACI
        for (rowIndex in 0 until rowCount) {
            val rowCells = mutableListOf<String>()

            if (showRowNumbers) {
                rowCells.add((rowIndex + 1).toString())
            }

            for (colName in columnNames) {
                val value = data[colName]!![rowIndex]
                rowCells.add(escapeCell(value))
            }

            sb.append("| ")
            sb.append(rowCells.joinToString(" | "))
            sb.appendLine(" |")
        }
    }

    // SUMMARY

    override fun renderSummary(
        sb: StringBuilder,
        summaryItems: List<SummaryItem>,
        data: Map<String, List<String>>
    ) {
        if (summaryItems.isEmpty()) return

        for (item in summaryItems) {
            val valueText: String = when (item.calcType) {
                SummaryCalcType.MANUAL -> item.manualValue ?: ""

                SummaryCalcType.SUM -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.sum(values).toString()
                    }
                }

                SummaryCalcType.AVG -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.average(values).toString()
                    }
                }

                SummaryCalcType.MIN -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.min(values).toString()
                    }
                }

                SummaryCalcType.MAX -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.max(values).toString()
                    }
                }

                SummaryCalcType.COUNT -> {
                    val col = item.columnName
                    if (col == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.count(values).toString()
                    }
                }

                SummaryCalcType.COUNT_IF -> {
                    val col = item.columnName
                    val cond = item.conditionValue
                    if (col == null || cond == null) "" else {
                        val values = data[col] ?: emptyList()
                        calculationProvider.countIf(values, cond).toString()
                    }
                }
            }

            sb.append("- **")
            sb.append(item.label.replace("\n", " "))
            sb.append(":** ")
            sb.appendLine(valueText.replace("\n", " "))
        }
    }
}
