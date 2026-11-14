package com.techgv.stash.core.platform

import com.techgv.stash.core.model.SharedPayload

/**
 * Links other apps shared into Stash that are waiting to be saved. Android hands them over in-process from the share
 * receiver; iOS reads the App Group inbox the Share Extension appends to. The app drains on launch and foreground.
 */
interface ShareInbox {
    /**
     * Hands every waiting payload to [save], one at a time, in the order it was shared. An entry is removed only after
     * [save] returns for it, so a process death mid-drain delivers it again on the next drain (the save path dedupes by
     * normalized URL), never zero times.
     *
     * When [save] throws, the drain stops: that entry and every later one stay in the inbox, in order, and the
     * exception is rethrown; the next drain starts with them. Entries appended while a drain runs (the iOS Share
     * Extension writes the same App Group file from another process, under file coordination) are never lost: they
     * are delivered by this drain or the next one.
     */
    suspend fun drain(save: suspend (SharedPayload) -> Unit)
}
