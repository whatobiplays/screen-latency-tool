package io.screenlatency.tool.usb

/** Creates an in-memory request-ID store for tests that do not exercise durable storage. */
internal fun testEngineeringRequestIdSequenceProvider(): EngineeringRequestIdSequenceProvider {
    val stores = mutableMapOf<String, TestEngineeringRequestIdStateStore>()
    return EngineeringRequestIdSequenceProvider { serial ->
        synchronized(stores) {
            stores.getOrPut(serial) { TestEngineeringRequestIdStateStore(nextRequestId = 1L) }
        }
    }
}

internal fun newTestUsbProbeCoordinator(platform: UsbProbePlatform): UsbProbeCoordinator =
    UsbProbeCoordinator(platform, testEngineeringRequestIdSequenceProvider())

private class TestEngineeringRequestIdStateStore(
    private var nextRequestId: Long?,
) : EngineeringRequestIdStateStore {
    override fun loadNextRequestId(): Long? = nextRequestId

    override fun saveNextRequestId(nextRequestId: Long): Boolean {
        this.nextRequestId = nextRequestId
        return true
    }
}
