package com.callbackdev.thabit.ui.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * The jump from `stats.md` into the log: a tag row points at the commit that
 * earned it.
 *
 * The requester names a day and navigates to the Log tab; the log opens that
 * commit, scrolls it into view and consumes the request. A small hand-rolled
 * channel rather than a navigation argument, and the reason is the tab
 * behaviour this shell already has: the Log tab's back-stack entry is
 * **restored** and not recreated (`saveState`/`restoreState`, Fase 4), so an
 * argument attached to a fresh navigation would never reach it.
 *
 * Ported from tsteps, where the same tag rows do the same thing.
 */
object LogFocus {

    private val _request = MutableStateFlow<LocalDate?>(null)
    val request: StateFlow<LocalDate?> = _request.asStateFlow()

    fun request(date: LocalDate) {
        _request.value = date
    }

    /** Consumed once acted on, so the log does not re-open it on every redraw. */
    fun consume() {
        _request.value = null
    }
}
