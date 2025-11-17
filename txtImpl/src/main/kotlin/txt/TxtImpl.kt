package txt

import calc.DefaultCalculationProvider
import spec.ReportInterface

class TxtImpl : ReportInterface {

    override val implName: String = "txt"
    override val defaultFileExtension: String = ".txt"
    override val supportsFormatting: Boolean = false

    override val calculationProvider: ReportInterface.CalculationProvider =
        DefaultCalculationProvider()

    override fun generateReport(sections: List<ReportInterface.Section>): ByteArray {
        val sb = StringBuilder()

        sections.forEachIndexed { index, section ->
            // razmak između sekcija
            if (index > 0) {
                sb.appendLine()
                sb.appendLine()
            }

            // primeni calculated kolone
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

            // summary (ako postoji)
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


    override fun renderTitle(
        sb: StringBuilder,
        title: String?,
        style: ReportInterface.SectionStyle
    ) {
        if (title.isNullOrBlank()) return
        sb.append(title)
    }


    override fun renderTable(
        sb: StringBuilder,
        data: Map<String, List<String>>,
        showRowNumbers: Boolean,
        style: ReportInterface.SectionStyle,
        showHeader: Boolean
    ) {
        if (data.isEmpty()) {
            sb.appendLine("[Nema podataka]")
            return
        }


        val columnNames = data.keys.toList()
        val rowCount = data.values.first().size

        // kolona za redne brojeve (ako treba)
        val rowNumberHeader = "#"
        val rowNumberWidth = if (showRowNumbers) {
            val maxRowNumLen = rowCount.toString().length
            maxOf(rowNumberHeader.length, maxRowNumLen)
        } else 0

        // sirine kolona
        val columnWidths = columnNames.map { colName ->
            val headerLen = colName.length
            val maxValueLen = data[colName]!!.maxOfOrNull { it.length } ?: 0
            maxOf(headerLen, maxValueLen)
        }

        fun String.padRight(width: Int): String =
            if (length >= width) this else this + " ".repeat(width - length)

        // dve praznine između kolona
        val colSeparator = "  "

        // Header
        if (showHeader) {
            // redni broj
            if (showRowNumbers) {
                sb.append(rowNumberHeader.padRight(rowNumberWidth))
                sb.append(colSeparator)
            }

            // nazivi kolona
            for ((index, colName) in columnNames.withIndex()) {
                val width = columnWidths[index]
                sb.append(colName.padRight(width))
                if (index != columnNames.lastIndex) {
                    sb.append(colSeparator)
                }
            }
            sb.appendLine()

            // crte ispod zaglavlja
            if (showRowNumbers) {
                val dashes = "-".repeat(rowNumberHeader.length)
                sb.append(dashes.padRight(rowNumberWidth))
                sb.append(colSeparator)
            }

            for ((index, colName) in columnNames.withIndex()) {
                val width = columnWidths[index]
                val dashCount = colName.length.coerceAtMost(width)
                val dashes = "-".repeat(dashCount)
                sb.append(dashes.padRight(width))
                if (index != columnNames.lastIndex) {
                    sb.append(colSeparator)
                }
            }
            sb.appendLine()
        }

        // Redovi
        for (rowIndex in 0 until rowCount) {
            if (showRowNumbers) {
                val rowNum = (rowIndex + 1).toString()
                sb.append(rowNum.padRight(rowNumberWidth))
                sb.append(colSeparator)
            }

            for ((colIndex, colName) in columnNames.withIndex()) {
                val value = data[colName]!![rowIndex]
                sb.append(value.padRight(columnWidths[colIndex]))
                if (colIndex != columnNames.lastIndex) {
                    sb.append(colSeparator)
                }
            }

            sb.appendLine()
        }
    }

    // Rezime

    override fun renderSummary(
        sb: StringBuilder,
        summaryItems: List<ReportInterface.SummaryItem>,
        data: Map<String, List<String>>
    ) {
        if (summaryItems.isEmpty()) return

        for (item in summaryItems) {
            val valueText: String = when (item.calcType) {
                ReportInterface.SummaryCalcType.MANUAL -> {
                    item.manualValue ?: ""
                }

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

            sb.append(item.label)
                .append(": ")
                .appendLine(valueText)
        }
    }
}
