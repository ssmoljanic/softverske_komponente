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

    var desiredFormat = "txt"

    var resourceCsvPath = "data.csv"

    // opcije prikaza
    var showHeader = true
    var showRowNumbers = true
    var includeSummary = true
    var includeCalculatedColumns = true

    // opcije formatiranja
    var titleBold = true
    var titleItalic = true
    var underline = false
    var headerBold = false
    var borderWidth = 0


    val dynamicSummaryItems = mutableListOf<SummaryItem>()


    // args[0] = format
    // args[1] = csv path
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

            arg == "--no-summary"      -> includeSummary = false
            arg == "--with-summary"    -> includeSummary = true

            arg == "--no-header"       -> showHeader = false
            arg == "--with-header"     -> showHeader = true

            arg == "--no-rownums"      -> showRowNumbers = false
            arg == "--with-rownums"    -> showRowNumbers = true

            arg == "--calc" ||
                    arg == "--with-calculated" -> includeCalculatedColumns = true
            arg == "--no-calc"         -> includeCalculatedColumns = false


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

            // summary kalkulacije za proizvoljne kolone

            // --sum=Cena
            arg.startsWith("--sum=") -> {
                val col = arg.substringAfter("=")
                dynamicSummaryItems += SummaryItem(
                    label = "SUM $col",
                    calcType = SummaryCalcType.SUM,
                    columnName = col
                )
            }

            // --avg=Plata
            arg.startsWith("--avg=") -> {
                val col = arg.substringAfter("=")
                dynamicSummaryItems += SummaryItem(
                    label = "AVG $col",
                    calcType = SummaryCalcType.AVG,
                    columnName = col
                )
            }

            // --min=Cena
            arg.startsWith("--min=") -> {
                val col = arg.substringAfter("=")
                dynamicSummaryItems += SummaryItem(
                    label = "MIN $col",
                    calcType = SummaryCalcType.MIN,
                    columnName = col
                )
            }

            // --max=Cena
            arg.startsWith("--max=") -> {
                val col = arg.substringAfter("=")
                dynamicSummaryItems += SummaryItem(
                    label = "MAX $col",
                    calcType = SummaryCalcType.MAX,
                    columnName = col
                )
            }

            // --count=Artikal
            arg.startsWith("--count=") -> {
                val col = arg.substringAfter("=")
                dynamicSummaryItems += SummaryItem(
                    label = "COUNT $col",
                    calcType = SummaryCalcType.COUNT,
                    columnName = col
                )
            }

            //--countif=Cena:100
            arg.startsWith("--countif=") -> {
                val payload = arg.substringAfter("=")      // Cena:100
                val parts = payload.split(":", limit = 2)  // ["Cena", "100"]

                if (parts.size == 2) {
                    val col = parts[0]
                    val cond = parts[1]
                    dynamicSummaryItems += SummaryItem(
                        label = "COUNT_IF $col == $cond",
                        calcType = SummaryCalcType.COUNT_IF,
                        columnName = col,
                        conditionValue = cond
                    )
                } else {
                    println("pogrešan format za --countif, ocekivano: --countif=Kolona:Vrednost")
                }
            }

            else -> {
                println("nepoznata opcija '$arg'")
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

    // Ucitavanje rendera - SPI

    val loader: ServiceLoader<ReportInterface> = ServiceLoader.load(ReportInterface::class.java)
    val implementations = loader.toList()

    if (implementations.isEmpty()) {
        println("Nije pronađena nijedna implementacija ReportInterface-a)")
        return
    }

    println("Pronadjene implementacije:")
    implementations.forEach { impl ->
        println(" - ${impl.implName} (${impl::class.qualifiedName})")
    }

    val renderer: ReportInterface = implementations.firstOrNull { it.implName == desiredFormat }
        ?: run {
            println("Nije pronađena implementacija sa implName='$desiredFormat'.")
            return
        }

    println("Koristim renderer: ${renderer.implName} (${renderer.defaultFileExtension})")

    // Citanje CSV-a iz resources

    val inputStream = Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream(resourceCsvPath)
        ?: run {
            println("Ne postoji CSV resource: $resourceCsvPath")
            return
        }

    val csvContent = inputStream.bufferedReader(Charsets.UTF_8).readText()

    val data: Map<String, List<String>> = renderer.prepareDataFromCsvContent(
        csvContent = csvContent,
        hasHeader = true,
        delimiter = ';'
    )

    if (data.isEmpty()) {
        println("CSV je prazan ili nije uspešno parsiran.")
        return
    }

    // Izracunate kolone

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
                println("nema kolona 'Cena' i 'Kolicina'")
                emptyList()
            }
        } else {
            emptyList()
        }

    // Rezime

    val summaryItems: List<SummaryItem> =
        if (includeSummary) {
            if (dynamicSummaryItems.isNotEmpty()) {

                val filtered = dynamicSummaryItems.filter { item ->
                    item.columnName == null || data.containsKey(item.columnName)
                }
                if (filtered.size < dynamicSummaryItems.size) {
                    println("neke kolone zadate u --sum/--avg/... ne postoje u CSV-u")
                }
                filtered
            } else {

                val items = mutableListOf<SummaryItem>()

                if (data.containsKey("Cena")) {
                    items += SummaryItem(
                        label = "Ukupna cena",
                        calcType = SummaryCalcType.SUM,
                        columnName = "Cena"
                    )
                    items += SummaryItem(
                        label = "Prosecna cena",
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
                        label = "Broj artikala",
                        calcType = SummaryCalcType.COUNT,
                        columnName = "Artikal"
                    )
                }

                if (data.containsKey("Cena")) {
                    items += SummaryItem(
                        label = "Broj sa cenom 100",
                        calcType = SummaryCalcType.COUNT_IF,
                        columnName = "Cena",
                        conditionValue = "100"
                    )
                }

                items
            }
        } else {
            emptyList()
        }

    // Stil

    val style = SectionStyle(
        titleBold = titleBold,
        titleItalic = titleItalic,
        underline = underline,
        headerBold = headerBold,
        borderWidth = borderWidth
    )

    // Sekcija

    val section = Section(
        title = "Izvestaj",
        data = data,
        summaryItems = summaryItems,
        showRowNumbers = showRowNumbers,
        style = style,
        showHeader = showHeader,
        calculatedColumns = calculatedColumns
    )

    //Generisanje

    val reportBytes = renderer.generateReport(listOf(section))

    val outFileName = "izvestaj${renderer.defaultFileExtension}"
    val outPath = Paths.get(outFileName)
    Files.write(outPath, reportBytes)

    println("Izveštaj generisan u fajl: $outPath")
}
