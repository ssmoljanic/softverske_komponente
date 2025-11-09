package testApp

import spec.ReportInterface
import spec.ReportInterface.Section
import spec.ReportInterface.SectionStyle
import spec.ReportInterface.SummaryItem
import spec.ReportInterface.SummaryCalcType
import spec.ReportInterface.CalculatedColumn
import spec.ReportInterface.ColumnCalcType
import java.nio.file.Files
import java.nio.file.Paths
import java.util.ServiceLoader

fun main(args: Array<String>) {
    // ============= 1. KONFIGURACIJA (default vrednosti) =============

    // format: "txt", "html", "pdf", "markdown" ...
    var desiredFormat = "markdown"

    // CSV fajl u resources, npr. src/main/resources/data.csv
    var resourceCsvPath = "data.csv"

    // opcije prikaza
    var showHeader = true
    var showRowNumbers = true
    var includeSummary = true
    var includeCalculatedColumns = false

    // opcije formatiranja (stil)
    var titleBold = true
    var titleItalic = true
    var underline = false
    var headerBold = false
    var borderWidth = 0

    // ============= 2. PARSIRANJE ARGUMENATA =============

    // args[0] = format   (ako nije flag)
    // args[1] = csv path (ako nije flag)
    if (args.isNotEmpty() && !args[0].startsWith("--")) {
        desiredFormat = args[0]
    }
    if (args.size >= 2 && !args[1].startsWith("--")) {
        resourceCsvPath = args[1]
    }

    // od kog indeksa krećemo da čitamo flagove
    var startIndex = 0
    if (args.isNotEmpty() && !args[0].startsWith("--")) startIndex = 1
    if (args.size >= 2 && !args[1].startsWith("--")) startIndex = 2

    var i = startIndex
    while (i < args.size) {
        val arg = args[i]

        when {
            // ----- postojeći funkcionalni flagovi -----
            arg == "--no-summary"      -> includeSummary = false
            arg == "--with-summary"    -> includeSummary = true

            arg == "--no-header"       -> showHeader = false
            arg == "--with-header"     -> showHeader = true

            arg == "--no-rownums"      -> showRowNumbers = false
            arg == "--with-rownums"    -> showRowNumbers = true

            arg == "--calc" ||
                    arg == "--with-calculated" -> includeCalculatedColumns = true
            arg == "--no-calc"         -> includeCalculatedColumns = false

            // ----- novi flagovi za formatiranje naslova/tabele -----
            arg == "--bold"            -> titleBold = true
            arg == "--no-bold"         -> titleBold = false

            arg == "--italic"          -> titleItalic = true
            arg == "--no-italic"       -> titleItalic = false

            arg == "--underline"       -> underline = true
            arg == "--no-underline"    -> underline = false

            arg == "--header-bold"     -> headerBold = true

            arg.startsWith("--border=") -> {
                val value = arg.substringAfter("=")
                borderWidth = value.toIntOrNull() ?: 0
            }

            else -> {
                println("Upozorenje: nepoznata opcija '$arg' – preskačem.")
            }
        }

        i++
    }

    println("Konfiguracija:")
    println(" - format: $desiredFormat")
    println(" - csv: $resourceCsvPath")
    println(" - showHeader: $showHeader")
    println(" - showRowNumbers: $showRowNumbers")
    println(" - includeSummary: $includeSummary")
    println(" - includeCalculatedColumns: $includeCalculatedColumns")
    println(" - style: bold=$titleBold, italic=$titleItalic, underline=$underline, headerBold=$headerBold, borderWidth=$borderWidth")

    // ============= 3. UČITAVANJE RENDERA (SPI) =============

    val loader: ServiceLoader<ReportInterface> = ServiceLoader.load(ReportInterface::class.java)
    val implementations = loader.toList()

    if (implementations.isEmpty()) {
        println("Nije pronađena nijedna implementacija ReportInterface-a. Proveri SPI (META-INF/services).")
        return
    }

    println("Pronađene implementacije:")
    implementations.forEach { impl ->
        println(" - ${impl.implName} (${impl::class.qualifiedName})")
    }

    val renderer: ReportInterface = implementations.firstOrNull { it.implName == desiredFormat }
        ?: run {
            println("Nije pronađena implementacija sa implName='$desiredFormat'.")
            return
        }

    println("Koristim renderer: ${renderer.implName} (${renderer.defaultFileExtension})")

    // ============= 4. ČITANJE CSV-a IZ RESOURCES =============

    val inputStream = Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream(resourceCsvPath)
        ?: run {
            println("Nisam našao CSV resource: $resourceCsvPath")
            return
        }

    val csvContent = inputStream.bufferedReader(Charsets.UTF_8).readText()

    val data: Map<String, List<String>> = renderer.prepareDataFromCsvContent(
        csvContent = csvContent,
        hasHeader = true,
        delimiter = ';'          // po potrebi promeni delimiter
    )

    if (data.isEmpty()) {
        println("CSV je prazan ili nije uspešno parsiran.")
        return
    }

    println("Kolone u CSV fajlu:")
    data.keys.forEach { col -> println(" - $col") }

    // ============= 5. IZRAČUNATE KOLONE =============

    val calculatedColumns: List<CalculatedColumn> =
        if (includeCalculatedColumns) {
            if (data.containsKey("Cena") && data.containsKey("Kolicina")) {
                listOf(
                    CalculatedColumn(
                        columnName = "Ukupno",
                        operation = ColumnCalcType.MULTIPLY,
                        sourceColumns = listOf("Cena", "Kolicina")
                    )
                )
            } else {
                println("Upozorenje: nema kolona 'Cena' i 'Kolicina', preskačem calculated kolone.")
                emptyList()
            }
        } else {
            emptyList()
        }

    // ============= 6. SUMMARY =============

    val summaryItems: List<SummaryItem> =
        if (includeSummary) {
            val items = mutableListOf<SummaryItem>()

            if (data.containsKey("Cena")) {
                items += SummaryItem(
                    label = "Ukupna cena (SUM Cena)",
                    calcType = SummaryCalcType.SUM,
                    columnName = "Cena"
                )
                items += SummaryItem(
                    label = "Prosecna cena (AVG Cena)",
                    calcType = SummaryCalcType.AVG,
                    columnName = "Cena"
                )
            }

            if (data.containsKey("Ukupno")) {
                items += SummaryItem(
                    label = "Ukupno (SUM Ukupno)",
                    calcType = SummaryCalcType.SUM,
                    columnName = "Ukupno"
                )
            }

            if (data.containsKey("Artikal")) {
                items += SummaryItem(
                    label = "Broj artikala (COUNT Artikal)",
                    calcType = SummaryCalcType.COUNT,
                    columnName = "Artikal"
                )
            }

            if (data.containsKey("Cena")) {
                items += SummaryItem(
                    label = "Broj sa cenom 100 (COUNT_IF Cena==100)",
                    calcType = SummaryCalcType.COUNT_IF,
                    columnName = "Cena",
                    conditionValue = "100"
                )
            }

            items
        } else {
            emptyList()
        }

    // ============= 7. STIL =============

    val style = SectionStyle(
        titleBold = titleBold,
        titleItalic = titleItalic,
        underline = underline,
        headerBold = headerBold,
        borderWidth = borderWidth
    )

    // ============= 8. SEKCIJA =============

    val section = Section(
        title = "Izvestaj",
        data = data,
        summaryItems = summaryItems,
        showRowNumbers = showRowNumbers,
        style = style,
        showHeader = showHeader,
        calculatedColumns = calculatedColumns
    )

    // ============= 9. GENERISANJE =============

    val reportBytes = renderer.generateReport(listOf(section))

    val outFileName = "izvestaj${renderer.defaultFileExtension}"
    val outPath = Paths.get(outFileName)
    Files.write(outPath, reportBytes)

    println("Izveštaj generisan u fajl: $outPath")
}
