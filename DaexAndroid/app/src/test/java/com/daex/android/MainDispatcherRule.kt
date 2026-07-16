package com.daex.android

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `viewModelScope` requires a `Main` dispatcher to exist, which isn't true by default in a plain
 * JVM unit test. Every ViewModel test needs this rule.
 *
 * Uses [UnconfinedTestDispatcher] (not [kotlinx.coroutines.test.StandardTestDispatcher]) so
 * `viewModelScope.launch { }` bodies in a ViewModel's `init` run eagerly, synchronously, on
 * construction - no manual `advanceUntilIdle()` needed for the non-delay-based coroutines these
 * ViewModels launch. `runTest { }`'s own internal scheduler is independent of this one; that's
 * fine as long as nothing under test relies on `delay()`-based virtual time ordering between them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
