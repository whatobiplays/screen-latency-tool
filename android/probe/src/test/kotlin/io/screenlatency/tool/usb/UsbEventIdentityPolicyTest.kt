package io.screenlatency.tool.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbEventIdentityPolicyTest {
    @Test
    fun ignoresASeparateDeviceWithDifferentIdsAndName() {
        assertFalse(
            UsbEventIdentityPolicy.mayAffectSelectedFixture(
                eventDeviceName = "separate-device",
                eventVendorId = 0xCAFE,
                eventProductId = 0xBEEF,
                selectedDeviceNames = setOf("selected-fixture"),
                selectedVendorId = 0x1209,
                selectedProductId = 0x0001,
            ),
        )
    }

    @Test
    fun revalidatesForTheSelectedNameEvenWhenEventIdsDiffer() {
        assertTrue(
            UsbEventIdentityPolicy.mayAffectSelectedFixture(
                eventDeviceName = "selected-fixture",
                eventVendorId = 0xCAFE,
                eventProductId = 0xBEEF,
                selectedDeviceNames = setOf("selected-fixture"),
                selectedVendorId = 0x1209,
                selectedProductId = 0x0001,
            ),
        )
    }

    @Test
    fun marksSameIdsAtANewNameAsPossiblyRelevantButNotAsFixtureIdentity() {
        assertTrue(
            UsbEventIdentityPolicy.mayAffectSelectedFixture(
                eventDeviceName = "reenumerated-device",
                eventVendorId = 0x1209,
                eventProductId = 0x0001,
                selectedDeviceNames = setOf("selected-fixture"),
                selectedVendorId = 0x1209,
                selectedProductId = 0x0001,
            ),
        )
    }

    @Test
    fun treatsAnUnidentifiableEventAsAmbiguousOnlyWhenAFixtureIsTracked() {
        assertTrue(
            UsbEventIdentityPolicy.mayAffectSelectedFixture(
                eventDeviceName = null,
                eventVendorId = null,
                eventProductId = null,
                selectedDeviceNames = setOf("selected-fixture"),
                selectedVendorId = 0x1209,
                selectedProductId = 0x0001,
            ),
        )
        assertFalse(
            UsbEventIdentityPolicy.mayAffectSelectedFixture(
                eventDeviceName = null,
                eventVendorId = null,
                eventProductId = null,
                selectedDeviceNames = emptySet(),
                selectedVendorId = null,
                selectedProductId = null,
            ),
        )
    }
}
