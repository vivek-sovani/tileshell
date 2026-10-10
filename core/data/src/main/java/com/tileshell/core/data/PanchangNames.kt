package com.tileshell.core.data

/**
 * The Panchang's own vocabulary in each [PanchangLanguage]: weekdays, tithis, paksha, months, nakshatras, ayana, the
 * digits and the 12-hour words. The keys are the English / transliterated names [HinduPanchang] computes with
 * (`"somavara"`, `"dwitiya"`, `"purva phalguni"` …); every table is written in the order of [PanchangLanguage].
 *
 * Written from the languages' own panchang conventions (Gujarati uses "એકમ / બીજ / ત્રીજ" for tithis, Tamil
 * "வளர்பிறை / தேய்பிறை" for the pakshas, Telugu "పాడ్యమి / విదియ" and so on), not transliterated; a test checks
 * every table is complete. Tamil and Malayalam use Latin digits, which is how both are normally written.
 */
object PanchangNames {
    private val LANGUAGES = PanchangLanguage.entries

    /** One table: [values] are in [PanchangLanguage] order. */
    private fun table(vararg values: List<String>): Map<PanchangLanguage, List<String>> {
        require(values.size == LANGUAGES.size) { "a table needs ${LANGUAGES.size} languages, got ${values.size}" }
        return LANGUAGES.zip(values.toList()).toMap()
    }

    internal val VARA_KEYS = listOf("ravivara", "somavara", "mangalavara", "budhavara", "guruvara", "shukravara", "shanivara")

    internal val TITHI_KEYS = listOf(
        "pratipada", "dwitiya", "tritiya", "chaturthi", "panchami", "shashthi", "saptami", "ashtami",
        "navami", "dashami", "ekadashi", "dwadashi", "trayodashi", "chaturdashi", "purnima", "amavasya",
    )

    /** Chaitra first (the amanta new year), not [MONTH_NAMES]' Vaishakha-first order. */
    internal val MONTH_KEYS = listOf(
        "chaitra", "vaishakha", "jyeshtha", "ashadha", "shravana", "bhadrapada",
        "ashwin", "kartika", "margashirsha", "pausha", "magha", "phalguna",
    )

    internal val NAKSHATRA_KEYS = NAKSHATRA_NAMES

    private val VARA = table(
        listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"),
        listOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"),
        listOf("रविवार", "सोमवार", "मंगळवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"),
        listOf("રવિવાર", "સોમવાર", "મંગળવાર", "બુધવાર", "ગુરુવાર", "શુક્રવાર", "શનિવાર"),
        listOf("ஞாயிறு", "திங்கள்", "செவ்வாய்", "புதன்", "வியாழன்", "வெள்ளி", "சனி"),
        listOf("ഞായർ", "തിങ്കൾ", "ചൊവ്വ", "ബുധൻ", "വ്യാഴം", "വെള്ളി", "ശനി"),
        listOf("ಭಾನುವಾರ", "ಸೋಮವಾರ", "ಮಂಗಳವಾರ", "ಬುಧವಾರ", "ಗುರುವಾರ", "ಶುಕ್ರವಾರ", "ಶನಿವಾರ"),
        listOf("ఆదివారం", "సోమవారం", "మంగళవారం", "బుధవారం", "గురువారం", "శుక్రవారం", "శనివారం"),
    )

    /** A short "which day" label — the full name's common abbreviation, not a mechanical cut. */
    private val SHORT_VARA = table(
        listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat"),
        listOf("रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि"),
        listOf("रवि", "सोम", "मंगळ", "बुध", "गुरु", "शुक्र", "शनि"),
        listOf("રવિ", "સોમ", "મંગળ", "બુધ", "ગુરુ", "શુક્ર", "શનિ"),
        listOf("ஞா", "தி", "செ", "பு", "வி", "வெ", "ச"),
        listOf("ഞാ", "തി", "ചൊ", "ബു", "വ്യാ", "വെ", "ശ"),
        listOf("ಭಾನು", "ಸೋಮ", "ಮಂಗಳ", "ಬುಧ", "ಗುರು", "ಶುಕ್ರ", "ಶನಿ"),
        listOf("ఆది", "సోమ", "మంగళ", "బుధ", "గురు", "శుక్ర", "శని"),
    )

    private val TITHI = table(
        listOf(
            "pratipada", "dwitiya", "tritiya", "chaturthi", "panchami", "shashthi", "saptami", "ashtami",
            "navami", "dashami", "ekadashi", "dwadashi", "trayodashi", "chaturdashi", "purnima", "amavasya",
        ),
        listOf(
            "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी", "अष्टमी",
            "नवमी", "दशमी", "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा", "अमावस्या",
        ),
        listOf(
            "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी", "अष्टमी",
            "नवमी", "दशमी", "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पौर्णिमा", "अमावास्या",
        ),
        listOf(
            "એકમ", "બીજ", "ત્રીજ", "ચોથ", "પાંચમ", "છઠ", "સાતમ", "આઠમ",
            "નોમ", "દશમ", "અગિયારસ", "બારસ", "તેરસ", "ચૌદશ", "પૂનમ", "અમાસ",
        ),
        listOf(
            "பிரதமை", "துவிதியை", "திருதியை", "சதுர்த்தி", "பஞ்சமி", "சஷ்டி", "சப்தமி", "அஷ்டமி",
            "நவமி", "தசமி", "ஏகாதசி", "துவாதசி", "திரயோதசி", "சதுர்த்தசி", "பௌர்ணமி", "அமாவாசை",
        ),
        listOf(
            "പ്രതിപദം", "ദ്വിതീയ", "തൃതീയ", "ചതുർത്ഥി", "പഞ്ചമി", "ഷഷ്ഠി", "സപ്തമി", "അഷ്ടമി",
            "നവമി", "ദശമി", "ഏകാദശി", "ദ്വാദശി", "ത്രയോദശി", "ചതുർദശി", "പൗർണ്ണമി", "അമാവാസി",
        ),
        listOf(
            "ಪಾಡ್ಯ", "ಬಿದಿಗೆ", "ತದಿಗೆ", "ಚೌತಿ", "ಪಂಚಮಿ", "ಷಷ್ಠಿ", "ಸಪ್ತಮಿ", "ಅಷ್ಟಮಿ",
            "ನವಮಿ", "ದಶಮಿ", "ಏಕಾದಶಿ", "ದ್ವಾದಶಿ", "ತ್ರಯೋದಶಿ", "ಚತುರ್ದಶಿ", "ಹುಣ್ಣಿಮೆ", "ಅಮಾವಾಸ್ಯೆ",
        ),
        listOf(
            "పాడ్యమి", "విదియ", "తదియ", "చవితి", "పంచమి", "షష్ఠి", "సప్తమి", "అష్టమి",
            "నవమి", "దశమి", "ఏకాదశి", "ద్వాదశి", "త్రయోదశి", "చతుర్దశి", "పౌర్ణమి", "అమావాస్య",
        ),
    )

    /** Shukla, then krishna paksha, as a phrase. */
    private val PAKSHA = table(
        listOf("shukla paksha", "krishna paksha"),
        listOf("शुक्ल पक्ष", "कृष्ण पक्ष"),
        listOf("शुक्ल पक्ष", "कृष्ण पक्ष"),
        listOf("શુક્લ પક્ષ", "કૃષ્ણ પક્ષ"),
        listOf("வளர்பிறை", "தேய்பிறை"),
        listOf("ശുക്ലപക്ഷം", "കൃഷ്ണപക്ഷം"),
        listOf("ಶುಕ್ಲ ಪಕ್ಷ", "ಕೃಷ್ಣ ಪಕ್ಷ"),
        listOf("శుక్ల పక్షం", "కృష్ణ పక్షం"),
    )

    /** Just the paksha's own name, for choosing a tithi ("शुक्ल", "कृष्ण"). */
    private val PAKSHA_SHORT = table(
        listOf("shukla", "krishna"),
        listOf("शुक्ल", "कृष्ण"),
        listOf("शुक्ल", "कृष्ण"),
        listOf("શુક્લ", "કૃષ્ણ"),
        listOf("வளர்பிறை", "தேய்பிறை"),
        listOf("ശുക്ല", "കൃഷ്ണ"),
        listOf("ಶುಕ್ಲ", "ಕೃಷ್ಣ"),
        listOf("శుక్ల", "కృష్ణ"),
    )

    private val MONTH = table(
        listOf("chaitra", "vaishakha", "jyeshtha", "ashadha", "shravana", "bhadrapada", "ashwin", "kartika", "margashirsha", "pausha", "magha", "phalguna"),
        listOf("चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद", "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"),
        listOf("चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ", "श्रावण", "भाद्रपद", "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"),
        listOf("ચૈત્ર", "વૈશાખ", "જેઠ", "અષાઢ", "શ્રાવણ", "ભાદરવો", "આસો", "કારતક", "માગશર", "પોષ", "મહા", "ફાગણ"),
        listOf("சைத்ரம்", "வைசாகம்", "ஜ்யேஷ்டம்", "ஆஷாடம்", "ஸ்ராவணம்", "பாத்ரபதம்", "ஆஸ்வினம்", "கார்த்திகம்", "மார்கசீர்ஷம்", "பௌஷம்", "மாகம்", "பால்குணம்"),
        listOf("ചൈത്രം", "വൈശാഖം", "ജ്യേഷ്ഠം", "ആഷാഢം", "ശ്രാവണം", "ഭാദ്രപദം", "ആശ്വിനം", "കാർത്തികം", "മാർഗ്ഗശീർഷം", "പൗഷം", "മാഘം", "ഫാൽഗുനം"),
        listOf("ಚೈತ್ರ", "ವೈಶಾಖ", "ಜ್ಯೇಷ್ಠ", "ಆಷಾಢ", "ಶ್ರಾವಣ", "ಭಾದ್ರಪದ", "ಆಶ್ವಯುಜ", "ಕಾರ್ತೀಕ", "ಮಾರ್ಗಶಿರ", "ಪುಷ್ಯ", "ಮಾಘ", "ಫಾಲ್ಗುಣ"),
        listOf("చైత్రం", "వైశాఖం", "జ్యేష్ఠం", "ఆషాఢం", "శ్రావణం", "భాద్రపదం", "ఆశ్వయుజం", "కార్తీకం", "మార్గశిరం", "పుష్యం", "మాఘం", "ఫాల్గుణం"),
    )

    /** "adhik" — the leap month's prefix. */
    private val ADHIK = table(listOf("adhik"), listOf("अधिक"), listOf("अधिक"), listOf("અધિક"), listOf("அதிக"), listOf("അധിക"), listOf("ಅಧಿಕ"), listOf("అధిక"))

    private val NAKSHATRA = table(
        NAKSHATRA_NAMES,
        listOf(
            "अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशिरा", "आर्द्रा", "पुनर्वसु", "पुष्य", "आश्लेषा",
            "मघा", "पूर्वाफाल्गुनी", "उत्तराफाल्गुनी", "हस्त", "चित्रा", "स्वाती", "विशाखा", "अनुराधा", "ज्येष्ठा",
            "मूल", "पूर्वाषाढ़ा", "उत्तराषाढ़ा", "श्रवण", "धनिष्ठा", "शतभिषा", "पूर्वाभाद्रपदा", "उत्तराभाद्रपदा", "रेवती",
        ),
        listOf(
            "अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशीर्ष", "आर्द्रा", "पुनर्वसू", "पुष्य", "आश्लेषा",
            "मघा", "पूर्वा फाल्गुनी", "उत्तरा फाल्गुनी", "हस्त", "चित्रा", "स्वाती", "विशाखा", "अनुराधा", "ज्येष्ठा",
            "मूळ", "पूर्वाषाढा", "उत्तराषाढा", "श्रवण", "धनिष्ठा", "शततारका", "पूर्वाभाद्रपदा", "उत्तराभाद्रपदा", "रेवती",
        ),
        listOf(
            "અશ્વિની", "ભરણી", "કૃત્તિકા", "રોહિણી", "મૃગશીર્ષ", "આર્દ્રા", "પુનર્વસુ", "પુષ્ય", "આશ્લેષા",
            "મઘા", "પૂર્વાફાલ્ગુની", "ઉત્તરાફાલ્ગુની", "હસ્ત", "ચિત્રા", "સ્વાતિ", "વિશાખા", "અનુરાધા", "જ્યેષ્ઠા",
            "મૂળ", "પૂર્વાષાઢા", "ઉત્તરાષાઢા", "શ્રવણ", "ધનિષ્ઠા", "શતભિષા", "પૂર્વાભાદ્રપદા", "ઉત્તરાભાદ્રપદા", "રેવતી",
        ),
        listOf(
            "அசுவினி", "பரணி", "கார்த்திகை", "ரோகிணி", "மிருகசீரிடம்", "திருவாதிரை", "புனர்பூசம்", "பூசம்", "ஆயில்யம்",
            "மகம்", "பூரம்", "உத்திரம்", "அஸ்தம்", "சித்திரை", "சுவாதி", "விசாகம்", "அனுஷம்", "கேட்டை",
            "மூலம்", "பூராடம்", "உத்திராடம்", "திருவோணம்", "அவிட்டம்", "சதயம்", "பூரட்டாதி", "உத்திரட்டாதி", "ரேவதி",
        ),
        listOf(
            "അശ്വതി", "ഭരണി", "കാർത്തിക", "രോഹിണി", "മകയിരം", "തിരുവാതിര", "പുണർതം", "പൂയം", "ആയില്യം",
            "മകം", "പൂരം", "ഉത്രം", "അത്തം", "ചിത്തിര", "ചോതി", "വിശാഖം", "അനിഴം", "തൃക്കേട്ട",
            "മൂലം", "പൂരാടം", "ഉത്രാടം", "തിരുവോണം", "അവിട്ടം", "ചതയം", "പൂരുരുട്ടാതി", "ഉത്രട്ടാതി", "രേവതി",
        ),
        listOf(
            "ಅಶ್ವಿನಿ", "ಭರಣಿ", "ಕೃತ್ತಿಕಾ", "ರೋಹಿಣಿ", "ಮೃಗಶಿರ", "ಆರಿದ್ರಾ", "ಪುನರ್ವಸು", "ಪುಷ್ಯ", "ಆಶ್ಲೇಷ",
            "ಮಘ", "ಪುಬ್ಬ", "ಉತ್ತರ", "ಹಸ್ತ", "ಚಿತ್ತಾ", "ಸ್ವಾತಿ", "ವಿಶಾಖ", "ಅನುರಾಧ", "ಜೇಷ್ಠ",
            "ಮೂಲ", "ಪೂರ್ವಾಷಾಢ", "ಉತ್ತರಾಷಾಢ", "ಶ್ರವಣ", "ಧನಿಷ್ಠ", "ಶತಭಿಷ", "ಪೂರ್ವಾಭಾದ್ರಪದ", "ಉತ್ತರಾಭಾದ್ರಪದ", "ರೇವತಿ",
        ),
        listOf(
            "అశ్విని", "భరణి", "కృత్తిక", "రోహిణి", "మృగశిర", "ఆరుద్ర", "పునర్వసు", "పుష్యమి", "ఆశ్లేష",
            "మఖ", "పుబ్బ", "ఉత్తర", "హస్త", "చిత్త", "స్వాతి", "విశాఖ", "అనూరాధ", "జ్యేష్ఠ",
            "మూల", "పూర్వాషాఢ", "ఉత్తరాషాఢ", "శ్రవణం", "ధనిష్ఠ", "శతభిషం", "పూర్వాభాద్ర", "ఉత్తరాభాద్ర", "రేవతి",
        ),
    )

    private val AYANA = table(
        listOf("uttarayana", "dakshinayana"),
        listOf("उत्तरायण", "दक्षिणायन"),
        listOf("उत्तरायण", "दक्षिणायन"),
        listOf("ઉત્તરાયણ", "દક્ષિણાયન"),
        listOf("உத்தராயணம்", "தட்சிணாயனம்"),
        listOf("ഉത്തരായനം", "ദക്ഷിണായനം"),
        listOf("ಉತ್ತರಾಯಣ", "ದಕ್ಷಿಣಾಯನ"),
        listOf("ఉత్తరాయణం", "దక్షిణాయనం"),
    )

    /** The ten digits, or null where the language is written with Latin digits (English, Tamil, Malayalam). */
    private val DIGITS: Map<PanchangLanguage, String?> = mapOf(
        PanchangLanguage.ENGLISH to null,
        PanchangLanguage.HINDI to "०१२३४५६७८९",
        PanchangLanguage.MARATHI to "०१२३४५६७८९",
        PanchangLanguage.GUJARATI to "૦૧૨૩૪૫૬૭૮૯",
        PanchangLanguage.TAMIL to null,
        PanchangLanguage.MALAYALAM to null,
        PanchangLanguage.KANNADA to "೦೧೨೩೪೫೬೭೮೯",
        PanchangLanguage.TELUGU to "౦౧౨౩౪౫౬౭౮౯",
    )

    /** "am" and "pm" as the language says them, written after the time. */
    private val AM_PM = table(
        listOf("am", "pm"),
        listOf("पूर्वाह्न", "अपराह्न"),
        listOf("पूर्वाह्न", "अपराह्न"),
        listOf("સવારે", "સાંજે"),
        listOf("காலை", "மாலை"),
        listOf("രാവിലെ", "വൈകിട്ട്"),
        listOf("ಪೂರ್ವಾಹ್ನ", "ಅಪರಾಹ್ನ"),
        listOf("ఉదయం", "సాయంత్రం"),
    )

    /** [n]'s digits in the language's own numerals (`13` → `"१३"`), or plain digits where it uses Latin ones. */
    fun digits(language: PanchangLanguage, n: Int): String = digits(language, n.toString())

    fun digits(language: PanchangLanguage, text: String): String {
        val set = DIGITS[language] ?: return text
        return text.map { c -> if (c in '0'..'9') set[c - '0'] else c }.joinToString("")
    }

    /** [TithiInfo.displayNumber] in the language's numerals — 1…15 within either paksha, 30 for amavasya. */
    fun tithiNumber(language: PanchangLanguage, tithi: TithiInfo): String = digits(language, tithi.displayNumber)

    fun vara(language: PanchangLanguage, key: String): String = VARA_KEYS.indexOf(key).let { if (it < 0) key else VARA.getValue(language)[it] }

    fun shortVara(language: PanchangLanguage, key: String): String = VARA_KEYS.indexOf(key).let { if (it < 0) key else SHORT_VARA.getValue(language)[it] }

    fun tithiName(language: PanchangLanguage, key: String): String = TITHI_KEYS.indexOf(key).let { if (it < 0) key else TITHI.getValue(language)[it] }

    fun paksha(language: PanchangLanguage, paksha: Paksha): String = PAKSHA.getValue(language)[if (paksha == Paksha.SHUKLA) 0 else 1]

    fun pakshaShort(language: PanchangLanguage, paksha: Paksha): String = PAKSHA_SHORT.getValue(language)[if (paksha == Paksha.SHUKLA) 0 else 1]

    fun month(language: PanchangLanguage, key: String): String = MONTH_KEYS.indexOf(key).let { if (it < 0) key else MONTH.getValue(language)[it] }

    fun adhik(language: PanchangLanguage): String = ADHIK.getValue(language)[0]

    fun nakshatra(language: PanchangLanguage, key: String): String = NAKSHATRA_KEYS.indexOf(key).let { if (it < 0) key else NAKSHATRA.getValue(language)[it] }

    fun ayana(language: PanchangLanguage, ayana: Ayana): String = AYANA.getValue(language)[if (ayana == Ayana.UTTARAYANA) 0 else 1]

    /** "am" / "pm" in the language's words. */
    fun amPm(language: PanchangLanguage, pm: Boolean): String = AM_PM.getValue(language)[if (pm) 1 else 0]

    /** Every table's size, for the completeness test. */
    internal fun tableSizes(): Map<String, Pair<Int, Int>> = mapOf(
        "vara" to (VARA.getValue(PanchangLanguage.ENGLISH).size to VARA_KEYS.size),
        "tithi" to (TITHI.getValue(PanchangLanguage.ENGLISH).size to TITHI_KEYS.size),
        "month" to (MONTH.getValue(PanchangLanguage.ENGLISH).size to MONTH_KEYS.size),
        "nakshatra" to (NAKSHATRA.getValue(PanchangLanguage.ENGLISH).size to NAKSHATRA_KEYS.size),
    )

    internal fun allTables(): Map<String, Map<PanchangLanguage, List<String>>> = mapOf(
        "vara" to VARA, "shortVara" to SHORT_VARA, "tithi" to TITHI, "paksha" to PAKSHA, "pakshaShort" to PAKSHA_SHORT,
        "month" to MONTH, "adhik" to ADHIK, "nakshatra" to NAKSHATRA, "ayana" to AYANA, "amPm" to AM_PM,
    )
}
