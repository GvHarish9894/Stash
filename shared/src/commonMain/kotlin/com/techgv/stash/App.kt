package com.techgv.stash

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.UserPreferences
import com.techgv.stash.core.network.platformHttpEngineFactory
import com.techgv.stash.core.platform.A11yPrefs
import com.techgv.stash.navigation.StashNavHost
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import okio.FileSystem
import org.koin.compose.koinInject

/**
 * Lets the platform match status/navigation bar icons to the app's own light or dark theme, and the window behind the
 * content to the theme's [background] (Android shows it while a screen or a web view has not drawn yet).
 */
@Composable
expect fun SystemBars(darkIcons: Boolean, background: Color)

/** The app's root: the user's theme around the navigation host. */
@Composable
fun App() {
    setSingletonImageLoaderFactory(::stashImageLoader)
    StashUserTheme { StashNavHost() }
}

/**
 * [StashTheme] from the stored preferences and the OS reduce-motion flag. Draws nothing until the preferences arrive, so
 * the default theme never flashes before the user's own. The share target uses it too.
 */
@Composable
internal fun StashUserTheme(content: @Composable () -> Unit) {
    val preferences by koinInject<SettingsRepository>().preferences.collectAsStateWithLifecycle<UserPreferences?>(null)
    val systemReduceMotion by koinInject<A11yPrefs>().reduceMotion.collectAsStateWithLifecycle()
    val prefs = preferences ?: return
    StashTheme(prefs.theme, prefs.accent, prefs.textSize, prefs.reduceMotion, systemReduceMotion, content)
}

private const val IMAGE_MEMORY_FRACTION = 0.15
private const val IMAGE_DISK_BYTES = 64L * 1024 * 1024

/**
 * Coil's HTTP client: a stalled image server gives up after these waits instead of holding a connection (the card keeps
 * its monogram tile).
 */
internal fun HttpClientConfig<*>.imageClientDefaults() {
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
    }
}

/** Thumbnails and reader images: https only, through Ktor on the platform engine, with bounded waits and caches. */
private fun stashImageLoader(context: PlatformContext): ImageLoader {
    val network = KtorNetworkFetcherFactory(httpClient = { HttpClient(platformHttpEngineFactory()) { imageClientDefaults() } })
    return ImageLoader.Builder(context)
        // Only the fetcher added below: no auto-registered one that would also load http.
        .serviceLoaderEnabled(false)
        .components {
            add(Fetcher.Factory<Uri> { data, options, loader -> if (data.scheme == "https") network.create(data, options, loader) else null })
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, IMAGE_MEMORY_FRACTION).build() }
        .diskCache { DiskCache.Builder().directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stash_images").maxSizeBytes(IMAGE_DISK_BYTES).build() }
        .build()
}
