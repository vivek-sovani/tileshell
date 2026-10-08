package com.tileshell.feature.livetiles

import java.util.concurrent.ConcurrentHashMap

private val compiled = ConcurrentHashMap<String, Regex>()

/**
 * A [Regex] for [pattern], compiled once. The parsers build patterns from word lists (`\b`-style word match per
 * word), and run on every posted notification; compiling a fresh one per word per call was most of their cost.
 */
internal fun cachedRegex(pattern: String): Regex = compiled.getOrPut(pattern) { Regex(pattern) }
