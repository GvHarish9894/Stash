package com.techgv.stash.platform

import com.techgv.stash.core.platform.IosAppLocale
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosAppLocaleTest {
    private val domain = "com.techgv.stash.test.locale-" + NSUUID().UUIDString
    private val defaults = NSUserDefaults.standardUserDefaults

    @AfterTest
    fun removeDomain() = defaults.removePersistentDomainForName(domain)

    @Test
    fun currentIsThePerAppLanguageIosStoresInTheAppsOwnDomain() {
        defaults.setPersistentDomain(mapOf("AppleLanguages" to listOf("hi")), forName = domain)
        assertEquals("hi", IosAppLocale(domain).current)
    }

    @Test
    fun currentIsNullWhenTheAppFollowsThePhone() {
        assertNull(IosAppLocale(domain).current)
        defaults.setPersistentDomain(mapOf("SomethingElse" to true), forName = domain)
        assertNull(IosAppLocale(domain).current)
        assertNull(IosAppLocale(defaultsDomain = null).current)
    }
}
