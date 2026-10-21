package com.appname.notes.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appname.notes.core.common.logging.NoOpLogger
import com.appname.notes.core.common.result.AppResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses a real DataStore file on disk, so this also proves values survive an app restart. */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val file get() = File(tmp.root, "settings.preferences_pb")

    /** Each "process" gets its own DataStore scope; cancel it before opening the file again. */
    private fun openStore(job: Job) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + job),
        produceFile = { file },
    )

    @Test
    fun `defaults when nothing was saved`() = testScope.runTest {
        val job = Job()
        val repo = DataStoreSettingsRepository(openStore(job), NoOpLogger)
        assertThat(repo.appearance.first()).isEqualTo(AppearanceSettings())
        job.cancelAndJoin()
    }

    @Test
    fun `saved values are read back and survive a restart`() = testScope.runTest {
        val firstRun = Job()
        val repo = DataStoreSettingsRepository(openStore(firstRun), NoOpLogger)
        assertThat(repo.setThemeMode(ThemeMode.DARK)).isEqualTo(AppResult.Success(Unit))
        assertThat(repo.setDynamicColor(true)).isEqualTo(AppResult.Success(Unit))
        assertThat(repo.appearance.first()).isEqualTo(AppearanceSettings(ThemeMode.DARK, dynamicColor = true))
        firstRun.cancelAndJoin()

        val secondRun = Job()
        val reopened = DataStoreSettingsRepository(openStore(secondRun), NoOpLogger)
        assertThat(reopened.appearance.first()).isEqualTo(AppearanceSettings(ThemeMode.DARK, dynamicColor = true))
        secondRun.cancelAndJoin()
    }

    @Test
    fun `unknown stored theme falls back to system`() = testScope.runTest {
        val job = Job()
        val store = openStore(job)
        store.edit { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        val repo = DataStoreSettingsRepository(store, NoOpLogger)
        assertThat(repo.appearance.first().themeMode).isEqualTo(ThemeMode.SYSTEM)
        job.cancelAndJoin()
    }

    @Test
    fun `theme mode parsing handles null and every enum name`() {
        assertThat((null as String?).toThemeMode()).isEqualTo(ThemeMode.SYSTEM)
        ThemeMode.entries.forEach { assertThat(it.name.toThemeMode()).isEqualTo(it) }
    }
}
