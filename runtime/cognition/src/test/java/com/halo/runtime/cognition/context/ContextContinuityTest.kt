package com.halo.runtime.cognition.context

import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.ContextRequirement
import com.halo.sdk.runtime.VisualFreshness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContextContinuityTest {
    private fun focus(conversation: String,asset: String,expiry: Long?=120) = InteractionContextFocus(conversation,
        ContextReference.MediaAsset(asset,"lease:$asset"),ContextCategory.VISUAL,ContextFocusReason.EXPLICIT_ATTACHMENT,0,expiry)
    @Test fun conversationsAreIsolatedAndOriginalSelectionCanReturn() {
        val owner=InteractionContextContinuity { 10 }
        owner.select(focus("A","X")); assertTrue(owner.current("B").isEmpty())
        owner.select(focus("B","Y")); assertEquals("X",(owner.current("A").single().reference as ContextReference.MediaAsset).assetId)
    }
    @Test fun expiryNeverSubstitutesNewerReference() {
        var now=10L; val owner=InteractionContextContinuity { now }; owner.select(focus("A","X"))
        now=120; assertEquals(ContextReferenceState.EXPIRED,owner.current("A").single().state)
        assertEquals("X",(owner.current("A").single().reference as ContextReference.MediaAsset).assetId)
    }
    @Test fun replacementAndRemovalAreCategoryBounded() {
        val owner=InteractionContextContinuity { 0 }; owner.select(focus("A","X"));owner.select(focus("A","Y"))
        owner.select(InteractionContextFocus("A",ContextReference.WorkspaceObject("note","4"),ContextCategory.WORKSPACE,ContextFocusReason.USER_SELECTED_WORKSPACE,0,null))
        owner.remove("A",ContextCategory.VISUAL); assertEquals(ContextCategory.WORKSPACE,owner.current("A").single().category)
    }
    @Test fun processRestartDoesNotRestoreTemporaryReferences() {
        val owner=InteractionContextContinuity { 0 };owner.select(focus("A","X"))
        assertTrue(InteractionContextContinuity { 0 }.current("A").isEmpty())
    }
    @Test fun workspaceRevisionMismatchFailsRequiredAcquisition() = runBlocking {
        val source=object: WorkspaceContextSource { override suspend fun content(includeMemories:Boolean)=emptyList<String>()
            override suspend fun contentFor(objectId:String)="revision five"
            override suspend fun versionFor(objectId:String)="5" }
        val id=InteractionId("turn")
        val result=DefaultContextAcquisitionCoordinator(workspaceContext=source).acquire(
            ContextAcquisitionPlan(id,listOf(ContextNeed(ContextKind.WORKSPACE_REFERENCE,ContextRequirement.REQUIRED,ContextAffinity.EXPLICIT_REFERENCE))),
            ContextQuery(id,"question",activeWorkspaceObjectId="note",workspaceVersion="4"))
        assertTrue(result.candidates.isEmpty());assertEquals(1,result.unsatisfiedRequired.size)
    }
    @Test fun explicitImageMustBelongToCurrentInteraction() = runBlocking {
        val id=InteractionId("new"); val plan=ContextAcquisitionPlan(id,listOf(ContextNeed(ContextKind.VISUAL_IMAGE,ContextRequirement.REQUIRED,ContextAffinity.EXPLICIT_REFERENCE,VisualFreshness.REFERENCE)))
        var captures=0
        val coordinator=DefaultContextAcquisitionCoordinator(visualContextSource={ captures++;Result.failure(IllegalStateException()) })
        val image=ImageContext(imageBytes=byteArrayOf(1),mimeType="image/png",timestampMs=0,assetId="X",interactionId="old",freshness=VisualFreshness.REFERENCE)
        assertTrue(coordinator.acquire(plan,ContextQuery(id,"question",explicitImage=image)).candidates.isEmpty())
        val exact=coordinator.acquire(plan,ContextQuery(id,"question",explicitImage=image.copy(interactionId="new")))
        assertEquals("X",((exact.candidates.single().content as ContextContent.ImageContent).image.assetId));assertEquals(0,captures)
    }
    @Test fun ordinaryGreetingHasNoActionsAndCompletedSourceHasTwoTypedActions() {
        val result=ContextReference.ConversationTurn("A","turn")
        assertTrue(ContextualNextActionProjector.project(ContextualResultState(result,true,false),0).isEmpty())
        val actions=ContextualNextActionProjector.project(ContextualResultState(result,true,true,ContextReference.WorkspaceObject("note","4"),true),0)
        assertEquals(listOf(ContextualNextActionKind.STUDY_SOURCE,ContextualNextActionKind.SAVE_RESULT),actions.map { it.kind })
        assertEquals(ContextualNextActionTarget.ReviewSaveResult("A","turn"),actions.last().target)
    }
    @Test fun pendingMemoryAndStudyAreBoundedAndExpiredResultsHaveNoActions() {
        val state=ContextualResultState(ContextReference.ConversationTurn("A","t"),true,true,studySessionId="session",memorySourceId="proposal",waitingRunId="run",expiresAtMs=50)
        val actions=ContextualNextActionProjector.project(state,0)
        assertEquals(2,actions.size);assertEquals(ContextualNextActionKind.REVIEW_MEMORY,actions.first().kind)
        assertTrue(ContextualNextActionProjector.project(state,50).isEmpty())
    }
    @Test fun missingSourceOnlyProposesTypedReattachment() {
        val ref=ContextReference.MediaAsset("lost","lease")
        val actions=ContextualNextActionProjector.project(ContextualResultState(null,false,false,unavailableSource=ref),0)
        assertEquals(ContextualNextActionTarget.ReattachSource(ref),actions.single().target)
        assertEquals("Reattach source",actions.single().title)
    }
}
