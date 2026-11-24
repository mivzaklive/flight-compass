package biz.dotan.flightcompass

/** יעדים מוכרים (מתוך places.json של מצפן הטיסות). קוד שדה → שם בעברית. */
object Places {
    val all: LinkedHashMap<String, String> = linkedMapOf(
        "TLV" to "תל אביב", "ETM" to "אילת", "HFA" to "חיפה",
        "ATH" to "אתונה", "SKG" to "סלוניקי", "RHO" to "רודוס", "HER" to "כרתים", "JTR" to "סנטוריני",
        "LCA" to "לרנקה", "PFO" to "פאפוס",
        "FCO" to "רומא", "MXP" to "מילאנו", "VCE" to "ונציה", "NAP" to "נאפולי",
        "BUD" to "בודפשט", "PRG" to "פראג", "VIE" to "וינה",
        "BER" to "ברלין", "MUC" to "מינכן", "FRA" to "פרנקפורט",
        "BCN" to "ברצלונה", "MAD" to "מדריד", "LIS" to "ליסבון",
        "CDG" to "פריז", "NCE" to "ניס", "LHR" to "לונדון", "AMS" to "אמסטרדם",
        "ZRH" to "ציריך", "GVA" to "ז'נבה",
        "OTP" to "בוקרשט", "SOF" to "סופיה", "BEG" to "בלגרד", "ZAG" to "זאגרב",
        "TIA" to "טירנה", "TGD" to "פודגוריצה", "KRK" to "קרקוב", "WAW" to "ורשה",
        "RIX" to "ריגה", "VNO" to "וילנה", "MLA" to "מלטה",
        "TBS" to "טביליסי", "BUS" to "בטומי", "GYD" to "באקו",
        "DXB" to "דובאי", "AUH" to "אבו דאבי", "RAK" to "מרקש",
        "BKK" to "בנגקוק", "JFK" to "ניו יורק", "MIA" to "מיאמי", "LAX" to "לוס אנג'לס",
        "BOM" to "מומבאי", "DEL" to "דלהי", "NRT" to "טוקיו",
    )

    private val israel = setOf("TLV", "ETM", "HFA")

    /** המאגר שממנו מגרילים יעדים אקראיים: כל היעדים שמחוץ לישראל. */
    val randomPool: List<String> get() = all.keys.filter { it !in israel }

    fun name(code: String): String = all[code] ?: code

    /** שם בעברית / באנגלית-קוד → קוד שדה. מחזיר null אם לא הובן. */
    fun resolve(input: String): String? {
        var t = input.trim().trim('"', '\'')
        if (t.isEmpty()) return null
        if (t.length == 3 && t.all { it.isLetter() && it.code < 128 }) return t.uppercase()
        all.entries.firstOrNull { it.value == t }?.let { return it.key }
        // "לאתונה", "מתל אביב"
        if (t.length > 3 && t[0] in "לבמהו") {
            t = t.substring(1)
            all.entries.firstOrNull { it.value == t }?.let { return it.key }
        }
        return null
    }
}
