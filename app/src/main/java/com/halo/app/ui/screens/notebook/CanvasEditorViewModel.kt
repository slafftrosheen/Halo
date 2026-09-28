package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.NotebookCanvasRepository
import kotlinx.coroutines.Job
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
    val addingNode: Boolean = false,
    val notice: String? = null,
)

class CanvasEditorViewModel(
    private val canvasId: String,
    private val repository: NotebookCanvasRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CanvasEditorUiState())
    val uiState: StateFlow<CanvasEditorUiState> = mutableState.asStateFlow()
    private var viewportSave: Job? = null
    private val nodePositionSaves = mutableMapOf<String, Job>()
    private val nodeTextSaves = mutableMapOf<String, Job>()
    private val pendingNodePositions = mutableMapOf<String, Pair<Float, Float>>()
    private val pendingNodeText = mutableMapOf<String, String>()
    private var pendingViewport: Triple<Float, Float, Float>? = null

    init {
        viewModelScope.launch {
            repository.observe(canvasId).collect { canvas ->
                if (canvas == null) {
                    mutableState.value = mutableState.value.copy(
                        nodes = emptyList(),
                        selectedNodeId = null,
                        loading = false,
                        missing = true,
                        addingNode = false,
                    )
                } else {
                    val viewport = pendingViewport
                    mutableState.value = mutableState.value.copy(
                        title = canvas.title,
                        nodes = canvas.nodes.map { node ->
                            val position = pendingNodePositions[node.id]
                            node.copy(
                                text = pendingNodeText[node.id] ?: node.text,
                                x = position?.first ?: node.x,
                                y = position?.second ?: node.y,
                            )
                        },
                        viewportX = viewport?.first ?: canvas.viewportX,
                        viewportY = viewport?.second ?: canvas.viewportY,
                        viewportScale = viewport?.third ?: canvas.viewportScale,
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
        if (mutableState.value.addingNode || mutableState.value.missing) return@launch
        mutableState.value = mutableState.value.copy(addingNode = true, notice = null)
        val state = mutableState.value
        val centerX = -state.viewportX / state.viewportScale + 80f
        val centerY = -state.viewportY / state.viewportScale + 120f
        runCatching { repository.addTextNode(canvasId, centerX, centerY) }
            .onSuccess { id ->
                mutableState.value = mutableState.value.copy(selectedNodeId = id, addingNode = false)
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(
                    addingNode = false,
                    notice = "Could not add a card. Try again.",
                )
            }
    }

    fun updateNodeText(nodeId: String, text: String) {
        pendingNodeText[nodeId] = text
        mutableState.value = mutableState.value.copy(
            nodes = mutableState.value.nodes.map { if (it.id == nodeId) it.copy(text = text) else it }
        )
        if (nodeTextSaves[nodeId]?.isActive == true) return
        nodeTextSaves[nodeId] = viewModelScope.launch {
            while (true) {
                val latestText = pendingNodeText[nodeId] ?: break
                val saved = runCatching { repository.updateText(canvasId, nodeId, latestText) }.getOrDefault(false)
                if (!saved) {
                    mutableState.value = mutableState.value.copy(notice = "This card could not be saved yet.")
                    break
                }
                if (pendingNodeText[nodeId] == latestText) {
                    pendingNodeText.remove(nodeId)
                    break
                }
            }
            nodeTextSaves.remove(nodeId)
        }
    }

    fun moveNode(nodeId: String, dx: Float, dy: Float) {
        val scale = mutableState.value.viewportScale
        val updated = mutableState.value.nodes.map { node ->
            if (node.id == nodeId) node.copy(x = node.x + dx / scale, y = node.y + dy / scale) else node
        }
        mutableState.value = mutableState.value.copy(nodes = updated, selectedNodeId = nodeId)
        updated.firstOrNull { it.id == nodeId }?.let { node ->
            val target = node.x to node.y
            pendingNodePositions[nodeId] = target
            if (nodePositionSaves[nodeId]?.isActive == true) return@let
            nodePositionSaves[nodeId] = viewModelScope.launch {
                while (true) {
                    val latestPosition = pendingNodePositions[nodeId] ?: break
                    val saved = runCatching {
                        repository.moveNode(canvasId, nodeId, latestPosition.first, latestPosition.second)
                    }.getOrDefault(false)
                    if (!saved) {
                        mutableState.value = mutableState.value.copy(notice = "This card position could not be saved yet.")
                        break
                    }
                    if (pendingNodePositions[nodeId] == latestPosition) {
                        pendingNodePositions.remove(nodeId)
                        break
                    }
                }
                nodePositionSaves.remove(nodeId)
            }
        }
    }

    fun deleteSelectedNode() {
        val id = mutableState.value.selectedNodeId ?: return
        val selectedNode = mutableState.value.nodes.firstOrNull { it.id == id }
        nodePositionSaves.remove(id)?.cancel()
        nodeTextSaves.remove(id)?.cancel()
        viewModelScope.launch {
            if (runCatching { repository.deleteNode(canvasId, id) }.getOrDefault(false)) {
                pendingNodePositions.remove(id)
                pendingNodeText.remove(id)
                mutableState.value = mutableState.value.copy(selectedNodeId = null)
            } else {
                mutableState.value = mutableState.value.copy(notice = "Could not delete this card.")
                selectedNode?.let { node ->
                    updateNodeText(id, node.text)
                    moveNode(id, 0f, 0f)
                }
            }
        }
    }

    fun transform(panX: Float, panY: Float, zoom: Float) {
        val current = mutableState.value
        val scale = (current.viewportScale * zoom).coerceIn(0.35f, 2.5f)
        val target = Triple(
            current.viewportX + panX,
            current.viewportY + panY,
            scale,
        )
        pendingViewport = target
        mutableState.value = current.copy(
            viewportX = target.first,
            viewportY = target.second,
            viewportScale = scale,
            selectedNodeId = null,
        )
        savePendingViewport()
    }

    fun resetViewport() {
        pendingViewport = Triple(0f, 0f, 1f)
        mutableState.value = mutableState.value.copy(viewportX = 0f, viewportY = 0f, viewportScale = 1f)
        savePendingViewport()
    }

    private fun savePendingViewport() {
        if (viewportSave?.isActive == true) return
        viewportSave = viewModelScope.launch {
            while (true) {
                val latest = pendingViewport ?: break
                val saved = runCatching {
                    repository.saveViewport(canvasId, latest.first, latest.second, latest.third)
                }.getOrDefault(false)
                if (!saved) {
                    mutableState.value = mutableState.value.copy(notice = "Canvas position could not be saved yet.")
                    break
                }
                if (pendingViewport == latest) {
                    pendingViewport = null
                    break
                }
            }
        }
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
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
