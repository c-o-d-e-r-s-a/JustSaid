package com.justsaid.app.stt

import java.util.Locale

/**
 * Every ISO code whisper.cpp supports (`g_lang` in whisper.cpp). Keep in sync when
 * vendored whisper updates that table.
 */
object WhisperLanguageCatalog {

    data class Entry(val code: String, val englishName: String)

    /** Whisper's canonical English names (from `g_lang`), used when [displayName] has no Locale label. */
    val entries: List<Entry> = listOf(
        Entry("en", "English"),
        Entry("zh", "Chinese"),
        Entry("de", "German"),
        Entry("es", "Spanish"),
        Entry("ru", "Russian"),
        Entry("ko", "Korean"),
        Entry("fr", "French"),
        Entry("ja", "Japanese"),
        Entry("pt", "Portuguese"),
        Entry("tr", "Turkish"),
        Entry("pl", "Polish"),
        Entry("ca", "Catalan"),
        Entry("nl", "Dutch"),
        Entry("ar", "Arabic"),
        Entry("sv", "Swedish"),
        Entry("it", "Italian"),
        Entry("id", "Indonesian"),
        Entry("hi", "Hindi"),
        Entry("fi", "Finnish"),
        Entry("vi", "Vietnamese"),
        Entry("he", "Hebrew"),
        Entry("uk", "Ukrainian"),
        Entry("el", "Greek"),
        Entry("ms", "Malay"),
        Entry("cs", "Czech"),
        Entry("ro", "Romanian"),
        Entry("da", "Danish"),
        Entry("hu", "Hungarian"),
        Entry("ta", "Tamil"),
        Entry("no", "Norwegian"),
        Entry("th", "Thai"),
        Entry("ur", "Urdu"),
        Entry("hr", "Croatian"),
        Entry("bg", "Bulgarian"),
        Entry("lt", "Lithuanian"),
        Entry("la", "Latin"),
        Entry("mi", "Maori"),
        Entry("ml", "Malayalam"),
        Entry("cy", "Welsh"),
        Entry("sk", "Slovak"),
        Entry("te", "Telugu"),
        Entry("fa", "Persian"),
        Entry("lv", "Latvian"),
        Entry("bn", "Bengali"),
        Entry("sr", "Serbian"),
        Entry("az", "Azerbaijani"),
        Entry("sl", "Slovenian"),
        Entry("kn", "Kannada"),
        Entry("et", "Estonian"),
        Entry("mk", "Macedonian"),
        Entry("br", "Breton"),
        Entry("eu", "Basque"),
        Entry("is", "Icelandic"),
        Entry("hy", "Armenian"),
        Entry("ne", "Nepali"),
        Entry("mn", "Mongolian"),
        Entry("bs", "Bosnian"),
        Entry("kk", "Kazakh"),
        Entry("sq", "Albanian"),
        Entry("sw", "Swahili"),
        Entry("gl", "Galician"),
        Entry("mr", "Marathi"),
        Entry("pa", "Punjabi"),
        Entry("si", "Sinhala"),
        Entry("km", "Khmer"),
        Entry("sn", "Shona"),
        Entry("yo", "Yoruba"),
        Entry("so", "Somali"),
        Entry("af", "Afrikaans"),
        Entry("oc", "Occitan"),
        Entry("ka", "Georgian"),
        Entry("be", "Belarusian"),
        Entry("tg", "Tajik"),
        Entry("sd", "Sindhi"),
        Entry("gu", "Gujarati"),
        Entry("am", "Amharic"),
        Entry("yi", "Yiddish"),
        Entry("lo", "Lao"),
        Entry("uz", "Uzbek"),
        Entry("fo", "Faroese"),
        Entry("ht", "Haitian Creole"),
        Entry("ps", "Pashto"),
        Entry("tk", "Turkmen"),
        Entry("nn", "Nynorsk"),
        Entry("mt", "Maltese"),
        Entry("sa", "Sanskrit"),
        Entry("lb", "Luxembourgish"),
        Entry("my", "Myanmar"),
        Entry("bo", "Tibetan"),
        Entry("tl", "Tagalog"),
        Entry("mg", "Malagasy"),
        Entry("as", "Assamese"),
        Entry("tt", "Tatar"),
        Entry("haw", "Hawaiian"),
        Entry("ln", "Lingala"),
        Entry("ha", "Hausa"),
        Entry("ba", "Bashkir"),
        Entry("jw", "Javanese"),
        Entry("su", "Sundanese"),
        Entry("yue", "Cantonese"),
    )

    val supportedCodes: Set<String> = entries.map { it.code }.toSet()

    private val englishByCode = entries.associate { it.code to it.englishName }

    /** User-visible label in the phone's locale when Android knows the language. */
    fun displayName(code: String, inLocale: Locale = Locale.getDefault()): String {
        englishByCode[code] ?: return code
        val platform = when (code) {
            "yue" -> null
            "haw", "jw", "su" -> null
            else -> {
                val tag = Locale.forLanguageTag(code)
                tag.getDisplayName(inLocale).takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            }
        }
        return platform ?: englishByCode[code]!!
    }
}
