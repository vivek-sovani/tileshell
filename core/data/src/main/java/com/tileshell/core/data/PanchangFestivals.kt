package com.tileshell.core.data

/** How a festival's day is found. */
sealed interface FestivalRule {
    /** A fixed lunar date: tithi 1-15 of a paksha in an amanta month (not adhika). */
    data class Lunar(val month: String, val paksha: Paksha, val tithi: Int) : FestivalRule

    /** The day the Sun enters a sidereal sign: [longitude] 0 = Mesha, 270 = Makara, 90 = Karkata, 120 = Simha. */
    data class Ingress(val longitude: Double) : FestivalRule

    /** The day the Moon is in [nakshatra] (0..26) at sunrise while the Sun is in the sign starting at [solarStart]. */
    data class StarInSolarMonth(val nakshatra: Int, val solarStart: Double) : FestivalRule
}

/**
 * A festival for the regions that keep it. [names] holds the name in each [PanchangLanguage] that writes it
 * its own way; a language without an entry gets the Hindi name in its script ([IndicTransliterator]).
 */
data class Festival(
    val english: String,
    val rule: FestivalRule,
    val regions: Set<PanchangRegion>,
    val at: ObserveAt,
    private val names: Map<PanchangLanguage, String>,
    private val hindi: String,
) {
    fun nameIn(language: PanchangLanguage): String = when (language) {
        PanchangLanguage.ENGLISH -> english
        PanchangLanguage.HINDI -> hindi
        else -> names[language] ?: IndicTransliterator.fromDevanagari(hindi, language)
    }
}

/**
 * The festivals each region keeps, found from the same lunar and solar calendar as everything else on the Panchang:
 * lunar dates by [HinduPanchang]'s tithi and amanta month, Sankranti-based ones (Pongal, Vishu, Chingam 1) by
 * the Sun's entry into a sign, and the Tamil and Malayalam star days (Thai Poosam, Onam) by the Moon's nakshatra
 * during a solar month. English and Hindi show the pan-India and North India set; every other language shows its own
 * state's. Names were written for this list, not copied from a calendar, and need a native speaker's review.
 */
object PanchangFestivals {
    private val R = PanchangRegion.entries.toSet()
    private val INDIA = PanchangRegion.INDIA
    private val NORTH = PanchangRegion.NORTH
    private val MAH = PanchangRegion.MAHARASHTRA
    private val GUJ = PanchangRegion.GUJARAT
    private val TN = PanchangRegion.TAMIL_NADU
    private val KER = PanchangRegion.KERALA
    private val KAR = PanchangRegion.KARNATAKA
    private val TEL = PanchangRegion.TELUGU

    private fun regions(vararg r: PanchangRegion) = r.toSet()

    private fun lunar(month: String, paksha: Paksha, tithi: Int) = FestivalRule.Lunar(month, paksha, tithi)
    private val S = Paksha.SHUKLA
    private val K = Paksha.KRISHNA

    /** One entry. Marathi defaults to the Hindi name; the other languages default to it in their own script. */
    private fun f(
        english: String,
        rule: FestivalRule,
        where: Set<PanchangRegion>,
        hi: String,
        mr: String = hi,
        at: ObserveAt = ObserveAt.SUNRISE,
        gu: String? = null,
        ta: String? = null,
        ml: String? = null,
        kn: String? = null,
        te: String? = null,
    ): Festival {
        val names = buildMap {
            put(PanchangLanguage.MARATHI, mr)
            gu?.let { put(PanchangLanguage.GUJARATI, it) }
            ta?.let { put(PanchangLanguage.TAMIL, it) }
            ml?.let { put(PanchangLanguage.MALAYALAM, it) }
            kn?.let { put(PanchangLanguage.KANNADA, it) }
            te?.let { put(PanchangLanguage.TELUGU, it) }
        }
        return Festival(english, rule, where, at, names, hi)
    }

    val ALL: List<Festival> = listOf(
        // — new year and spring
        f("gudi padwa · ugadi", lunar("chaitra", S, 1), regions(INDIA), "गुढी पाडवा · उगादि", mr = "गुढीपाडवा"),
        f("chaitra navratri · nav samvatsar", lunar("chaitra", S, 1), regions(NORTH), "चैत्र नवरात्रि · नव संवत्सर"),
        f("gudi padwa", lunar("chaitra", S, 1), regions(MAH), "गुढीपाडवा"),
        f("ugadi", lunar("chaitra", S, 1), regions(KAR), "उगादि", kn = "ಯುಗಾದಿ"),
        f("ugadi", lunar("chaitra", S, 1), regions(TEL), "उगादि", te = "ఉగాది"),
        f("ram navami", lunar("chaitra", S, 9), R, "राम नवमी", at = ObserveAt.NOON, gu = "રામ નવમી", ta = "ராம நவமி", ml = "ശ്രീരാമനവമി", kn = "ರಾಮ ನವಮಿ", te = "శ్రీరామ నవమి"),
        f("hanuman jayanti", lunar("chaitra", S, 15), regions(INDIA, NORTH, MAH, GUJ, TEL), "हनुमान जयंती", gu = "હનુમાન જયંતી", te = "హనుమాన్ జయంతి"),
        f("akshaya tritiya", lunar("vaishakha", S, 3), R, "अक्षय तृतीया", gu = "અક્ષય તૃતીયા", ta = "அட்சய திருதியை", ml = "അക്ഷയതൃതീയ", kn = "ಅಕ್ಷಯ ತೃತೀಯ", te = "అక్షయ తృతీయ"),
        f("vat purnima", lunar("jyeshtha", S, 15), regions(INDIA, MAH, GUJ), "वट पूर्णिमा", mr = "वटपौर्णिमा", gu = "વટ સાવિત્રી"),
        f("rath yatra", lunar("ashadha", S, 2), regions(INDIA, NORTH, GUJ), "जगन्नाथ रथ यात्रा", gu = "રથયાત્રા"),
        f("ashadhi ekadashi", lunar("ashadha", S, 11), regions(INDIA, NORTH, MAH, GUJ, KAR, TEL), "देवशयनी एकादशी", mr = "आषाढी एकादशी", gu = "દેવપોઢી અગિયારસ", kn = "ಆಷಾಢ ಏಕಾದಶಿ", te = "తొలి ఏకాదశి"),
        f("guru purnima", lunar("ashadha", S, 15), R, "गुरु पूर्णिमा", mr = "गुरुपौर्णिमा", gu = "ગુરુ પૂર્ણિમા", ta = "குரு பூர்ணிமா", ml = "ഗുരുപൂർണ്ണിമ", kn = "ಗುರು ಪೂರ್ಣಿಮೆ", te = "గురు పౌర్ణమి"),

        // — shravana
        f("hariyali teej", lunar("shravana", S, 3), regions(INDIA, NORTH), "हरियाली तीज"),
        f("nag panchami", lunar("shravana", S, 5), regions(INDIA, NORTH, MAH, GUJ, KAR), "नाग पंचमी", mr = "नागपंचमी", gu = "નાગ પાંચમ", kn = "ನಾಗರ ಪಂಚಮಿ"),
        f("raksha bandhan", lunar("shravana", S, 15), regions(INDIA, NORTH, MAH, GUJ, KAR, TEL), "रक्षाबंधन", gu = "રક્ષાબંધન", kn = "ರಕ್ಷಾ ಬಂಧನ", te = "రాఖీ పౌర్ణమి"),
        f("avani avittam", lunar("shravana", S, 15), regions(TN), "आवणि अवित्तम", ta = "ஆவணி அவிட்டம்"),
        f("janmashtami", lunar("shravana", K, 8), R, "कृष्ण जन्माष्टमी", mr = "श्रीकृष्ण जन्माष्टमी", at = ObserveAt.MIDNIGHT, gu = "જન્માષ્ટમી", ta = "கோகுலாஷ்டமி", ml = "അഷ്ടമിരോഹിണി", kn = "ಕೃಷ್ಣ ಜನ್ಮಾಷ್ಟಮಿ", te = "కృష్ణాష్టమి"),

        // — bhadrapada
        f("hartalika teej", lunar("bhadrapada", S, 3), regions(NORTH), "हरतालिका तीज"),
        f("gowri habba", lunar("bhadrapada", S, 3), regions(KAR), "गौरी हब्बा", kn = "ಗೌರಿ ಹಬ್ಬ"),
        f("ganesh chaturthi", lunar("bhadrapada", S, 4), R, "गणेश चतुर्थी", at = ObserveAt.NOON, gu = "ગણેશ ચતુર્થી", ta = "விநாயகர் சதுர்த்தி", ml = "ഗണേശ ചതുർത്ഥി", kn = "ಗಣೇಶ ಚತುರ್ಥಿ", te = "వినాయక చవితి"),
        f("anant chaturdashi", lunar("bhadrapada", S, 14), regions(INDIA, NORTH, MAH, GUJ, KAR, TEL), "अनंत चतुर्दशी", gu = "અનંત ચતુર્દશી", kn = "ಅನಂತ ಚತುರ್ದಶಿ", te = "అనంత చతుర్దశి"),
        f("bathukamma begins", lunar("bhadrapada", K, 15), regions(TEL), "बतुकम्मा आरंभ", te = "బతుకమ్మ ప్రారంభం"),

        // — ashwin: navratri, dussehra, diwali
        f("navratri begins", lunar("ashwin", S, 1), regions(INDIA), "शारदीय नवरात्रि आरंभ", mr = "घटस्थापना"),
        f("navratri begins", lunar("ashwin", S, 1), regions(NORTH), "शारदीय नवरात्रि आरंभ"),
        f("ghatasthapana · navratri", lunar("ashwin", S, 1), regions(MAH), "घटस्थापना"),
        f("navratri begins", lunar("ashwin", S, 1), regions(GUJ), "नवरात्रि आरंभ", gu = "નવરાત્રિ પ્રારંભ"),
        f("navaratri begins", lunar("ashwin", S, 1), regions(TN), "नवरात्रि आरंभ", ta = "நவராத்திரி ஆரம்பம்"),
        f("navaratri begins", lunar("ashwin", S, 1), regions(KER), "नवरात्रि आरंभ", ml = "നവരാത്രി ആരംഭം"),
        f("navaratri begins", lunar("ashwin", S, 1), regions(KAR), "नवरात्रि आरंभ", kn = "ನವರಾತ್ರಿ ಆರಂಭ"),
        f("navratri begins", lunar("ashwin", S, 1), regions(TEL), "नवरात्रि आरंभ", te = "నవరాత్రులు ప్రారంభం"),
        f("saddula bathukamma", lunar("ashwin", S, 8), regions(TEL), "सद्दुला बतुकम्मा", te = "సద్దుల బతుకమ్మ"),
        f("durga ashtami", lunar("ashwin", S, 8), regions(INDIA, NORTH), "दुर्गा अष्टमी"),
        f("maha navami · ayudha puja", lunar("ashwin", S, 9), regions(INDIA), "महानवमी · आयुध पूजा", ta = "ஆயுத பூஜை"),
        f("maha navami", lunar("ashwin", S, 9), regions(NORTH), "महानवमी"),
        f("ayudha puja", lunar("ashwin", S, 9), regions(TN), "आयुध पूजा", ta = "ஆயுத பூஜை"),
        f("maha navami", lunar("ashwin", S, 9), regions(KER), "महानवमी", ml = "മഹാനവമി"),
        f("ayudha puja", lunar("ashwin", S, 9), regions(KAR), "आयुध पूजा", kn = "ಆಯುಧ ಪೂಜೆ"),
        f("maha navami", lunar("ashwin", S, 9), regions(TEL), "महानवमी", te = "మహార్నవమి"),
        f("dussehra", lunar("ashwin", S, 10), R, "दशहरा", mr = "दसरा", at = ObserveAt.AFTERNOON, gu = "દશેરા", ta = "விஜயதசமி", ml = "വിജയദശമി", kn = "ವಿಜಯದಶಮಿ · ದಸರಾ", te = "విజయదశమి · దసరా"),
        f("sharad purnima", lunar("ashwin", S, 15), regions(INDIA, NORTH, GUJ), "शरद पूर्णिमा", at = ObserveAt.MIDNIGHT, gu = "શરદ પૂનમ"),
        f("kojagiri purnima", lunar("ashwin", S, 15), regions(MAH), "कोजागिरी पौर्णिमा", at = ObserveAt.MIDNIGHT),
        f("karva chauth", lunar("ashwin", K, 4), regions(INDIA, NORTH), "करवा चौथ", at = ObserveAt.MOONRISE),
        f("ahoi ashtami", lunar("ashwin", K, 8), regions(NORTH), "अहोई अष्टमी"),
        f("dhanteras", lunar("ashwin", K, 13), regions(INDIA, NORTH, MAH, GUJ, KAR, TEL), "धनतेरस", mr = "धनत्रयोदशी", at = ObserveAt.EVENING, gu = "ધનતેરસ", kn = "ಧನತ್ರಯೋದಶಿ", te = "ధన త్రయోదశి"),
        f("narak chaturdashi", lunar("ashwin", K, 14), regions(INDIA), "नरक चतुर्दशी"),
        f("choti diwali · narak chaturdashi", lunar("ashwin", K, 14), regions(NORTH), "छोटी दिवाली · नरक चतुर्दशी"),
        f("narak chaturdashi", lunar("ashwin", K, 14), regions(MAH), "नरक चतुर्दशी", mr = "नरक चतुर्दशी"),
        f("kali chaudas", lunar("ashwin", K, 14), regions(GUJ), "काली चौदस", gu = "કાળી ચૌદશ"),
        f("deepavali", lunar("ashwin", K, 14), regions(TN), "दीपावली", ta = "தீபாவளி"),
        f("deepavali", lunar("ashwin", K, 14), regions(KER), "दीपावली", ml = "ദീപാവലി"),
        f("naraka chaturdashi", lunar("ashwin", K, 14), regions(KAR), "नरक चतुर्दशी", kn = "ನರಕ ಚತುರ್ದಶಿ"),
        f("naraka chaturdashi", lunar("ashwin", K, 14), regions(TEL), "नरक चतुर्दशी", te = "నరక చతుర్దశి"),
        f("lakshmi pujan · diwali", lunar("ashwin", K, 15), regions(INDIA), "दीपावली · लक्ष्मी पूजन", at = ObserveAt.EVENING),
        f("diwali · lakshmi puja", lunar("ashwin", K, 15), regions(NORTH), "दीपावली · लक्ष्मी पूजन", at = ObserveAt.EVENING),
        f("lakshmi pujan · diwali", lunar("ashwin", K, 15), regions(MAH), "लक्ष्मीपूजन", at = ObserveAt.EVENING),
        f("diwali · lakshmi puja", lunar("ashwin", K, 15), regions(GUJ), "दिवाली · लक्ष्मी पूजन", at = ObserveAt.EVENING, gu = "દિવાળી · લક્ષ્મીપૂજન"),
        f("deepavali amavasya", lunar("ashwin", K, 15), regions(KAR), "दीपावली अमावस्या", at = ObserveAt.EVENING, kn = "ದೀಪಾವಳಿ ಅಮಾವಾಸ್ಯೆ"),
        f("deepavali", lunar("ashwin", K, 15), regions(TEL), "दीपावली", at = ObserveAt.EVENING, te = "దీపావళి"),

        // — kartika
        f("diwali padwa · govardhan puja", lunar("kartika", S, 1), regions(INDIA), "गोवर्धन पूजा · बलिप्रतिपदा"),
        f("govardhan puja", lunar("kartika", S, 1), regions(NORTH), "गोवर्धन पूजा"),
        f("diwali padwa", lunar("kartika", S, 1), regions(MAH), "बलिप्रतिपदा"),
        f("gujarati new year", lunar("kartika", S, 1), regions(GUJ), "गुजराती नववर्ष", gu = "નૂતન વર્ષ (બેસતું વર્ષ)"),
        f("bali padyami", lunar("kartika", S, 1), regions(KAR), "बलिपाड्यमी", kn = "ಬಲಿಪಾಡ್ಯಮಿ"),
        f("bhai dooj", lunar("kartika", S, 2), regions(INDIA, NORTH), "भाई दूज"),
        f("bhaubeej", lunar("kartika", S, 2), regions(MAH), "भाऊबीज"),
        f("bhai beej", lunar("kartika", S, 2), regions(GUJ), "भाई बीज", gu = "ભાઈ બીજ"),
        f("labh pancham", lunar("kartika", S, 5), regions(GUJ), "लाभ पंचमी", gu = "લાભ પાંચમ"),
        f("chhath puja", lunar("kartika", S, 6), regions(INDIA, NORTH), "छठ पूजा"),
        f("soorasamharam", lunar("kartika", S, 6), regions(TN), "सूरसंहारम्", ta = "சூரசம்ஹாரம்"),
        f("dev uthani ekadashi", lunar("kartika", S, 11), regions(INDIA, NORTH), "देवउठनी एकादशी"),
        f("kartiki ekadashi", lunar("kartika", S, 11), regions(MAH), "कार्तिकी एकादशी"),
        f("dev uthi ekadashi", lunar("kartika", S, 11), regions(GUJ), "देवउठी एकादशी", gu = "દેવ ઊઠી અગિયારસ"),
        f("prabodhini ekadashi", lunar("kartika", S, 11), regions(KAR), "प्रबोधिनी एकादशी", kn = "ಪ್ರಬೋಧಿನಿ ಏಕಾದಶಿ"),
        f("prabodhini ekadashi", lunar("kartika", S, 11), regions(TEL), "प्रबोधिनी एकादशी", te = "ప్రబోధిని ఏకాదశి"),
        f("tulsi vivah", lunar("kartika", S, 12), regions(INDIA, NORTH, MAH, GUJ), "तुलसी विवाह", mr = "तुळशी विवाह", gu = "તુલસી વિવાહ"),
        f("kartik purnima · dev diwali", lunar("kartika", S, 15), regions(INDIA), "कार्तिक पूर्णिमा · देव दिवाली"),
        f("dev diwali · kartik purnima", lunar("kartika", S, 15), regions(NORTH), "देव दिवाली · कार्तिक पूर्णिमा"),
        f("tripurari purnima", lunar("kartika", S, 15), regions(MAH), "त्रिपुरारी पौर्णिमा"),
        f("kartik purnima", lunar("kartika", S, 15), regions(GUJ), "कार्तिक पूर्णिमा", gu = "કારતકી પૂનમ"),
        f("karthigai deepam", lunar("kartika", S, 15), regions(TN), "कार्तिगै दीपम", ta = "கார்த்திகை தீபம்"),
        f("karthika vilakku", lunar("kartika", S, 15), regions(KER), "कार्तिक विळक्कु", ml = "കാർത്തിക വിളക്ക്"),
        f("karthika deepotsava", lunar("kartika", S, 15), regions(KAR), "कार्तिक दीपोत्सव", kn = "ಕಾರ್ತಿಕ ದೀಪೋತ್ಸವ"),
        f("karthika pournami", lunar("kartika", S, 15), regions(TEL), "कार्तिक पूर्णिमा", te = "కార్తీక పౌర్ణమి"),

        // — margashirsha, pausha, magha, phalguna
        f("subramanya shashti", lunar("margashirsha", S, 6), regions(KAR), "सुब्रह्मण्य षष्ठी", kn = "ಸುಬ್ರಹ್ಮಣ್ಯ ಷಷ್ಠಿ"),
        f("subramanya shashti", lunar("margashirsha", S, 6), regions(TEL), "सुब्रह्मण्य षष्ठी", te = "సుబ్రహ్మణ్య షష్ఠి"),
        f("datta jayanti", lunar("margashirsha", S, 15), regions(INDIA, MAH, GUJ, KAR), "दत्त जयंती", at = ObserveAt.EVENING, gu = "દત્ત જયંતી", kn = "ದತ್ತ ಜಯಂತಿ"),
        f("vaikuntha ekadashi", lunar("pausha", S, 11), regions(INDIA), "वैकुंठ एकादशी"),
        f("vaikunta ekadashi", lunar("pausha", S, 11), regions(TN), "वैकुंठ एकादशी", ta = "வைகுண்ட ஏகாதசி"),
        f("vaikuntha ekadashi", lunar("pausha", S, 11), regions(KER), "वैकुंठ एकादशी", ml = "വൈകുണ്ഠ ഏകാദശി"),
        f("vaikuntha ekadashi", lunar("pausha", S, 11), regions(KAR), "वैकुंठ एकादशी", kn = "ವೈಕುಂಠ ಏಕಾದಶಿ"),
        f("vaikuntha ekadashi", lunar("pausha", S, 11), regions(TEL), "वैकुंठ एकादशी", te = "వైకుంఠ ఏకాదశి"),
        f("vasant panchami", lunar("magha", S, 5), regions(INDIA, NORTH, MAH, GUJ, KAR), "बसंत पंचमी", mr = "वसंत पंचमी", gu = "વસંત પંચમી", kn = "ವಸಂತ ಪಂಚಮಿ"),
        f("sri panchami", lunar("magha", S, 5), regions(TEL), "श्री पंचमी", te = "శ్రీ పంచమి"),
        f("ratha saptami", lunar("magha", S, 7), regions(KAR), "रथ सप्तमी", kn = "ರಥ ಸಪ್ತಮಿ"),
        f("ratha saptami", lunar("magha", S, 7), regions(TEL), "रथ सप्तमी", te = "రథ సప్తమి"),
        f("ratha saptami", lunar("magha", S, 7), regions(TN), "रथ सप्तमी", ta = "ரத சப்தமி"),
        f("holika dahan", lunar("phalguna", S, 15), regions(INDIA, NORTH), "होलिका दहन", at = ObserveAt.EVENING),
        f("holi", lunar("phalguna", S, 15), regions(MAH), "होळी", at = ObserveAt.EVENING),
        f("holi", lunar("phalguna", S, 15), regions(GUJ), "होली", at = ObserveAt.EVENING, gu = "હોળી"),
        f("kamana habba · holi", lunar("phalguna", S, 15), regions(KAR), "कामन हब्बा", at = ObserveAt.EVENING, kn = "ಕಾಮನ ಹಬ್ಬ · ಹೋಳಿ"),
        f("holi · kamadahanam", lunar("phalguna", S, 15), regions(TEL), "होली", at = ObserveAt.EVENING, te = "హోలీ · కామదహనం"),
        f("holi · dhulandi", lunar("phalguna", K, 1), regions(INDIA, NORTH), "होली (धुलेंडी)"),
        f("dhulivandan", lunar("phalguna", K, 1), regions(MAH), "धूलिवंदन"),
        f("dhuleti", lunar("phalguna", K, 1), regions(GUJ), "धुलेटी", gu = "ધુળેટી"),
        f("rang panchami", lunar("phalguna", K, 5), regions(MAH), "रंग पंचमी", mr = "रंगपंचमी"),

        // — the Sun's entry into a sign
        f("makar sankranti", FestivalRule.Ingress(270.0), regions(INDIA), "मकर संक्रांति"),
        f("makar sankranti", FestivalRule.Ingress(270.0), regions(NORTH), "मकर संक्रांति"),
        f("makar sankranti", FestivalRule.Ingress(270.0), regions(MAH), "मकर संक्रांती"),
        f("uttarayan", FestivalRule.Ingress(270.0), regions(GUJ), "उत्तरायण", gu = "ઉત્તરાયણ (મકરસંક્રાંતિ)"),
        f("pongal", FestivalRule.Ingress(270.0), regions(TN), "पोंगल", ta = "தைப் பொங்கல்"),
        f("makaravilakku", FestivalRule.Ingress(270.0), regions(KER), "मकर संक्रांति", ml = "മകര സംക്രാന്തി · മകരവിളക്ക്"),
        f("makara sankranti", FestivalRule.Ingress(270.0), regions(KAR), "मकर संक्रांति", kn = "ಮಕರ ಸಂಕ್ರಾಂತಿ"),
        f("sankranti", FestivalRule.Ingress(270.0), regions(TEL), "संक्रांति", te = "సంక్రాంతి"),
        f("baisakhi", FestivalRule.Ingress(0.0), regions(NORTH), "बैसाखी"),
        f("puthandu · tamil new year", FestivalRule.Ingress(0.0), regions(TN), "पुथांडु", ta = "தமிழ்ப் புத்தாண்டு"),
        f("vishu", FestivalRule.Ingress(0.0), regions(KER), "विषु", ml = "വിഷു"),
        f("karkidakam begins", FestivalRule.Ingress(90.0), regions(KER), "कर्कटकम् आरंभ", ml = "കർക്കിടകം ആരംഭം"),
        f("chingam 1 · malayalam new year", FestivalRule.Ingress(120.0), regions(KER), "चिंगम् १", ml = "ചിങ്ങം ൧ · മലയാള പുതുവർഷം"),

        // — star days: a nakshatra during a solar month
        f("thai poosam", FestivalRule.StarInSolarMonth(7, 270.0), regions(TN), "थाई पूसम", ta = "தைப்பூசம்"),
        f("vaikasi visakam", FestivalRule.StarInSolarMonth(15, 30.0), regions(TN), "वैकासी विसाखम", ta = "வைகாசி விசாகம்"),
        f("aadi pooram", FestivalRule.StarInSolarMonth(10, 90.0), regions(TN), "आडि पूरम", ta = "ஆடிப்பூரம்"),
        f("arudra darshanam", FestivalRule.StarInSolarMonth(5, 240.0), regions(TN), "आरुद्र दर्शन", ta = "திருவாதிரை (ஆருத்ரா தரிசனம்)"),
        f("panguni uthiram", FestivalRule.StarInSolarMonth(11, 330.0), regions(TN), "पंगुनि उत्तिरम", ta = "பங்குனி உத்திரம்"),
        f("thiruvathira", FestivalRule.StarInSolarMonth(5, 240.0), regions(KER), "तिरुवातिरा", ml = "തിരുവാതിര"),
        f("thrissur pooram", FestivalRule.StarInSolarMonth(10, 0.0), regions(KER), "त्रिशूर पूरम", ml = "തൃശ്ശൂർ പൂരം"),
        f("onam · thiruvonam", FestivalRule.StarInSolarMonth(21, 120.0), regions(KER), "ओणम", ml = "തിരുവോണം"),
    )

    /** The festivals [region] keeps. */
    fun forRegion(region: PanchangRegion): List<Festival> = ALL.filter { region in it.regions }

    /** The festivals shown in [language]: its region's. */
    fun forLanguage(language: PanchangLanguage): List<Festival> = forRegion(language.region)
}
