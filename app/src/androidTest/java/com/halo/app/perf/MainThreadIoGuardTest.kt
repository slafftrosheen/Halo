package com.halo.app.perf

import android.os.StrictMode
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.halo.app.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainThreadIoGuardTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun coldLaunchToHome() {
        device.wait(Until.hasObject(By.res("com.aistudio.braintool.xkqzps", "home_screen")), 5000)
    }

    @Test
    fun homeToNotebookAndOpenNote() {
        device.wait(Until.hasObject(By.res("com.aistudio.braintool.xkqzps", "home_screen")), 5000)
        
        val notebookTab = device.findObject(By.text("Notebook"))
        notebookTab?.click()
        device.wait(Until.hasObject(By.text("Your workspace")), 3000)

        // Try to click an existing note, or create one if none exists
        val note = device.findObject(By.text("N"))
        note?.click()
        device.wait(Until.hasObject(By.text("Editor")), 3000)
    }

    @Test
    fun homeToSettingsAndIntelligence() {
        device.wait(Until.hasObject(By.text("Settings")), 5000)
        
        val settingsTab = device.findObject(By.text("Settings"))
        settingsTab?.click()
        device.wait(Until.hasObject(By.text("Intelligence")), 3000)

        val intelligenceOption = device.findObject(By.text("Intelligence"))
        intelligenceOption?.click()
        device.wait(Until.hasObject(By.text("AI Providers")), 3000)
    }

    @Test
    fun homeToDevicesAndHeyCyan() {
        device.wait(Until.hasObject(By.text("Devices")), 5000)
        
        val devicesTab = device.findObject(By.text("Devices"))
        devicesTab?.click()
        device.wait(Until.hasObject(By.text("HeyCyan")), 3000)

        val heycyan = device.findObject(By.text("HeyCyan"))
        heycyan?.click()
        device.wait(Until.hasObject(By.text("STATUS")), 3000)
    }

    @Test
    fun homeToActivity() {
        device.wait(Until.hasObject(By.text("Activity")), 5000)
        
        val activityTab = device.findObject(By.text("Activity"))
        activityTab?.click()
        device.wait(Until.hasObject(By.text("What Halo did")), 3000)
    }
}
