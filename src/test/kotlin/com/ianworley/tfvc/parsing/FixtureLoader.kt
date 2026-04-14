package com.ianworley.tfvc.parsing

object FixtureLoader {
    fun read(name: String): String =
        checkNotNull(javaClass.classLoader.getResource("fixtures/$name")) { "Missing fixture $name" }
            .readText()
}
