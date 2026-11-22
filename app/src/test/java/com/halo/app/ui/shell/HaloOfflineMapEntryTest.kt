package com.halo.app.ui.shell

import android.app.Application
import android.net.Uri
import com.halo.app.ui.toolkit.offlineMapSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloOfflineMapEntryTest {
    @Test fun wordsAndDestinationLinksOnlyPrefillOfflineSearch() {
        assertEquals("halo-entry:maps/offline?q=pharmacy%20open%20now",
            HaloDeepLinks.resolve(Uri.parse("halo://home/tools/maps?q=pharmacy%20open%20now")))
        assertEquals("halo-entry:maps/offline?q=Riga",
            HaloDeepLinks.resolve(Uri.parse("halo://home/tools/maps?to=Riga")))
        assertNull(HaloDeepLinks.resolve(Uri.parse("halo://home/tools/maps?q=cafe&to=Riga")))
        assertNull(HaloDeepLinks.resolve(Uri.parse("halo://home/tools/maps?q=%0A")))
    }

    @Test fun supportedPhrasesPreserveThePlaceCategoryAndActivateTheOpenFilter() {
        assertEquals("pharmacy" to true, offlineMapSearch("Pharmacy open now".lowercase()))
        assertEquals("аптека" to true, offlineMapSearch("аптека открыта сейчас"))
        assertEquals("quiet café" to false, offlineMapSearch("quiet café"))
    }
}
