package com.halo.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HaloJourneysBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartHome() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test
    fun warmStartHome() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @ExperimentalMetricApi
    @Test
    fun homeToNotebook() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), TraceSectionMetric("Halo.Notebook.compose")),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { 
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "home_screen")), 5000)
        }
    ) {
        val notebookTab = device.findObject(By.text("Notebook"))
        notebookTab?.click()
        device.wait(Until.hasObject(By.text("Your workspace")), 3000)
    }

    @Test
    fun openExistingNote() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { 
            pressHome()
            startActivityAndWait()
            val notebookTab = device.findObject(By.text("Notebook"))
            notebookTab?.click()
            device.wait(Until.hasObject(By.text("Your workspace")), 3000)
        }
    ) {
        val note = device.findObject(By.text("N"))
        note?.click()
        device.wait(Until.hasObject(By.text("Editor")), 3000)
    }

    @Test
    fun homeToSettings() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { 
            pressHome()
            startActivityAndWait()
        }
    ) {
        val settingsTab = device.findObject(By.text("Settings"))
        settingsTab?.click()
        device.wait(Until.hasObject(By.text("Intelligence")), 3000)
    }

    @Test
    fun homeToDevices() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { 
            pressHome()
            startActivityAndWait()
        }
    ) {
        val devicesTab = device.findObject(By.text("Devices"))
        devicesTab?.click()
        device.wait(Until.hasObject(By.text("HeyCyan")), 3000)
    }

    @Test
    fun openHeyCyanDevice() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { 
            pressHome()
            startActivityAndWait()
            val devicesTab = device.findObject(By.text("Devices"))
            devicesTab?.click()
            device.wait(Until.hasObject(By.text("HeyCyan")), 3000)
        }
    ) {
        val heycyan = device.findObject(By.text("HeyCyan"))
        heycyan?.click()
        device.wait(Until.hasObject(By.text("STATUS")), 3000)
    }

    @Test
    fun backgroundForeground() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { 
            pressHome()
            startActivityAndWait()
        }
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.aistudio.braintool.xkqzps"
    }
}
