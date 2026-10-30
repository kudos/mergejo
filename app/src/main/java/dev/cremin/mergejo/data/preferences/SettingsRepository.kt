package dev.cremin.mergejo.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.cremin.mergejo.domain.model.MergeStyle
import dev.cremin.mergejo.domain.model.PollInterval
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App settings backed by DataStore, exposed as [Flow]s. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val defaultMergeStyle: Flow<MergeStyle> = context.dataStore.data.map { prefs ->
        prefs[KEY_MERGE_STYLE]
            ?.let { runCatching { MergeStyle.valueOf(it) }.getOrNull() }
            ?: MergeStyle.SQUASH
    }

    suspend fun setDefaultMergeStyle(style: MergeStyle) {
        context.dataStore.edit { it[KEY_MERGE_STYLE] = style.name }
    }

    val pollInterval: Flow<PollInterval> = context.dataStore.data.map { prefs ->
        prefs[KEY_POLL_INTERVAL]
            ?.let { runCatching { PollInterval.valueOf(it) }.getOrNull() }
            ?: PollInterval.OFF
    }

    suspend fun setPollInterval(interval: PollInterval) {
        context.dataStore.edit { it[KEY_POLL_INTERVAL] = interval.name }
    }

    /** PR ids already seen by the background poll; null until a baseline has been recorded. */
    suspend fun notifiedPrIds(): Set<String>? = context.dataStore.data.first()[KEY_NOTIFIED_PR_IDS]

    suspend fun setNotifiedPrIds(ids: Set<String>) {
        context.dataStore.edit { it[KEY_NOTIFIED_PR_IDS] = ids }
    }

    /** Drops the baseline so the next poll records the current PRs without notifying. */
    suspend fun clearNotifiedPrIds() {
        context.dataStore.edit { it.remove(KEY_NOTIFIED_PR_IDS) }
    }

    private companion object {
        val KEY_MERGE_STYLE = stringPreferencesKey("default_merge_style")
        val KEY_POLL_INTERVAL = stringPreferencesKey("poll_interval")
        val KEY_NOTIFIED_PR_IDS = stringSetPreferencesKey("notified_pr_ids")
    }
}
