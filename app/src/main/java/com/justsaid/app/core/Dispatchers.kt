package com.justsaid.app.core

import javax.inject.Qualifier

/**
 * Dispatcher qualifiers. Every layer injects the dispatcher it needs instead of
 * hardcoding [kotlinx.coroutines.Dispatchers], so tests can substitute a
 * deterministic test dispatcher (AGENTS.md §2 Concurrency).
 */

/** Backing store / network / disk I/O. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU-bound work (later: native inference off the main thread). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
