package io.screenlatency.tool.usb

/** Flags USB events that may affect a selected fixture so callers can revalidate its identity. */
object UsbEventIdentityPolicy {
    fun mayAffectSelectedFixture(
        eventDeviceName: String?,
        eventVendorId: Int?,
        eventProductId: Int?,
        selectedDeviceNames: Set<String>,
        selectedVendorId: Int?,
        selectedProductId: Int?,
    ): Boolean {
        val fixtureIsTracked = selectedDeviceNames.isNotEmpty() ||
            (selectedVendorId != null && selectedProductId != null)
        if (!fixtureIsTracked) return false
        if (eventDeviceName == null || eventDeviceName in selectedDeviceNames) return true
        return selectedVendorId != null && selectedProductId != null &&
            eventVendorId == selectedVendorId && eventProductId == selectedProductId
    }
}
