package com.techgv.stash

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.techgv.stash.core.data.APPLICATION_SCOPE
import com.techgv.stash.core.domain.Command
import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.DispatchResult
import com.techgv.stash.core.domain.LinkCapture
import com.techgv.stash.core.domain.LinkCapturePolicy
import com.techgv.stash.core.model.SharedPayload
import com.techgv.stash.feature.quicksave.QuickSaveOutcome
import com.techgv.stash.feature.quicksave.QuickSaveSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import kotlin.time.Clock

/** What a share into Stash did, for the quick-save sheet. */
enum class ShareOutcome {
    Saved,

    /** The link was already on the shelf: moved to the top and unread again. */
    AlreadyOnShelf,

    /** The shared text and subject hold no http(s) link. */
    NothingToSave,

    /** The database could not store it. */
    NotSaved,
}

/**
 * The share target (`ACTION_SEND` text): saves the link with one local insert through the command bus while the
 * activity is still opening; the bus schedules the metadata fetch. Translucent and kept out of recents, with an empty
 * task affinity. A share that arrives while a sheet is open is delivered to that instance (`singleTop`, so Android
 * calls [onNewIntent] instead of only bringing the task forward): it is saved too, and the sheet restarts with its
 * outcome. Saves run in the application scope and are held by a [ViewModel], so a configuration change neither loses
 * one nor saves twice: the recreated instance waits for the latest save and shows its outcome. AppCompat so the per-app
 * language also applies below Android 13.
 */
class ShareReceiverActivity : AppCompatActivity() {
    /** The latest share's save; survives configuration changes. Earlier saves finish on their own. */
    class Save : ViewModel() {
        var outcome: Deferred<ShareOutcome>? = null

        /** Shares this sheet has received; keys the sheet so each one restarts it. */
        var shares = 0
    }

    private lateinit var save: Save
    private var showing: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open++
        save = ViewModelProvider(this)[Save::class.java]
        if (save.outcome == null && !startSave(intent)) {
            finish()
            return
        }
        showLatest()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (startSave(intent)) showLatest()
    }

    override fun onDestroy() {
        open--
        super.onDestroy()
    }

    /** Starts saving the share in [intent]; false when it is not a share. */
    private fun startSave(intent: Intent): Boolean {
        val payload = sharedPayload(intent) ?: return false
        val started = SystemClock.elapsedRealtime()
        val bus = get<CommandBus>()
        save.outcome = get<CoroutineScope>(APPLICATION_SCOPE).async {
            saveShared(payload, bus).also { Log.d(TAG, "Share: $it in ${SystemClock.elapsedRealtime() - started} ms") }
        }
        save.shares++
        return true
    }

    /** Shows the latest share's outcome once its save is done; an earlier share's outcome is no longer shown. */
    private fun showLatest() {
        val outcome = checkNotNull(save.outcome)
        val share = save.shares
        showing?.cancel()
        showing = lifecycleScope.launch { show(outcome.await(), share) }
    }

    /** The quick-save sheet over the sharing app, in the user's theme; it closes the activity when it is done. */
    private fun show(outcome: ShareOutcome, share: Int) {
        val sheet = when (outcome) {
            ShareOutcome.Saved -> QuickSaveOutcome.Saved
            ShareOutcome.AlreadyOnShelf -> QuickSaveOutcome.AlreadyOnShelf
            ShareOutcome.NothingToSave -> QuickSaveOutcome.NothingToSave
            ShareOutcome.NotSaved -> QuickSaveOutcome.NotSaved
        }
        setContent {
            StashUserTheme {
                // A sheet replaced by a newer share must not close the activity (its timer may fire before the save).
                key(share) { QuickSaveSheet(sheet, onDone = { if (share == save.shares) finish() }) }
            }
        }
    }

    private fun sharedPayload(intent: Intent): SharedPayload? {
        if (intent.action != Intent.ACTION_SEND) return null
        return SharedPayload(
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            // The sharing app when the platform tells us (it is the sender's claim, used only to label the source).
            sourceApp = referrer?.takeIf { it.scheme == "android-app" }?.host,
            at = Clock.System.now(),
        )
    }

    companion object {
        private const val TAG = "Stash"

        /** Share activities alive now (main thread only): a share does not count as the app coming to the foreground. */
        internal var open = 0
    }
}

/** Captures the link in [payload] and saves it; a link already on the shelf is re-shared, never saved twice. */
suspend fun saveShared(payload: SharedPayload, bus: CommandBus): ShareOutcome {
    val capture = LinkCapturePolicy.capture(payload) as? LinkCapture.Captured ?: return ShareOutcome.NothingToSave
    return when (val result = bus.dispatch(Command.SaveLink(capture, payload.sourceApp))) {
        is DispatchResult.Done -> if (result.reshared) ShareOutcome.AlreadyOnShelf else ShareOutcome.Saved
        is DispatchResult.Rejected -> ShareOutcome.NotSaved
    }
}
