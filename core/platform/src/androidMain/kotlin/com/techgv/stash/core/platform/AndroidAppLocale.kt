package com.techgv.stash.core.platform

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app language: the platform `LocaleManager` on Android 13+, which also owns the value (the user can change it in
 * system Settings). Below 13, AppCompat's backport: it stores the choice itself (the `AppLocalesMetadataHolderService`
 * entry in the app manifest) and applies it to AppCompat activities only. AppCompat's getter answers only after an
 * AppCompat activity was created and its store is a restricted API, so below 13 this class also keeps the tag in its
 * own preferences; there the app is the only place the language can change, so that record is the truth.
 * On 13+ AppCompat would ignore [set] while no AppCompat activity exists, so the platform is called directly.
 */
class AndroidAppLocale(private val context: Context) : AppLocale {
    override val switching = LanguageSwitching.InApp

    override val current: String?
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().ifEmpty { null }
            } else {
                storedTag(context)
            }
        } catch (e: RuntimeException) {
            null
        }

    override fun set(languageTag: String?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
            } else {
                preferences(context).edit().putString(KEY_TAG, languageTag).commit()
                AppCompatDelegate.setApplicationLocales(
                    if (languageTag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(languageTag),
                )
            }
        } catch (e: RuntimeException) {
            // Never throws; the language stays as it was.
        }
    }

    companion object {
        private const val PREFERENCES = "stash_app_locale"
        private const val KEY_TAG = "tag"

        /**
         * Below Android 13 a process started without an activity (an alarm, a worker) runs in the phone's language,
         * so text rendered there (reminders) would ignore the app language. Call at process start, before anything
         * renders: makes the stored app language the process default. Android 13+ already does this itself.
         */
        fun applyToProcess(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val tag = storedTag(context) ?: return
            Locale.setDefault(Locale.forLanguageTag(tag))
        }

        private fun storedTag(context: Context): String? = preferences(context).getString(KEY_TAG, null)

        private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }
}
