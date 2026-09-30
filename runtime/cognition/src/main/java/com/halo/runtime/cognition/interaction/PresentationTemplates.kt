package com.halo.runtime.cognition.interaction

/**
 * MINING-P2-001 presentation recipes.
 *
 * Two producers feed the closed catalog:
 *
 * 1. **Templates** — what a model asks for through `halo_present`. A 0.6B model supplies a tiny flat
 *    vocabulary (`template` + lists), never a nested component tree.
 * 2. **Adapters** — deterministic Runtime projections of known results (device status, model status,
 *    web extraction, memory search, MCP discovery). A model does not have to be involved at all.
 *
 * Both are pure and bounded; neither can express authority.
 */
object PresentationTemplates {

    /** Expands a model-supplied directive into nodes. Callers must validate the result. */
    fun fromDirective(directive: PresentationDirective): StructuredPresentation {
        val root = when (directive.template) {
            PresentationTemplate.KEY_VALUE_SUMMARY -> keyValueSummary(
                title = directive.title,
                entries = directive.entries,
                subtitle = directive.subtitle,
                conclusion = directive.conclusion,
            )
            PresentationTemplate.STATUS -> status(
                title = directive.title ?: directive.entries.firstOrNull()?.label ?: "Status",
                state = directive.status,
                detail = directive.subtitle ?: directive.entries.firstOrNull()?.value,
                progress = directive.progress,
                conclusion = directive.conclusion,
            )
            PresentationTemplate.COMPARISON -> comparison(
                title = directive.title,
                columns = directive.columns,
                rows = directive.entries.map { listOf(it.label, it.value) },
                conclusion = directive.conclusion,
            )
            PresentationTemplate.CHECKLIST -> checklist(
                title = directive.title,
                items = directive.items,
                conclusion = directive.conclusion,
            )
            PresentationTemplate.SEARCH_RESULTS -> searchResults(
                title = directive.title,
                items = directive.items,
                conclusion = directive.conclusion,
            )
            PresentationTemplate.CHOICE -> choice(
                prompt = directive.title ?: "Choose an option",
                inputRequestId = directive.inputRequestId.orEmpty(),
                options = directive.entries.map { entry ->
                    PresentationChoice(optionId = entry.label, label = entry.value)
                },
                allowMultiple = directive.allowMultiple,
            )
            PresentationTemplate.CUSTOM -> StructuredPresentation(
                root = directive.customRoot ?: textCard(
                    title = directive.title,
                    lines = listOfNotNull(directive.conclusion, directive.subtitle),
                ),
            )
        }
        return root
    }

    // ---- template builders -------------------------------------------------

    fun keyValueSummary(
        title: String?,
        entries: List<PresentationDirectiveEntry>,
        subtitle: String? = null,
        conclusion: String? = null,
    ): StructuredPresentation {
        val children = mutableListOf<PresentationNode>()
        title?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Heading(nodeId = "title", text = it)
        }
        subtitle?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Caption(nodeId = "subtitle", text = it)
        }
        if (entries.isNotEmpty()) children += PresentationNode.Divider(nodeId = "divider-head")
        entries.forEachIndexed { index, entry ->
            children += PresentationNode.KeyValue(
                nodeId = "kv-$index",
                label = entry.label,
                value = entry.value,
            )
        }
        conclusion?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Divider(nodeId = "divider-tail")
            children += PresentationNode.Text(nodeId = "conclusion", text = it)
        }
        return StructuredPresentation(
            root = PresentationNode.Card(nodeId = "root", children = children, title = null),
        )
    }

    fun status(
        title: String,
        state: PresentationStatusState,
        detail: String? = null,
        progress: Float? = null,
        conclusion: String? = null,
    ): StructuredPresentation {
        val children = mutableListOf<PresentationNode>(
            PresentationNode.Status(nodeId = "status", label = title, state = state, detail = detail),
        )
        if (progress != null) {
            children += PresentationNode.Progress(
                nodeId = "progress",
                label = title,
                progress = progress,
                state = state,
            )
        }
        conclusion?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Text(nodeId = "conclusion", text = it)
        }
        return StructuredPresentation(root = PresentationNode.Card(nodeId = "root", children = children))
    }

    fun comparison(
        title: String?,
        columns: List<String>,
        /** Each row is `[label, cell...]`; the label column is added to [PresentationNode.Comparison.columns]. */
        rows: List<List<String>>,
        conclusion: String? = null,
    ): StructuredPresentation {
        val children = mutableListOf<PresentationNode>()
        title?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Heading(nodeId = "title", text = it)
        }
        children += PresentationNode.Comparison(
            nodeId = "comparison",
            columns = columns,
            rows = rows,
        )
        conclusion?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Divider(nodeId = "divider-tail")
            children += PresentationNode.Text(nodeId = "conclusion", text = it)
        }
        return StructuredPresentation(root = PresentationNode.Card(nodeId = "root", children = children))
    }

    fun checklist(
        title: String?,
        items: List<PresentationDirectiveItem>,
        conclusion: String? = null,
    ): StructuredPresentation {
        val children = mutableListOf<PresentationNode>()
        title?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Heading(nodeId = "title", text = it)
        }
        children += PresentationNode.Checklist(
            nodeId = "checklist",
            items = items.mapIndexed { index, item ->
                PresentationChecklistItem(
                    stepId = "step-$index",
                    text = item.text,
                    state = item.state,
                )
            },
        )
        conclusion?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Text(nodeId = "conclusion", text = it)
        }
        return StructuredPresentation(root = PresentationNode.Card(nodeId = "root", children = children))
    }

    fun searchResults(
        title: String?,
        items: List<PresentationDirectiveItem>,
        conclusion: String? = null,
    ): StructuredPresentation {
        val children = mutableListOf<PresentationNode>()
        title?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Heading(nodeId = "title", text = it)
        }
        if (items.isNotEmpty()) {
            children += PresentationNode.BulletList(
                nodeId = "results",
                items = items.map { it.text },
            )
        }
        conclusion?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Text(nodeId = "conclusion", text = it)
        }
        if (children.isEmpty()) {
            children += PresentationNode.Text(nodeId = "empty", text = "No matching results.")
        }
        return StructuredPresentation(root = PresentationNode.Card(nodeId = "root", children = children))
    }

    /**
     * A choice surface. It resolves a durable input request only; option ids are supplied by the
     * Runtime from that request, never invented by a model.
     */
    fun choice(
        prompt: String,
        inputRequestId: String,
        options: List<PresentationChoice>,
        allowMultiple: Boolean = false,
    ): StructuredPresentation = StructuredPresentation(
        root = PresentationNode.ChoiceGroup(
            nodeId = "root",
            inputRequestId = inputRequestId,
            prompt = prompt,
            options = options,
            allowMultiple = allowMultiple,
        ),
    )

    // ---- deterministic result adapters ---------------------------------------

    /** Device status card from already-known values. Unknown values stay visibly unknown. */
    fun deviceStatus(
        heading: String,
        batteryPercent: String?,
        charging: String?,
        storageFree: String?,
        extraFacts: List<Pair<String, String>> = emptyList(),
    ): StructuredPresentation {
        val entries = buildList {
            add(PresentationDirectiveEntry("Battery", batteryPercent ?: "unknown"))
            add(PresentationDirectiveEntry("Charging", charging ?: "unknown"))
            add(PresentationDirectiveEntry("Storage free", storageFree ?: "unknown"))
            extraFacts.forEach { (label, value) -> add(PresentationDirectiveEntry(label, value)) }
        }
        return keyValueSummary(title = heading, entries = entries)
    }

    fun modelStatus(
        modelName: String,
        state: PresentationStatusState,
        facts: List<Pair<String, String>>,
        progress: Float? = null,
    ): StructuredPresentation {
        val children = mutableListOf<PresentationNode>(
            PresentationNode.Heading(nodeId = "title", text = modelName),
            PresentationNode.Status(nodeId = "status", label = modelName, state = state),
        )
        if (progress != null) {
            children += PresentationNode.Progress(nodeId = "progress", label = modelName, progress = progress, state = state)
        }
        facts.forEachIndexed { index, (label, value) ->
            children += PresentationNode.KeyValue(nodeId = "fact-$index", label = label, value = value)
        }
        return StructuredPresentation(root = PresentationNode.Card(nodeId = "root", children = children))
    }

    /** Web extraction result card. Content is untrusted external data and stays text-only. */
    fun webResult(
        title: String,
        domain: String,
        readableChars: Int,
        linkCount: Int,
        extractionMode: String,
        conclusion: String? = null,
    ): StructuredPresentation = keyValueSummary(
        title = title,
        subtitle = domain,
        entries = listOf(
            PresentationDirectiveEntry("Readable text", "$readableChars characters"),
            PresentationDirectiveEntry("Links", linkCount.toString()),
            PresentationDirectiveEntry("Extraction", extractionMode),
        ),
        conclusion = conclusion,
    )

    fun memoryResults(
        title: String,
        entries: List<PresentationDirectiveEntry>,
        conclusion: String? = null,
    ): StructuredPresentation = keyValueSummary(title = title, entries = entries, conclusion = conclusion)

    fun mcpDiscovery(
        title: String,
        servers: List<PresentationDirectiveEntry>,
    ): StructuredPresentation = keyValueSummary(title = title, entries = servers)

    private fun textCard(title: String?, lines: List<String>): PresentationNode.Card {
        val children = mutableListOf<PresentationNode>()
        title?.takeIf { it.isNotBlank() }?.let {
            children += PresentationNode.Heading(nodeId = "title", text = it)
        }
        lines.filter { it.isNotBlank() }.forEachIndexed { index, line ->
            children += PresentationNode.Text(nodeId = "text-$index", text = line)
        }
        if (children.isEmpty()) children += PresentationNode.Text(nodeId = "text-0", text = "Nothing to show.")
        return PresentationNode.Card(nodeId = "root", children = children)
    }
}
