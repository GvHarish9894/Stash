package com.techgv.stash.core.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS owns the per-app language: it offers a Language row on the app's Settings page because the app declares
 * `CFBundleLocalizations` (en, es, hi, ta). [set] opens that page. [current] is the per-app choice iOS stores as
 * `AppleLanguages` in the app's own defaults domain ([defaultsDomain]), or null when the app follows the phone. Reading
 * only the app's own domain keeps the UserDefaults access within the privacy manifest's reason CA92.1.
 */
class IosAppLocale(private val defaultsDomain: String? = NSBundle.mainBundle.bundleIdentifier) : AppLocale {
    override val switching: LanguageSwitching = LanguageSwitching.SystemSettings

    override val current: String?
        get() {
            val domain = defaultsDomain ?: return null
            val languages = NSUserDefaults.standardUserDefaults.persistentDomainForName(domain)?.get("AppleLanguages") as? List<*>
            return languages?.firstOrNull() as? String
        }

    override fun set(languageTag: String?) {
        val settings = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(settings, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
