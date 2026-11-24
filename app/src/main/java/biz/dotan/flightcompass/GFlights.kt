package biz.dotan.flightcompass

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * קריאה של לוח המחירים הציבורי של Google Flights (GetCalendarGraph), כמו בסקיל "מצפן הטיסות".
 * הבקשות יוצאות אחת אחרי השנייה עם הפסקה, לא במקביל. אם גוגל מבקש הפסקה, עוצרים.
 */
object GFlights {
    private const val BASE =
        "https://www.google.com/_/FlightsFrontendUi/data/travel.frontend.flights.FlightsFrontendService/"
    private const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    private const val MAX_CHUNK = 61L

    class Blocked(msg: String) : Exception(msg)
    class Cancelled : Exception("בוטל")

    data class Row(val out: String, val back: String, val price: Double)

    /** הפסקה בין בקשות במילישניות. בסריקת יעדים אקראיים מאטים ל-3 שניות. */
    @Volatile var pauseMs = 1500L
    @Volatile var cancelled = false
    /** יומן מפורט (לאבחון): כל בקשה, קוד התגובה ומה חזר. */
    @Volatile var trace: ((String) -> Unit)? = null
    private var reqNo = 0
    @Volatile var traceBody = false
    @Volatile var extraHeaders = true
    private var last = 0L

    // מטמון לאותו יום: אותה סריקה לא נשלחת שוב
    private val cache = HashMap<String, Pair<List<Row>, LocalDate>>()

    private fun arr(vararg xs: Any?): JSONArray {
        val a = JSONArray()
        for (x in xs) a.put(x ?: JSONObject.NULL)
        return a
    }

    private fun nulls(n: Int): Array<Any?> = arrayOfNulls(n)

    private fun sleepChecked(ms: Long) {
        var left = ms
        while (left > 0) {
            if (cancelled) throw Cancelled()
            val s = minOf(left, 250L)
            Thread.sleep(s)
            left -= s
        }
    }

    private var warmed = false

    /** כמו דפדפן: קודם פותחים את דף Google Flights כדי לקבל עוגיות, ורק אז שולחים בקשות. */
    private fun warmUp() {
        if (warmed) return
        if (java.net.CookieHandler.getDefault() == null) {
            java.net.CookieHandler.setDefault(java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
        }
        try {
            val c = URL("https://www.google.com/travel/flights?hl=en").openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("user-agent", UA)
            c.setRequestProperty("accept-language", "en-US,en;q=0.9")
            val code = c.responseCode
            c.inputStream.use { it.readBytes() }
            c.disconnect()
            trace?.invoke("חיבור ראשוני לגוגל: HTTP $code")
        } catch (e: Exception) {
            trace?.invoke("חיבור ראשוני נכשל: ${e.javaClass.simpleName}")
        }
        warmed = true
    }

    /** קוד 13 מגוגל לפעמים אקראי: מנסים שוב כמה פעמים בהפסקות קצרות לפני שמוותרים. */
    private fun post(endpoint: String, inner: JSONArray, currency: String): JSONArray? {
        var n = 0
        while (true) {
            try {
                return postOnce(endpoint, inner, currency)
            } catch (e: Blocked) {
                if (e.message?.contains("13") != true || n >= retries13) throw e
                n++
                trace?.invoke("גוגל החזיר שגיאה זמנית, מנסה שוב ($n/$retries13)…")
                sleepChecked(3000L * n)
            }
        }
    }

    /** כמה פעמים לנסות שוב על קוד 13 (בסוף הטווח מקטינים, כי שם 13 = אין עוד תאריכים). */
    @Volatile var retries13 = 3

    private fun postOnce(endpoint: String, inner: JSONArray, currency: String): JSONArray? {
        if (cancelled) throw Cancelled()
        warmUp()
        val freq = arr(null, inner.toString()).toString()
        val body = "f.req=" + URLEncoder.encode(freq, "UTF-8")
        if (traceBody) trace?.invoke("BODY $freq")
        val wait = pauseMs - (System.currentTimeMillis() - last)
        if (wait > 0) sleepChecked(wait)
        var text: String? = null
        var attempt = 0
        while (text == null) {
            reqNo++
            trace?.invoke("בקשה $reqNo ל-Google Flights…")
            try {
                text = send("$BASE$endpoint?curr=$currency&hl=en", body)
            } catch (e: Blocked) {
                throw e
            } catch (e: java.io.IOException) {
                trace?.invoke("בקשה $reqNo נכשלה: ${e.javaClass.simpleName} ${e.message ?: ""}")
                if (attempt >= 2) throw e
                attempt++
                sleepChecked(2000L * attempt)
            }
        }
        last = System.currentTimeMillis()
        return parse(text!!)
    }

    private fun send(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/x-www-form-urlencoded;charset=UTF-8")
            c.setRequestProperty("user-agent", UA)
            if (extraHeaders) {
            c.setRequestProperty("accept-language", "en-US,en;q=0.9")
            c.setRequestProperty("origin", "https://www.google.com")
            c.setRequestProperty("referer", "https://www.google.com/travel/flights")
            // אישור עוגיות מראש, כדי שגוגל לא יפנה לדף ההסכמה
            }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val loc = c.getHeaderField("location") ?: ""
            if (code == 429 || (code in 300..399 && loc.contains("sorry"))) {
                throw Blocked("גוגל ביקש הפסקה")
            }
            if (code !in 200..299) throw java.io.IOException("HTTP $code ${loc.take(80)}")
            val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            trace?.invoke("תשובה: HTTP $code, ${t.length} תווים")
            return t
        } finally {
            c.disconnect()
        }
    }

    private fun parse(text: String): JSONArray? {
        var raw = text.trimStart()
        if (raw.startsWith(")]}'")) raw = raw.substring(4).trimStart()
        for (l in raw.lines()) {
            val line = l.trim()
            if (!line.startsWith("[")) continue
            val outer = try { JSONArray(line) } catch (e: Exception) { continue }
            for (i in 0 until outer.length()) {
                val item = outer.optJSONArray(i) ?: continue
                if (item.length() == 0 || item.optString(0) != "wrb.fr") continue
                if (item.isNull(2)) {
                    val code = item.optJSONArray(5)
                    trace?.invoke("גוגל החזיר שגיאה: ${item.toString().take(200)}")
                    if (code != null && code.optInt(0) in listOf(13, 7, 8)) {
                        throw Blocked("גוגל החזיר קוד ${code.optInt(0)}")
                    }
                    return null
                }
                return JSONArray(item.getString(2))
            }
        }
        trace?.invoke("התשובה לא בפורמט הצפוי: ${raw.take(160).replace("\n", " ")}")
        return null
    }

    private fun seg(o: String, d: String, day: String, stops: Int): JSONArray =
        arr(arr(arr(arr(o, 0))), arr(arr(arr(d, 0))), null, stops, null, null, day,
            *nulls(7), 3)

    private fun filters(o: String, d: String, outDay: String, backDay: String?, adults: Int, stops: Int): JSONArray {
        val trip = if (backDay != null) 1 else 2
        val segs = JSONArray().put(seg(o, d, outDay, stops))
        if (backDay != null) segs.put(seg(d, o, backDay, stops))
        return arr(null, null, trip, null, JSONArray(), 1, arr(adults, 0, 0, 0), *nulls(6),
            segs, null, null, null, 1)
    }

    /** מחיר הלוך-חזור זול ביותר לכל יום יציאה בין start ל-end. stops: 0 = הכול, 1 = ישירות בלבד. */
    fun calendarChunk(o: String, d: String, start: LocalDate, end: LocalDate, nights: Int,
                      adults: Int, stops: Int, currency: String = "ILS"): List<Row> {
        val inner = arr(null,
            filters(o, d, start.toString(), start.plusDays(nights.toLong()).toString(), adults, stops),
            arr(start.toString(), end.toString()), null, arr(nights, nights))
        val data = post("GetCalendarGraph", inner, currency) ?: return emptyList()
        val list = data.opt(data.length() - 1) as? JSONArray ?: return emptyList()
        val out = ArrayList<Row>()
        for (i in 0 until list.length()) {
            try {
                val it = list.getJSONArray(i)
                val price = it.getJSONArray(2).getJSONArray(0).getDouble(1)
                out.add(Row(it.getString(0), it.getString(1), price))
            } catch (e: Exception) {
                continue
            }
        }
        return out
    }

    /**
     * כל השנה הקרובה בחלקים של 61 יום (כמו calendar_year בסקיל).
     * גוגל מוכר כ-330 יום קדימה; בחלקים המאוחרים שגיאה = סוף הטווח, מקצרים ומנסים שוב.
     */
    fun calendarYear(o: String, d: String, nights: Int, adults: Int, stops: Int,
                     log: (String) -> Unit): List<Row> {
        val today = LocalDate.now()
        val start = today.plusDays(1)
        val final = minD(today.plusDays(365), today.plusDays(329L - nights))
        val late = today.plusDays(240)
        val rows = ArrayList<Row>()
        var cur = start
        var waited = false
        while (!cur.isAfter(final)) {
            var end = minD(cur.plusDays(MAX_CHUNK - 1), final)
            val key = "$o-$d-$nights-$adults-$stops-$cur-$today"
            var got: List<Row>? = null
            var tries = 0
            cache[key]?.let { got = it.first; end = it.second }
            while (got == null) {
                try {
                    retries13 = if (cur.isAfter(late)) 0 else 3
                    val g = calendarChunk(o, d, cur, end, nights, adults, stops)
                    if (g.isEmpty() && cur.isAfter(start) && end.minusDays(6).isAfter(cur) && tries < 4) {
                        tries++; end = end.minusDays(7)
                    } else {
                        got = g
                    }
                } catch (e: Blocked) {
                    if (cur.isAfter(late) && end.minusDays(6).isAfter(cur) && tries < 4) {
                        tries++; end = end.minusDays(7); continue
                    }
                    if (cur.isAfter(late)) { got = emptyList(); break }
                    if (!waited) {
                        waited = true
                        log("גוגל מבקש להאט. מחכה דקה וממשיך…")
                        sleepChecked(60_000)
                        continue
                    }
                    throw e
                }
            }
            val g = got ?: emptyList()
            if (g.isNotEmpty() || !cur.isAfter(late)) cache[key] = Pair(g, end)
            rows.addAll(g)
            log("${Places.name(d)} · $nights לילות: ${fmt(cur)}–${fmt(end)} · ${g.size} תאריכים")
            if (g.isEmpty() && cur.isAfter(start)) break
            cur = end.plusDays(1)
        }
        return rows
    }

    private fun minD(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b

    private fun fmt(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}"

    // ---- קישור ישיר ל-Google Flights (פרמטר tfs, protobuf בבסיס 64) ----

    private fun varint(n0: Int, out: ByteArrayOutputStream) {
        var n = n0
        while (true) {
            val b = n and 0x7F
            n = n ushr 7
            if (n != 0) out.write(b or 0x80) else { out.write(b); return }
        }
    }

    private fun pb(field: Int, payload: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        varint((field shl 3) or 2, o); varint(payload.size, o); o.write(payload)
        return o.toByteArray()
    }

    private fun pb(field: Int, value: Int): ByteArray {
        val o = ByteArrayOutputStream()
        varint(field shl 3, o); varint(value, o)
        return o.toByteArray()
    }

    private fun leg(a: String, b: String, day: String): ByteArray =
        pb(2, day.toByteArray()) + pb(13, pb(2, a.toByteArray())) + pb(14, pb(2, b.toByteArray()))

    fun flightPage(o: String, d: String, outDay: String, backDay: String?, adults: Int): String {
        var msg = pb(3, leg(o, d, outDay))
        if (backDay != null) msg += pb(3, leg(d, o, backDay))
        msg += pb(8, ByteArray(maxOf(1, adults)) { 1 }) + pb(9, 1) + pb(19, if (backDay != null) 1 else 2)
        val tfs = Base64.encodeToString(msg, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return "https://www.google.com/travel/flights/search?tfs=$tfs&hl=he&curr=ILS"
    }
}
