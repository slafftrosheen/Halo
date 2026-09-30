package com.halo.runtime.cognition.delegation

import com.halo.runtime.cognition.agent.AgentRunId

data class ResearchResult(val question: String, val findings: List<DelegatedTaskResult>, val evidence: List<ResearchEvidence>)

/** Two bounded evidence-gathering tasks; synthesis remains the parent's normal provider turn. */
class ResearchTaskCoordinator(private val delegation: DelegatedTaskCoordinator) {
    suspend fun research(parent: AgentRunId, invocationId: String, question: String,
        topics: List<String>, inputs: List<ContextReference>): ResearchResult {
        val tasks = topics.filter { it.isNotBlank() }.take(2).ifEmpty { listOf(question) }
        val results = tasks.mapIndexed { index, topic ->
            delegation.delegate(DelegatedTask("$invocationId:$index", parent, DelegatedRole.RESEARCH,
                "${question.take(400)}\nInvestigate: ${topic.take(400)}. Discover available sources, collect and read evidence, compare findings. Return bounded evidence with stable source references and open questions. Parent owns synthesis.", inputs))
        }
        return ResearchResult(question.take(1024), results, results.flatMap { it.evidence }.distinctBy { it.sourceRef }.take(16))
    }
}
