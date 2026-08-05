package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.core.engine.EngineState
import com.halo.core.runtime.RuntimeState
import com.halo.core.runtime.RuntimeContext
import com.halo.runtime.engine.fake.*
import com.halo.runtime.kernel.HaloRuntime

@Composable
fun DeveloperConsole() {
    val voice = remember { FakeVoiceEngine() }
    val memory = remember { FakeMemoryEngine() }
    val workflow = remember { FakeWorkflowEngine() }
    val contextEngine = remember { FakeContextEngine() }
    
    val fakeContext = remember { 
        object : RuntimeContext {
            override val eventBus = Any()
            override val commandBus = Any()
            override val queryBus = Any()
            override val services = Any()
            override val scheduler = Any()
            override val logger = Any()
            override val clock = Any()
            override val configuration = Any()
        }
    }

    val runtime = remember {
        HaloRuntime(voice, memory, workflow, contextEngine, fakeContext)
    }

    LaunchedEffect(Unit) {
        runtime.start()
    }

    val runtimeState by runtime.state.collectAsState()
    val voiceState by voice.state.collectAsState()
    val memoryState by memory.state.collectAsState()
    val workflowState by workflow.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13)) // Deep console background
            .padding(24.dp)
            .padding(top = 48.dp)
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ConsoleHeader("HAL Runtime")
                ConsoleRow("Status", runtimeState.name)
            }
            item { HorizontalDivider(color = Color.DarkGray) }
            item {
                ConsoleHeader("Engines")
                ConsoleRow("Voice", voiceState.name)
                ConsoleRow("Memory", memoryState.name)
                ConsoleRow("Workflow", workflowState.name)
            }
            item { HorizontalDivider(color = Color.DarkGray) }
            item {
                ConsoleHeader("Drivers")
                ConsoleRow("HeyCyan", "Ready (Simulator)")
            }
            item {
                ConsoleHeader("Providers")
                ConsoleRow("Local Llama", "Ready")
            }
        }
    }
}

@Composable
fun ConsoleHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF888899),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
    )
}

@Composable
fun ConsoleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFFCCCCCC),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = if (value == "READY" || value.startsWith("Ready")) Color(0xFF44FF44) else Color(0xFFFFCC00),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}
