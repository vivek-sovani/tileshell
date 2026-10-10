package com.tileshell.core.data

/**
 * The regions whose festival calendars the Panchang knows. A language picks one
 * ([PanchangLanguage.region]); English shows the pan-India set.
 */
enum class PanchangRegion { INDIA, NORTH, MAHARASHTRA, GUJARAT, TAMIL_NADU, KERALA, KARNATAKA, TELUGU }

/**
 * The languages the Panchang tile, widget and hub can be shown in. [region] is where its festivals come from.
 * The declaration order is the order every per-language table in [PanchangNames] / [PanchangStrings] is written in.
 */
enum class PanchangLanguage(val code: String, val nativeName: String, val region: PanchangRegion) {
    ENGLISH("en", "English", PanchangRegion.INDIA),
    HINDI("hi", "हिन्दी", PanchangRegion.NORTH),
    MARATHI("mr", "मराठी", PanchangRegion.MAHARASHTRA),
    GUJARATI("gu", "ગુજરાતી", PanchangRegion.GUJARAT),
    TAMIL("ta", "தமிழ்", PanchangRegion.TAMIL_NADU),
    MALAYALAM("ml", "മലയാളം", PanchangRegion.KERALA),
    KANNADA("kn", "ಕನ್ನಡ", PanchangRegion.KARNATAKA),
    TELUGU("te", "తెలుగు", PanchangRegion.TELUGU),
    ;

    companion object {
        /** The language the Panchang has always been shown in, until one is chosen. */
        val DEFAULT = MARATHI

        fun fromCode(code: String?): PanchangLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
