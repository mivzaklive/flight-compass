package biz.dotan.flightcompass

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** בדיקה חיה על אמולטור: שליחה דרך הדפדפן המובנה (WebView) מול Google Flights. */
@RunWith(AndroidJUnit4::class)
class WebLiveTest {
    @Test
    fun browserTransport() {
        val inst = InstrumentationRegistry.getInstrumentation()
        var web: WebFlights? = null
        inst.runOnMainSync { web = WebFlights(inst.targetContext) }
        GFlights.trace = { Log.i("FCTEST", it) }
        GFlights.transport = { u, b -> web!!.send(u, b) }
        val s = LocalDate.now().plusDays(1)
        var ok = 0
        for (d in listOf("ATH", "LCA", "BKK")) {
            try {
                val rows = GFlights.calendarChunk("TLV", d, s, s.plusDays(60), 4, 1, 0)
                Log.i("FCTEST", "RESULT $d rows=${rows.size} min=${rows.minOfOrNull { it.price }} page=${web!!.lastUrl}")
                if (rows.isNotEmpty()) ok++
            } catch (e: Exception) {
                Log.i("FCTEST", "RESULT $d exception ${e.javaClass.simpleName}: ${e.message} page=${web!!.lastUrl}")
            }
        }
        val year = GFlights.calendarYear("TLV", "ATH", 4, 1, 0, { Log.i("FCTEST", it) })
        Log.i("FCTEST", "RESULT year rows=${year.size} best=${year.minByOrNull { it.price }}")
        assertTrue("browser transport failed", ok > 0)
    }
}
