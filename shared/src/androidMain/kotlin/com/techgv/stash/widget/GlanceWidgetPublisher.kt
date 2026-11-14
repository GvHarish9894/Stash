package com.techgv.stash.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.model.WidgetItem
import com.techgv.stash.core.model.WidgetSnapshot
import com.techgv.stash.core.platform.WidgetPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Keeps the latest snapshot in preferences, where [StashWidget] reads it, and redraws every placed widget. */
class GlanceWidgetPublisher(private val context: Context) : WidgetPublisher {
    override suspend fun publish(snapshot: WidgetSnapshot) {
        withContext(Dispatchers.IO) { WidgetSnapshotStore.write(context, snapshot) }
        StashWidget().updateAll(context)
    }
}

/**
 * The snapshot as JSON in the app's private preferences, which outlive the process, plus the latest one in memory so a
 * widget that is showing redraws with it (Glance keeps a widget's composition alive between updates).
 */
object WidgetSnapshotStore {
    private const val PREFERENCES = "stash_widget"
    private const val KEY = "snapshot"

    private val published = MutableStateFlow<WidgetSnapshot?>(null)
    private val language = MutableStateFlow(Locale.getDefault().toLanguageTag())

    /** The latest snapshot, then each one published, and again after a language change, while it is collected. */
    fun updates(context: Context): Flow<WidgetSnapshot> = combine(published, language) { snapshot, _ -> snapshot ?: read(context) }

    /** Redraws every placed widget in the process language (`Locale.getDefault()`); call after it changed. */
    suspend fun languageChanged(context: Context) {
        language.value = Locale.getDefault().toLanguageTag()
        StashWidget().updateAll(context)
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        val items = JSONArray(
            snapshot.items.map {
                JSONObject()
                    .put("id", it.id.value)
                    .put("title", it.title)
                    .put("site", it.siteName)
                    .put("type", it.contentType.name)
                    .put("minutes", it.readingMinutes ?: JSONObject.NULL)
                    .put("image", it.imageUrl ?: JSONObject.NULL)
            },
        )
        val json = JSONObject()
            .put("items", items)
            .put("unread", snapshot.unreadCount)
            .put("streak", snapshot.streak)
            .put("readToday", snapshot.readToday)
        preferences(context).edit().putString(KEY, json.toString()).commit()
        published.value = snapshot
    }

    /** The last published snapshot; an empty shelf when none was published yet or the record is unreadable. */
    fun read(context: Context): WidgetSnapshot = try {
        val json = JSONObject(preferences(context).getString(KEY, null) ?: return EMPTY)
        val items = json.getJSONArray("items")
        WidgetSnapshot(
            items = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                WidgetItem(
                    id = ItemId(item.getString("id")),
                    title = item.getString("title"),
                    siteName = item.getString("site"),
                    contentType = ContentType.valueOf(item.getString("type")),
                    readingMinutes = if (item.isNull("minutes")) null else item.getInt("minutes"),
                    imageUrl = if (item.isNull("image")) null else item.getString("image"),
                )
            },
            unreadCount = json.getInt("unread"),
            streak = json.getInt("streak"),
            readToday = json.getBoolean("readToday"),
        )
    } catch (e: Exception) {
        EMPTY
    }

    private val EMPTY = WidgetSnapshot(emptyList(), unreadCount = 0, streak = 0, readToday = false)

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
