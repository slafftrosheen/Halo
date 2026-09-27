package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.NotebookCanvasRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class CanvasEditorUiState(
    val title: String = "Canvas",
    val nodes: List<CanvasNode> = emptyList(),
    val viewportX: Float = 0f,
    val viewportY: Float = 0f,
    val viewportScale: Float = 1f,
    val selectedNodeId: String? = null,
    val loading: Boolean = true,
    val missing: Boolean = false,
)

class CanvasEditorViewModel(
    private val canvasId: String,
    private val repository: NotebookCanvasRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CanvasEditorUiState())
    val uiState: StateFlow<CanvasEditorUiState> = mutableState.asStateFlow()
    private var viewportSave: Job? = null
    private val nodePositionSaves = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.observe(canvasId).collect { canvas ->
                if (canvas == null) {
                    mutableState.value = mutableState.value.copy(loading = false, missing = true)
                } else {
                    mutableState.value = mutableState.value.copy(
                        title = canvas.title,
                        nodes = canvas.nodes,
                        viewportX = canvas.viewportX,
                        viewportY = canvas.viewportY,
                        viewportScale = canvas.viewportScale,
                        loading = false,
                        missing = false,
                    )
                }
            }
        }
    }

    fun selectNode(nodeId: String?) {
        mutableState.value = mutableState.value.copy(selectedNodeId = nodeId)
    }

    fun addTextNode() = viewModelScope.launch {
        val state = mutableState.value
        val centerX = -state.viewportX / state.viewportScale + 80f
        val centerY = -state.viewportY / state.viewportScale + 120f
        val id = repository.addTextNode(canvasId, centerX, centerY)
        mutableState.value = mutableState.value.copy(selectedNodeId = id)
    }

    fun updateNodeText(nodeId: String, text: String) {
        mutableState.value = mutableState.value.copy(
            nodes = mutableState.value.nodes.map { if (it.id == nodeId) it.copy(text = text) else it }
        )
        viewModelScope.launch { repository.updateText(canvasId, nodeId, text) }
    }

    fun moveNode(nodeId: String, dx: Float, dy: Float) {
        val scale = mutableState.value.viewportScale
        val updated = mutableState.value.nodes.map { node ->
            if (node.id == nodeId) node.copy(x = node.x + dx / scale, y = node.y + dy / scale) else node
        }
        mutableState.value = mutableState.value.copy(nodes = updated, selectedNodeId = nodeId)
        updated.firstOrNull { it.id == nodeId }?.let { node ->
            nodePositionSaves.remove(nodeId)?.cancel()
            nodePositionSaves[nodeId] = viewModelScope.launch {
                delay(120)
                repository.moveNode(canvasId, nodeId, node.x, node.y)
                nodePositionSaves.remove(nodeId)
            }
        }
    }

    fun deleteSelectedNode() {
        val id = mutableState.value.selectedNodeId ?: return
        nodePositionSaves.remove(id)?.cancel()
        viewModelScope.launch {
            if (repository.deleteNode(canvasId, id)) {
                mutableState.value = mutableState.value.copy(selectedNodeId = null)
            }
        }
    }

    fun transform(panX: Float, panY: Float, zoom: Float) {
        val current = mutableState.value
        val scale = (current.viewportScale * zoom).coerceIn(0.35f, 2.5f)
        mutableState.value = current.copy(
            viewportX = current.viewportX + panX,
            viewportY = current.viewportY + panY,
            viewportScale = scale,
            selectedNodeId = null,
        )
        viewportSave?.cancel()
        viewportSave = viewModelScope.launch {
            delay(250)
            val latest = mutableState.value
            repository.saveViewport(canvasId, latest.viewportX, latest.viewportY, latest.viewportScale)
        }
    }

    fun resetViewport() {
        mutableState.value = mutableState.value.copy(viewportX = 0f, viewportY = 0f, viewportScale = 1f)
        viewportSave?.cancel()
        viewportSave = viewModelScope.launch { repository.saveViewport(canvasId, 0f, 0f, 1f) }
    }

    class Factory(
        private val canvasId: String,
        private val repository: NotebookCanvasRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CanvasEditorViewModel(canvasId, repository) as T
    }
}
