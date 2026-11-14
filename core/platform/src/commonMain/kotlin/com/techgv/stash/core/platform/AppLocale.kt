package com.techgv.stash.core.platform

/** How this platform changes the app's language. */
enum class LanguageSwitching {
    /** [AppLocale.set] applies the language at once (Android per-app locales; the activity recreates). */
    InApp,

    /** The OS owns the per-app language (iOS): [AppLocale.set] opens the app's page in system Settings. */
    SystemSettings,
}

/**
 * The app's language. The UI reads [switching] to word the language row: a picker for [LanguageSwitching.InApp],
 * a row that opens Settings for [LanguageSwitching.SystemSettings].
 */
interface AppLocale {
    val switching: LanguageSwitching

    /**
     * BCP 47 tag of the app's language now, or null when it follows the phone. The OS owns this value (the user can
     * change it in system Settings), so read it each time the language row is shown rather than trusting a stored
     * preference.
     */
    val current: String?

    /**
     * Android: switches to [languageTag] (BCP 47, for example "hi"), or to the phone's language when null.
     * iOS: opens the app's system Settings page; [languageTag] is ignored. Call on the main thread; never throws.
     */
    fun set(languageTag: String?)
}
