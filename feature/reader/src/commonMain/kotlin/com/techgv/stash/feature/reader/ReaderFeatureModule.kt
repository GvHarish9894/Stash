package com.techgv.stash.feature.reader

import com.techgv.stash.core.model.ItemId
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** `koinViewModel<ReaderViewModel> { parametersOf(itemId, markedRead, readerMode) }`. */
val readerFeatureModule = module {
    viewModel { (id: ItemId, markedRead: Boolean, readerMode: Boolean?) -> ReaderViewModel(id, markedRead, readerMode, get(), get(), get(), get()) }
}
