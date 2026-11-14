package com.techgv.stash.feature.search

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchFeatureModule = module { viewModelOf(::SearchViewModel) }
