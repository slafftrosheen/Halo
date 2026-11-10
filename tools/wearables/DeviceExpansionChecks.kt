package checks

import com.halo.sdk.runtime.display.*
import com.halo.sdk.runtime.embodiment.*

fun main() {
    var passed = 0
    fun verify(name: String, test: () -> Unit) { test(); passed++; println("PASS $name") }
    val registry = DeviceAdapterRegistry(listOf(DeviceKind.EYEVUE_GLASSES to "eye", DeviceKind.TUNEBUDS_GLASSES to "tune"))
    verify("explicit registered adapters route to their own factory") {
        check(registry.requireAdapter(DeviceKind.EYEVUE_GLASSES) == "eye")
        check(registry.requireAdapter(DeviceKind.TUNEBUDS_GLASSES) == "tune")
    }
    verify("unsupported families never fall through to another driver") {
        check(!registry.supports(DeviceKind.MYVU_GLASSES))
        check(runCatching { registry.requireAdapter(DeviceKind.MYVU_GLASSES) }.exceptionOrNull() is UnsupportedOperationException)
    }
    verify("duplicate adapter ownership is rejected") {
        check(runCatching { DeviceAdapterRegistry(listOf(DeviceKind.EYEVUE_GLASSES to 1, DeviceKind.EYEVUE_GLASSES to 2)) }.isFailure)
    }
    verify("unknown devices cannot register a fallback driver") {
        check(runCatching { DeviceAdapterRegistry(listOf(DeviceKind.UNKNOWN to 1)) }.isFailure)
    }
    var tokenCounter = 0
    val session = ExternalDisplaySession { "connection-${++tokenCounter}" }
    val display = ExternalDisplayConnection(7, "External display", 1920, 1080)
    session.refresh(true, listOf(display))
    var token = session.state.endpoints.single().token
    verify("discovery creates no output and requires explicit token") {
        check(session.state.activeToken == null)
        check(session.resolve("7") == null)
        check(session.resolve(token) == 7)
    }
    verify("unchanged inventory preserves token and source revision") {
        val old = session.state
        session.refresh(true, listOf(display))
        check(session.state == old)
    }
    verify("stale revisions cannot select a display") {
        check(session.resolve(token, session.state.revision - 1) == null)
        check(session.resolve(token, session.state.revision) == 7)
    }
    verify("submission is dispatched and increments source version") {
        val before = session.state.revision
        val receipt = session.presented(token)
        check(receipt.status == "DISPATCHED" && receipt.sourceVersion > before)
        check(session.state.activeToken == token)
    }
    verify("disconnect clears output and reused Android IDs get new tokens") {
        session.refresh(true, emptyList())
        check(session.state.activeToken == null && session.resolve(token) == null)
        session.refresh(true, listOf(display))
        check(session.state.endpoints.single().token != token && session.state.activeToken == null)
        token = session.state.endpoints.single().token
    }
    verify("display-change callbacks invalidate even an unchanged name and size") {
        session.presented(token)
        session.refresh(true, listOf(display), setOf(7))
        check(session.resolve(token) == null && session.state.activeToken == null)
        token = session.state.endpoints.single().token
    }
    verify("background clears output and resume never replays it") {
        session.presented(token)
        session.refresh(false, listOf(display))
        check(session.state.endpoints.isEmpty() && session.resolve(token) == null && session.state.activeToken == null)
        session.refresh(true, listOf(display))
        check(session.resolve(token) == null && session.state.activeToken == null)
    }
    verify("inventory is bounded and names are bounded data") {
        session.refresh(true, (1..30).map { display.copy(id = it, name = "n".repeat(300)) })
        check(session.state.endpoints.size == 8 && session.state.endpoints.all { it.name.length == 120 })
    }
    verify("changing another display does not move or erase the selected output") {
        token = session.state.endpoints.first().token
        session.presented(token)
        session.refresh(true, (1..30).map { display.copy(id = it, name = "n".repeat(300)) }, setOf(2))
        check(session.state.activeToken == token && session.resolve(token) == 1)
    }
    println("Device expansion checks: $passed passed")
}
