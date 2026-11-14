package com.techgv.stash.feature.settings

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsFeatureModule = module { viewModelOf(::SettingsViewModel) }
