package biz.dotan.flightcompass

import org.junit.Test
import java.time.LocalDate

/** בדיקה חיה מול Google Flights. רצה רק כש-LIVE=1. */
class LiveTest {
    @Test
    fun calendarTlvAth() {
        if (System.getenv("LIVE") != "1") return
        GFlights.trace = { println("TRACE $it") }
        GFlights.traceBody = true
        val s = LocalDate.now().plusDays(1)
        for (extra in listOf(true, false)) {
            GFlights.extraHeaders = extra
            try {
                val rows = GFlights.calendarChunk("TLV", "ATH", s, s.plusDays(60), 4, 1, 0)
                println("RESULT extra=$extra rows=${rows.size} first=${rows.take(3)}")
            } catch (e: Exception) {
                println("RESULT extra=$extra exception ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
