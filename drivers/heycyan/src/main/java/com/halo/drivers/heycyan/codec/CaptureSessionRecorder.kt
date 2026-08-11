package com.halo.drivers.heycyan.codec

import java.util.UUID

/**
 * Collects transport observations into one session and persists it on stop.
 * All callbacks are synchronized because Android GATT callbacks and driver
 * lifecycle calls may arrive on different threads.
 */
class CaptureSessionRecorder(
    private val store: CaptureSessionStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val captureIdFactory: (Long) -> String = { timestamp ->
        "${timestamp}-${System.nanoTime()}"
    }
) : PacketTracer {

    private val lock = Any()
    private var active = false
    private var captureId: String? = null
    private var startedAt = 0L
    private var endedAt: Long? = null
    private var scenario = "discovery"
    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var metadata = CaptureMetadata()
    private var requestedMtu: Int? = null
    private var mtu: Int? = null
    private var nextSequence = 0L
    private var nextPacketSequence = 0L
    private val packets = mutableListOf<BlePacket>()
    private val advertisements = mutableListOf<CaptureAdvertisement>()
    private val timeline = mutableListOf<CaptureEvent>()
    private val services = linkedMapOf<String, MutableService>()

    fun start(
        scenario: String = "discovery",
        captureId: String? = null,
        deviceAddress: String? = null,
        deviceName: String? = null,
        metadata: CaptureMetadata = CaptureMetadata()
    ) {
        synchronized(lock) {
            if (active) abort()
            startedAt = now()
            endedAt = null
            this.captureId = captureId?.also {
                require(it.isNotBlank()) { "Capture ID must not be blank" }
            } ?: captureIdFactory(startedAt)
            this.scenario = scenario
            this.deviceAddress = deviceAddress
            this.deviceName = deviceName
            this.metadata = metadata
            this.requestedMtu = null
            this.mtu = null
            this.nextSequence = 0L
            this.nextPacketSequence = 0L
            packets.clear()
            advertisements.clear()
            timeline.clear()
            services.clear()
            active = true
            appendEvent(CaptureEventKind.SESSION_STARTED, startedAt)
        }
    }

    fun setDevice(address: String?, name: String? = null) {
        synchronized(lock) {
            if (!active) return
            deviceAddress = address
            if (name != null) deviceName = name
        }
    }

    fun setMetadata(metadata: CaptureMetadata) {
        synchronized(lock) {
            if (active) this.metadata = metadata
        }
    }

    fun snapshot(): BleCapture? = synchronized(lock) {
        if (!active && captureId == null) return@synchronized null
        buildCapture(endedAt)
    }

    fun stop(): java.io.File? {
        val capture = synchronized(lock) {
            if (!active) {
                null
            } else {
                endedAt = now()
                appendEvent(CaptureEventKind.SESSION_FINISHED, endedAt!!)
                buildCapture(endedAt)
            }
        } ?: return null

        return try {
            val file = store.save(capture)
            synchronized(lock) { active = false }
            file
        } catch (error: Throwable) {
            // A duplicate or filesystem failure must not crash the UI coroutine.
            // Leave no active session behind; the next explicit capture can retry.
            abort()
            throw error
        }
    }

    /** Ends the in-memory session without persisting partial evidence. */
    fun abort() {
        synchronized(lock) {
            active = false
            captureId = null
            endedAt = null
            packets.clear()
            advertisements.clear()
            timeline.clear()
            services.clear()
        }
    }

    fun record(packet: BlePacket) {
        synchronized(lock) {
            if (!active) return
            appendPacket(packet.copy(sequence = nextPacketSequence++))
        }
    }

    fun recordAdvertisement(advertisement: CaptureAdvertisement) {
        synchronized(lock) {
            if (!active) return
            val index = advertisements.size
            advertisements += advertisement.copy(
                serviceUuids = advertisement.serviceUuids.toList(),
                manufacturerData = advertisement.manufacturerData.toMap()
            )
            appendEvent(
                kind = CaptureEventKind.ADVERTISEMENT,
                timestamp = advertisement.timestamp,
                advertisementIndex = index
            )
        }
    }

    override fun onConnectionStateChanged(status: Int, newState: Int) {
        synchronized(lock) {
            if (!active) return
            appendEvent(
                kind = CaptureEventKind.CONNECTION_STATE_CHANGED,
                timestamp = now(),
                status = status,
                newState = newState
            )
        }
    }

    override fun onReady() {
        synchronized(lock) {
            if (active) appendEvent(CaptureEventKind.READY, now())
        }
    }

    override fun onMtuRequested(mtu: Int) {
        synchronized(lock) {
            if (active) requestedMtu = mtu
        }
    }

    override fun onDiscoveredService(serviceUuid: UUID, serviceType: Int) {
        synchronized(lock) {
            if (!active) return
            services.getOrPut(serviceUuid.toString()) { MutableService(serviceUuid.toString(), serviceType) }
            appendEvent(
                kind = CaptureEventKind.SERVICE_DISCOVERED,
                timestamp = now(),
                serviceUuid = serviceUuid.toString()
            )
        }
    }

    override fun onDiscoveredCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        properties: Int,
        permissions: Int
    ) {
        synchronized(lock) {
            if (!active) return
            val service = services.getOrPut(serviceUuid.toString()) {
                MutableService(serviceUuid.toString(), 0)
            }
            service.characteristics.getOrPut(characteristicUuid.toString()) {
                MutableCharacteristic(characteristicUuid.toString(), properties, permissions)
            }
            appendEvent(
                kind = CaptureEventKind.CHARACTERISTIC_DISCOVERED,
                timestamp = now(),
                serviceUuid = serviceUuid.toString(),
                characteristicUuid = characteristicUuid.toString(),
                properties = properties,
                permissions = permissions
            )
        }
    }

    override fun onDiscoveredDescriptor(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        descriptorUuid: UUID,
        permissions: Int
    ) {
        synchronized(lock) {
            if (!active) return
            val service = services.getOrPut(serviceUuid.toString()) {
                MutableService(serviceUuid.toString(), 0)
            }
            val characteristic = service.characteristics.getOrPut(characteristicUuid.toString()) {
                MutableCharacteristic(characteristicUuid.toString(), 0, 0)
            }
            characteristic.descriptors[descriptorUuid.toString()] = CaptureDescriptorInfo(
                uuid = descriptorUuid.toString(),
                permissions = permissions
            )
            appendEvent(
                kind = CaptureEventKind.DESCRIPTOR_DISCOVERED,
                timestamp = now(),
                serviceUuid = serviceUuid.toString(),
                characteristicUuid = characteristicUuid.toString(),
                descriptorUuid = descriptorUuid.toString(),
                permissions = permissions
            )
        }
    }

    override fun onDescriptorWrite(descriptorUuid: UUID, status: Int) {
        synchronized(lock) {
            if (!active) return
            appendEvent(
                kind = CaptureEventKind.DESCRIPTOR_WRITE,
                timestamp = now(),
                descriptorUuid = descriptorUuid.toString(),
                status = status
            )
        }
    }

    override fun onMtuChanged(mtu: Int) {
        synchronized(lock) {
            if (!active) return
            this.mtu = mtu
            appendEvent(CaptureEventKind.MTU_CHANGED, now(), mtu = mtu)
        }
    }

    override fun onTx(characteristicUuid: UUID, bytes: ByteArray) {
        record(
            BlePacket(
                sequence = 0L,
                timestamp = now(),
                direction = PacketDirection.TX,
                characteristic = characteristicUuid,
                payload = bytes.copyOf()
            )
        )
    }

    override fun onRx(characteristicUuid: UUID, bytes: ByteArray) {
        record(
            BlePacket(
                sequence = 0L,
                timestamp = now(),
                direction = PacketDirection.RX,
                characteristic = characteristicUuid,
                payload = bytes.copyOf()
            )
        )
    }

    override fun onRfcomm(channel: String, direction: PacketDirection, bytes: ByteArray) {
        val characteristic = UUID.nameUUIDFromBytes("rfcomm:$channel".toByteArray())
        record(
            BlePacket(
                sequence = 0L,
                timestamp = now(),
                direction = direction,
                characteristic = characteristic,
                payload = bytes.copyOf(),
                transport = PacketTransport.RFCOMM,
                transportChannel = channel
            )
        )
    }

    override fun onRfcommStateChanged(channel: String, connected: Boolean) {
        synchronized(lock) {
            if (active) {
                appendEvent(
                    kind = if (connected) {
                        CaptureEventKind.RFCOMM_CHANNEL_CONNECTED
                    } else {
                        CaptureEventKind.RFCOMM_CHANNEL_DISCONNECTED
                    },
                    timestamp = now(),
                    transport = PacketTransport.RFCOMM,
                    transportChannel = channel
                )
            }
        }
    }

    private fun appendPacket(packet: BlePacket) {
        val packetIndex = packets.size
        packets += packet.copy(payload = packet.payload.copyOf())
        appendEvent(
            kind = CaptureEventKind.PACKET,
            timestamp = packet.timestamp,
            characteristicUuid = packet.characteristic.toString(),
            direction = packet.direction,
            packetIndex = packetIndex
        )
    }

    private fun appendEvent(
        kind: CaptureEventKind,
        timestamp: Long,
        serviceUuid: String? = null,
        characteristicUuid: String? = null,
        descriptorUuid: String? = null,
        direction: PacketDirection? = null,
        packetIndex: Int? = null,
        advertisementIndex: Int? = null,
        properties: Int? = null,
        permissions: Int? = null,
        status: Int? = null,
        mtu: Int? = null,
        newState: Int? = null,
        transport: PacketTransport? = null,
        transportChannel: String? = null
    ) {
        timeline += CaptureEvent(
            sequence = nextSequence++,
            timestamp = timestamp,
            kind = kind,
            serviceUuid = serviceUuid,
            characteristicUuid = characteristicUuid,
            descriptorUuid = descriptorUuid,
            direction = direction,
            packetIndex = packetIndex,
            advertisementIndex = advertisementIndex,
            properties = properties,
            permissions = permissions,
            status = status,
            mtu = mtu,
            newState = newState,
            transport = transport,
            transportChannel = transportChannel
        )
    }

    private fun buildCapture(end: Long?): BleCapture = BleCapture(
        captureId = requireNotNull(captureId),
        startedAt = startedAt,
        endedAt = end,
        scenario = scenario,
        deviceAddress = deviceAddress,
        deviceName = deviceName,
        metadata = metadata,
        advertisements = advertisements.toList(),
        packets = packets.toList(),
        services = services.values.map { service -> service.toInfo() },
        requestedMtu = requestedMtu,
        mtu = mtu,
        timeline = timeline.toList()
    )

    private data class MutableService(
        val uuid: String,
        val type: Int,
        val characteristics: LinkedHashMap<String, MutableCharacteristic> = linkedMapOf()
    ) {
        fun toInfo() = CaptureServiceInfo(
            uuid = uuid,
            type = type,
            characteristics = characteristics.values.map { it.toInfo() }
        )
    }

    private data class MutableCharacteristic(
        val uuid: String,
        val properties: Int,
        val permissions: Int,
        val descriptors: LinkedHashMap<String, CaptureDescriptorInfo> = linkedMapOf()
    ) {
        fun toInfo() = CaptureCharacteristicInfo(
            uuid = uuid,
            properties = properties,
            permissions = permissions,
            descriptors = descriptors.values.toList()
        )
    }
}

/** Forwards all transport observations to multiple independent observers. */
class CompositePacketTracer(
    private vararg val tracers: PacketTracer
) : PacketTracer {
    override fun onConnectionStateChanged(status: Int, newState: Int) = tracers.forEach {
        it.onConnectionStateChanged(status, newState)
    }

    override fun onReady() = tracers.forEach(PacketTracer::onReady)

    override fun onDiscoveredService(serviceUuid: UUID) = tracers.forEach {
        it.onDiscoveredService(serviceUuid)
    }

    override fun onDiscoveredService(serviceUuid: UUID, serviceType: Int) = tracers.forEach {
        it.onDiscoveredService(serviceUuid, serviceType)
    }

    override fun onDiscoveredCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, properties: Int) = tracers.forEach {
        it.onDiscoveredCharacteristic(serviceUuid, characteristicUuid, properties)
    }

    override fun onDiscoveredCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        properties: Int,
        permissions: Int
    ) = tracers.forEach {
        it.onDiscoveredCharacteristic(serviceUuid, characteristicUuid, properties, permissions)
    }

    override fun onDiscoveredDescriptor(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        descriptorUuid: UUID,
        permissions: Int
    ) = tracers.forEach {
        it.onDiscoveredDescriptor(serviceUuid, characteristicUuid, descriptorUuid, permissions)
    }

    override fun onDescriptorWrite(descriptorUuid: UUID, status: Int) = tracers.forEach {
        it.onDescriptorWrite(descriptorUuid, status)
    }

    override fun onMtuChanged(mtu: Int) = tracers.forEach { it.onMtuChanged(mtu) }

    override fun onMtuRequested(mtu: Int) = tracers.forEach { it.onMtuRequested(mtu) }

    override fun onTx(characteristicUuid: UUID, bytes: ByteArray) = tracers.forEach {
        it.onTx(characteristicUuid, bytes.copyOf())
    }

    override fun onRx(characteristicUuid: UUID, bytes: ByteArray) = tracers.forEach {
        it.onRx(characteristicUuid, bytes.copyOf())
    }

    override fun onRfcomm(channel: String, direction: PacketDirection, bytes: ByteArray) = tracers.forEach {
        it.onRfcomm(channel, direction, bytes.copyOf())
    }

    override fun onRfcommStateChanged(channel: String, connected: Boolean) = tracers.forEach {
        it.onRfcommStateChanged(channel, connected)
    }
}