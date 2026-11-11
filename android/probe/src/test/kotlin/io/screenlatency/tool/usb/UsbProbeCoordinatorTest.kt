package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbProbeCoordinatorTest {
    @Test
    fun permissionDenialDoesNotOpenOrClaimTheDevice() {
        val platform = FakeUsbPlatform()
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)

        assertEquals(ProbeConnectResult.PermissionDenied, coordinator.completePermission(candidate, granted = false))
        assertEquals(0, platform.openCount)
        assertTrue(platform.connection.claimRequests.isEmpty())
    }

    @Test
    fun claimsOnlyTheVendorInterfaceWithoutForcingAndClosesItOnCancellation() {
        val platform = FakeUsbPlatform()
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)
        val connected = coordinator.completePermission(candidate, granted = true) as ProbeConnectResult.Connected

        assertEquals(listOf(1 to false), platform.connection.claimRequests)
        coordinator.cancel()
        assertEquals(1, platform.connection.releaseCount)
        assertEquals(1, platform.connection.closeCount)
        connected.session.close()
        assertEquals(1, platform.connection.releaseCount)
        assertEquals(1, platform.connection.closeCount)
    }

    @Test
    fun malformedDescriptorsCloseTheConnectionBeforeAnyInterfaceClaim() {
        val platform = FakeUsbPlatform().apply {
            connection.raw = TestUsbFixture.descriptorBytes().copyOf(TestUsbFixture.descriptorBytes().size - 1)
        }
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)

        assertTrue(coordinator.completePermission(candidate, granted = true) is ProbeConnectResult.Rejected)
        assertTrue(platform.connection.claimRequests.isEmpty())
        assertEquals(1, platform.connection.closeCount)
        assertEquals(0, platform.connection.releaseCount)
    }

    @Test
    fun aFailedVendorClaimClosesTheConnectionAndNeverClaimsHid() {
        val platform = FakeUsbPlatform().apply { connection.claimResult = false }
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)

        assertTrue(coordinator.completePermission(candidate, granted = true) is ProbeConnectResult.Rejected)
        assertEquals(listOf(1 to false), platform.connection.claimRequests)
        assertEquals(1, platform.connection.closeCount)
        assertEquals(0, platform.connection.releaseCount)
    }

    @Test
    fun detachInvalidatesThePendingPermissionAndReenumerationRequiresFreshValidation() {
        val platform = FakeUsbPlatform()
        val coordinator = newTestUsbProbeCoordinator(platform)
        val first = readyCandidate(coordinator)

        coordinator.onUsbDetached()
        assertEquals(ProbeConnectResult.Stale, coordinator.completePermission(first, granted = true))
        assertEquals(0, platform.openCount)

        val replacement = readyCandidate(coordinator)
        assertNotEquals(first.generation, replacement.generation)
        assertTrue(coordinator.completePermission(replacement, granted = true) is ProbeConnectResult.Connected)
        assertEquals(1, platform.connection.rawDescriptorReadCount)
        assertEquals(1, platform.connection.protectedStringReadCount)
    }

    @Test
    fun attachInvalidatesAnOpenSessionAndReleasesItsClaim() {
        val platform = FakeUsbPlatform()
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)
        assertTrue(coordinator.completePermission(candidate, granted = true) is ProbeConnectResult.Connected)

        coordinator.onUsbAttached()

        assertEquals(1, platform.connection.releaseCount)
        assertEquals(1, platform.connection.closeCount)
        assertTrue(coordinator.discover() is ProbeDiscovery.Ready)
    }

    @Test
    fun discoveryIsBusyWhilePermissionCompletionOpensTheDevice() {
        val platform = FakeUsbPlatform()
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)
        var discoveryDuringOpen: ProbeDiscovery? = null
        platform.beforeOpen = { discoveryDuringOpen = coordinator.discover() }

        val result = coordinator.completePermission(candidate, granted = true)

        assertEquals(ProbeDiscovery.Busy, discoveryDuringOpen)
        assertTrue(result is ProbeConnectResult.Connected)
        coordinator.cancel()
        assertEquals(1, platform.connection.releaseCount)
        assertEquals(1, platform.connection.closeCount)
    }

    @Test
    fun exceptionsDuringDescriptorValidationCloseTheConnection() {
        val platform = FakeUsbPlatform().apply { connection.throwOnRawRead = true }
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = readyCandidate(coordinator)

        assertTrue(coordinator.completePermission(candidate, granted = true) is ProbeConnectResult.Rejected)
        assertEquals(1, platform.connection.closeCount)
        assertEquals(0, platform.connection.releaseCount)
    }

    @Test
    fun platformExceptionsRejectTheRequestAndReleaseConnectionAdmission() {
        listOf("permission", "enumeration", "open").forEach { failingOperation ->
            val platform = FakeUsbPlatform()
            val coordinator = newTestUsbProbeCoordinator(platform)
            val candidate = readyCandidate(coordinator)
            platform.failingOperation = failingOperation

            assertTrue(
                "Expected $failingOperation failure to be reported as rejected",
                coordinator.completePermission(candidate, granted = true) is ProbeConnectResult.Rejected,
            )

            platform.failingOperation = null
            assertTrue(
                "Expected discovery to recover after $failingOperation failure",
                coordinator.discover() is ProbeDiscovery.Ready,
            )
        }
    }

    private fun readyCandidate(coordinator: UsbProbeCoordinator): UsbCandidate =
        (coordinator.discover() as ProbeDiscovery.Ready).candidate

    private class FakeUsbPlatform : UsbProbePlatform {
        val connection = FakeUsbConnection()
        var devices = listOf(TestUsbFixture.device)
        var permissionGranted = true
        var openCount = 0
        var beforeOpen: (() -> Unit)? = null
        var failingOperation: String? = null
        private var connectedDeviceReadCount = 0

        override fun connectedDevices(): List<UsbDeviceSnapshot> {
            connectedDeviceReadCount++
            if (failingOperation == "enumeration" && connectedDeviceReadCount == 2) {
                error("device enumeration failed")
            }
            return devices
        }

        override fun hasPermission(deviceName: String): Boolean {
            if (failingOperation == "permission") error("permission query failed")
            return permissionGranted
        }

        override fun openDevice(deviceName: String): UsbConnectionPort? {
            openCount++
            if (failingOperation == "open") error("device open failed")
            beforeOpen?.invoke()
            return connection
        }
    }

    private class FakeUsbConnection : UsbConnectionPort {
        var raw = TestUsbFixture.descriptorBytes()
        var claimResult = true
        var throwOnRawRead = false
        var rawDescriptorReadCount = 0
        var protectedStringReadCount = 0
        var claimRequests = mutableListOf<Pair<Int, Boolean>>()
        var releaseCount = 0
        var closeCount = 0

        override fun rawDescriptors(): ByteArray {
            rawDescriptorReadCount++
            if (throwOnRawRead) error("raw descriptor read failed")
            return raw.copyOf()
        }

        override fun protectedStrings(): ProtectedUsbStrings {
            protectedStringReadCount++
            return TestUsbFixture.strings
        }

        override fun claimInterface(interfaceNumber: Int, force: Boolean): Boolean {
            claimRequests += interfaceNumber to force
            return claimResult
        }

        override fun releaseInterface(interfaceNumber: Int): Boolean {
            releaseCount++
            return true
        }

        override fun bulkOut(endpoint: UsbEndpointSnapshot, bytes: ByteArray, timeoutMs: Int): Int = bytes.size

        override fun bulkIn(endpoint: UsbEndpointSnapshot, maxLength: Int, timeoutMs: Int): ByteArray = byteArrayOf()

        override fun close() {
            closeCount++
        }
    }
}
