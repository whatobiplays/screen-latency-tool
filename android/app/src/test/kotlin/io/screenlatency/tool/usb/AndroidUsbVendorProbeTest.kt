package io.screenlatency.tool.usb

import android.app.PendingIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUsbVendorProbeTest {
    @Test
    fun usbPermissionPendingIntentAllowsSystemFillInDataOnAndroid12AndNewer() {
        val flags = usbPermissionPendingIntentFlags(androidSApiLevel)

        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, flags)
    }

    @Test
    fun usbPermissionPendingIntentUsesDefaultMutableBehaviorOnAndroid11() {
        val flags = usbPermissionPendingIntentFlags(androidSApiLevel - 1)

        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, flags)
    }

    @Test
    fun permissionReceiverRoutingRequiresItsActionAndDataScheme() {
        val action = "screen.latency.USB_PERMISSION.request"

        assertTrue(usbPermissionBroadcastMatches(
            expectedAction = action,
            action = action,
            dataScheme = USB_PERMISSION_URI_SCHEME,
        ))
        assertFalse(usbPermissionBroadcastMatches(action, action, null))
        assertFalse(usbPermissionBroadcastMatches(action, action, "wrong-scheme"))
        assertFalse(usbPermissionBroadcastMatches(action, "other-action", USB_PERMISSION_URI_SCHEME))
    }

    private companion object {
        const val androidSApiLevel = 31
    }
}
