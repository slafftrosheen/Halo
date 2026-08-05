package com.halo.engines.interpretation

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.icl.ontology.Observation
import com.halo.icl.ontology.Thought
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import com.halo.icl.core.CognitiveEdge
import com.halo.icl.core.EdgeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock

class RuleInterpretationProcess(
    private val perceptionBus: PerceptionBus,
    private val cognitiveBus: CognitiveBus
) : Process {
    override val id: String = "service.interpretation.rules"
    override val name: String = "Rule Pipeline"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    private val intentRules = mapOf(
        Regex(".*play.*(spotify|music).*", RegexOption.IGNORE_CASE) to "PlayMusic",
        Regex(".*open.*camera.*", RegexOption.IGNORE_CASE) to "OpenCamera",
        Regex(".*(turn on|enable).*flashlight.*", RegexOption.IGNORE_CASE) to "ToggleFlashlight",
        Regex(".*volume up.*", RegexOption.IGNORE_CASE) to "VolumeUp",
        Regex(".*volume down.*", RegexOption.IGNORE_CASE) to "VolumeDown",
        Regex(".*navigate.*home.*", RegexOption.IGNORE_CASE) to "NavigateHome",
        Regex(".*what time.*", RegexOption.IGNORE_CASE) to "CheckTime",
        Regex(".*who are you.*", RegexOption.IGNORE_CASE) to "IdentifySelf",
        Regex(".*set.*timer.*", RegexOption.IGNORE_CASE) to "SetTimer",
        Regex(".*set.*alarm.*", RegexOption.IGNORE_CASE) to "SetAlarm",
        Regex(".*send.*message.*", RegexOption.IGNORE_CASE) to "SendMessage",
        Regex(".*call.*", RegexOption.IGNORE_CASE) to "CallContact",
        Regex(".*what.*weather.*", RegexOption.IGNORE_CASE) to "CheckWeather",
        Regex(".*check.*battery.*", RegexOption.IGNORE_CASE) to "CheckBattery",
        Regex(".*turn on.*wifi.*", RegexOption.IGNORE_CASE) to "TurnOnWifi",
        Regex(".*turn off.*wifi.*", RegexOption.IGNORE_CASE) to "TurnOffWifi",
        Regex(".*turn on.*bluetooth.*", RegexOption.IGNORE_CASE) to "TurnOnBluetooth",
        Regex(".*turn off.*bluetooth.*", RegexOption.IGNORE_CASE) to "TurnOffBluetooth",
        Regex(".*read.*notifications.*", RegexOption.IGNORE_CASE) to "ReadNotifications",
        Regex(".*what.*calendar.*", RegexOption.IGNORE_CASE) to "CheckCalendar"
    )

    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        launch {
            perceptionBus.subscribe(id)
                .filterIsInstance<Observation>()
                .flowOn(Dispatchers.Default)
                .collect { observation ->
                if (observation.modality == "Speech" && observation.rawData is String) {
                    val transcript = observation.rawData as String
                    
                    var matchedIntent = "UnknownIntent"
                    for ((regex, intent) in intentRules) {
                        if (regex.matches(transcript)) {
                            matchedIntent = intent
                            break
                        }
                    }
                    
                    val thought = Thought(
                        id = CognitiveId(),
                        created = Clock.System.now(),
                        confidence = observation.confidence,
                        provenance = Provenance(id, observation.id),
                        edges = listOf(CognitiveEdge(observation.id, EdgeType.Derived)),
                        content = matchedIntent
                    )
                    
                    val goal = com.halo.icl.ontology.Goal(
                        id = CognitiveId(),
                        created = Clock.System.now(),
                        confidence = observation.confidence,
                        provenance = Provenance(id, thought.id),
                        edges = listOf(CognitiveEdge(thought.id, EdgeType.Derived)),
                        desiredState = matchedIntent
                    )
                    
                    cognitiveBus.publish(id, thought)
                    cognitiveBus.publish(id, goal)
                }
            }
        }
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }
}
