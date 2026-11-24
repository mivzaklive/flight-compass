package biz.dotan.flightcompass

import org.junit.Test
import java.time.LocalDate

/** בדיקה חיה מול Google Flights. רצה רק כש-LIVE=1. */
class LiveTest {
    @Test
    fun calendarTlvAth() {
        if (System.getenv("LIVE") != "1") return
        GFlights.trace = { println("TRACE $it") }
        val s = LocalDate.now().plusDays(1)
        var ok = 0
        val dests = listOf("ATH", "FCO", "LCA", "BUD", "PRG", "BCN", "BKK", "LHR")
        for (d in dests) {
            try {
                val rows = GFlights.calendarChunk("TLV", d, s, s.plusDays(60), 4, 1, 0)
                println("RESULT $d rows=${rows.size} min=${rows.minOfOrNull { it.price }}")
                if (rows.isNotEmpty()) ok++
            } catch (e: Exception) {
                println("RESULT $d exception ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        println("RESULT summary ok=$ok/${dests.size}")
        try {
            val year = GFlights.calendarYear("TLV", "ATH", 4, 1, 0) { println("TRACE $it") }
            val b = year.minByOrNull { it.price }
            println("RESULT year ATH rows=${year.size} best=$b last=${year.maxOfOrNull { it.out }}")
        } catch (e: Exception) {
            println("RESULT year exception ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
