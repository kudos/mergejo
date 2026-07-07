package dev.cremin.gitops.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.cremin.gitops.domain.model.MergeStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
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

    private companion object {
        val KEY_MERGE_STYLE = stringPreferencesKey("default_merge_style")
    }
}
