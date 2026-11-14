package com.techgv.stash.core.platform

import com.techgv.stash.core.model.SharedPayload

/**
 * Always empty on Android: `ShareReceiverActivity` saves a shared link through the command bus while it is open, so
 * nothing ever waits here. Only iOS, whose Share Extension cannot open the database, fills an inbox.
 */
object AndroidShareInbox : ShareInbox {
    override suspend fun drain(save: suspend (SharedPayload) -> Unit) = Unit
}
