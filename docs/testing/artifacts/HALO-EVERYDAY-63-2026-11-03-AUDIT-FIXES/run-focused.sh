#!/usr/bin/env bash
set -euo pipefail
cd /home/pi/Projects/Halo
source /home/pi/.config/halo/env.sh
# Run only after the complete coding/source-review batch. No per-edit gates.
nice -n 10 taskset -c 0 ./gradlew --no-daemon --max-workers=1 --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx3g -XX:ActiveProcessorCount=1 -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :libraries:halo-cognition-language:test --tests '*NativeCommandParserTest' \
  :runtime:cognition:test --tests '*NativeCapabilityDispatcherTest' --tests '*InterpretationRouteTest' --tests '*PersonalConfigurationPolicyTest' \
  :app:testDebugUnitTest \
  --tests '*EverydayToolExecutorTest' --tests '*OrganizerCalendarServiceTest' --tests '*EverydayCalendarExportTest' \
  --tests '*EverydayBriefTest' --tests '*BriefPersistenceTest' --tests '*OutstandingRemindersTest' --tests '*DailyAssistantReadOnlyTest' \
  --tests '*OrganizerDeliveryTest' --tests '*OrganizerDeliveryTimingTest' --tests '*OrganizerIcsTest' --tests '*OrganizerRecurrenceTest' --tests '*OrganizerPersistenceTest' \
  --tests '*CollectorIntegrationTest' --tests '*CollectorParsingTest' --tests '*CollectorReviewedRefreshTest' \
  --tests '*EverydaySetupChoicesTest' --tests '*OrganizerAuditCorrectionsTest' --tests '*WorldClockAndCitiesTest' \
  --tests '*ScreenLayoutPersistenceTest' --tests '*ScreenWorkspaceChromeTest' --tests '*ScreenToolExecutorTest' --tests '*EverydayScreenInstancesTest' \
  --tests '*EverydayCardsTest' --tests '*EverydayWidgetStateTest' --tests '*EverydayWidgetApplyOperationTest' --tests '*EverydayWidgetEntryQueueTest' --tests '*EverydayWidgetRenderingTest' \
  --tests '*EverydayArchiveTest' --tests '*EverydayRetentionTest' --tests '*ConfigurationSkillManifestParityTest' \
  --tests '*OrganizerToolHierarchyTest' --tests '*FamilyModeMenuControlTest' --tests '*FamilyModeMenuVisibilityTest' --tests '*EverydayEntryNavigationTest'
