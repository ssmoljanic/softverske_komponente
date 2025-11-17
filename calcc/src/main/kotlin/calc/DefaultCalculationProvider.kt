package calc

import spec.ReportInterface

class DefaultCalculationProvider: ReportInterface.CalculationProvider {

    // Pomoćna funkcija odbacuje nevalidne vrednosti.
    private fun toNumbers(values: List<String>): List<Double> =
        values.mapNotNull { it.trim().toDoubleOrNull() }

    override fun sum(columnValues: List<String>): Double {
        val nums = toNumbers(columnValues)
        return nums.sum()
    }

    override fun average(columnValues: List<String>): Double {
        val nums = toNumbers(columnValues)
        if (nums.isEmpty()) return 0.0
        return nums.average()
    }

    override fun min(columnValues: List<String>): Double {
        val nums = toNumbers(columnValues)
        if (nums.isEmpty()) return 0.0
        return nums.minOrNull() ?: 0.0
    }

    override fun max(columnValues: List<String>): Double {
        val nums = toNumbers(columnValues)
        if (nums.isEmpty()) return 0.0
        return nums.maxOrNull() ?: 0.0
    }

    override fun count(columnValues: List<String>): Int {
        return columnValues.size
    }

    override fun countIf(columnValues: List<String>, conditionValue: String): Int {
        return columnValues.count { it == conditionValue }
    }
}