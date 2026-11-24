package biz.dotan.flightcompass

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private val ink = Color.parseColor("#1B2333")
    private val muted = Color.parseColor("#5B6475")
    private val fire = Color.parseColor("#E8590C")
    private val bg = Color.parseColor("#F6F4EF")

    private lateinit var fromIn: EditText
    private lateinit var destIn: AutoCompleteTextView
    private lateinit var nightsIn: EditText
    private lateinit var adultsIn: EditText
    private lateinit var countIn: EditText
    private lateinit var directCb: CheckBox
    private lateinit var scanBtn: Button
    private lateinit var randomBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var scroll: ScrollView

    private val logLines = ArrayDeque<String>()
    @Volatile private var busy = false

    private data class Best(val code: String, val row: GFlights.Row, val avg: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(text("🧭 מצפן הטיסות", 26f, true, ink))
        root.addView(text("המחירים הכי זולים בשנה הקרובה, יום אחרי יום, מלוח המחירים של Google Flights.", 14f, false, muted))

        // ---- הגדרות משותפות ----
        val common = card(root)
        common.addView(text("הגדרות", 17f, true, ink))
        fromIn = input(common, "יוצאים מ (עיר או קוד שדה)", "תל אביב", InputType.TYPE_CLASS_TEXT)
        nightsIn = input(common, "כמה לילות", "4", InputType.TYPE_CLASS_NUMBER)
        adultsIn = input(common, "כמה מבוגרים", "1", InputType.TYPE_CLASS_NUMBER)
        directCb = CheckBox(this).apply { text = "טיסות ישירות בלבד"; setTextColor(ink) }
        common.addView(directCb)

        // ---- יעד מסוים ----
        val single = card(root)
        single.addView(text("יעד מסוים", 17f, true, ink))
        destIn = AutoCompleteTextView(this).apply {
            hint = "לאן? (למשל אתונה, בנגקוק, או קוד כמו LJU)"
            threshold = 1
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line,
                Places.all.values.distinct()))
            textDirection = View.TEXT_DIRECTION_RTL
        }
        single.addView(destIn)
        scanBtn = button(single, "סרוק את השנה הקרובה") { startSingle(null) }

        // ---- הגרלת יעדים ----
        val rnd = card(root)
        rnd.addView(text("🎲 יעדים אקראיים", 17f, true, ink))
        rnd.addView(text("מגריל יעדים מתוך ${Places.randomPool.size} יעדים פופולריים מישראל, סורק לכל אחד את השנה הקרובה ומדרג לפי המחיר הזול.", 13f, false, muted))
        countIn = input(rnd, "כמה יעדים להגריל (1–8)", "4", InputType.TYPE_CLASS_NUMBER)
        randomBtn = button(rnd, "הגרל וסרוק") { startRandom() }

        stopBtn = button(root, "עצור סריקה") {
            GFlights.cancelled = true
            log("עוצר…")
        }
        stopBtn.isEnabled = false

        status = text("", 13f, false, muted)
        root.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
    }

    // ================= פעולות =================

    private fun readCommon(): Triple<String, Int, Int>? {
        val o = Places.resolve(fromIn.text.toString())
        if (o == null) { toastLine("לא הבנתי מאיפה יוצאים. כתוב עיר מהרשימה או קוד שדה בן 3 אותיות (TLV)."); return null }
        val n = nightsIn.text.toString().toIntOrNull()
        if (n == null || n !in 1..30) { toastLine("מספר הלילות צריך להיות בין 1 ל-30."); return null }
        val a = adultsIn.text.toString().toIntOrNull()
        if (a == null || a !in 1..9) { toastLine("מספר המבוגרים צריך להיות בין 1 ל-9."); return null }
        return Triple(o, n, a)
    }

    private fun startSingle(forced: String?) {
        if (busy) return
        val (o, nights, adults) = readCommon() ?: return
        val d = forced ?: Places.resolve(destIn.text.toString())
        if (d == null) { toastLine("לא מכיר את היעד. בחר עיר מהרשימה, או כתוב קוד שדה תעופה בן 3 אותיות באנגלית (למשל LJU)."); return }
        if (d == o) { toastLine("היעד זהה לנקודת היציאה."); return }
        val stops = if (directCb.isChecked) 1 else 0
        results.removeAllViews()
        logLines.clear()
        log("סורק ל${Places.name(d)} ($d), $nights לילות. עובר על כל השנה הקרובה, יום אחרי יום. לוקח פחות מדקה.")
        runWork {
            GFlights.pauseMs = 1500
            val rows = GFlights.calendarYear(o, d, nights, adults, stops) { log(it) }
            runOnUiThread { showSingle(o, d, nights, adults, rows) }
        }
    }

    private fun startRandom() {
        if (busy) return
        val (o, nights, adults) = readCommon() ?: return
        val count = (countIn.text.toString().toIntOrNull() ?: 4).coerceIn(1, 8)
        val stops = if (directCb.isChecked) 1 else 0
        val picks = Places.randomPool.filter { it != o }.shuffled().take(count)
        results.removeAllViews()
        logLines.clear()
        log("הוגרלו: ${picks.joinToString(", ") { Places.name(it) }}. סורק אחד אחרי השני, בקצב איטי (כ-30 שניות ליעד).")
        val found = ArrayList<Best>()
        runWork {
            GFlights.pauseMs = 3000
            try {
                for (d in picks) {
                    val rows = GFlights.calendarYear(o, d, nights, adults, stops) { log(it) }
                    val best = rows.minByOrNull { it.price }
                    if (best != null) {
                        found.add(Best(d, best, rows.map { it.price }.average()))
                        val snapshot = found.sortedBy { it.row.price }
                        runOnUiThread { showRandom(o, nights, adults, snapshot, false) }
                    } else {
                        log("${Places.name(d)}: לא נמצאו מחירים.")
                    }
                }
                runOnUiThread { showRandom(o, nights, adults, found.sortedBy { it.row.price }, true) }
            } catch (e: GFlights.Blocked) {
                if (found.isNotEmpty()) {
                    runOnUiThread { showRandom(o, nights, adults, found.sortedBy { it.row.price }, true) }
                    log("שימו לב: הסריקה חלקית, רק ${found.size} מתוך ${picks.size} יעדים נבדקו.")
                }
                throw e
            }
        }
    }

    private fun runWork(job: () -> Unit) {
        busy = true
        GFlights.cancelled = false
        setBusy(true)
        Thread {
            try {
                job()
            } catch (e: GFlights.Cancelled) {
                log("הסריקה נעצרה.")
            } catch (e: GFlights.Blocked) {
                log("גוגל ביקש הפסקה. זה זמני ולא נוגע בחשבון שלך. חכה 20–30 דקות ונסה שוב: מה שכבר נסרק היום נשמר.")
            } catch (e: java.io.IOException) {
                log("אין חיבור ל-Google Flights (${e.message}). בדוק אינטרנט ונסה שוב.")
            } catch (e: Exception) {
                log("שגיאה: ${e.message}")
            } finally {
                busy = false
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun setBusy(b: Boolean) {
        scanBtn.isEnabled = !b
        randomBtn.isEnabled = !b
        stopBtn.isEnabled = b
    }

    // ================= תוצאות =================

    private fun showSingle(o: String, d: String, nights: Int, adults: Int, rows: List<GFlights.Row>) {
        results.removeAllViews()
        if (rows.isEmpty()) {
            results.addView(text("לא נמצאו מחירים לחיפוש הזה. נסה אורך אחר או בלי 'ישירות בלבד'.", 15f, true, ink))
            return
        }
        val sorted = rows.sortedBy { it.price }
        val best = sorted.first()
        val avg = rows.map { it.price }.average()
        val max = rows.maxOf { it.price }

        val head = card(results)
        head.addView(text("${Places.name(o)} → ${Places.name(d)} · $nights לילות", 15f, false, muted))
        head.addView(text(money(best.price), 34f, true, fire))
        head.addView(text("${heDate(best.out)} → ${heDate(best.back)} · הלוך-חזור", 16f, true, ink))
        head.addView(text("חוסכים ${money(avg - best.price)} מול הממוצע (${money(avg)}), ו-${money(max - best.price)} מול היקר ביותר.", 14f, false, ink))
        button(head, "לטיסה ב-Google Flights") { open(GFlights.flightPage(o, d, best.out, best.back, adults)) }

        val more = card(results)
        more.addView(text("עוד תאריכים זולים", 16f, true, ink))
        for (r in sorted.drop(1).take(4)) {
            more.addView(linkRow("${heDate(r.out)} → ${heDate(r.back)}", money(r.price)) {
                open(GFlights.flightPage(o, d, r.out, r.back, adults))
            })
        }

        val months = rows.groupBy { it.out.substring(0, 7) }.toSortedMap()
        val mc = card(results)
        mc.addView(text("הכי זול בכל חודש", 16f, true, ink))
        for ((m, list) in months) {
            val r = list.minByOrNull { it.price } ?: continue
            mc.addView(linkRow(monthName(m), "${money(r.price)} · ${heDate(r.out)}") {
                open(GFlights.flightPage(o, d, r.out, r.back, adults))
            })
        }
        val lastDay = rows.maxOf { it.out }
        mc.addView(text("נבדקו ${rows.size} תאריכי יציאה, עד ${heDate(lastDay)}. אחרי זה עוד לא במכירה.", 12f, false, muted))

        results.addView(text(disclaimer(adults), 12f, false, muted))
        scroll.post { scroll.smoothScrollTo(0, results.top) }
    }

    private fun showRandom(o: String, nights: Int, adults: Int, list: List<Best>, done: Boolean) {
        results.removeAllViews()
        val c = card(results)
        c.addView(text(if (done) "היעדים שהוגרלו, מהזול ליקר" else "בינתיים (${list.size} נסרקו)…", 16f, true, ink))
        list.forEachIndexed { i, b ->
            val medal = when (i) { 0 -> "🥇 "; 1 -> "🥈 "; 2 -> "🥉 "; else -> "${i + 1}. " }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(text("$medal${Places.name(b.code)} · ${money(b.row.price)}", 18f, true, if (i == 0) fire else ink))
            row.addView(text("${heDate(b.row.out)} → ${heDate(b.row.back)} · חוסכים ${money(b.avg - b.row.price)} מול הממוצע", 13f, false, muted))
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btns.addView(smallButton("לטיסה") { open(GFlights.flightPage(o, b.code, b.row.out, b.row.back, adults)) })
            btns.addView(smallButton("כל השנה") {
                destIn.setText(Places.name(b.code))
                startSingle(b.code)
            })
            row.addView(btns)
            c.addView(row)
        }
        if (done) results.addView(text("$nights לילות לכל יעד. ${disclaimer(adults)}", 12f, false, muted))
    }

    // ================= עזרים =================

    private fun disclaimer(adults: Int): String {
        val who = if (adults == 1) "למבוגר אחד" else "ל-$adults נוסעים, כפי שגוגל מציג אותם"
        val at = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"))
        return "המחירים נלקחו מלוח המחירים של Google Flights בשעת הבדיקה ($at), $who הלוך-חזור במחלקת תיירים, " +
            "ומשתנים כל הזמן. לפני הזמנה בודקים את המחיר הסופי באתר, כולל כבודה ומושבים."
    }

    private val daysHe = listOf("שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "ראשון")
    private val monthsHe = listOf("ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי",
        "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר")

    private fun heDate(s: String): String {
        val d = LocalDate.parse(s)
        return "${daysHe[d.dayOfWeek.value - 1]} ${d.dayOfMonth}.${d.monthValue}"
    }

    private fun monthName(ym: String): String {
        val (y, m) = ym.split("-")
        return "${monthsHe[m.toInt() - 1]} $y"
    }

    private fun money(v: Double) = "₪" + String.format(Locale.US, "%,.0f", v)

    private fun open(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun log(line: String) {
        runOnUiThread {
            logLines.addLast(line)
            while (logLines.size > 6) logLines.removeFirst()
            status.text = logLines.joinToString("\n")
        }
    }

    private fun toastLine(msg: String) {
        logLines.clear()
        log("⚠️ $msg")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        textDirection = View.TEXT_DIRECTION_RTL
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(2).toFloat()
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(14)
        parent.addView(c, lp)
        return c
    }

    private fun input(parent: LinearLayout, label: String, def: String, type: Int): EditText {
        parent.addView(text(label, 13f, false, muted))
        val e = EditText(this).apply {
            setText(def)
            inputType = type
            textDirection = View.TEXT_DIRECTION_RTL
        }
        parent.addView(e)
        return e
    }

    private fun button(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 16f
            background = GradientDrawable().apply {
                setColor(fire)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
        lp.topMargin = dp(10)
        parent.addView(b, lp)
        return b
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setOnClickListener { onClick() }
    }

    private fun linkRow(left: String, right: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        val a = text(left, 15f, false, ink)
        addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("$right  ›", 15f, true, fire))
        setOnClickListener { onClick() }
    }
}
