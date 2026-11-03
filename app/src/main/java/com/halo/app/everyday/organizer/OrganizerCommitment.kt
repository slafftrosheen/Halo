package com.halo.app.everyday.organizer

import java.time.Duration
import java.time.Instant

enum class CommitmentOwner { ME, HALO, SHARED }
enum class CommitmentPurpose { STANDARD, INBOX, ROUTINE, WATCH, STOPWATCH }
enum class HaloWorkState { DRAFT, READY, RUNNING, WAITING, NEEDS_USER, COMPLETED, FAILED }
enum class CommitmentCreator { USER, HALO, IMPORTED }

/** Capability names are resolved against installed, automation-eligible descriptors at review time. */
data class OrganizerPlannedAction(val capability: String, val argumentsJson: String = "{}") {
    init {
        require(capability.matches(Regex("[a-z][a-z0-9_.-]{0,99}")))
        require(argumentsJson.length in 2..8192)
    }
}

data class OrganizerSourceRef(val id: String, val version: Long) {
    init { require(id.isNotBlank() && id.length <= 256 && version > 0) }
}

/** References the existing automation owner. It does not claim that scheduling or execution succeeded. */
data class OrganizerExecutionBinding(val planId: String, val routineId: String,
    val routineVersion: String? = null, val runId: String? = null, val scheduling: String = "UNCONFIRMED", val claimedStep: Int = -1,
    val lastEvidenceIds: List<String> = emptyList(), val planNonce: String = "") {
    init {
        require(planId.matches(Regex("[A-Za-z0-9_.:-]{1,80}")))
        require(routineId.length in 1..80 && (routineVersion?.length ?: 0) <= 200 && (runId?.length ?: 0) <= 256)
        require(scheduling in setOf("UNCONFIRMED", "SCHEDULED", "EVENT_DRIVEN", "BLOCKED", "DISPATCHED", "CANCEL_REQUESTED"))
        require(claimedStep in -1..5)
        require(lastEvidenceIds.size <= 20 && lastEvidenceIds.all { it.length <= 256 })
        require(planNonce.length <= 80)
    }
}

data class OrganizerWorkResult(val summary: String, val confirmedAt: Instant,
    val evidenceIds: List<String> = emptyList(), val conditionMatched: Boolean? = null,
    val newMatch: Boolean? = null, val messages: List<String> = emptyList(), val waiting: Boolean = false,
    val sourceRefs: List<OrganizerSourceRef> = emptyList()) {
    init {
        require((summary.isNotBlank() || messages.isNotEmpty()) && summary.length <= 8000 && evidenceIds.size <= 20)
        require(evidenceIds.all { it.isNotBlank() && it.length <= 256 })
        require(messages.size <= 12 && messages.all { it.isNotBlank() && it.length <= 300 })
        require(sourceRefs.size <= 100 && sourceRefs.distinctBy { it.id }.size == sourceRefs.size)
    }
}

/** Same-boot projection is monotonic. Wall time is only a conservative reboot fallback. No wake lock. */
data class StopwatchState(val accumulatedMs: Long = 0, val startedAt: Instant? = null,
    val elapsedStartMs: Long? = null, val bootId: String? = null, val laps: List<Long> = emptyList()) {
    init {
        require(accumulatedMs >= 0 && (startedAt == null) == (elapsedStartMs == null) && (startedAt == null) == (bootId == null))
        require(elapsedStartMs == null || elapsedStartMs >= 0)
        require(laps.size <= 50 && laps.all { it >= 0 } && laps.zipWithNext().all { it.first <= it.second })
    }
    fun elapsed(now: Instant, monotonicMs: Long, currentBoot: String): Long {
        if (startedAt == null) return accumulatedMs
        val extra = if (bootId == currentBoot && monotonicMs >= requireNotNull(elapsedStartMs)) monotonicMs - elapsedStartMs
            else try { Duration.between(startedAt, now).toMillis().coerceAtLeast(0) }
            catch (_: ArithmeticException) { if (now >= startedAt) Long.MAX_VALUE else 0 }
        return (if (extra > Long.MAX_VALUE - accumulatedMs) Long.MAX_VALUE else accumulatedMs + extra).coerceAtLeast(laps.lastOrNull() ?: 0)
    }
    fun start(now: Instant, monotonicMs: Long, currentBoot: String): StopwatchState {
        require(startedAt == null); return copy(startedAt = now, elapsedStartMs = monotonicMs, bootId = currentBoot)
    }
    fun pause(now: Instant, monotonicMs: Long, currentBoot: String) = copy(accumulatedMs = elapsed(now, monotonicMs, currentBoot),
        startedAt = null, elapsedStartMs = null, bootId = null)
    fun lap(now: Instant, monotonicMs: Long, currentBoot: String): StopwatchState {
        require(startedAt != null && laps.size < 50); return copy(laps = laps + elapsed(now, monotonicMs, currentBoot))
    }
}

/** Additive metadata on OrganizerRecord: commitments, schedules and results keep one durable owner. */
data class OrganizerCommitment(val owner: CommitmentOwner = CommitmentOwner.ME,
    val purpose: CommitmentPurpose = CommitmentPurpose.STANDARD, val intent: String = "",
    val state: HaloWorkState = HaloWorkState.READY, val actions: List<OrganizerPlannedAction> = emptyList(),
    val sources: List<OrganizerSourceRef> = emptyList(), val execution: OrganizerExecutionBinding? = null,
    val result: OrganizerWorkResult? = null, val nextHumanAction: String? = null,
    val createdBy: CommitmentCreator = CommitmentCreator.USER, val stopwatch: StopwatchState? = null) {
    val requiredCapabilities: Set<String> get() = actions.map { it.capability }.toSet()
    fun validate() {
        require(intent.length <= 2000 && actions.size <= 6 && sources.size <= 10 && sources.distinctBy { it.id }.size == sources.size)
        require(nextHumanAction == null || nextHumanAction.isNotBlank() && nextHumanAction.length <= 1000)
        require((purpose == CommitmentPurpose.STOPWATCH) == (stopwatch != null))
        require(purpose != CommitmentPurpose.STOPWATCH || owner == CommitmentOwner.ME && execution == null && actions.isEmpty())
        require(state != HaloWorkState.RUNNING || owner != CommitmentOwner.ME && execution?.runId != null)
        require(state != HaloWorkState.COMPLETED || owner == CommitmentOwner.ME || result != null)
        require(state != HaloWorkState.NEEDS_USER || nextHumanAction != null)
        require(execution == null || owner != CommitmentOwner.ME)
    }
}

object OrganizerResponsibility {
    fun state(record: OrganizerRecord): HaloWorkState = if (record.commitment.owner == CommitmentOwner.ME && record.status == OrganizerStatus.COMPLETED)
        HaloWorkState.COMPLETED else record.commitment.state
    /** Invalidate execution authority in the durable record before asking the scheduler to cancel. */
    fun transfer(value: OrganizerCommitment, owner: CommitmentOwner): OrganizerCommitment {
        require(value.owner != owner) { "This responsibility is already selected" }
        require(value.state != HaloWorkState.RUNNING) { "Stop the active work and read its result before changing responsibility" }
        return value.copy(owner = owner, execution = null,
            state = if (owner == CommitmentOwner.ME) HaloWorkState.READY else HaloWorkState.DRAFT,
            nextHumanAction = null).also { it.validate() }
    }
    fun mayExecute(record: OrganizerRecord, planId: String): Boolean = record.deletedAt == null &&
        record.status == OrganizerStatus.ACTIVE && record.commitment.owner != CommitmentOwner.ME &&
        record.commitment.execution?.planId == planId && record.commitment.state in setOf(HaloWorkState.READY, HaloWorkState.WAITING)
}
