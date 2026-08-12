package com.halo.drivers.heycyan.protocol

data class CommandDescriptor(
    val command: HeyCyanCommand,
    val commandId: Int?,
    val payload: ByteArray = byteArrayOf(),
    val mode: HeyCyanMode? = null,
    val status: ProtocolDefinitionStatus,
    val evidence: List<ProtocolEvidenceReference>,
    val reason: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommandDescriptor) return false
        return command == other.command &&
                commandId == other.commandId &&
                payload.contentEquals(other.payload) &&
                mode == other.mode &&
                status == other.status &&
                evidence == other.evidence &&
                reason == other.reason
    }

    override fun hashCode(): Int {
        var result = command.hashCode()
        result = 31 * result + (commandId ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (mode?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + evidence.hashCode()
        result = 31 * result + (reason?.hashCode() ?: 0)
        return result
    }

    fun toCommandDefinition(): HeyCyanCommandDefinition = HeyCyanCommandDefinition(
        command = command,
        commandId = commandId,
        payload = payload,
        evidence = evidence,
        status = status,
    )
}

object HeyCyanCommandCatalog {

    private val referenceEvidence = ProtocolEvidenceReference(
        source = "CyanBridge + HeyCyanSmartGlassesSDK AAR Disassembly",
        // Verified: Vendor AAR bytecode disassembly (glasses_sdk_20250723_v01.aar) establishes
        // complete wire framing (0xBC + Action + Length LE + CRC16 LE + Payload).
        locator = "LargeDataHandler.addHeader / GlassModelControl / CRC16.calcCrc16",
        kind = EvidenceKind.AUTHORITATIVE_SOURCE,
        confidence = ProtocolConfidence.HIGH,
    )

    val all: List<CommandDescriptor> = listOf(
        CommandDescriptor(
            command = HeyCyanCommand.GetBattery,
            commandId = 0x13,
            payload = byteArrayOf(),
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.GetVersion,
            commandId = 0x10,
            payload = byteArrayOf(),
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        // Semantic alias — GetDeviceInfo is the PR-025 capability-layer name for GetVersion.
        CommandDescriptor(
            command = HeyCyanCommand.GetDeviceInfo,
            commandId = 0x10,
            payload = byteArrayOf(),
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.GetMacAddress,
            commandId = 0x11,
            payload = byteArrayOf(),
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.GetMediaCounts,
            commandId = 0x14,
            payload = byteArrayOf(),
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.TakePhoto,
            commandId = 0x01,
            payload = byteArrayOf(0x01),
            mode = HeyCyanMode.PHOTO,
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.ToggleVideo,
            commandId = 0x01,
            payload = byteArrayOf(0x02),
            mode = HeyCyanMode.VIDEO,
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.PrepareVoiceRouting,
            commandId = 0x01,
            payload = byteArrayOf(0x02, 0x01, 0x0B),
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.EnterWifiTransferMode,
            commandId = 0x01,
            payload = byteArrayOf(0x02, 0x01, 0x04, 0x01),
            mode = HeyCyanMode.TRANSFER,
            status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
            evidence = listOf(referenceEvidence),
        ),

        CommandDescriptor(
            command = HeyCyanCommand.SetDateTime(),
            commandId = 0x12,
            payload = byteArrayOf(),
            status = ProtocolDefinitionStatus.BLOCKED,
            evidence = listOf(
                ProtocolEvidenceReference(
                    source = "SDK Forensics",
                    locator = "BLE_COMMANDS.md / SetDateTime",
                    kind = EvidenceKind.AUTHORITATIVE_SOURCE,
                    confidence = ProtocolConfidence.MEDIUM,
                )
            ),
            reason = "Timestamp payload binary format is unresolved",
        ),
    )

    fun definition(command: HeyCyanCommand): HeyCyanCommandDefinition? {
        return when (command) {
            is HeyCyanCommand.SetMode -> {
                val descriptor = all.firstOrNull { it.mode == command.mode }
                descriptor?.toCommandDefinition() ?: HeyCyanCommandDefinition(
                    command = command,
                    commandId = 0x01,
                    payload = byteArrayOf(command.mode.value.toByte()),
                    evidence = listOf(referenceEvidence),
                    status = ProtocolDefinitionStatus.VERIFIED_FROM_REFERENCE,
                )
            }
            is HeyCyanCommand.SetDateTime -> all.firstOrNull { it.command is HeyCyanCommand.SetDateTime }?.toCommandDefinition()
            else -> all.firstOrNull { it.command == command }?.toCommandDefinition()
        }
    }

    fun descriptor(command: HeyCyanCommand): CommandDescriptor? {
        return when (command) {
            is HeyCyanCommand.SetMode -> all.firstOrNull { it.mode == command.mode }
            is HeyCyanCommand.SetDateTime -> all.firstOrNull { it.command is HeyCyanCommand.SetDateTime }
            else -> all.firstOrNull { it.command == command }
        }
    }
}
