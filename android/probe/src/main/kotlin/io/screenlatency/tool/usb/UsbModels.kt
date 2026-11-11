package io.screenlatency.tool.usb

/** Public interface and endpoint fields Android exposes before permission is granted. */
data class UsbEndpointSnapshot(
    val address: Int,
    val transferType: Int,
    val maxPacketSize: Int,
    val interval: Int,
)

data class UsbInterfaceSnapshot(
    val id: Int,
    val alternateSetting: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpoints: List<UsbEndpointSnapshot>,
)

data class UsbDeviceSnapshot(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val configurationCount: Int,
    val interfaces: List<UsbInterfaceSnapshot>,
)

/** USB strings that the Android host API protects until permission is granted. */
data class ProtectedUsbStrings(
    val manufacturer: String?,
    val product: String?,
    val serial: String?,
)

data class ValidatedFixture(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String,
    val product: String,
    val serial: String,
)

class FixtureIdentityViolation(message: String) : IllegalArgumentException(message)

class DescriptorViolation(message: String) : IllegalArgumentException(message)
