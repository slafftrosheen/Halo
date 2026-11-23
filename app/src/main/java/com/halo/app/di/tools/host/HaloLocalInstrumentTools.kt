package com.halo.app.di.tools.host

import android.app.KeyguardManager
import com.halo.app.data.toolkit.CalcUnit
import com.halo.app.data.toolkit.CalcUnitKind
import com.halo.app.data.toolkit.HaloCalcEngine
import com.halo.app.data.toolkit.HaloCurrencyRates
import com.halo.app.data.toolkit.HaloMapsStore
import com.halo.app.data.toolkit.HaloMeasurement
import com.halo.app.data.toolkit.HaloSavedPlace
import com.halo.app.data.toolkit.RoomWall
import com.halo.app.data.workspace.AdaptivePlanner
import com.halo.app.data.workspace.AdaptiveSlot
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Typed and spoken requests use the same numeric and local place owners as the three tool screens. */
internal class HaloLocalInstrumentTools(private val foregroundUnlocked: () -> Boolean = { false }) : ToolExecutor {
    override fun definitions(): List<ToolDefinition> = catalogue

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        val input = toolCall.arguments
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, name, reason, kind)
        if (name.startsWith("halo_maps_")) {
            if (coroutineContext[RunToolScope]?.targetIsLocal != true)
                return reject("Maps accepts only an on-device run", ToolRejectionKind.PERMISSION_REQUIRED)
            val family = Graph.kidsPreferences
            if (!family.loadedState.value || family.enabled.value)
                return reject("Maps requires the grown-up space", ToolRejectionKind.PERMISSION_REQUIRED)
            if (name != "halo_maps_distance" &&
                Graph.applicationContext.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false)
                return reject("Unlock the phone to read or change saved places and plans", ToolRejectionKind.PERMISSION_REQUIRED)
        }
        return try {
            val json = when (name) {
                "halo_page_plan_sections" -> {
                    if (coroutineContext[RunToolScope]?.targetIsLocal != true ||
                        !Graph.kidsPreferences.loadedState.value || Graph.kidsPreferences.enabled.value)
                        return reject("This layout planner is available in an adult on-device session.", ToolRejectionKind.PERMISSION_REQUIRED)
                    require(input.keys == setOf("request"))
                    val request = input.text("request").trim()
                    require(request.isNotEmpty() && request.none(Char::isISOControl))
                    val requested = AdaptivePlanner.select(request, AdaptiveSlot.entries.toSet(), 7)
                    JSONObject().put("sections", JSONArray(requested.map { it.name })).put("needsChoice", requested.isEmpty())
                }
                "halo_calc_evaluate" -> {
                    val result = HaloCalcEngine.calculate(input.text("expression"))
                    JSONObject().put("expression", result.expression).put("value", result.result.stripTrailingZeros().toString())
                }
                "halo_calc_convert" -> {
                    val from = CalcUnit.valueOf(input.text("from"))
                    val to = CalcUnit.valueOf(input.text("to"))
                    val rate = input["rate"] as? String
                    val currency = from.kind == CalcUnitKind.CURRENCY && to.kind == CalcUnitKind.CURRENCY && from != to
                    val reference = if (currency && rate == null && !Graph.kidsPreferences.enabled.value)
                        HaloCurrencyRates.saved(Graph.applicationContext) else null
                    val result = HaloCalcEngine.convert(input.text("expression"), from, to, rate ?: reference?.crossRate(from, to))
                    JSONObject().put("value", result.stripTrailingZeros().toString()).put("unit", to.symbol)
                        .put("manualRate", currency && rate != null).apply {
                            if (reference != null) put("referenceDate", reference.date.toString()).put("referenceSource", "ECB")
                        }
                }
                "halo_calc_graph" -> {
                    val samples = HaloCalcEngine.graph(input.text("formula"), input.number("from"), input.number("through"), 81)
                    JSONObject().put("points", JSONArray(samples.map { sample ->
                        JSONObject().put("x", sample.x).put("y", sample.y ?: JSONObject.NULL)
                    }))
                }
                "halo_measure_estimate" -> JSONObject().put("centimetres", HaloMeasurement.imageEstimate(
                    input.number("reference_cm"), input.number("reference_px"), input.number("measured_px"),
                )).put("estimated", true)
                "halo_measure_outline" -> {
                    val walls = input["walls"] as? List<*> ?: throw IllegalArgumentException("Walls are required")
                    require(walls.size in 1..120)
                    val room = HaloMeasurement.roomPoints(walls.map { value ->
                        val map = value as? Map<*, *> ?: throw IllegalArgumentException("Invalid wall")
                        require(map.keys == setOf("metres", "turn"))
                        val turn = map["turn"] as? String
                        require(turn == "LEFT" || turn == "RIGHT")
                        RoomWall((map["metres"] as? Number)?.toDouble() ?: throw IllegalArgumentException("Wall length required"),
                            turn == "LEFT")
                    })
                    JSONObject().put("points", JSONArray(room.map { JSONObject().put("x", it.x).put("y", it.y) }))
                        .put("units", "m")
                }
                "halo_maps_distance" -> JSONObject().put("km", HaloMapsStore.straightLineKm(
                    HaloMapsStore.coordinate(input.text("from")), HaloMapsStore.coordinate(input.text("to")),
                )).put("basis", "straight-line")
                "halo_maps_saved_places" -> {
                    val query = (input["query"] as? String)?.trim().orEmpty()
                    require(query.length <= 120)
                    val places = HaloMapsStore.currentPlaces().filter { it.title.contains(query, ignoreCase = true) }.take(40)
                    JSONObject().put("items", JSONArray(places.map(::placeJson)))
                }
                "halo_maps_keep_place" -> placeJson(HaloMapsStore.keep(input.text("title"), HaloMapsStore.coordinate(input.text("coordinates"))))
                "halo_maps_leave_by" -> {
                    val next = HaloMapsStore.nextDeparture()
                    if (next == null) JSONObject().put("found", false)
                    else JSONObject().put("found", true).put("eventId", next.eventId).put("eventVersion", next.eventVersion)
                        .put("title", next.title).put("departAt", next.departAt.toString())
                }
                "halo_maps_arrived" -> {
                    val place = HaloMapsStore.readPlace(input.text("place_id"))
                        ?.takeIf { it.version == input.text("place_version") }
                        ?: return reject("The saved place changed. Find the current place before opening its plans.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    val arrival = HaloMapsStore.arrived(place)
                    JSONObject().put("placeId", place.id).put("placeVersion", place.version)
                        .put("upcoming", JSONArray(arrival.upcoming.map(::eventJson)))
                        .put("previous", JSONArray(arrival.previous.map(::eventJson)))
                }
                "halo_maps_go_home" -> {
                    if (input.isNotEmpty()) return reject("The saved Home place is selected on the phone.", ToolRejectionKind.INVALID_ARGUMENTS)
                    val family = Graph.kidsPreferences
                    val revision = family.adultActionRevision()
                    if (!foregroundUnlocked()) return reject("Open Halo on the unlocked adult phone.", ToolRejectionKind.PERMISSION_REQUIRED)
                    val place = HaloMapsStore.homePlace() ?: return reject("Choose a saved Home place in halo maps first.",
                        ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    val route = com.halo.app.ui.shell.HaloDeepLinks.PREFIX +
                        "maps/offline?placeId=${android.net.Uri.encode(place.id)}"
                    val dispatched = withContext(Dispatchers.Main.immediate) {
                        family.requireAdultAction(revision)
                        Graph.haloPause.requireActive()
                        foregroundUnlocked() && Graph.requestedHaloScreen.compareAndSet(null, route)
                    }
                    if (!dispatched) return reject("The phone cannot open a new Maps page right now.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    return ToolExecutionResult.Success(toolCall.id, name, JSONObject().put("opened", "maps").toString(),
                        ActionTruth.DISPATCHED)
                }
                else -> return reject("Unknown calculator, measurement or maps operation", ToolRejectionKind.TOOL_UNKNOWN)
            }
            ToolExecutionResult.Success(toolCall.id, name, json.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { reject("The inputs are outside the supported format or range", ToolRejectionKind.INVALID_ARGUMENTS) }
        catch (_: ArithmeticException) { reject("The calculation is undefined or exceeds the supported range", ToolRejectionKind.INVALID_ARGUMENTS) }
        catch (_: Exception) { reject("The operation could not finish on this phone", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
    }

    private fun placeJson(place: HaloSavedPlace) = JSONObject().put("id", place.id).put("version", place.version)
        .put("title", place.title).put("latitude", place.coordinate.latitude).put("longitude", place.coordinate.longitude)

    private fun eventJson(event: OrganizerOccurrence) = JSONObject().put("id", event.record.id)
        .put("version", event.record.version).put("title", event.record.title)
        .put("start", event.start.toString()).put("allDay", event.allDay)

    private fun Map<String, Any?>.text(key: String): String = requireNotNull(this[key] as? String)
    private fun Map<String, Any?>.number(key: String): Double =
        requireNotNull((this[key] as? Number)?.toDouble()?.takeIf(Double::isFinite))

    companion object {
        private val expression = mapOf("type" to "string", "maxLength" to 180)
        private val coordinate = mapOf("type" to "string", "maxLength" to 48)
        private val number = mapOf("type" to "number")
        private val unit = mapOf("type" to "string", "enum" to CalcUnit.entries.map { it.name })

        private fun definition(id: String, description: String, args: Map<String, Any>, vararg required: String) =
            ToolDefinition(id, id, description, mapOf("type" to "object", "properties" to args, "required" to required.toList(), "additionalProperties" to false))

        private val catalogue = listOf(
            definition("halo_page_plan_sections", "Suggest an ordered list of local Today sections for an adult's request. Use for made-for-you and page layout requests. This does not fetch content, save a page or open a screen.",
                mapOf("request" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 280)), "request"),
            definition("halo_calc_evaluate", "Evaluate an arithmetic expression or a supported phrase such as 15% of 84. Scientific angles are radians.",
                mapOf("expression" to expression), "expression"),
            definition("halo_calc_convert", "Convert compatible units. Currency uses the dated ECB table saved on this phone or an explicit rate supplied by the user. Do not invent a rate.",
                mapOf("expression" to expression, "from" to unit, "to" to unit, "rate" to mapOf("type" to "string", "maxLength" to 32)),
                "expression", "from", "to"),
            definition("halo_calc_graph", "Calculate sampled points of one expression in x. Undefined points have null y.",
                mapOf("formula" to expression, "from" to number, "through" to number), "formula", "from", "through"),
            definition("halo_measure_estimate", "Estimate length from a known reference and two pixel lengths on a shared plane. Use actual marked image distances, never guess from the image.",
                mapOf("reference_cm" to number, "reference_px" to number, "measured_px" to number),
                "reference_cm", "reference_px", "measured_px"),
            definition("halo_measure_outline", "Construct a right-angle outline from a reviewed sequence of measured walls. Each wall turns using the specified direction.",
                mapOf("walls" to mapOf("type" to "array", "maxItems" to 120, "items" to mapOf(
                    "type" to "object", "properties" to mapOf("metres" to number, "turn" to mapOf("type" to "string", "enum" to listOf("LEFT", "RIGHT"))),
                    "required" to listOf("metres", "turn"), "additionalProperties" to false))), "walls"),
            definition("halo_maps_distance", "Calculate the straight-line distance between two explicitly provided latitude/longitude pairs on this phone.",
                mapOf("from" to coordinate, "to" to coordinate), "from", "to"),
            definition("halo_maps_saved_places", "Find existing local Library places by name. Access is limited to an on-device adult session.",
                mapOf("query" to mapOf("type" to "string", "maxLength" to 120))),
            definition("halo_maps_keep_place", "Keep an explicitly reviewed place name and coordinate pair in Library. Always requests confirmation.",
                mapOf("title" to mapOf("type" to "string", "maxLength" to 120), "coordinates" to coordinate), "title", "coordinates"),
            definition("halo_maps_leave_by", "Read the next timed Planner event with a saved departure lead time on this phone. No route or traffic estimate.",
                emptyMap()),
            definition("halo_maps_arrived", "Show upcoming and previous Planner events at an explicitly selected saved place. Opening it does not detect or track location.",
                mapOf("place_id" to mapOf("type" to "string", "maxLength" to 256),
                    "place_version" to mapOf("type" to "string", "maxLength" to 40)), "place_id", "place_version"),
            definition("halo_maps_go_home", "For take me home, open offline Maps to the Home place chosen by the person. It does not start location or navigation.",
                emptyMap()),
        )

        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            val id = definition.name
            if (catalogue.none { it.name == id }) return null
            val writes = id == "halo_maps_keep_place"
            val presents = id == "halo_maps_go_home"
            val capability = CapabilityId(if (writes) "workspace.write" else "workspace.read")
            val shape = when (id) {
                "halo_page_plan_sections" -> mapOf("sections" to "array", "needsChoice" to "boolean")
                "halo_calc_evaluate" -> mapOf("expression" to "string", "value" to "string")
                "halo_calc_convert" -> mapOf("value" to "string", "unit" to "string", "manualRate" to "boolean",
                    "referenceDate" to "string", "referenceSource" to "string")
                "halo_calc_graph" -> mapOf("points" to "array")
                "halo_measure_estimate" -> mapOf("centimetres" to "number", "estimated" to "boolean")
                "halo_measure_outline" -> mapOf("points" to "array", "units" to "string")
                "halo_maps_distance" -> mapOf("km" to "number", "basis" to "string")
                "halo_maps_saved_places" -> mapOf("items" to "array")
                "halo_maps_keep_place" -> mapOf("id" to "string", "version" to "string", "title" to "string", "latitude" to "number", "longitude" to "number")
                "halo_maps_arrived" -> mapOf("placeId" to "string", "placeVersion" to "string", "upcoming" to "array", "previous" to "array")
                "halo_maps_go_home" -> mapOf("opened" to "string")
                else -> mapOf("found" to "boolean", "eventId" to "string", "eventVersion" to "integer",
                    "title" to "string", "departAt" to "string")
            }
            val owner = when {
                id == "halo_page_plan_sections" -> "AdaptivePlanner"
                id.startsWith("halo_calc") -> "HaloCalcEngine"
                id.startsWith("halo_measure") -> "HaloMeasurement"
                else -> "HaloMapsStore"
            }
            return ToolDescriptor(
                id = ToolId(id), definition = definition, category = when {
                    writes -> ToolCategory.WORKSPACE
                    presents -> ToolCategory.HOST_CONTROL
                    else -> ToolCategory.INFORMATION
                },
                keywords = when (owner) {
                    "AdaptivePlanner" -> setOf("make a page", "made for you", "page layout", "собери страницу")
                    "HaloCalcEngine" -> setOf("calc", "calculator", "graph", "convert", "вычислить", "калькулятор")
                    "HaloMeasurement" -> setOf("measure", "room", "estimate", "линейка", "измерить")
                    else -> if (presents) setOf("maps", "take me home", "карты", "домой")
                        else setOf("maps", "places", "coordinates", "карты", "координаты", "места")
                },
                requirements = setOf(ToolRequirement.Capability(capability)),
                security = if (writes) ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY)
                    else if (presents) ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.USER_POLICY, ReplaySafety.NEVER_REPLAY)
                    else ToolSecurityPolicy.READ_ONLY,
                execution = ToolExecutionPolicy(maxOutputChars = when (id) {
                    "halo_calc_graph", "halo_measure_outline", "halo_maps_arrived" -> 8_000
                    "halo_maps_saved_places" -> 24_000
                    else -> ToolExecutionPolicy.DEFAULT_MAX_OUTPUT
                }),
                presentation = if (writes) ToolPresentation(title = "Keep place", actionVerb = "Keep", targetArgument = "title") else null,
                operability = ToolOperability(capabilityId = capability, owner = owner,
                    operation = if (writes) ToolOperation.CREATE else if (presents) ToolOperation.PRESENT else ToolOperation.QUERY,
                    resultEncoding = ToolResultEncoding.JSON,
                    resultSchema = mapOf("type" to "object", "properties" to shape.mapValues { mapOf("type" to it.value) },
                        "required" to when (id) {
                            "halo_maps_leave_by" -> listOf("found")
                            "halo_calc_convert" -> listOf("value", "unit", "manualRate")
                            else -> shape.keys.toList()
                        }, "additionalProperties" to false),
                    sourceVersionArguments = if (id == "halo_maps_arrived") setOf("place_version") else emptySet(),
                    resultIdFields = if (writes) setOf("id") else emptySet(),
                    limitations = when (owner) {
                        "AdaptivePlanner" -> setOf("The planning result contains section identifiers. Today resolves the current collector data separately.")
                        "HaloCalcEngine" -> setOf("Currency conversion uses a saved dated ECB reference rate or a rate supplied by the person. This tool does not fetch rates.")
                        "HaloMeasurement" -> setOf("Camera point placement and phone level are on-device interactions. Typed measures require explicit numeric inputs.")
                        else -> setOf("Adult local runs only. Organic Maps provides offline routing and region downloads. Live departures are off.")
                    }),
            )
        }
    }
}
