package com.halo.app.devtools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.components.*
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import com.halo.icl.ontology.Observation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class InjectionViewModel : ViewModel() {
    private val perceptionBus = ManualDependencyGraph.perceptionBus

    fun injectSpeech(text: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val observation = Observation(
                id = CognitiveId(),
                created = Clock.System.now(),
                confidence = Confidence(1.0f), // 100% confidence for developer injected text
                provenance = Provenance("driver.devtools.injection", null),
                edges = emptyList(),
                rawData = text,
                modality = "Speech" // Case matches AndroidSpeechProcess
            )
            perceptionBus.publish("driver.devtools.injection", observation)
        }
    }
}

class InjectionViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InjectionViewModel() as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InjectionScreen(viewModel: InjectionViewModel = viewModel(factory = InjectionViewModelFactory())) {
    var inputText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HaloSection("SIMULATE SPEECH") {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Simulated Speech Transcript", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.injectSpeech(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inject Observation", fontFamily = FontFamily.Monospace)
            }
        }
    }
}
