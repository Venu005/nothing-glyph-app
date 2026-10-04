package app.backlit.alerts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How many Backlit toys are currently bound (showing). Main thread only. */
object ToyPresence {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    fun enter() { _count.value += 1 }
    fun leave() { _count.value = (_count.value - 1).coerceAtLeast(0) }
}
