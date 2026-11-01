package com.privateai.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hands a spoken command from the voice service to the chat UI, which owns the loaded model. */
object VoiceCommandBus {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun submit(command: String) {
        if (command.isNotBlank()) _pending.value = command.trim()
    }

    fun consume() {
        _pending.value = null
    }
}
