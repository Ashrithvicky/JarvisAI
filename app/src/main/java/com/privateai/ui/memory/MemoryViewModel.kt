package com.privateai.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privateai.PrivateAIApplication
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Memory Management Screen.
 */
data class MemoryUiState(
    val memories: List<Memory> = emptyList(),
    val searchQuery: String = "",
    val selectedTypeFilter: MemoryType? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for viewing, searching, adding, editing, and deleting long-term memories.
 */
class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContainer = (application as PrivateAIApplication).appContainer
    private val memoryManager: MemoryManager = appContainer.memoryManager

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val query = _uiState.value.searchQuery
                val filter = _uiState.value.selectedTypeFilter

                val rawList = if (query.isNotBlank()) {
                    memoryManager.searchMemories(query)
                } else {
                    memoryManager.getAllMemories()
                }

                val filtered = if (filter != null) {
                    rawList.filter { it.type == filter }
                } else {
                    rawList
                }

                val sorted = filtered.sortedByDescending { it.updatedAt }

                _uiState.value = _uiState.value.copy(
                    memories = sorted,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load memories"
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadMemories()
    }

    fun setTypeFilter(type: MemoryType?) {
        _uiState.value = _uiState.value.copy(selectedTypeFilter = type)
        loadMemories()
    }

    fun addMemory(content: String, type: MemoryType, tags: List<String>) {
        if (content.isBlank()) return
        viewModelScope.launch {
            memoryManager.addMemory(
                content = content.trim(),
                type = type,
                tags = tags.map { it.trim() }.filter { it.isNotBlank() }
            ).onSuccess {
                loadMemories()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun updateMemory(memory: Memory) {
        viewModelScope.launch {
            memoryManager.updateMemory(memory)
                .onSuccess {
                    loadMemories()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            memoryManager.deleteMemory(memoryId)
                .onSuccess {
                    loadMemories()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryManager.deleteAllMemories()
                .onSuccess {
                    loadMemories()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
