package com.callbackdev.thabit.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The jump from a reminder into the suite: the notification names a test, and
 * `habits.test` opens on it.
 *
 * Same hand-rolled channel as [com.callbackdev.thabit.ui.log.LogFocus] and for
 * the same reason — the editor tab's back-stack entry is restored rather than
 * recreated (`saveState`/`restoreState`), so a navigation argument attached to a
 * fresh navigation would never reach the screen that is already there.
 *
 * What the request means is deliberately different per kind of test, because
 * what the reader came to do is different: a counter opens its **prompt** (they
 * were sent here precisely because a value cannot be tapped from a shade), while
 * anything else unfolds its **spec** — an app that ticks a box for you because
 * you tapped a notification has answered a question you were only being asked.
 */
object SuiteFocus {

    private val _request = MutableStateFlow<Long?>(null)
    val request: StateFlow<Long?> = _request.asStateFlow()

    fun request(habitId: Long) {
        _request.value = habitId
    }

    /** Consumed once acted on, so the file does not re-open it on every redraw. */
    fun consume() {
        _request.value = null
    }
}
