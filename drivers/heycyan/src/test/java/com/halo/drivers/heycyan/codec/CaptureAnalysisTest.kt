package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class CaptureAnalysisTest {
    private val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun importerCopiesValidatedCaptureWithoutRewritingSourceBytes() {
        val sourceDirectory = Files.createTempDirectory("heycyan-import-source").toFile()
        val managedDirectory = Files.createTempDirectory("heycyan-import-managed").toFile()
        val sourceStore = JsonCaptureSessionStore(sourceDirectory)
        val managedStore = JsonCaptureSessionStore(managedDirectory)
        val source = sourceStore.save(capture("import-001"))
        val originalJson = source.readText()
            .replaceFirst("{", "{\n  \"producerExtension\": \"preserve-me\",")
        source.writeText(originalJson)

        val imported = CaptureImporter(managedStore).importFiles(listOf(source)).single()

        assertEquals("import-001", imported.capture.captureId)
        assertEquals(originalJson, imported.destination.readText())
        assertEquals(originalJson, managedStore.listCaptures().single().readText())
        assertEquals("preserve-me", imported.destination.readText().substringAfter("producerExtension")
            .substringAfter(": \"").substringBefore("\""))
    }

    @Test
    fun importingSameCaptureAgainIsIdempotent() {
        val sourceDirectory = Files.createTempDirectory("heycyan-import-source").toFile()
        val managedDirectory = Files.createTempDirectory("heycyan-import-managed").toFile()
        val source = JsonCaptureSessionStore(sourceDirectory).save(capture("import-002"))
        val importer = CaptureImporter(JsonCaptureSessionStore(managedDirectory))

        val first = importer.importFiles(listOf(source)).single()
        val second = importer.importFiles(listOf(source)).single()

        assertEquals(first.destination, second.destination)
        assertEquals(1, JsonCaptureSessionStore(managedDirectory).listCaptures().size)
    }

    @Test
    fun analysisPersistsAnnotationsWithPacketAndByteEvidence() {
        val captureDirectory = Files.createTempDirectory("heycyan-analysis-captures").toFile()
        val analysisDirectory = Files.createTempDirectory("heycyan-analysis").toFile()
        val captureStore = JsonCaptureSessionStore(captureDirectory)
        val captureFile = captureStore.save(capture("analysis-001"))
        val originalCaptureJson = captureFile.readText()
        val analysisStore = JsonCaptureAnalysisStore(
            directory = analysisDirectory,
            captureStore = captureStore
        )
        val annotation = PacketAnnotation(
            annotationId = "battery-candidate-001",
            target = PacketEvidenceReference(
                captureId = "analysis-001",
                packetSequence = 0L,
                offset = 1,
                length = 2
            ),
            kind = ProtocolFindingKind.BATTERY_UPDATE,
            confidence = AnalysisConfidence.HYPOTHESIS,
            note = "Candidate byte range; no protocol meaning established.",
            evidence = listOf(
                PacketEvidenceReference("analysis-001", packetSequence = 0L)
            )
        )
        val analysis = CaptureAnalysis(
            analysisId = "heycyan-analysis-001",
            captureIds = listOf("analysis-001"),
            annotations = listOf(annotation)
        )

        val file = analysisStore.save(analysis)
        val loaded = analysisStore.load(file)

        assertEquals(analysis, loaded)
        assertEquals(originalCaptureJson, captureFile.readText())
        assertEquals(listOf(file), analysisStore.listAnalyses())
    }

    @Test
    fun analysisRejectsUnknownPacketsAndOutOfRangeEvidence() {
        val captureDirectory = Files.createTempDirectory("heycyan-analysis-invalid").toFile()
        val captureStore = JsonCaptureSessionStore(captureDirectory)
        captureStore.save(capture("analysis-002"))
        val analysisStore = JsonCaptureAnalysisStore(
            directory = Files.createTempDirectory("heycyan-analysis-invalid-output").toFile(),
            captureStore = captureStore
        )

        val unknownPacket = CaptureAnalysis(
            analysisId = "invalid-packet",
            captureIds = listOf("analysis-002"),
            annotations = listOf(
                PacketAnnotation(
                    annotationId = "invalid-packet-annotation",
                    target = PacketEvidenceReference("analysis-002", packetSequence = 99L),
                    kind = ProtocolFindingKind.UNKNOWN
                )
            )
        )
        val outOfRange = unknownPacket.copy(
            analysisId = "invalid-range",
            annotations = listOf(
                unknownPacket.annotations.single().copy(
                    annotationId = "invalid-range-annotation",
                    target = PacketEvidenceReference(
                        "analysis-002",
                        packetSequence = 0L,
                        offset = 2,
                        length = 2
                    )
                )
            )
        )

        assertFails { analysisStore.save(unknownPacket) }
        assertFails { analysisStore.save(outOfRange) }
        assertTrue(analysisStore.listAnalyses().isEmpty())
    }

    private fun capture(captureId: String): BleCapture = BleCapture(
        captureId = captureId,
        startedAt = 100L,
        packets = listOf(
            BlePacket(
                sequence = 0L,
                timestamp = 101L,
                direction = PacketDirection.RX,
                characteristic = characteristic,
                payload = byteArrayOf(0x10, 0x20, 0x30)
            )
        ),
        timeline = listOf(
            CaptureEvent(0L, 100L, CaptureEventKind.SESSION_STARTED),
            CaptureEvent(
                sequence = 1L,
                timestamp = 101L,
                kind = CaptureEventKind.PACKET,
                characteristicUuid = characteristic.toString(),
                direction = PacketDirection.RX,
                packetIndex = 0
            )
        )
    )

    private fun assertFails(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}