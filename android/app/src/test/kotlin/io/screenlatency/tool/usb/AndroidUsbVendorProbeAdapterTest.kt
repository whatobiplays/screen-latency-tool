package io.screenlatency.tool.usb

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Looper
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidUsbVendorProbeAdapterTest {
    @Test
    fun firmwareButtonUsageOneUsesAndroidButtonAAndRejectsOtherButtons() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, FIXTURE_BUTTON_ANDROID_KEY_CODE)
        assertNotEquals(KeyEvent.KEYCODE_BUTTON_1, FIXTURE_BUTTON_ANDROID_KEY_CODE)

        val accepted = GamepadEventMatcher(FIXTURE_BUTTON_ANDROID_KEY_CODE).also { it.begin(44) }
        assertEquals(
            GamepadEventResult.PressAccepted(44, 7),
            accepted.observe(
                44,
                GamepadAssociation.Matched(7),
                GamepadKeyEventSnapshot(GamepadKeyAction.DOWN, KeyEvent.KEYCODE_BUTTON_A, 0),
            ),
        )
        assertEquals(
            GamepadEventResult.ReleaseAccepted(44, 7),
            accepted.observe(
                44,
                GamepadAssociation.Matched(7),
                GamepadKeyEventSnapshot(GamepadKeyAction.UP, KeyEvent.KEYCODE_BUTTON_A, 0),
            ),
        )
        assertEquals(GamepadEventResult.Complete(44, 7), accepted.finish(44))

        listOf(KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_B).forEach { keyCode ->
            val rejected = GamepadEventMatcher(FIXTURE_BUTTON_ANDROID_KEY_CODE).also { it.begin(45) }
            assertEquals(
                GamepadEventResult.Rejected(45, GamepadEventIssue.WRONG_BUTTON),
                rejected.observe(
                    45,
                    GamepadAssociation.Matched(7),
                    GamepadKeyEventSnapshot(GamepadKeyAction.DOWN, keyCode, 0),
                ),
            )
        }
    }

    @Test
    fun discoveryRejectsMultipleMatchingAndroidDevices() {
        val first = androidDevice("/dev/bus/usb/001/002")
        val second = androidDevice("/dev/bus/usb/001/003")
        val rig = probeFor(first.device, second.device)

        try {
            assertSame(ProbeDiscovery.Ambiguous, rig.probe.discover())
        } finally {
            rig.probe.close()
        }
    }

    @Test
    fun permissionBroadcastClaimsOnlyVendorInterfaceAndCancelReleasesIt() {
        val fixture = androidDevice("/dev/bus/usb/001/002")
        val connection = Mockito.mock(UsbDeviceConnection::class.java)
        `when`(connection.rawDescriptors).thenReturn(validFixtureDescriptorBytes())
        `when`(connection.serial).thenReturn("0123456789ABCDEF")
        `when`(connection.claimInterface(fixture.vendorInterface, false)).thenReturn(true)
        val rig = probeFor(fixture.device, connection = connection)
        val callbacks = mutableListOf<ProbeConnectResult>()

        try {
            val candidate = (rig.probe.discover() as ProbeDiscovery.Ready).candidate
            rig.probe.requestPermission(candidate, callbacks::add)
            val permissionIntent = permissionIntentFor(rig.usbManager, fixture.device)

            permissionIntent.send(
                RuntimeEnvironment.getApplication(),
                0,
                Intent()
                    .putExtra(UsbManager.EXTRA_DEVICE, fixture.device)
                    .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true),
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, callbacks.size)
            assertTrue(callbacks.single() is ProbeConnectResult.Connected)
            verify(connection).claimInterface(fixture.vendorInterface, false)
            verify(connection, never()).claimInterface(fixture.hidInterface, false)

            rig.probe.cancel()

            verify(connection).releaseInterface(fixture.vendorInterface)
            verify(connection, never()).releaseInterface(fixture.hidInterface)
            verify(connection).close()
        } finally {
            rig.probe.close()
        }
    }

    @Test
    fun logicalDetachCannotInitializeMissingStateOrSendIdOneAfterFirmwareEpochIsRetained() {
        val fixture = androidDevice("/dev/bus/usb/001/002")
        val originalConnection = validatedConnection(fixture)
        val originalRig = probeFor(fixture.device, connection = originalConnection)
        clearRequestIdState()
        val store = AndroidEngineeringRequestIdStateStore(
            RuntimeEnvironment.getApplication(),
            "0123456789ABCDEF",
        )
        // ID 1 was accepted by firmware before the host lost its durable high-water mark.
        store.saveNextRequestId(2L)

        try {
            selectFixture(originalRig, fixture)
            clearRequestIdState()
            `when`(originalRig.usbManager.deviceList).thenReturn(hashMapOf())
            RuntimeEnvironment.getApplication().sendBroadcast(
                Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    .putExtra(UsbManager.EXTRA_DEVICE, fixture.device),
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(null, store.loadNextRequestId())
            originalRig.probe.close()

            val reconnectedConnection = validatedConnection(fixture)
            val reconnectedRig = probeFor(fixture.device, connection = reconnectedConnection)
            try {
                selectFixture(reconnectedRig, fixture)
                var pulseResult: Result<PulseRunResult>? = null
                reconnectedRig.probe.runEngineeringPulse(10, object : EngineeringPulseListener {
                    override fun onProgress(update: PulseUpdate, information: EngineeringInfo?) = Unit
                    override fun onSequenceAccepted(sequence: Long) = Unit
                    override fun onGamepadEventDiagnostics(result: GamepadEventResult) = Unit
                    override fun onFinished(result: Result<PulseRunResult>) { pulseResult = result }
                })
                shadowOf(Looper.getMainLooper()).idle()

                assertTrue(pulseResult?.isFailure == true)
                assertEquals(null, store.loadNextRequestId())
                verify(reconnectedConnection, never()).bulkTransfer(
                    Mockito.any(UsbEndpoint::class.java),
                    Mockito.any(ByteArray::class.java),
                    Mockito.anyInt(),
                    Mockito.anyInt(),
                )
            } finally {
                reconnectedRig.probe.close()
            }
        } finally {
            originalRig.probe.close()
            clearRequestIdState()
        }
    }

    @Test
    fun detachDoesNotResetAnExistingRequestIdHighWaterMark() {
        val fixture = androidDevice("/dev/bus/usb/001/002")
        clearRequestIdState()
        val rig = probeFor(fixture.device, connection = validatedConnection(fixture))
        val store = AndroidEngineeringRequestIdStateStore(
            RuntimeEnvironment.getApplication(),
            "0123456789ABCDEF",
        )
        store.saveNextRequestId(23L)

        try {
            selectFixture(rig, fixture)
            `when`(rig.usbManager.deviceList).thenReturn(hashMapOf())
            RuntimeEnvironment.getApplication().sendBroadcast(
                Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    .putExtra(UsbManager.EXTRA_DEVICE, fixture.device),
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(23L, store.loadNextRequestId())
        } finally {
            rig.probe.close()
        }
    }

    @Test
    fun ownerConfirmedFirmwareRestartInitializesOnlyMissingStateForTheConnectedFixture() {
        val fixture = androidDevice("/dev/bus/usb/001/002")
        clearRequestIdState()
        val connection = validatedConnection(fixture)
        val rig = probeFor(fixture.device, connection = connection)
        val store = AndroidEngineeringRequestIdStateStore(
            RuntimeEnvironment.getApplication(),
            "0123456789ABCDEF",
        )

        try {
            assertFalse(rig.probe.initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart())
            assertEquals(null, store.loadNextRequestId())

            selectFixture(rig, fixture)
            assertTrue(rig.probe.initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart())
            assertEquals(1L, store.loadNextRequestId())
            store.saveNextRequestId(23L)
            assertTrue(rig.probe.initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart())
            assertEquals(23L, store.loadNextRequestId())
            verify(connection, never()).bulkTransfer(
                Mockito.any(UsbEndpoint::class.java),
                Mockito.any(ByteArray::class.java),
                Mockito.anyInt(),
                Mockito.anyInt(),
            )
        } finally {
            rig.probe.close()
            clearRequestIdState()
        }
    }

    @Test
    fun detachBroadcastCannotInitializeRequestIdsWhileTheDeviceRemainsEnumerated() {
        val fixture = androidDevice("/dev/bus/usb/001/002")
        val rig = probeFor(fixture.device, connection = validatedConnection(fixture))
        clearRequestIdState()
        val store = AndroidEngineeringRequestIdStateStore(
            RuntimeEnvironment.getApplication(),
            "0123456789ABCDEF",
        )

        try {
            selectFixture(rig, fixture)
            RuntimeEnvironment.getApplication().sendBroadcast(
                Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    .putExtra(UsbManager.EXTRA_DEVICE, fixture.device),
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(null, store.loadNextRequestId())
        } finally {
            rig.probe.close()
        }
    }

    @Test
    fun detachInvalidatesPendingPermissionAndAdvancesDiscoveryGeneration() {
        val fixture = androidDevice("/dev/bus/usb/001/002")
        val rig = probeFor(fixture.device)
        val callbacks = mutableListOf<ProbeConnectResult>()

        try {
            val original = (rig.probe.discover() as ProbeDiscovery.Ready).candidate
            rig.probe.requestPermission(original, callbacks::add)

            RuntimeEnvironment.getApplication().sendBroadcast(
                Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    .putExtra(UsbManager.EXTRA_DEVICE, fixture.device),
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(listOf(ProbeConnectResult.Stale), callbacks)
            val rediscovered = (rig.probe.discover() as ProbeDiscovery.Ready).candidate
            assertNotEquals(original.generation, rediscovered.generation)
        } finally {
            rig.probe.close()
        }
    }

    private fun probeFor(
        vararg devices: UsbDevice,
        connection: UsbDeviceConnection? = null,
    ): ProbeRig {
        val usbManager = Mockito.mock(UsbManager::class.java)
        val deviceMap = devices.associateBy(UsbDevice::getDeviceName)
        `when`(usbManager.deviceList).thenAnswer { HashMap(deviceMap) }
        devices.forEach { device -> `when`(usbManager.hasPermission(device)).thenReturn(true) }
        if (connection != null) {
            `when`(usbManager.openDevice(devices.single())).thenReturn(connection)
        }
        val appContext = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this

            override fun getSystemService(name: String): Any? =
                if (name == Context.USB_SERVICE) usbManager else super.getSystemService(name)
        }
        return ProbeRig(AndroidUsbVendorProbe(context, ImmediateExecutorService()), usbManager)
    }

    private fun selectFixture(rig: ProbeRig, fixture: AndroidFixture) {
        val callbacks = mutableListOf<ProbeConnectResult>()
        val candidate = (rig.probe.discover() as ProbeDiscovery.Ready).candidate
        rig.probe.requestPermission(candidate, callbacks::add)
        permissionIntentFor(rig.usbManager, fixture.device).send(
            RuntimeEnvironment.getApplication(),
            0,
            Intent()
                .putExtra(UsbManager.EXTRA_DEVICE, fixture.device)
                .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(callbacks.single() is ProbeConnectResult.Connected)
    }

    private fun validatedConnection(fixture: AndroidFixture): UsbDeviceConnection =
        Mockito.mock(UsbDeviceConnection::class.java).also { connection ->
            `when`(connection.rawDescriptors).thenReturn(validFixtureDescriptorBytes())
            `when`(connection.serial).thenReturn("0123456789ABCDEF")
            `when`(connection.claimInterface(fixture.vendorInterface, false)).thenReturn(true)
        }

    private fun clearRequestIdState() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("engineering_request_id_state_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun permissionIntentFor(usbManager: UsbManager, device: UsbDevice): PendingIntent {
        val captor = ArgumentCaptor.forClass(PendingIntent::class.java)
        verify(usbManager).requestPermission(Mockito.eq(device), captor.capture())
        return captor.value
    }

    private data class ProbeRig(val probe: AndroidUsbVendorProbe, val usbManager: UsbManager)

    private class ImmediateExecutorService : AbstractExecutorService() {
        private var stopped = false

        override fun execute(command: Runnable) {
            check(!stopped)
            command.run()
        }

        override fun shutdown() {
            stopped = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            stopped = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = stopped

        override fun isTerminated(): Boolean = stopped

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = stopped
    }

    private data class AndroidFixture(
        val device: UsbDevice,
        val hidInterface: UsbInterface,
        val vendorInterface: UsbInterface,
    )

    private fun androidDevice(name: String): AndroidFixture {
        val hidEndpoint = endpoint(0x81, UsbConstants.USB_ENDPOINT_XFER_INT, 64, 1)
        val outEndpoint = endpoint(0x02, UsbConstants.USB_ENDPOINT_XFER_BULK, 64, 0)
        val inEndpoint = endpoint(0x83, UsbConstants.USB_ENDPOINT_XFER_BULK, 64, 0)
        val hid = usbInterface(0, 0x03, listOf(hidEndpoint))
        val vendor = usbInterface(1, 0xFF, listOf(outEndpoint, inEndpoint))
        val device = Mockito.mock(UsbDevice::class.java)
        `when`(device.deviceName).thenReturn(name)
        `when`(device.vendorId).thenReturn(FixtureIdentityValidator.VENDOR_ID)
        `when`(device.productId).thenReturn(FixtureIdentityValidator.PRODUCT_ID)
        `when`(device.configurationCount).thenReturn(1)
        `when`(device.interfaceCount).thenReturn(2)
        `when`(device.getInterface(0)).thenReturn(hid)
        `when`(device.getInterface(1)).thenReturn(vendor)
        `when`(device.manufacturerName).thenReturn(FixtureIdentityValidator.EXPECTED_MANUFACTURER)
        `when`(device.productName).thenReturn(FixtureIdentityValidator.EXPECTED_PRODUCT)
        return AndroidFixture(device, hid, vendor)
    }

    private fun usbInterface(id: Int, interfaceClass: Int, endpoints: List<UsbEndpoint>): UsbInterface =
        Mockito.mock(UsbInterface::class.java).also { usbInterface ->
            `when`(usbInterface.id).thenReturn(id)
            `when`(usbInterface.alternateSetting).thenReturn(0)
            `when`(usbInterface.interfaceClass).thenReturn(interfaceClass)
            `when`(usbInterface.interfaceSubclass).thenReturn(0)
            `when`(usbInterface.interfaceProtocol).thenReturn(0)
            `when`(usbInterface.endpointCount).thenReturn(endpoints.size)
            endpoints.forEachIndexed { index, endpoint ->
                `when`(usbInterface.getEndpoint(index)).thenReturn(endpoint)
            }
        }

    private fun endpoint(address: Int, type: Int, maxPacketSize: Int, interval: Int): UsbEndpoint =
        Mockito.mock(UsbEndpoint::class.java).also { endpoint ->
            `when`(endpoint.address).thenReturn(address)
            `when`(endpoint.type).thenReturn(type)
            `when`(endpoint.maxPacketSize).thenReturn(maxPacketSize)
            `when`(endpoint.interval).thenReturn(interval)
        }

    private fun validFixtureDescriptorBytes(): ByteArray = byteArrayOf(
        18, 1, 0, 2, 0, 0, 0, 64, 9, 18, 1, 0, 0, 1, 1, 2, 3, 1,
        9, 2, 57, 0, 2, 1, 0, 0xC0.toByte(), 1,
        9, 4, 0, 0, 1, 3, 0, 0, 0,
        9, 0x21, 0x11, 1, 0, 1, 0x22, 43, 0,
        7, 5, 0x81.toByte(), 3, 64, 0, 1,
        9, 4, 1, 0, 2, 0xFF.toByte(), 0, 0, 0,
        7, 5, 2, 2, 64, 0, 0,
        7, 5, 0x83.toByte(), 2, 64, 0, 0,
    )
}
