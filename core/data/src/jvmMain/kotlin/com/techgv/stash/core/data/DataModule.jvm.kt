package com.techgv.stash.core.data

import org.koin.core.module.Module
import org.koin.dsl.module

// The JVM target exists for tests only; they bind an in-memory database and a temporary settings file themselves.
internal actual val platformDataModule: Module = module { }
