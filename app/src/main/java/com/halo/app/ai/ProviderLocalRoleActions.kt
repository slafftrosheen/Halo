package com.halo.app.ai

import com.halo.sdk.provider.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Adapter over the existing provider configuration/router owners; no independent selection store. */
data class LocalRoleSnapshot(val role: AiModelRole, val sourceVersion: String, val binding: AiRoleBinding,
    val candidates: List<AiModelRef>, val localPolicy: Boolean, internal val ownerState: String)
interface LocalRoleControl {
    suspend fun snapshot(role: AiModelRole): LocalRoleSnapshot?
    suspend fun select(role: AiModelRole, sourceVersion: String, selection: AiRoleSelection,
        target: AiModelRef?, canConfigure: () -> Boolean): LocalRoleSnapshot?
}
class ProviderLocalRoleActions(private val control: ProviderControlRepository) : LocalRoleControl {
    private val lifetime = UUID.randomUUID().toString()
    private val intent = AtomicLong()
    private val router get() = control.router
    private fun state(): String = synchronized(router) {
        val configured = control.rows.value.map { it.descriptor.providerId to it.configured }.sortedBy { it.first }
        listOf(lifetime, intent.get(), router.configurationRevision, router.remoteDispatchRevision,
            router.selectedProviderId.value, router.selectedModelId.value, router.routingPolicy.value,
            router.roleBindings.value.toSortedMap(compareBy { it.name }), configured,
            control.catalogue.state.value.models.sortedBy { it.runtime.ref.toString() },
            control.catalogue.state.value.refreshingProviders.sorted(),
            control.catalogue.state.value.providerFailures.toSortedMap(),
            control.providers.map { it.id to it.status.value }.sortedBy { it.first }).joinToString("\u0000")
    }
    private fun localPolicy(): Boolean = router.routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY ||
        (router.routingPolicy.value == AiRoutingPolicy.EXPLICIT && control.providers.any {
            it.id == router.selectedProviderId.value && it.isLocal })
    override suspend fun snapshot(role: AiModelRole): LocalRoleSnapshot? {
        if (role == AiModelRole.CHAT || router.configurationRevision == null) return null
        repeat(3) {
            val before = state()
            val catalogue = control.catalogue.state.value.models
            val configured = control.rows.value.filter { it.configured }.map { it.descriptor.providerId }.toSet()
            val candidates = router.candidatesForRole(role, catalogue, configured)
            val eligible = candidates.filter { it.eligible && it.executionLocation == AiExecutionLocation.ON_DEVICE }
                .map { it.ref }.filter { ref -> control.providers.any { it.id == ref.providerId && it.isLocal } &&
                    catalogue.any { it.runtime.ref == ref && it.runtime.installed == true &&
                        it.runtime.location == AiExecutionLocation.ON_DEVICE && !it.runtime.requiresNetwork } }
                .distinct().sortedWith(compareBy({ it.providerId }, { it.modelId }))
            synchronized(router) {
                if (before == state()) {
                    val material = before + "\u0000" + role.name + "\u0000" + candidates.toString()
                    val token = "local-role:" + MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    return LocalRoleSnapshot(role, token, router.roleBinding(role), eligible, localPolicy(), before)
                }
            }
        }
        return null
    }
    override suspend fun select(role: AiModelRole, sourceVersion: String, selection: AiRoleSelection,
        target: AiModelRef?, canConfigure: () -> Boolean): LocalRoleSnapshot? {
        if (!canConfigure() || selection == AiRoleSelection.AUTOMATIC || role == AiModelRole.CHAT) return null
        val observed = snapshot(role) ?: return null
        if (observed.sourceVersion != sourceVersion) return null
        val required = when (selection) {
            AiRoleSelection.EXPLICIT -> target ?: return null
            AiRoleSelection.INHERIT_DEFAULT -> AiModelRef(router.selectedProviderId.value ?: return null,
                router.selectedModelId.value ?: return null)
            AiRoleSelection.DISABLED -> null
            else -> return null
        }
        if (required != null) {
            if (required !in observed.candidates ||
                selection == AiRoleSelection.INHERIT_DEFAULT && !observed.localPolicy) return null
            val provider = control.providers.singleOrNull { it.id == required.providerId && it.isLocal } ?: return null
            // Same installed-inventory revalidation as ModelBrowserViewModel.pick; no remote discovery.
            control.catalogue.refresh(provider.id)
            if (provider.listModels().none { it.id == required.modelId }) return null
        }
        val fresh = snapshot(role) ?: return null
        // Refresh timestamps are excluded; actual catalogue/candidate/configuration changes invalidate.
        if (fresh.sourceVersion != sourceVersion || required != null && required !in fresh.candidates) return null
        val binding = AiRoleBinding(role, target?.providerId.takeIf { selection == AiRoleSelection.EXPLICIT },
            target?.modelId.takeIf { selection == AiRoleSelection.EXPLICIT }, selection)
        synchronized(router) {
            if (!canConfigure() || fresh.ownerState != state() ||
                selection == AiRoleSelection.INHERIT_DEFAULT && !localPolicy()) return null
            intent.incrementAndGet() // Consume before existing owner write; no uncertain automatic replay.
            router.setRoleBinding(binding)
            check(router.roleBinding(role) == binding) { "Role selection readback failed" }
        }
        val after = checkNotNull(snapshot(role)) { "Role readback became unavailable" }
        check(after.binding == binding) { "Role changed during readback" }
        return after
    }
}
