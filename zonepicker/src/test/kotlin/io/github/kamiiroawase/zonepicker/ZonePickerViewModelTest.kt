package io.github.kamiiroawase.zonepicker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class ZonePickerViewModelTest {
    @Test
    fun `setInputs builds once and caches identical inputs`() = runTest {
        withTestViewModel { viewModel ->
            viewModel.setInputs(CHINESE_NAMES, null)

            advanceUntilIdle()

            val snapshot = viewModel.snapshot.value

            assertNotNull(snapshot)
            assertEquals("中国台北时间", snapshot!!.zones.first { it.zoneId == "Asia/Taipei" }.displayName)

            // The same inputs again must not rebuild
            viewModel.setInputs(CHINESE_NAMES, null)

            advanceUntilIdle()

            assertSame(snapshot, viewModel.snapshot.value)
        }
    }

    @Test
    fun `changed names rebuild the snapshot in the new language`() = runTest {
        withTestViewModel { viewModel ->
            viewModel.setInputs(CHINESE_NAMES, null)

            advanceUntilIdle()

            val first = viewModel.snapshot.value

            assertNotNull(first)

            viewModel.setInputs(ENGLISH_NAMES, null)

            advanceUntilIdle()

            val second = viewModel.snapshot.value

            assertNotNull(second)
            assertNotSame(first, second)
            assertEquals("China Standard Time", second!!.zones.first { it.zoneId == "Asia/Shanghai" }.displayName)
        }
    }

    @Test
    fun `a names change before the build lands replaces it`() = runTest {
        withTestViewModel { viewModel ->
            viewModel.setInputs(CHINESE_NAMES, null)
            viewModel.setInputs(ENGLISH_NAMES, null)

            advanceUntilIdle()

            assertEquals(
                "China Standard Time",
                viewModel.snapshot.value!!
                    .zones
                    .first { it.zoneId == "Asia/Shanghai" }
                    .displayName,
            )
        }
    }

    @Test
    fun `changed default zone ids rebuild, equal inputs do not`() = runTest {
        withTestViewModel { viewModel ->
            viewModel.setInputs(CHINESE_NAMES, null)

            advanceUntilIdle()

            val first = viewModel.snapshot.value

            assertNotNull(first)

            viewModel.setInputs(CHINESE_NAMES, listOf("Asia/Tokyo"))

            advanceUntilIdle()

            assertNotSame(first, viewModel.snapshot.value)
            assertTrue(viewModel.snapshot.value!!.defaultZones.any { it.zoneId == "Asia/Tokyo" })

            val second = viewModel.snapshot.value

            viewModel.setInputs(CHINESE_NAMES, listOf("Asia/Tokyo"))

            advanceUntilIdle()

            assertSame(second, viewModel.snapshot.value)
        }
    }

    @Test
    fun `one inputs push builds once under both values`() = runTest {
        // Each snapshot build samples the clock exactly once, so the counter is a build count
        val builds = AtomicLong()

        withTestViewModel(nowMillis = { builds.incrementAndGet() }) { viewModel ->
            viewModel.setInputs(CHINESE_NAMES, listOf("Asia/Tokyo"))

            advanceUntilIdle()

            val snapshot = viewModel.snapshot.value

            assertNotNull(snapshot)
            assertEquals(1L, builds.get())
            assertEquals("中国台北时间", snapshot!!.zones.first { it.zoneId == "Asia/Taipei" }.displayName)
            assertTrue(snapshot.defaultZones.any { it.zoneId == "Asia/Tokyo" })
        }
    }

    @Test
    fun `requestFreshSnapshot builds a first snapshot without zone names`() = runTest {
        withTestViewModel { viewModel ->
            viewModel.requestFreshSnapshot()

            advanceUntilIdle()

            // The default names: simplified Chinese, no overrides
            assertEquals(
                "中国标准时间",
                viewModel.snapshot.value!!
                    .zones
                    .first { it.zoneId == "Asia/Shanghai" }
                    .displayName,
            )
        }
    }

    @Test
    fun `requestFreshSnapshot rebuilds past the staleness window`() = runTest {
        val now = AtomicLong(1_000_000L)

        withTestViewModel(nowMillis = now::get) { viewModel ->
            viewModel.setInputs(CHINESE_NAMES, null)

            advanceUntilIdle()

            val first = viewModel.snapshot.value

            assertNotNull(first)

            now.addAndGet(ZonePickerViewModel.STALE_AFTER_MILLIS + 1)

            viewModel.requestFreshSnapshot()

            advanceUntilIdle()

            assertNotSame(first, viewModel.snapshot.value)

            // The rebuilt snapshot is fresh again: no rebuild
            val second = viewModel.snapshot.value

            viewModel.requestFreshSnapshot()

            advanceUntilIdle()

            assertSame(second, viewModel.snapshot.value)
        }
    }

    /** Runs [block] with a ViewModel whose builds and the main dispatcher share this test's
     *  scheduler, so everything is sequential and advanceUntilIdle sees it all. */
    private fun TestScope.withTestViewModel(nowMillis: () -> Long = { 0L }, block: TestScope.(ZonePickerViewModel) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)

        Dispatchers.setMain(dispatcher)

        try {
            block(this, ZonePickerViewModel(dispatcher, nowMillis))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        val CHINESE_NAMES =
            ZonePickerViewModel.ZoneNames(
                nameLocale = Locale.SIMPLIFIED_CHINESE,
                overrides = mapOf("Asia/Taipei" to "中国台北时间"),
            )

        val ENGLISH_NAMES =
            ZonePickerViewModel.ZoneNames(
                nameLocale = Locale.ENGLISH,
                overrides = emptyMap(),
            )
    }
}
