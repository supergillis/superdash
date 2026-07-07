package com.superdash.core.locale

/** Languages superdash ships UI translations for. [tag] is the BCP-47 language
 *  subtag used with the framework locale APIs; [nativeName] is shown in the picker. */
enum class SupportedLanguage(
    val tag: String,
    val nativeName: String,
) {
    ENGLISH("en", "English"),
    DUTCH("nl", "Nederlands"),
    FRENCH("fr", "Français"),
    ;

    companion object {
        fun fromTag(tag: String?): SupportedLanguage? {
            val language = tag?.substringBefore('-')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == language }
        }
    }
}
