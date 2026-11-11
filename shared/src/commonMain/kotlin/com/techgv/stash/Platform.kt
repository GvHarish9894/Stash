package com.techgv.stash

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform