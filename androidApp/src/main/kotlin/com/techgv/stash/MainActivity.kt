package com.techgv.stash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import com.techgv.stash.navigation.DeepLinks

// AppCompat so that per-app languages also apply below Android 13.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity's intent was handled by the first instance; a task reopened from Recents replays the
        // intent that first started it, whose link was already opened.
        val fromRecents = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromRecents) openDeepLink(intent)

        setContent {
            // Exposes Compose testTags as resource ids so UI automation (Maestro) can select by id; iOS maps them natively.
            Box(Modifier.semantics { testTagsAsResourceId = true }) { App() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDeepLink(intent)
    }

    /** `stash://reader/{id}` from a notification or widget row: hands the id to the navigation host. */
    private fun openDeepLink(intent: Intent) {
        val itemId = intent.dataString?.let(DeepLinks::itemIdOf) ?: return
        DeepLinks.open(itemId)
        // The item id only; never the URL or title.
        Log.d("Stash", "DeepLinks.pending = ${DeepLinks.pending.value}")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
