package dev.storozhenko.music

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Locale

fun capitalize(s: String): String {
    return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun String.asResource(): String {
    return this.javaClass::class.java.getResource(this)?.readText()
        ?: throw IllegalStateException("Resource $this is not found")
}

@Suppress("unused")
inline fun <reified T> T.getLogger(): Logger = LoggerFactory.getLogger(T::class.java)
