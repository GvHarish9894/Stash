package com.techgv.stash.feature.streak

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val streakFeatureModule = module { viewModelOf(::StreakSheetViewModel) }
