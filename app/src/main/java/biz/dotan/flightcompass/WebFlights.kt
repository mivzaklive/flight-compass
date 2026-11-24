package biz.dotan.flightcompass

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * שולח את הבקשות מתוך דפדפן אמיתי (WebView) שפתוח על Google Flights.
 * כך הבקשה יוצאת בדיוק כמו מהאתר עצמו: אותו מנוע כרום, אותן עוגיות, אותו מקור.
 * חובה ליצור את המחלקה מה-UI thread. את send קוראים מ-thread ברקע.
 */
class WebFlights(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val results = ConcurrentHashMap<Int, Pair<Int, String>>()
    private val latches = ConcurrentHashMap<Int, CountDownLatch>()
    private val ids = AtomicInteger()
    @Volatile private var ready = false
    @Volatile private var pageLatch = CountDownLatch(1)
    @Volatile var lastUrl = ""
        private set

    @SuppressLint("SetJavaScriptEnabled")
    val view: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), "FC")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                lastUrl = url
                pageLatch.countDown()
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun done(id: Int, status: Int, text: String) {
            results[id] = Pair(status, text)
            latches[id]?.countDown()
        }

        @JavascriptInterface
        fun fail(id: Int, msg: String) {
            results[id] = Pair(-1, msg)
            latches[id]?.countDown()
        }
    }

    /** פותח את Google Flights (פעם אחת), כדי שהבקשות יישלחו מתוך האתר. */
    private fun ensureReady() {
        if (ready) return
        pageLatch = CountDownLatch(1)
        main.post { view.loadUrl("https://www.google.com/travel/flights?hl=en&curr=ILS") }
        if (!pageLatch.await(40, TimeUnit.SECONDS)) throw IOException("דף Google Flights לא נטען")
        Thread.sleep(1500)
        if (!lastUrl.contains("google.com/travel")) {
            throw IOException("גוגל הפנה לדף אחר: ${lastUrl.take(80)}")
        }
        ready = true
    }

    /** שולח POST מתוך הדף. מחזיר (קוד HTTP, טקסט התשובה). */
    fun send(url: String, body: String): Pair<Int, String> {
        ensureReady()
        val id = ids.incrementAndGet()
        val latch = CountDownLatch(1)
        latches[id] = latch
        val js = "(function(){fetch(" + JSONObject.quote(url) + ",{method:'POST',credentials:'include'," +
            "headers:{'content-type':'application/x-www-form-urlencoded;charset=UTF-8'},body:" +
            JSONObject.quote(body) + "}).then(function(r){return r.text().then(function(t){FC.done(" + id +
            ",r.status,t);});}).catch(function(e){FC.fail(" + id + ",String(e));});})();"
        main.post { view.evaluateJavascript(js, null) }
        try {
            if (!latch.await(45, TimeUnit.SECONDS)) throw IOException("אין תשובה מגוגל (45 שניות)")
            val r = results.remove(id) ?: throw IOException("אין תשובה")
            if (r.first == -1) {
                ready = false // הדף אולי נסגר; בפעם הבאה נטען מחדש
                throw IOException("הבקשה מהדפדפן נכשלה: ${r.second}")
            }
            return r
        } finally {
            latches.remove(id)
        }
    }

    fun reset() {
        ready = false
    }
}
