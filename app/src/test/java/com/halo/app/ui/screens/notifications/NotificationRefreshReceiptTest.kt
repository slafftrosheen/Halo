package com.halo.app.ui.screens.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRefreshReceiptTest {
    @Test fun noPrematureRefreshCompletion() {
        assertTrue(notificationRefreshPending(3L, 2L))
        assertFalse(notificationRefreshPending(3L, 3L))
        assertFalse(notificationRefreshPending(3L, 4L))
    }

    @Test fun initialSnapshotDoesNotShowPullRefresh() {
        assertFalse(notificationRefreshPending(0L, 0L))
    }
}
