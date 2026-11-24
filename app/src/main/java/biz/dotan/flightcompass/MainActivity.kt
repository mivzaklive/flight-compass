package biz.dotan.flightcompass

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    // ---- צבעים ----
    private val navy = Color.parseColor("#14213D")
    private val navy2 = Color.parseColor("#24365F")
    private val ink = Color.parseColor("#1B2333")
    private val muted = Color.parseColor("#6B7280")
    private val fire = Color.parseColor("#E8590C")
    private val firePressed = Color.parseColor("#B94508")
    private val disabledBg = Color.parseColor("#D6D3CC")
    private val bg = Color.parseColor("#F4F1EA")
    private val line = Color.parseColor("#E7E2D8")
    private val errBg = Color.parseColor("#FDECEC")
    private val errInk = Color.parseColor("#9B1C1C")

    // ---- רכיבים ----
    private lateinit var fromIn: EditText
    private lateinit var destIn: AutoCompleteTextView
    private lateinit var nightsIn: EditText
    private lateinit var adultsIn: EditText
    private lateinit var countIn: EditText
    private lateinit var directCb: CheckBox
    private lateinit var browserCb: CheckBox
    private lateinit var scanBtn: Button
    private lateinit var randomBtn: Button
    private lateinit var diagBtn: Button
    private lateinit var progressCard: LinearLayout
    private lateinit var progTitle: TextView
    private lateinit var progSub: TextView
    private lateinit var progBar: ProgressBar
    private lateinit var messageBox: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var techBody: LinearLayout
    private lateinit var techLog: TextView
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private lateinit var web: WebFlights

    private val ui = Handler(Looper.getMainLooper())
    private val logLines = ArrayDeque<String>()
    @Volatile private var busy = false
    private var startedAt = 0L
    private var progText = ""

    private data class Best(val code: String, val row: GFlights.Row, val avg: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)
        setContentView(scroll)

        // ---- כותרת ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(30))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(navy, navy2))
        }
        header.addView(text("🧭 מצפן הטיסות", 28f, true, Color.WHITE))
        header.addView(text("התאריכים הכי זולים בשנה הקרובה, יום אחרי יום, מלוח המחירים של Google Flights", 14f, false, Color.parseColor("#C9D3E8")))
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(32))
        }
        root.addView(body)

        // ---- הגדרות ----
        val common = card(body, top = -16)
        common.addView(sectionTitle("הגדרות הנסיעה"))
        fromIn = input(common, "יוצאים מ", "תל אביב", InputType.TYPE_CLASS_TEXT)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val c1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val c2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(c1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        row.addView(c2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nightsIn = input(c1, "לילות", "4", InputType.TYPE_CLASS_NUMBER)
        adultsIn = input(c2, "מבוגרים", "1", InputType.TYPE_CLASS_NUMBER)
        common.addView(row)
        directCb = CheckBox(this).apply { text = "טיסות ישירות בלבד"; setTextColor(ink); textSize = 15f }
        common.addView(directCb)

        // ---- יעד מסוים ----
        val single = card(body)
        single.addView(sectionTitle("✈️ יעד מסוים"))
        single.addView(caption("סורק את כל השנה הקרובה ומראה את התאריכים הזולים"))
        destIn = AutoCompleteTextView(this).apply {
            hint = "לאן? אתונה, בנגקוק, או קוד שדה כמו LJU"
            threshold = 1
            textSize = 17f
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line,
                Places.all.values.distinct()))
            textDirection = View.TEXT_DIRECTION_RTL
            background = fieldBg()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        single.addView(destIn, lpTop(8))
        scanBtn = primaryButton(single, "סרוק את השנה הקרובה") { startSingle(null) }

        // ---- הגרלת יעדים ----
        val rnd = card(body)
        rnd.addView(sectionTitle("🎲 יעדים אקראיים"))
        rnd.addView(caption("מגריל יעדים מתוך ${Places.randomPool.size} יעדים פופולריים מישראל, סורק כל אחד ומדרג מהזול ליקר"))
        countIn = input(rnd, "כמה יעדים להגריל (1–8)", "4", InputType.TYPE_CLASS_NUMBER)
        randomBtn = primaryButton(rnd, "הגרל וסרוק") { startRandom() }

        // ---- התקדמות ----
        progressCard = card(body)
        progTitle = text("", 18f, true, ink)
        progSub = text("", 14f, false, muted)
        progBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(fire)
            progressBackgroundTintList = ColorStateList.valueOf(line)
        }
        progressCard.addView(progTitle)
        progressCard.addView(progBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(10) })
        progressCard.addView(progSub, lpTop(6))
        secondaryButton(progressCard, "עצור") {
            GFlights.cancelled = true
            progTitle.text = "עוצר…"
        }
        progressCard.visibility = View.GONE

        // ---- הודעות ותוצאות ----
        messageBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(messageBox)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)

        // ---- פרטים טכניים (מקופל) ----
        val tech = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(tech, lpTop(24))
        val techHead = text("🔧 פרטים טכניים ובדיקת חיבור  ▾", 14f, false, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                techBody.visibility = if (techBody.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        tech.addView(techHead)
        techBody = card(tech).apply { visibility = View.GONE }
        browserCb = CheckBox(this).apply {
            text = "שליחה דרך דפדפן מובנה (מומלץ)"
            isChecked = true
            setTextColor(ink)
            setOnCheckedChangeListener { _, on -> applyTransport(on) }
        }
        techBody.addView(browserCb)
        diagBtn = secondaryButton(techBody, "הרץ בדיקת חיבור") { startDiagnose() }
        secondaryButton(techBody, "העתק יומן") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("log", techLog.text))
            techLog.append("\n✓ הועתק\n")
        }
        techLog = text("", 12f, false, ink).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
        }
        techBody.addView(techLog, lpTop(8))

        // ---- הדפדפן המובנה (בלתי נראה) ----
        web = WebFlights(this)
        root.addView(web.view, LinearLayout.LayoutParams(1, 1))
        applyTransport(true)
        GFlights.trace = { log(it) }
    }

    private fun applyTransport(browser: Boolean) {
        GFlights.transport = if (browser) { u, b -> web.send(u, b) } else null
    }

    // ================= פעולות =================

    private fun readCommon(): Triple<String, Int, Int>? {
        val o = Places.resolve(fromIn.text.toString())
        if (o == null) { showError("לא הבנתי מאיפה יוצאים", "כתוב עיר מהרשימה (למשל תל אביב) או קוד שדה בן 3 אותיות באנגלית (TLV)."); return null }
        val n = nightsIn.text.toString().toIntOrNull()
        if (n == null || n !in 1..30) { showError("מספר לילות לא תקין", "אפשר לבחור בין לילה אחד ל-30 לילות."); return null }
        val a = adultsIn.text.toString().toIntOrNull()
        if (a == null || a !in 1..9) { showError("מספר מבוגרים לא תקין", "אפשר לבחור בין 1 ל-9 מבוגרים."); return null }
        return Triple(o, n, a)
    }

    private fun startSingle(forced: String?) {
        if (busy) return
        val (o, nights, adults) = readCommon() ?: return
        val d = forced ?: Places.resolve(destIn.text.toString())
        if (d == null) { showError("לא מכיר את היעד", "בחר עיר מהרשימה שנפתחת, או כתוב קוד שדה תעופה בן 3 אותיות באנגלית (למשל LJU)."); return }
        if (d == o) { showError("היעד זהה לנקודת היציאה", "בחר יעד אחר."); return }
        val stops = if (directCb.isChecked) 1 else 0
        results.removeAllViews()
        runWork("סורק ל${Places.name(d)}", "$nights לילות · מתחבר ל-Google Flights…") {
            GFlights.pauseMs = 1500
            val rows = GFlights.calendarYear(o, d, nights, adults, stops, { log(it) }) { done, total ->
                setProgress(done * 100 / maxOf(total, 1), "חלק $done מתוך $total של השנה הקרובה")
            }
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
        val found = ArrayList<Best>()
        runWork("מגריל וסורק $count יעדים", picks.joinToString(" · ") { Places.name(it) }) {
            GFlights.pauseMs = 3000
            try {
                picks.forEachIndexed { i, d ->
                    runOnUiThread { progTitle.text = "יעד ${i + 1} מתוך ${picks.size}: ${Places.name(d)}" }
                    val rows = GFlights.calendarYear(o, d, nights, adults, stops, { log(it) }) { done, total ->
                        val pct = ((i + done.toDouble() / maxOf(total, 1)) * 100 / picks.size).toInt()
                        setProgress(pct, "חלק $done מתוך $total · נסרקו ${found.size} יעדים")
                    }
                    val best = rows.minByOrNull { it.price }
                    if (best != null) {
                        found.add(Best(d, best, rows.map { it.price }.average()))
                        val snapshot = found.sortedBy { it.row.price }
                        runOnUiThread { showRandom(o, nights, adults, snapshot, false) }
                    } else {
                        log("${Places.name(d)}: לא נמצאו מחירים")
                    }
                }
                runOnUiThread { showRandom(o, nights, adults, found.sortedBy { it.row.price }, true) }
            } catch (e: Exception) {
                if (found.isNotEmpty()) runOnUiThread { showRandom(o, nights, adults, found.sortedBy { it.row.price }, true) }
                throw e
            }
        }
    }

    private fun startDiagnose() {
        if (busy) return
        techLog.text = ""
        runWork("בודק חיבור ל-Google Flights", "שולח את אותה בקשה בכמה דרכים…") {
            GFlights.diagnose(browser = { u, b -> web.send(u, b) }) { line -> runOnUiThread { techLog.append(line + "\n") } }
            runOnUiThread { applyTransport(browserCb.isChecked) }
        }
    }

    private fun runWork(title: String, sub: String, job: () -> Unit) {
        busy = true
        GFlights.cancelled = false
        messageBox.removeAllViews()
        logLines.clear()
        setBusy(true)
        progTitle.text = title
        progText = sub
        progBar.progress = 3
        startedAt = System.currentTimeMillis()
        tick()
        scroll.post { scroll.smoothScrollTo(0, progressCard.top + (progressCard.parent as View).top - dp(12)) }
        Thread {
            try {
                job()
            } catch (e: GFlights.Cancelled) {
                runOnUiThread { showInfo("הסריקה נעצרה", "אפשר להתחיל סריקה חדשה בכל רגע.") }
            } catch (e: GFlights.Blocked) {
                runOnUiThread {
                    showError("גוגל לא מאשר כרגע בקשות מהטלפון",
                        "זה זמני ולא קשור לחשבון שלך. נסה שוב בעוד 20–30 דקות. אם זה חוזר, פתח את \"פרטים טכניים\" בתחתית, הרץ בדיקת חיבור ושלח את היומן.")
                }
            } catch (e: java.io.IOException) {
                runOnUiThread { showError("אין חיבור ל-Google Flights", "${e.message ?: ""}\nבדוק שיש אינטרנט ונסה שוב.") }
            } catch (e: Exception) {
                runOnUiThread { showError("משהו השתבש", e.message ?: e.javaClass.simpleName) }
            } finally {
                busy = false
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun tick() {
        if (!busy) return
        val s = (System.currentTimeMillis() - startedAt) / 1000
        progSub.text = "$progText · ${s / 60}:${String.format(Locale.US, "%02d", s % 60)}"
        ui.postDelayed({ tick() }, 1000)
    }

    private fun setProgress(pct: Int, sub: String) {
        runOnUiThread {
            progBar.progress = pct.coerceIn(3, 100)
            progText = sub
        }
    }

    private fun setBusy(b: Boolean) {
        scanBtn.isEnabled = !b
        randomBtn.isEnabled = !b
        diagBtn.isEnabled = !b
        progressCard.visibility = if (b) View.VISIBLE else View.GONE
    }

    // ================= הודעות =================

    private fun showError(title: String, msg: String) = message(title, msg, errBg, errInk, "⚠️")
    private fun showInfo(title: String, msg: String) = message(title, msg, Color.WHITE, ink, "ℹ️")

    private fun message(title: String, msg: String, bgc: Int, fg: Int, icon: String) {
        messageBox.removeAllViews()
        val c = card(messageBox)
        (c.background as GradientDrawable).setColor(bgc)
        c.addView(text("$icon $title", 17f, true, fg))
        c.addView(text(msg, 14f, false, fg))
        scroll.post { scroll.smoothScrollTo(0, (messageBox.parent as View).top + messageBox.top - dp(12)) }
    }

    // ================= תוצאות =================

    private fun showSingle(o: String, d: String, nights: Int, adults: Int, rows: List<GFlights.Row>) {
        results.removeAllViews()
        if (rows.isEmpty()) {
            showInfo("לא נמצאו מחירים", "אין טיסות שמתאימות לחיפוש. נסה מספר לילות אחר, או בלי \"ישירות בלבד\".")
            return
        }
        val sorted = rows.sortedBy { it.price }
        val best = sorted.first()
        val avg = rows.map { it.price }.average()
        val max = rows.maxOf { it.price }

        val head = card(results)
        head.addView(text("${Places.name(o)} ← ${Places.name(d)} · $nights לילות", 14f, false, muted))
        head.addView(text("התאריך הכי זול", 13f, true, fire), lpTop(10))
        head.addView(text(money(best.price), 40f, true, ink))
        head.addView(text("${heDate(best.out)} עד ${heDate(best.back)} · הלוך-חזור", 16f, true, ink))
        val save = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        save.addView(stat("חיסכון מול הממוצע", money(avg - best.price)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        save.addView(stat("חיסכון מול היקר", money(max - best.price)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(save, lpTop(12))
        primaryButton(head, "להזמנה ב-Google Flights") { open(GFlights.flightPage(o, d, best.out, best.back, adults)) }

        val more = card(results)
        more.addView(sectionTitle("עוד תאריכים זולים"))
        for (r in sorted.drop(1).take(4)) {
            more.addView(linkRow("${heDate(r.out)} עד ${heDate(r.back)}", money(r.price)) {
                open(GFlights.flightPage(o, d, r.out, r.back, adults))
            })
        }

        val months = rows.groupBy { it.out.substring(0, 7) }.toSortedMap()
        val mc = card(results)
        mc.addView(sectionTitle("הכי זול בכל חודש"))
        for ((m, list) in months) {
            val r = list.minByOrNull { it.price } ?: continue
            mc.addView(linkRow(monthName(m), "${money(r.price)} · ${heDate(r.out)}") {
                open(GFlights.flightPage(o, d, r.out, r.back, adults))
            })
        }
        val lastDay = rows.maxOf { it.out }
        mc.addView(caption("נבדקו ${rows.size} תאריכי יציאה, עד ${heDate(lastDay)}. מעבר לזה עדיין לא במכירה."))

        results.addView(caption(disclaimer(adults)), lpTop(12))
        scroll.post { scroll.smoothScrollTo(0, (results.parent as View).top + results.top - dp(12)) }
    }

    private fun showRandom(o: String, nights: Int, adults: Int, list: List<Best>, done: Boolean) {
        results.removeAllViews()
        val c = card(results)
        c.addView(sectionTitle(if (done) "היעדים שהוגרלו, מהזול ליקר" else "התוצאות עד עכשיו (${list.size})"))
        c.addView(caption("$nights לילות · לחיצה על יעד פותחת את הטיסה"))
        list.forEachIndexed { i, b ->
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(12), dp(4), dp(12))
                background = pressable(Color.WHITE, Color.parseColor("#F3EFE7"), 12)
                isClickable = true
                setOnClickListener { open(GFlights.flightPage(o, b.code, b.row.out, b.row.back, adults)) }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(text("$medal  ${Places.name(b.code)}", 18f, true, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(text(money(b.row.price), 18f, true, if (i == 0) fire else ink))
            item.addView(top)
            item.addView(text("${heDate(b.row.out)} עד ${heDate(b.row.back)} · חוסכים ${money(b.avg - b.row.price)} מול הממוצע", 13f, false, muted))
            item.addView(text("כל השנה ל${Places.name(b.code)} ›", 13f, true, fire).apply {
                setPadding(0, dp(6), 0, 0)
                setOnClickListener {
                    destIn.setText(Places.name(b.code))
                    startSingle(b.code)
                }
            })
            c.addView(item)
            if (i < list.size - 1) c.addView(divider())
        }
        if (done) results.addView(caption(disclaimer(adults)), lpTop(12))
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

    /** יומן טכני: נשמר רק בתוך "פרטים טכניים", לא על המסך הראשי. */
    private fun log(lineText: String) {
        runOnUiThread {
            val t = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            logLines.addLast("$t  $lineText")
            while (logLines.size > 60) logLines.removeFirst()
            if (techBody.visibility == View.VISIBLE || logLines.size % 5 == 0) techLog.text = logLines.joinToString("\n")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lpTop(m: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(m) }

    private fun text(s: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        textDirection = View.TEXT_DIRECTION_RTL
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun sectionTitle(s: String) = text(s, 18f, true, ink)
    private fun caption(s: String) = text(s, 13f, false, muted)

    private fun divider() = View(this).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun stat(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { setColor(Color.parseColor("#FFF4EC")); cornerRadius = dp(12).toFloat() }
        addView(text(label, 12f, false, muted))
        addView(text(value, 18f, true, fire))
    }

    private fun card(parent: LinearLayout, top: Int = 14): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), line)
            }
            elevation = dp(3).toFloat()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(c, lp)
        return c
    }

    private fun fieldBg() = GradientDrawable().apply {
        setColor(Color.parseColor("#FAF8F4"))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), line)
    }

    private fun input(parent: LinearLayout, label: String, def: String, type: Int): EditText {
        parent.addView(text(label, 13f, true, muted), lpTop(10))
        val e = EditText(this).apply {
            setText(def)
            inputType = type
            textSize = 17f
            setTextColor(ink)
            textDirection = View.TEXT_DIRECTION_RTL
            background = fieldBg()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        parent.addView(e, lpTop(4))
        return e
    }

    /** רקע שמגיב ללחיצה (אדווה), ומאפיר כשהכפתור לא זמין. */
    private fun pressable(normal: Int, pressed: Int, radius: Int, stroke: Int? = null): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(if (stroke == null && normal != Color.WHITE) disabledBg else normal, normal)))
            if (stroke != null) setStroke(dp(2), stroke)
        }
        val mask = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(Color.WHITE) }
        return RippleDrawable(ColorStateList.valueOf(pressed), shape, mask)
    }

    private fun primaryButton(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(Color.parseColor("#8A8478"), Color.WHITE)))
            background = pressable(fire, firePressed, 14)
            stateListAnimator = null
            elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }
        parent.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(14) })
        return b
    }

    private fun secondaryButton(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(Color.parseColor("#B8B2A6"), fire)))
            background = pressable(Color.WHITE, Color.parseColor("#FFE3D1"), 14, fire)
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
        parent.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })
        return b
    }

    private fun linkRow(left: String, right: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(14), dp(6), dp(14))
        background = pressable(Color.WHITE, Color.parseColor("#F3EFE7"), 10)
        isClickable = true
        addView(text(left, 15f, false, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text("$right  ›", 15f, true, fire))
        setOnClickListener { onClick() }
    }
}
