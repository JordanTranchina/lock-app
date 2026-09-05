package com.nathanb.lock.util

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Languages Lock ships translations for (must match app/src/main/res/xml/locales_config.xml).
 * SYSTEM means "follow the phone's language setting".
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    FRENCH("fr"),
    GERMAN("de"),
}

/**
 * Wraps the platform per-app language API (minSdk 33), so the language choice is stored and
 * enforced by Android itself rather than by app-level preferences. Setting a locale here
 * triggers the OS to recreate the current activity with the new configuration.
 */
object LanguageHelper {

    fun getCurrent(context: Context): AppLanguage {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val tag = localeManager?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
        localeManager.applicationLocales = when (language) {
            AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
            else -> LocaleList.forLanguageTags(language.tag)
        }
    }
}
