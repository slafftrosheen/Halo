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
        val receipt = session.presented(token, listOf("Test page"))
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
        session.presented(token, listOf("Test page"))
        session.refresh(true, listOf(display), setOf(7))
        check(session.resolve(token) == null && session.state.activeToken == null)
        token = session.state.endpoints.single().token
    }
    verify("background clears output and resume never replays it") {
        session.presented(token, listOf("Test page"))
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
        session.presented(token, listOf("Test page"))
        session.refresh(true, (1..30).map { display.copy(id = it, name = "n".repeat(300)) }, setOf(2))
        check(session.state.activeToken == token && session.resolve(token) == 1)
    }
    verify("pagination preserves words, line endings and all text") {
        val source = "a".repeat(210) + "\r\n" + "word ".repeat(150)
        val pages = requireNotNull(ExternalDisplaySession.paginate(source))
        check(pages.first().endsWith("\n") && pages.all { it.length in 1..400 })
        check(pages.joinToString("") == source.replace("\r\n", "\n"))
        check(pages == ExternalDisplaySession.paginate(source))
    }
    verify("pagination never splits an emoji surrogate pair") {
        val source = "x".repeat(399) + "😀" + "y".repeat(399)
        val pages = requireNotNull(ExternalDisplaySession.paginate(source))
        check(pages.first().length == 399 && pages.joinToString("") == source)
        check(pages.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
    }
    verify("pagination bounds long words, maximum documents and invalid text") {
        val pages = requireNotNull(ExternalDisplaySession.paginate("a".repeat(8000)))
        check(pages.size == 20 && pages.all { it.length == 400 })
        check(ExternalDisplaySession.paginate("a".repeat(8001)) == null)
        check(ExternalDisplaySession.paginate(" \n\t") == null)
    }
    verify("multiline text has bounded page height and excessive blank pages are rejected") {
        val source = (1..100).joinToString("\n") { "Line $it" }
        val pages = requireNotNull(ExternalDisplaySession.paginate(source))
        check(pages.all { it.count { ch -> ch == '\n' } < ExternalDisplaySession.MAX_PAGE_LINES })
        check(pages.joinToString("") == source)
        check(ExternalDisplaySession.paginate("\n".repeat(1000) + "end") == null)
    }
    verify("page navigation uses the exact active token, revision and page") {
        val pages = requireNotNull(ExternalDisplaySession.paginate("z".repeat(1000)))
        session.presented(token, pages)
        val revision = session.state.revision
        check(session.state.currentPage == 1 && session.state.pageCount == 3)
        check(session.pageText("other-token", 2, revision) == null)
        check(session.pageText(token, 0, revision) == null && session.pageText(token, 4, revision) == null)
        check(session.pageText(token, 2, revision) == pages[1])
        val receipt = session.pagePresented(token, 2, revision)
        check(receipt.status == "DISPATCHED" && receipt.currentPage == 2 && receipt.pageCount == 3)
        check(session.pageText(token, 3, revision) == null)
        check(session.pageText(token, 3, receipt.sourceVersion) == pages[2])
    }
    verify("replacement invalidates prior page authority even on the same display") {
        val revision = session.state.revision
        session.presented(token, listOf("Replacement"))
        check(session.state.currentPage == 1 && session.state.pageCount == 1)
        check(session.pageText(token, 1, revision) == null)
    }
    verify("clear discards text and page metadata without disconnecting the endpoint") {
        session.cleared()
        check(session.state.currentPage == 0 && session.state.pageCount == 0 && session.resolve(token) != null)
        check(session.pageText(token, 1, session.state.revision) == null)
    }
    verify("background discards the document as well as its output token") {
        session.presented(token, listOf("Private transient text"))
        session.refresh(false, emptyList())
        check(session.state.pageCount == 0 && session.state.currentPage == 0)
        check(session.pageText(token, 1, session.state.revision) == null)
    }
    println("Device expansion checks: $passed passed")
}
