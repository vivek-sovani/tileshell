package com.tileshell.core.data

/**
 * The highlighted tithis', ekadashis' and grahans' names in each [PanchangLanguage]. Festivals carry their own
 * ([Festival.nameIn]). English is the transliterated name the engine already used.
 */
internal object PanchangObservanceNames {
    private fun byLanguage(vararg v: String): Map<PanchangLanguage, String> {
        require(v.size == PanchangLanguage.entries.size)
        return PanchangLanguage.entries.zip(v.toList()).toMap()
    }

    /** Order: English, Hindi, Marathi, Gujarati, Tamil, Malayalam, Kannada, Telugu. */
    private val HIGHLIGHT = mapOf(
        "sankashti" to byLanguage("sankashti chaturthi", "संकष्टी चतुर्थी", "संकष्टी चतुर्थी", "સંકષ્ટી ચતુર્થી", "சங்கடஹர சதுர்த்தி", "സങ്കഷ്ടി ചതുർത്ഥി", "ಸಂಕಷ್ಟ ಚತುರ್ಥಿ", "సంకష్టహర చతుర్థి"),
        "angaraki" to byLanguage("angaraki sankashti chaturthi", "अंगारकी संकष्टी चतुर्थी", "अंगारकी संकष्टी चतुर्थी", "અંગારકી સંકષ્ટી ચતુર્થી", "அங்காரக சதுர்த்தி", "അംഗാരക ചതുർത്ഥി", "ಅಂಗಾರಕ ಸಂಕಷ್ಟ ಚತುರ್ಥಿ", "అంగారక సంకష్టహర చతుర్థి"),
        "vinayaki" to byLanguage("vinayaki chaturthi", "विनायक चतुर्थी", "विनायकी चतुर्थी", "વિનાયક ચતુર્થી", "வினாயக சதுர்த்தி", "വിനായക ചതുർത്ഥി", "ವಿನಾಯಕ ಚತುರ್ಥಿ", "వినాయక చవితి"),
        "ekadashi" to byLanguage("ekadashi", "एकादशी", "एकादशी", "અગિયારસ", "ஏகாதசி", "ഏകാദശി", "ಏಕಾದಶಿ", "ఏకాదశి"),
        "pradosh" to byLanguage("pradosh", "प्रदोष व्रत", "प्रदोष", "પ્રદોષ", "பிரதோஷம்", "പ്രദോഷം", "ಪ್ರದೋಷ", "ప్రదోషం"),
        "mahashivaratri" to byLanguage("mahashivaratri", "महाशिवरात्रि", "महाशिवरात्री", "મહાશિવરાત્રિ", "மகா சிவராத்திரி", "മഹാശിവരാത്രി", "ಮಹಾ ಶಿವರಾತ್ರಿ", "మహా శివరాత్రి"),
    )

    /** A highlight's name by id, or null where it is just the tithi's own name (purnima, amavasya). */
    fun highlight(id: String, language: PanchangLanguage): String? = when (id) {
        "purnima" -> PanchangNames.tithiName(language, "purnima")
        "amavasya" -> PanchangNames.tithiName(language, "amavasya")
        else -> HIGHLIGHT[id]?.get(language)
    }

    fun angaraki(language: PanchangLanguage): String = HIGHLIGHT.getValue("angaraki").getValue(language)

    /** The ekadashis by their Hindi names, shukla then krishna (amanta month). Marathi keeps उत्पत्ती. */
    private val EKADASHI_HINDI = mapOf(
        "chaitra" to ("कामदा" to "वरूथिनी"), "vaishakha" to ("मोहिनी" to "अपरा"),
        "jyeshtha" to ("निर्जला" to "योगिनी"), "ashadha" to ("देवशयनी" to "कामिका"),
        "shravana" to ("पुत्रदा" to "अजा"), "bhadrapada" to ("परिवर्तिनी" to "इंदिरा"),
        "ashwin" to ("पाशांकुशा" to "रमा"), "kartika" to ("प्रबोधिनी" to "उत्पन्ना"),
        "margashirsha" to ("मोक्षदा" to "सफला"), "pausha" to ("पुत्रदा" to "षट्तिला"),
        "magha" to ("जया" to "विजया"), "phalguna" to ("आमलकी" to "पापमोचनी"),
    )

    private val EKADASHI_ENGLISH = mapOf(
        "कामदा" to "kamada", "वरूथिनी" to "varuthini", "मोहिनी" to "mohini", "अपरा" to "apara",
        "निर्जला" to "nirjala", "योगिनी" to "yogini", "देवशयनी" to "devshayani", "कामिका" to "kamika",
        "पुत्रदा" to "putrada", "अजा" to "aja", "परिवर्तिनी" to "parivartini", "इंदिरा" to "indira",
        "पाशांकुशा" to "papankusha", "रमा" to "rama", "प्रबोधिनी" to "prabodhini", "उत्पन्ना" to "utpanna",
        "मोक्षदा" to "mokshada", "सफला" to "saphala", "षट्तिला" to "shattila", "जया" to "jaya",
        "विजया" to "vijaya", "आमलकी" to "amalaki", "पापमोचनी" to "papmochani", "पद्मिनी" to "padmini", "परमा" to "parama",
    )

    private val EKADASHI_WORD = byLanguage("ekadashi", "एकादशी", "एकादशी", "અગિયારસ", "ஏகாதசி", "ഏകാദശി", "ಏಕಾದಶಿ", "ఏకాదశి")

    /** "कामिका एकादशी" in [language]; the leap month's two are पद्मिनी / परमा. */
    fun ekadashi(language: PanchangLanguage, month: String, adhik: Boolean, paksha: Paksha): String {
        val hindi = if (adhik) {
            if (paksha == Paksha.SHUKLA) "पद्मिनी" else "परमा"
        } else {
            EKADASHI_HINDI[month]?.let { if (paksha == Paksha.SHUKLA) it.first else it.second }
        } ?: return EKADASHI_WORD.getValue(language)
        val word = EKADASHI_WORD.getValue(language)
        val name = when (language) {
            PanchangLanguage.ENGLISH -> EKADASHI_ENGLISH[hindi] ?: hindi
            PanchangLanguage.MARATHI -> if (hindi == "उत्पन्ना") "उत्पत्ती" else hindi
            else -> IndicTransliterator.fromDevanagari(hindi, language)
        }
        return "$name $word"
    }

    /** "(स्मार्त)" / "(वैष्णव)" — the two ways of keeping an ekadashi that falls on different days. */
    fun tradition(language: PanchangLanguage, vaishnava: Boolean): String = when (language) {
        PanchangLanguage.ENGLISH -> if (vaishnava) "(vaishnava)" else "(smarta)"
        else -> "(${IndicTransliterator.fromDevanagari(if (vaishnava) "वैष्णव" else "स्मार्त", language)})"
    }

    private val SOLAR = byLanguage("solar eclipse", "सूर्य ग्रहण", "सूर्यग्रहण", "સૂર્યગ્રહણ", "சூரிய கிரகணம்", "സൂര്യഗ്രഹണം", "ಸೂರ್ಯಗ್ರಹಣ", "సూర్యగ్రహణం")
    private val LUNAR = byLanguage("lunar eclipse", "चंद्र ग्रहण", "चंद्रग्रहण", "ચંદ્રગ્રહણ", "சந்திர கிரகணம்", "ചന്ദ്രഗ്രഹണം", "ಚಂದ್ರಗ್ರಹಣ", "చంద్రగ్రహణం")

    /** Order of kinds: total, annular, hybrid, partial. */
    private val KIND = mapOf(
        Eclipse.Kind.TOTAL to byLanguage("total", "पूर्ण", "खग्रास", "પૂર્ણ", "முழு", "പൂർണ്ണ", "ಖಗ್ರಾಸ", "సంపూర్ణ"),
        Eclipse.Kind.ANNULAR to byLanguage("annular", "वलयाकार", "कंकणाकृती", "કંકણાકૃતિ", "வளைய", "വലയ", "ಕಂಕಣ", "కంకణాకార"),
        Eclipse.Kind.HYBRID to byLanguage("hybrid", "संकर", "संकरित", "સંકર", "கலப்பு", "സങ്കര", "ಸಂಕರ", "సంకర"),
        Eclipse.Kind.PARTIAL to byLanguage("partial", "आंशिक", "खंडग्रास", "ખંડગ્રાસ", "பகுதி", "ഭാഗിക", "ಖಂಡ", "పాక్షిక"),
    )

    fun grahan(language: PanchangLanguage, kind: Eclipse.Kind, solar: Boolean): String {
        val body = (if (solar) SOLAR else LUNAR).getValue(language)
        return "${KIND.getValue(kind).getValue(language)} $body"
    }

    fun customTithi(language: PanchangLanguage, paksha: Paksha?, tithi: Int): String {
        val both = paksha == null && tithi == 15
        val name = if (both) {
            "${PanchangNames.tithiName(language, "purnima")} / ${PanchangNames.tithiName(language, "amavasya")}"
        } else {
            val key = if (tithi == 15) {
                if (paksha == Paksha.KRISHNA) "amavasya" else "purnima"
            } else {
                PanchangNames.TITHI_KEYS[tithi - 1]
            }
            PanchangNames.tithiName(language, key)
        }
        return when (paksha) {
            null -> name
            else -> "${PanchangNames.pakshaShort(language, paksha)} $name"
        }
    }
}
