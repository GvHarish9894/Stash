package com.techgv.stash.feature.shelf

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val shelfFeatureModule = module { viewModelOf(::ShelfViewModel) }
