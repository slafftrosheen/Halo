package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class HostFirstSkillAuditTest {
    @Test fun everyBundledSkillResolvesThroughTheSharedAuthorityWithoutHome() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        initializeUnitTestWorkManager(context)
        ManualDependencyGraph.init(context)
        val audit = ManualDependencyGraph.skillRuntimeAudit.inspect()
        println("HALO_SKILL_RUNTIME_AUDIT skills=${audit.skillCount} enabled=${audit.enabledSkillCount} tools=${audit.toolCount} orphans=${audit.orphanToolIds.size} unresolved=${audit.unresolvedToolIds.size} duplicates=${audit.duplicateToolIds.size} permissionMismatches=${audit.permissionMismatches.size}")
        assertTrue("Audit problems: orphans=${audit.orphanToolIds}; unresolved=${audit.unresolvedToolIds}; duplicates=${audit.duplicateToolIds}; permissions=${audit.permissionMismatches}",
            audit.orphanToolIds.isEmpty() && audit.unresolvedToolIds.isEmpty() &&
                audit.duplicateToolIds.isEmpty() && audit.permissionMismatches.isEmpty())
    }
}
