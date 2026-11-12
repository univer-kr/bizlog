package kr.univer.bizlog.export

import kr.univer.bizlog.domain.u11Records
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private val csv = CsvExporter.export(CsvHeader(year = 2026, companyName = "유니버 주식회사"), u11Records())
    private val lines = csv.removePrefix("﻿").split("\r\n")

    @Test fun `U12 BOM과 CRLF`() {
        assertTrue(csv.startsWith("﻿"))
        assertFalse(csv.replace("\r\n", "").contains("\n"))
    }

    @Test fun `U12 본문 컬럼이 서식 순서`() {
        assertEquals(CsvExporter.BODY_COLUMNS.joinToString(","), lines.first { it.startsWith("③") })
        assertEquals(
            listOf("③사용일자(요일)", "④사용자 부서", "④사용자 성명", "⑤주행 전 계기판의 거리(km)", "⑥주행 후 계기판의 거리(km)",
                "⑦주행거리(km)", "⑧출·퇴근용(km)", "⑨일반 업무용(km)", "⑩비고"),
            CsvExporter.BODY_COLUMNS,
        )
    }

    @Test fun `U12 행 값과 누락 구간 비고, 합계`() {
        assertTrue(lines.contains("2026-11-12(목),영업 1팀,사용자 A,48213.4,48225.9,12.5,12.5,,"))
        assertTrue(lines.contains("2026-11-12(목),,미확인,48270.0,48291.8,21.8,,,누락 구간"))
        assertTrue(lines.contains("78.4,49.7,63.4%"))
    }

    @Test fun `파일명은 연도와 차량번호`() {
        assertEquals("운행기록부_2026_12가3456.csv", CsvExporter.fileName(2026, plateNo = "12가3456", vin = "KMHXX00000A123456"))
        assertEquals("운행기록부_2026_123456.csv", CsvExporter.fileName(2026, plateNo = "", vin = "KMHXX00000A123456"))
    }
}
