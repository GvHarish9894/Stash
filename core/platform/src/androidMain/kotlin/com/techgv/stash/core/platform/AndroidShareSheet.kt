package com.techgv.stash.core.platform

import android.content.Context
import android.content.Intent

/** An `ACTION_SEND` chooser for the link, with the title as subject and as the chooser's preview title. */
class AndroidShareSheet(private val context: Context) : ShareSheet {
    override fun share(url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TITLE, title)
        val chooser = Intent.createChooser(send, null)
        val activity = ForegroundActivity.current
        if (activity == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            (activity ?: context).startActivity(chooser)
            ForegroundActivity.startedOtherApp()
        } catch (e: RuntimeException) {
            // Never throws: the system chooser always exists; nothing to tell the user if it somehow does not.
        }
    }
}
