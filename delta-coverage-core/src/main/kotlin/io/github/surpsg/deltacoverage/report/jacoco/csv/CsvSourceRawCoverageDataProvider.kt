package io.github.surpsg.deltacoverage.report.jacoco.csv

import io.github.surpsg.deltacoverage.report.light.RawCoverageData
import io.github.surpsg.deltacoverage.report.light.RawCoverageDataProvider
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

internal class CsvSourceRawCoverageDataProvider(
    private val csvReportDataBytes: ByteArray
) : RawCoverageDataProvider {

    override fun obtainData(): List<RawCoverageData> {
        return csvReportDataBytes
            .let(::ByteArrayInputStream)
            .let(::InputStreamReader)
            .use { reader ->
                JacocoCsvReportParser.parseCsvReport(reader)
                    .map { it.toCoverageData() }
            }
    }
}
