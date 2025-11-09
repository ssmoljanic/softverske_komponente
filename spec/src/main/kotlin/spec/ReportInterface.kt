package spec

import java.sql.ResultSet
import java.sql.ResultSetMetaData

/**
 * Osnovni interfejs za generisanje različitih formata izveštaja
 * (TXT, HTML, PDF, Markdown...).
 *
 * Ideja je da ovaj interfejs bude API (specifikacija) koji implementiraju
 * različiti renderer-i (TXT, HTML/PDF, Markdown).
 */
interface ReportInterface {

    /// Kratko ime implementacije (npr. "txt", "html-pdf", "markdown").
    val implName: String

    /// Tip izlaznog fajla — npr. "text/plain", "text/html", "application/pdf", "text/markdown".
    val contentType: String

    /// Podrazumevana ekstenzija fajla za taj tip izveštaja (".txt", ".html", ".pdf", ".md"...).
    val defaultFileExtension: String

    /// Da li renderer podržava formatiranje (bold, italic, tabele, itd.).
    val supportsFormatting: Boolean

    /**
     * Komponenta zadužena za računanje agregatnih vrednosti (SUM, AVG, MIN, MAX, COUNT, COUNT_IF).
     * Dolazi iz posebne kalkulacione komponente (odvojen modul).
     */
    val calculationProvider: CalculationProvider

    /**
     * Glavna metoda — generiše izveštaj na osnovu jedne ili više sekcija.
     *
     * @param sections lista sekcija koje će biti prikazane u izveštaju.
     * Svaka sekcija sadrži naslov, tabelarne podatke, summary, stil, itd.
     *
     * @return sadržaj kompletnog izveštaja kao `ByteArray`, koji se može
     * direktno upisati u fajl (npr. preko `Files.write(...)`) ili poslati dalje.
     */
    fun generateReport(sections: List<Section>): ByteArray

    /**
     * Prečica — pravi jednu [Section] na osnovu prosleđenih parametara i
     * delegira poziv na [generateReport].
     *
     * @param data tabelarni podaci za izveštaj:
     *             ključ je ime kolone, a vrednost je lista vrednosti u toj koloni.
     *             Sve liste moraju imati istu dužinu (broj vrsta).
     * @param title naslov sekcije (može biti `null` ako se naslov ne koristi).
     * @param summaryItems lista stavki koje definišu šta treba prikazati u
     *                     summary delu (računanja ili ručne vrednosti).
     * @param showRowNumbers da li da se generišu redni brojevi za vrste
     *                       (1, 2, 3, ...), i dodaju kao prva kolona.
     * @param style stil sekcije (bold naslov, bold zaglavlje, debljina ivica tabele itd).
     * @param showHeader da li treba prikazati zaglavlje tabele (nazive kolona).
     * @param calculatedColumns lista izračunatih kolona koje treba dodati
     *                          na osnovu postojećih kolona i definisanih operacija.
     *
     * @return sadržaj izveštaja za jednu sekciju kao `ByteArray`.
     */
    fun generateReport(
        data: Map<String, List<String>>,
        title: String? = null,
        summaryItems: List<SummaryItem> = emptyList(),
        showRowNumbers: Boolean = false,
        style: SectionStyle = SectionStyle(),
        showHeader: Boolean = true,
        calculatedColumns: List<CalculatedColumn> = emptyList()
    ): ByteArray {
        val section = Section(
            title = title,
            data = data,
            summaryItems = summaryItems,
            showRowNumbers = showRowNumbers,
            style = style,
            showHeader = showHeader,
            calculatedColumns = calculatedColumns
        )
        return generateReport(listOf(section))
    }

    /**
     * Iscrtava naslov sekcije.
     *
     * Implementacija odlučuje kako će se naslov prikazati
     * (plain tekst, HTML heading, Markdown heading, itd.)
     *
     * @param sb StringBuilder u koji se generiše izlazni tekst.
     * @param title naslov koji treba prikazati (može biti `null` ako nema naslova).
     * @param style stil sekcije — može da utiče na prikaz naslova
     *              (bold, italic, underline, itd.).
     */
    fun renderTitle(sb: StringBuilder, title: String?, style: SectionStyle)

    /**
     * Iscrtava tabelu sa podacima.
     *
     * @param sb StringBuilder u koji se generiše izlazni tekst.
     * @param data tabelarni podaci: mapa "ime kolone" -> "lista vrednosti po vrstama".
     *             Sve liste moraju imati isti broj elemenata (broj vrsta).
     * @param showRowNumbers ako je `true`, dodaje se kolona sa rednim brojevima (1, 2, 3, ...).
     * @param style stil sekcije, može uticati na način prikaza tabele
     *              (npr. debljina ivica, bold zaglavlje).
     * @param showHeader da li treba prikazati imena kolona kao zaglavlje tabele.
     */
    fun renderTable(
        sb: StringBuilder,
        data: Map<String, List<String>>,
        showRowNumbers: Boolean,
        style: SectionStyle,
        showHeader: Boolean
    )

    /**
     * Iscrtava rezime (summary) za jednu sekciju izveštaja.
     *
     * Implementacija je zadužena da:
     *  - interpretira [SummaryItem] (SUM, AVG, COUNT, MANUAL, ...),
     *  - koristi [calculationProvider] za računanje agregatnih vrednosti,
     *  - formatira i upiše rezultat u [sb] u zavisnosti od formata (TXT/HTML/Markdown...).
     *
     * @param sb StringBuilder u koji se generiše izlazni tekst rezimea.
     * @param summaryItems lista stavki koje opisuju šta treba prikazati u rezime delu.
     * @param data tabela podataka nad kojom se vrše kalkulacije (ako je potrebno),
     *             mapirana po kolonama.
     */
    fun renderSummary(
        sb: StringBuilder,
        summaryItems: List<SummaryItem>,
        data: Map<String, List<String>>
    )

    /**
     * Primeni sve izračunate kolone ([CalculatedColumn]) na postojeće podatke.
     *
     * Ova metoda:
     *  - za svaku [CalculatedColumn] izračuna novu kolonu na osnovu [data] i [operation],
     *  - vraća NOVU mapu sa svim originalnim kolonama + novim izračunatim kolonama.
     *
     * Pretpostavlja se da:
     *  - sve kolone u [data] imaju isti broj redova,
     *  - ime kolone iz [sourceColumns] postoji u [data].
     *
     * Napomena:
     *  - vrednosti se tumače kao brojevi (uz pokušaj konverzije uz zamenu ',' sa '.'),
     *  - nevažeće vrednosti se tretiraju kao 0.0,
     *  - kod DIVIDE ako je delilac 0 ili nevažeći, rezultat je 0.0.
     */
    fun applyCalculatedColumns(
        data: Map<String, List<String>>,
        calculatedColumns: List<CalculatedColumn>
    ): Map<String, List<String>> {
        if (calculatedColumns.isEmpty() || data.isEmpty()) return data

        // čuvamo redosled kolona
        val mutable = LinkedHashMap<String, MutableList<String>>()
        data.forEach { (col, values) ->
            mutable[col] = values.toMutableList()
        }

        val rowCount = data.values.first().size

        fun parseNumber(value: String?): Double? =
            value
                ?.replace(',', '.')
                ?.toDoubleOrNull()

        for (calc in calculatedColumns) {
            if (calc.sourceColumns.isEmpty()) continue

            val newColumnValues = MutableList(rowCount) { "0" }

            when (calc.operation) {
                ColumnCalcType.SUM -> {
                    for (row in 0 until rowCount) {
                        var sum = 0.0
                        for (src in calc.sourceColumns) {
                            val v = mutable[src]?.getOrNull(row)
                            val d = parseNumber(v) ?: 0.0
                            sum += d
                        }
                        newColumnValues[row] = sum.toString()
                    }
                }

                ColumnCalcType.MULTIPLY -> {
                    for (row in 0 until rowCount) {
                        var product: Double? = null
                        for (src in calc.sourceColumns) {
                            val v = mutable[src]?.getOrNull(row)
                            val d = parseNumber(v)
                            if (d == null) continue
                            product = if (product == null) d else product!! * d
                        }
                        newColumnValues[row] = (product ?: 0.0).toString()
                    }
                }

                ColumnCalcType.DIFF -> {
                    if (calc.sourceColumns.size < 2) continue
                    val aCol = calc.sourceColumns[0]
                    val bCol = calc.sourceColumns[1]
                    for (row in 0 until rowCount) {
                        val a = parseNumber(mutable[aCol]?.getOrNull(row)) ?: 0.0
                        val b = parseNumber(mutable[bCol]?.getOrNull(row)) ?: 0.0
                        newColumnValues[row] = (a - b).toString()
                    }
                }

                ColumnCalcType.DIVIDE -> {
                    if (calc.sourceColumns.size < 2) continue
                    val aCol = calc.sourceColumns[0]
                    val bCol = calc.sourceColumns[1]
                    for (row in 0 until rowCount) {
                        val a = parseNumber(mutable[aCol]?.getOrNull(row)) ?: 0.0
                        val b = parseNumber(mutable[bCol]?.getOrNull(row))
                        val result = if (b == null || b == 0.0) null else a / b
                        newColumnValues[row] = (result ?: 0.0).toString()
                    }
                }
            }

            mutable[calc.columnName] = newColumnValues
        }

        return mutable.mapValues { it.value.toList() }
    }

    /**
     * Pretvara [ResultSet] (rezultat izvršavanja SQL upita) u mapu:
     * ime kolone -> lista vrednosti.
     *
     * Ova metoda omogućava da se podaci iz baze podataka lako prebace
     * u glavni format koji koristi izveštaj ([Map]<String, List<String>>).
     *
     * @param rs [ResultSet] dobijen izvršavanjem SQL upita nad bazom.
     *
     * @return mapa gde je ključ ime kolone iz ResultSet-a, a vrednost lista
     *         stringova koja predstavlja vrednosti te kolone po svim vrstama.
     */
    fun prepareData(rs: ResultSet): Map<String, List<String>> {
        val reportData = mutableMapOf<String, MutableList<String>>()  // mapa: ime kolone → lista vrednosti

        // 1. Uzmemo meta-podatke o kolonama (npr. koliko ih ima, kako se zovu)
        val metaData: ResultSetMetaData = rs.metaData
        val columnCount = metaData.columnCount

        // 2. Dodamo prazne liste za svaku kolonu
        for (i in 1..columnCount) {
            val columnName = metaData.getColumnName(i)
            reportData[columnName] = mutableListOf()
        }

        // 3. Prođemo kroz svaki red u ResultSet-u i upišemo vrednosti u odgovarajuće kolone
        while (rs.next()) {
            for (i in 1..columnCount) {
                val columnName = metaData.getColumnName(i)
                reportData[columnName]!!.add(rs.getString(i))  // dodajemo vrednost u listu
            }
        }

        return reportData
    }

    /**
     * Pretvara sadržaj CSV fajla u mapu: ime kolone -> lista vrednosti.
     *
     * @param csvContent ceo sadržaj CSV fajla kao jedan string
     *                   (npr. pročitan iz fajla `Files.readString(...)`).
     * @param hasHeader  da li prva linija sadrži nazive kolona.
     *                   Ako je `true`, prva linija se koristi kao header.
     *                   Ako je `false`, imena kolona se automatski
     *                   generišu kao "col0", "col1", ...
     * @param delimiter  znak koji razdvaja kolone u CSV fajlu
     *                   (npr. ',' za standardni CSV, ';' za neke lokalne formate).
     *
     * @return mapa gde je ključ ime kolone, a vrednost lista stringova
     *         sa vrednostima te kolone po vrstama.
     */
    fun prepareDataFromCsvContent(
        csvContent: String,
        hasHeader: Boolean = true,
        delimiter: Char = ','
    ): Map<String, List<String>> {
        val lines = csvContent
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return emptyMap()

        // Header (imena kolona) ili generiši col0, col1, ...
        val header: List<String>
        val dataStartIndex: Int
        if (hasHeader) {
            header = lines.first().split(delimiter).map { it.trim() }
            dataStartIndex = 1
        } else {
            val cols = lines.first().split(delimiter).size
            header = (0 until cols).map { "col$it" }
            dataStartIndex = 0
        }

        // Pripremi mapu (čuvamo redosled kolona)
        val result = linkedMapOf<String, MutableList<String>>()
        header.forEach { col -> result[col] = mutableListOf() }

        // Upis vrednosti po kolonama
        for (i in dataStartIndex until lines.size) {
            val parts = lines[i].split(delimiter).map { it.trim() }
            for (c in header.indices) {
                val value = parts.getOrNull(c) ?: ""       // ako nema vrednosti, stavi prazno
                result[header[c]]!!.add(value)
            }
        }

        // Vratimo kao Map<String, List<String>>
        return result.mapValues { it.value.toList() }
    }

    /**
     * Osnovna validacija podataka, summary-ja i izračunatih kolona pre generisanja izveštaja.
     *
     * @param data tabelarni podaci: mapa "ime kolone" -> "lista vrednosti".
     *             Svi spiskovi vrednosti moraju imati istu dužinu (broj vrsta).
     * @param summaryItems lista stavki koje treba prikazati u rezime delu.
     *                     Proverava se da li su ispravno popunjena polja
     *                     (kolona postoji, manualValue nije null kada treba itd.)
     * @param calculatedColumns lista izračunatih kolona koje treba dodati.
     *                          Proverava se da li kolone iz [sourceColumns] postoje,
     *                          kao i da li broj kolona odgovara tipu operacije.
     *
     * @return `true` ako su podaci i summary i calculated kolone logički ispravni,
     *         `false` ako postoji neka greška (koja se opisuje porukama na konzoli).
     */
    fun validate(
        data: Map<String, List<String>>,
        summaryItems: List<SummaryItem> = emptyList(),
        calculatedColumns: List<CalculatedColumn> = emptyList()
    ): Boolean {
        // 1. Proveri da li ima uopšte podataka
        if (data.isEmpty()) {
            println("Greška: Nema podataka za izveštaj.")
            return false
        }

        // 2. Proveri da li sve kolone imaju isti broj redova
        val firstColumnSize = data.values.first().size
        for ((name, column) in data) {
            if (column.size != firstColumnSize) {
                println("Greška: Kolona '$name' ima pogrešan broj redova.")
                return false
            }
        }

        // 3. Provera SummaryItem-ova
        for (item in summaryItems) {
            when (item.calcType) {
                SummaryCalcType.MANUAL -> {
                    if (item.manualValue == null) {
                        println("Greška: SummaryItem '${item.label}' je MANUAL, ali manualValue je null.")
                        return false
                    }
                }

                SummaryCalcType.COUNT_IF -> {
                    if (item.columnName == null) {
                        println("Greška: SummaryItem '${item.label}' (COUNT_IF) nema zadato ime kolone.")
                        return false
                    }
                    if (item.conditionValue == null) {
                        println("Greška: SummaryItem '${item.label}' (COUNT_IF) nema zadatu conditionValue.")
                        return false
                    }
                    if (!data.containsKey(item.columnName)) {
                        println("Greška: SummaryItem '${item.label}' koristi nepostojeću kolonu '${item.columnName}'.")
                        return false
                    }
                }

                SummaryCalcType.SUM,
                SummaryCalcType.AVG,
                SummaryCalcType.MIN,
                SummaryCalcType.MAX,
                SummaryCalcType.COUNT -> {
                    if (item.columnName == null) {
                        println("Greška: SummaryItem '${item.label}' (${item.calcType}) nema zadato ime kolone.")
                        return false
                    }
                    if (!data.containsKey(item.columnName)) {
                        println("Greška: SummaryItem '${item.label}' koristi nepostojeću kolonu '${item.columnName}'.")
                        return false
                    }
                }
            }
        }

        // 4. Provera CalculatedColumn-ova
        for (calc in calculatedColumns) {
            if (calc.sourceColumns.isEmpty()) {
                println("Greška: CalculatedColumn '${calc.columnName}' nema nijednu izvor kolonu.")
                return false
            }

            when (calc.operation) {
                ColumnCalcType.SUM,
                ColumnCalcType.MULTIPLY -> {
                    if (calc.sourceColumns.size < 2) {
                        println("Greška: CalculatedColumn '${calc.columnName}' (${calc.operation}) mora imati bar dve izvor kolone.")
                        return false
                    }
                }

                ColumnCalcType.DIFF,
                ColumnCalcType.DIVIDE -> {
                    if (calc.sourceColumns.size != 2) {
                        println("Greška: CalculatedColumn '${calc.columnName}' (${calc.operation}) mora imati tačno dve izvor kolone.")
                        return false
                    }
                }
            }

            for (src in calc.sourceColumns) {
                if (!data.containsKey(src)) {
                    println("Greška: CalculatedColumn '${calc.columnName}' koristi nepostojeću kolonu '$src'.")
                    return false
                }
            }
        }

        return true
    }

    // ===== Kalkulaciona komponenta =====

    /**
     * Interfejs za kalkulacionu komponentu koja računa agregatne vrednosti nad kolonom.
     *
     * Implementacija ovog interfejsa se nalazi u posebnom modulu i koristi se kao dependensi.
     */
    interface CalculationProvider {

        /**
         * Računa zbir svih vrednosti u koloni.
         *
         * @param columnValues lista vrednosti kolone u vidu stringova.
         *                     Implementacija treba da pretvori u broj (npr. Double)
         *                     i ignoriše/obradi nevažeće vrednosti po potrebi.
         *
         * @return zbir svih važećih vrednosti u koloni kao [Double].
         */
        fun sum(columnValues: List<String>): Double

        /**
         * Računa prosečnu vrednost u koloni.
         *
         * @param columnValues lista vrednosti kolone u vidu stringova.
         *
         * @return aritmetička sredina svih važećih vrednosti u koloni kao [Double].
         */
        fun average(columnValues: List<String>): Double

        /**
         * Računa minimalnu vrednost u koloni.
         *
         * @param columnValues lista vrednosti kolone u vidu stringova.
         *
         * @return najmanja važeća vrednost u koloni kao [Double].
         */
        fun min(columnValues: List<String>): Double

        /**
         * Računa maksimalnu vrednost u koloni.
         *
         * @param columnValues lista vrednosti kolone u vidu stringova.
         *
         * @return najveća važeća vrednost u koloni kao [Double].
         */
        fun max(columnValues: List<String>): Double

        /**
         * Prebrojava koliko ima vrednosti u koloni.
         *
         * @param columnValues lista vrednosti kolone u vidu stringova.
         *
         * @return broj elemenata u listi (ili broj važećih vrednosti, u zavisnosti od implementacije).
         */
        fun count(columnValues: List<String>): Int

        /**
         * Prebrojava koliko vrednosti u koloni ispunjava zadati uslov.
         * Tipičan slučaj: koliko vrednosti je jednako [conditionValue].
         *
         * @param columnValues lista vrednosti kolone u vidu stringova.
         * @param conditionValue vrednost koja treba da bude ispunjena
         *                       (npr. "Plaćeno", "DA", "true", "100").
         *
         * @return broj vrednosti koje zadovoljavaju uslov.
         */
        fun countIf(columnValues: List<String>, conditionValue: String): Int
    }

    // ===== Model za izračunate kolone =====

    /**
     * Tip aritmetičke operacije za kolonu koja se računa na osnovu drugih kolona.
     */
    enum class ColumnCalcType {
        /** Zbir više kolona. */
        SUM,

        /** Razlika tačno dve kolone: col1 - col2. */
        DIFF,

        /** Proizvod više kolona. */
        MULTIPLY,

        /** Deljenje tačno dve kolone: col1 / col2. */
        DIVIDE
    }

    /**
     * Opis jedne izračunate kolone u tabeli.
     *
     * @property columnName ime nove (izračunate) kolone koja će se pojaviti u tabeli.
     * @property operation tip aritmetičke operacije nad izvorima (SUM/DIFF/MULTIPLY/DIVIDE).
     * @property sourceColumns imena postojećih kolona nad kojima se operacija primenjuje.
     */
    data class CalculatedColumn(
        val columnName: String,
        val operation: ColumnCalcType,
        val sourceColumns: List<String>
    )

    // ===== Model za summary =====

    /**
     * Tip kalkulacije koja se koristi u rezime delu (summary).
     */
    enum class SummaryCalcType {
        /** Zbir kolone. */
        SUM,

        /** Prosečna vrednost kolone. */
        AVG,

        /** Minimalna vrednost u koloni. */
        MIN,

        /** Maksimalna vrednost u koloni. */
        MAX,

        /** Prebrojavanje svih vrednosti u koloni. */
        COUNT,

        /** Prebrojavanje vrednosti koje zadovoljavaju uslov (npr. == neka vrednost). */
        COUNT_IF,

        /** Ručno zadat tekst, bez ikakve kalkulacije. */
        MANUAL
    }

    /**
     * Jedna stavka u rezime delu izveštaja.
     *
     * @property label tekst koji se prikazuje kao naziv stavke (npr. "Ukupno").
     * @property calcType tip kalkulacije ili podatka (SUM, AVG, MANUAL...).
     * @property columnName ime kolone nad kojom se radi kalkulacija
     *                      (za SUM/AVG/MIN/MAX/COUNT/COUNT_IF).
     * @property conditionValue vrednost uslova za COUNT_IF (npr. brojimo samo gde je kolona == "DA").
     * @property manualValue ručno zadat tekst, koristi se ako je [calcType] = MANUAL.
     */
    data class SummaryItem(
        val label: String,
        val calcType: SummaryCalcType,
        val columnName: String? = null,
        val conditionValue: String? = null,
        val manualValue: String? = null
    )

    // ===== Stil i sekcija =====

    /**
     * Klasa koja opisuje stilove koji se mogu primeniti na delove izveštaja.
     * Koristi se uglavnom kod formatiranih renderer-a (HTML, PDF, Markdown).
     *
     * @property titleBold da li je naslov podebljan.
     * @property titleItalic da li je naslov kurziv.
     * @property underline da li je naslov (ili drugi elementi) podvučen.
     * @property headerBold da li je zaglavlje tabele (kolone) podebljano.
     * @property borderWidth debljina ivica tabele (0 znači bez ivica).
     */
    data class SectionStyle(
        val titleBold: Boolean = false,
        val titleItalic: Boolean = false,
        val underline: Boolean = false,
        val headerBold: Boolean = false,
        val borderWidth: Int = 1
    )

    /**
     * Jedna sekcija izveštaja: naslov + tabela + summary + opcije prikaza.
     *
     * @property title naslov sekcije (može biti `null`).
     * @property data glavni tabelarni podaci: ime kolone -> lista vrednosti.
     * @property summaryItems lista stavki koje će biti prikazane u rezime delu.
     * @property showRowNumbers da li treba dodati kolonu sa rednim brojevima.
     * @property style stil sekcije (bold naslov, bold header, ivice tabele...).
     * @property showHeader da li prikazati zaglavlje tabele (imena kolona).
     * @property calculatedColumns definicije izračunatih kolona koje treba dodati u tabelu.
     */
    data class Section(
        val title: String? = null,
        val data: Map<String, List<String>>,
        val summaryItems: List<SummaryItem> = emptyList(),
        val showRowNumbers: Boolean = false,
        val style: SectionStyle = SectionStyle(),
        val showHeader: Boolean = true,
        val calculatedColumns: List<CalculatedColumn> = emptyList()
    )
}
