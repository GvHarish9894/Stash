package com.techgv.stash.feature.widgetpreview

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val widgetPreviewFeatureModule = module { viewModelOf(::WidgetPreviewViewModel) }
