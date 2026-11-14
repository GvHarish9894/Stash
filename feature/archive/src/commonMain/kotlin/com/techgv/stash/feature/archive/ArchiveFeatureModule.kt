package com.techgv.stash.feature.archive

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val archiveFeatureModule = module { viewModelOf(::ArchiveViewModel) }
