package checks

import com.halo.sdk.runtime.hardware.*

fun main() {
    var passed = 0
    fun verify(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
    var next = 0
    val inventory = PeripheralInventorySession { "token-${++next}" }
    val pen = PeripheralObservation("input:12", PeripheralProfile(PeripheralKind.INPUT, "Tablet",
        roles = setOf(InputRole.STYLUS), axes = listOf(InputAxisReport(2, 16386, 0f, 4f, 0f, 0f, 1f)), axisCount = 1))
    val usb = PeripheralObservation("usb:/private/os/path", PeripheralProfile(PeripheralKind.USB, "USB device", usbClass = 3))
    fun update(items: List<PeripheralObservation> = listOf(pen, usb), invalidated: Set<String> = emptySet()) = inventory.update(items,
        InventoryAvailability.AVAILABLE, InventoryAvailability.AVAILABLE, items.count { it.profile.kind == PeripheralKind.INPUT },
        items.count { it.profile.kind == PeripheralKind.USB }, invalidated)
    update()
    verify("input and USB endpoints remain separate even when names match") {
        check(inventory.state.devices.size == 2)
        check(inventory.state.devices.map { it.token }.distinct().size == 2)
    }
    verify("snapshot excludes raw OS paths") { check(!inventory.state.toString().contains("/private/os/path")) }
    verify("USB HID class does not imply a pen or a qualified driver") {
        check(inventory.state.devices.single { it.profile.kind == PeripheralKind.USB }.profile.roles.isEmpty())
    }
    verify("unchanged reads preserve version and tokens") {
        val before = inventory.state
        update()
        check(inventory.state == before)
    }
    verify("detail selection requires both exact token and revision") {
        val token = inventory.state.devices.first().token
        check(inventory.find(token, inventory.state.sourceVersion) != null)
        check(inventory.find(token, inventory.state.sourceVersion - 1) == null)
        check(inventory.find("input:12", inventory.state.sourceVersion) == null)
    }
    verify("input-change event invalidates token without changing advertised metadata") {
        val old = inventory.state.devices.first { it.profile.kind == PeripheralKind.INPUT }.token
        update(invalidated = setOf("input:12"))
        check(inventory.find(old, inventory.state.sourceVersion) == null)
    }
    verify("reused device identifiers cannot revive disconnected tokens") {
        val old = inventory.state.devices.first().token
        update(emptyList())
        update()
        check(inventory.find(old, inventory.state.sourceVersion) == null)
    }
    verify("background clears inventory and does not restore old authority") {
        val old = inventory.state.devices.first().token
        inventory.stop()
        check(inventory.state.devices.isEmpty() && inventory.state.inputAvailability == InventoryAvailability.BACKGROUND)
        update()
        check(inventory.find(old, inventory.state.sourceVersion) == null)
    }
    verify("invalid advertised axes remain unavailable instead of breaking JSON") {
        val invalid = pen.copy(profile = pen.profile.copy(axes = pen.profile.axes + InputAxisReport(25, 16386, Float.NaN, 1f, 0f, 0f, 0f), axisCount = 2))
        update(listOf(invalid))
        val profile = inventory.state.devices.single().profile
        check(profile.axes.size == 1 && profile.detailsTruncated)
        check(profile.axes.single().max == 4f)
    }
    verify("inventory, labels and technical detail collections are bounded") {
        val endpoint = UsbEndpointReport(1, 128, 3, 64, 1)
        val iface = UsbInterfaceReport(1, 0, 3, 0, 0, List(20) { endpoint }, 20)
        val many = (1..70).map { usb.copy(platformKey = "usb:$it", profile = usb.profile.copy(name = "x".repeat(300),
            interfaces = List(20) { iface }, interfaceCount = 20)) }
        update(many)
        check(inventory.state.devices.size == 48 && inventory.state.usbCount == 70 && inventory.state.truncated)
        check(inventory.state.devices.all { it.profile.name.length == 120 && it.profile.interfaces.size == 8 &&
            it.profile.interfaces.all { i -> i.endpoints.size == 8 } && it.profile.detailsTruncated })
    }
    verify("unavailable service cannot retain a previously observed device") {
        inventory.update(listOf(pen, usb), InventoryAvailability.UNAVAILABLE, InventoryAvailability.AVAILABLE, 0, 1)
        check(inventory.state.devices.all { it.profile.kind == PeripheralKind.USB })
    }
    verify("snapshot takes ownership of caller-provided lists") {
        val axes = pen.profile.axes.toMutableList()
        update(listOf(pen.copy(profile = pen.profile.copy(axes = axes))))
        axes.clear()
        check(inventory.state.devices.single().profile.axes.size == 1)
    }
    verify("omitted collection counts cannot hide detail truncation") {
        val raw = PeripheralProfile(PeripheralKind.INPUT, "Large report", axes = List(20) { pen.profile.axes.single() },
            interfaces = List(10) { UsbInterfaceReport(1, 0, 3, 0, 0, List(12) { UsbEndpointReport(1, 128, 3, 64, 1) }, 0) })
        val bounded = PeripheralInventorySession.boundedProfile(raw)
        check(bounded.detailsTruncated && bounded.axisCount == 20 && bounded.interfaceCount == 10)
        check(bounded.interfaces.all { it.endpointCount == 12 && it.endpoints.size == 8 })
    }
    println("Peripheral inventory checks: $passed passed")
}
