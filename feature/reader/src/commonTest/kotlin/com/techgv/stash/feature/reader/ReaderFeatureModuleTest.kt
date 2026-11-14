package com.techgv.stash.feature.reader

import com.techgv.stash.core.domain.CommandBus
import com.techgv.stash.core.domain.ItemRepository
import com.techgv.stash.core.domain.ReaderRepository
import com.techgv.stash.core.domain.ReaderResult
import com.techgv.stash.core.domain.SettingsRepository
import com.techgv.stash.core.model.ItemId
import com.techgv.stash.core.testing.FakeCommandBus
import com.techgv.stash.core.testing.FakeItemRepository
import com.techgv.stash.core.testing.FakeReaderRepository
import com.techgv.stash.core.testing.savedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderFeatureModuleTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun viewModelGetsTheItemTheMarkedReadFlagAndTheModeFromItsParameters() = runTest(dispatcher) {
        val bus = FakeCommandBus(FakeItemRepository(listOf(savedItem("a"))))
        val koin = koinApplication {
            modules(
                module {
                    single<ItemRepository> { bus.items }
                    single<CommandBus> { bus }
                    single<SettingsRepository> { bus.settings }
                    single<ReaderRepository> { FakeReaderRepository(ReaderResult.Offline) }
                },
                readerFeatureModule,
            )
        }.koin
        val viewModel = koin.get<ReaderViewModel> { parametersOf(ItemId("a"), true, null) }
        val carried = koin.get<ReaderViewModel> { parametersOf(ItemId("a"), false, true) }
        dispatcher.scheduler.runCurrent()
        assertEquals("Title a", viewModel.state.value.item?.title)
        assertEquals(ReaderToast.MarkedRead, viewModel.state.value.toast)
        assertEquals(false, viewModel.state.value.readerMode)
        assertEquals(true, carried.state.value.readerMode)
    }
}
