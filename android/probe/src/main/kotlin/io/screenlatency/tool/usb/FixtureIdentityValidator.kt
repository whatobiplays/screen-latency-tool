package io.screenlatency.tool.usb

/** Applies the provisional fixture identity and interface contract before any vendor claim. */
object FixtureIdentityValidator {
    const val VENDOR_ID = 0x1209
    const val PRODUCT_ID = 0x0001
    const val EXPECTED_MANUFACTURER = "Screen Latency Tool"
    const val EXPECTED_PRODUCT = "Screen Latency Fixture v1"
    const val HID_INTERFACE = 0
    const val VENDOR_INTERFACE = 1
    const val HID_REPORT_DESCRIPTOR_LENGTH = 43

    private const val DEVICE_DESCRIPTOR_LENGTH = 18
    private const val CONFIGURATION_DESCRIPTOR_TYPE = 2
    private const val INTERFACE_DESCRIPTOR_TYPE = 4
    private const val ENDPOINT_DESCRIPTOR_TYPE = 5
    private const val HID_DESCRIPTOR_TYPE = 0x21
    private const val HID_REPORT_DESCRIPTOR_TYPE = 0x22
    private const val HID_VERSION = 0x0111
    private const val CONFIGURATION_DESCRIPTOR_LENGTH = 9
    private const val INTERFACE_DESCRIPTOR_LENGTH = 9
    private const val ENDPOINT_DESCRIPTOR_LENGTH = 7
    private const val HID_DESCRIPTOR_LENGTH = 9

    /** Screens VID/PID and the public interface model before the permission prompt. */
    fun preflight(device: UsbDeviceSnapshot) {
        if (device.vendorId != VENDOR_ID || device.productId != PRODUCT_ID) {
            throw FixtureIdentityViolation("USB vendor/product ID does not match the provisional fixture")
        }
        if (device.configurationCount != 1 || device.interfaces.size != 2) {
            throw FixtureIdentityViolation("Fixture must expose one configuration and two interfaces")
        }

        val interfaces = device.interfaces.associateBy(UsbInterfaceSnapshot::id)
        if (interfaces.size != 2 || interfaces.keys != setOf(HID_INTERFACE, VENDOR_INTERFACE)) {
            throw FixtureIdentityViolation("Fixture interface numbers must be 0 and 1")
        }
        verifyInterface(
            interfaces.getValue(HID_INTERFACE),
            expectedClass = 0x03,
            expectedSubclass = 0,
            expectedProtocol = 0,
            expectedEndpoints = listOf(UsbEndpointSnapshot(0x81, 0x03, 64, 1)),
        )
        verifyInterface(
            interfaces.getValue(VENDOR_INTERFACE),
            expectedClass = 0xFF,
            expectedSubclass = 0,
            expectedProtocol = 0,
            expectedEndpoints = listOf(
                UsbEndpointSnapshot(0x02, 0x02, 64, 0),
                UsbEndpointSnapshot(0x83, 0x02, 64, 0),
            ),
        )
    }

    /** Validates raw descriptors and protected strings, then cross-checks the public model. */
    fun validate(
        device: UsbDeviceSnapshot,
        rawDescriptors: ByteArray,
        strings: ProtectedUsbStrings,
    ): ValidatedFixture {
        preflight(device)
        val manufacturer = strings.manufacturer
        val product = strings.product
        val serial = strings.serial
        if (manufacturer != EXPECTED_MANUFACTURER || product != EXPECTED_PRODUCT) {
            throw FixtureIdentityViolation("Protected manufacturer or product string does not match")
        }
        if (serial == null || !SERIAL_PATTERN.matches(serial)) {
            throw FixtureIdentityViolation("Protected serial must be 16 uppercase hexadecimal characters")
        }

        val parsed = parseDescriptors(rawDescriptors)
        if (parsed.vendorId != device.vendorId || parsed.productId != device.productId) {
            throw FixtureIdentityViolation("Raw device descriptor contradicts the public VID/PID")
        }
        if (parsed.usbVersion != 0x0200 || parsed.deviceClass != 0 ||
            parsed.deviceSubclass != 0 || parsed.deviceProtocol != 0 || parsed.endpointZeroPacketSize != 64
        ) {
            throw FixtureIdentityViolation("Raw device descriptor does not match the fixture signature")
        }
        if (parsed.configurationCount != 1 || parsed.manufacturerStringIndex == 0 ||
            parsed.productStringIndex == 0 || parsed.serialStringIndex == 0
        ) {
            throw FixtureIdentityViolation("Raw device descriptor has an unexpected configuration or string table")
        }

        val configuration = parsed.configuration
            ?: throw DescriptorViolation("Raw descriptor stream omits its declared configuration")
        if (configuration.configurationValue != 1 || configuration.attributes != 0xC0 ||
            configuration.maxPower != 1
        ) {
            throw FixtureIdentityViolation("Raw configuration descriptor does not match the fixture signature")
        }
        crossCheckInterfaces(device.interfaces, configuration.interfaces)

        return ValidatedFixture(
            deviceName = device.deviceName,
            vendorId = VENDOR_ID,
            productId = PRODUCT_ID,
            manufacturer = manufacturer,
            product = product,
            serial = serial,
        )
    }

    private fun verifyInterface(
        actual: UsbInterfaceSnapshot,
        expectedClass: Int,
        expectedSubclass: Int,
        expectedProtocol: Int,
        expectedEndpoints: List<UsbEndpointSnapshot>,
    ) {
        if (actual.alternateSetting != 0 || actual.interfaceClass != expectedClass ||
            actual.interfaceSubclass != expectedSubclass || actual.interfaceProtocol != expectedProtocol
        ) {
            throw FixtureIdentityViolation("Interface ${actual.id} has an unexpected class signature")
        }
        if (actual.endpoints.sortedBy(UsbEndpointSnapshot::address) !=
            expectedEndpoints.sortedBy(UsbEndpointSnapshot::address)
        ) {
            throw FixtureIdentityViolation("Interface ${actual.id} has unexpected endpoints")
        }
    }

    private fun crossCheckInterfaces(
        publicInterfaces: List<UsbInterfaceSnapshot>,
        rawInterfaces: List<RawInterface>,
    ) {
        if (rawInterfaces.size != publicInterfaces.size) {
            throw FixtureIdentityViolation("Raw and public interface counts disagree")
        }
        val publicById = publicInterfaces.associateBy(UsbInterfaceSnapshot::id)
        rawInterfaces.forEach { raw ->
            val public = publicById[raw.id]
                ?: throw FixtureIdentityViolation("Raw interface ${raw.id} is missing from the public model")
            if (raw.alternateSetting != public.alternateSetting || raw.interfaceClass != public.interfaceClass ||
                raw.interfaceSubclass != public.interfaceSubclass || raw.interfaceProtocol != public.interfaceProtocol
            ) {
                throw FixtureIdentityViolation("Raw interface ${raw.id} contradicts the public model")
            }
            val rawEndpoints = raw.endpoints.sortedBy(UsbEndpointSnapshot::address)
            val publicEndpoints = public.endpoints.sortedBy(UsbEndpointSnapshot::address)
            if (rawEndpoints != publicEndpoints) {
                throw FixtureIdentityViolation("Raw endpoints for interface ${raw.id} contradict the public model")
            }
        }
    }

    private fun parseDescriptors(bytes: ByteArray): RawDevice {
        if (bytes.size < DEVICE_DESCRIPTOR_LENGTH) throw DescriptorViolation("Raw descriptors omit the device descriptor")
        val deviceLength = u8(bytes, 0)
        if (deviceLength != DEVICE_DESCRIPTOR_LENGTH || u8(bytes, 1) != 1) {
            throw DescriptorViolation("First raw descriptor is not an 18-byte device descriptor")
        }

        val device = RawDevice(
            usbVersion = u16(bytes, 2),
            deviceClass = u8(bytes, 4),
            deviceSubclass = u8(bytes, 5),
            deviceProtocol = u8(bytes, 6),
            endpointZeroPacketSize = u8(bytes, 7),
            vendorId = u16(bytes, 8),
            productId = u16(bytes, 10),
            configurationCount = u8(bytes, 17),
            manufacturerStringIndex = u8(bytes, 14),
            productStringIndex = u8(bytes, 15),
            serialStringIndex = u8(bytes, 16),
        )

        var offset = deviceLength
        var configuration: RawConfiguration? = null
        while (offset < bytes.size) {
            val length = descriptorLength(bytes, offset, bytes.size)
            val type = u8(bytes, offset + 1)
            if (type != CONFIGURATION_DESCRIPTOR_TYPE) {
                throw DescriptorViolation("Unexpected descriptor outside the device configuration")
            }
            if (configuration != null) throw DescriptorViolation("Multiple raw configurations are not supported")
            configuration = parseConfiguration(bytes, offset, length)
            offset += configuration.totalLength
        }
        return device.copy(configuration = configuration)
    }

    private fun parseConfiguration(bytes: ByteArray, offset: Int, descriptorLength: Int): RawConfiguration {
        if (descriptorLength != CONFIGURATION_DESCRIPTOR_LENGTH) {
            throw DescriptorViolation("Configuration descriptor must be exactly nine bytes")
        }
        val totalLength = u16(bytes, offset + 2)
        val end = offset + totalLength
        if (totalLength < descriptorLength || end > bytes.size) {
            throw DescriptorViolation("Configuration total length is truncated or inconsistent")
        }

        val configuration = RawConfiguration(
            totalLength = totalLength,
            declaredInterfaceCount = u8(bytes, offset + 4),
            configurationValue = u8(bytes, offset + 5),
            attributes = u8(bytes, offset + 7),
            maxPower = u8(bytes, offset + 8),
            interfaces = mutableListOf(),
        )
        var current: RawInterface? = null
        var hidDescriptorSeen = false
        var childOffset = offset + descriptorLength
        while (childOffset < end) {
            val childLength = descriptorLength(bytes, childOffset, end)
            when (u8(bytes, childOffset + 1)) {
                CONFIGURATION_DESCRIPTOR_TYPE -> throw DescriptorViolation("Nested configuration descriptor")
                INTERFACE_DESCRIPTOR_TYPE -> {
                    finishInterface(current)
                    if (childLength != INTERFACE_DESCRIPTOR_LENGTH) {
                        throw DescriptorViolation("Interface descriptor must be exactly nine bytes")
                    }
                    val nextInterface = RawInterface(
                        id = u8(bytes, childOffset + 2),
                        alternateSetting = u8(bytes, childOffset + 3),
                        declaredEndpointCount = u8(bytes, childOffset + 4),
                        interfaceClass = u8(bytes, childOffset + 5),
                        interfaceSubclass = u8(bytes, childOffset + 6),
                        interfaceProtocol = u8(bytes, childOffset + 7),
                        endpoints = mutableListOf(),
                    )
                    current = nextInterface
                    if (configuration.interfaces.any { it.id == nextInterface.id }) {
                        throw DescriptorViolation("Alternate interface settings are not part of the fixture signature")
                    }
                    configuration.interfaces += nextInterface
                }
                HID_DESCRIPTOR_TYPE -> {
                    if (childLength != HID_DESCRIPTOR_LENGTH) {
                        throw DescriptorViolation("HID descriptor must be exactly nine bytes")
                    }
                    val owner = current
                        ?: throw DescriptorViolation("HID descriptor appears before an interface")
                    if (owner.id != HID_INTERFACE || owner.interfaceClass != 0x03) {
                        throw DescriptorViolation("HID descriptor is attached to a non-HID interface")
                    }
                    if (hidDescriptorSeen) throw DescriptorViolation("Multiple HID descriptors are not supported")
                    if (u16(bytes, childOffset + 2) != HID_VERSION ||
                        u8(bytes, childOffset + 4) != 0 ||
                        u8(bytes, childOffset + 5) != 1 ||
                        u8(bytes, childOffset + 6) != HID_REPORT_DESCRIPTOR_TYPE ||
                        u16(bytes, childOffset + 7) != HID_REPORT_DESCRIPTOR_LENGTH
                    ) {
                        throw DescriptorViolation("HID descriptor does not match the fixture report contract")
                    }
                    hidDescriptorSeen = true
                }
                ENDPOINT_DESCRIPTOR_TYPE -> {
                    if (childLength != ENDPOINT_DESCRIPTOR_LENGTH) {
                        throw DescriptorViolation("Endpoint descriptor must be exactly seven bytes")
                    }
                    val owner = current ?: throw DescriptorViolation("Endpoint appears before an interface")
                    val expectedAttributes = when (owner.id) {
                        HID_INTERFACE -> 0x03
                        VENDOR_INTERFACE -> 0x02
                        else -> null
                    }
                    if (expectedAttributes == null || u8(bytes, childOffset + 3) != expectedAttributes) {
                        throw DescriptorViolation("Endpoint attributes do not match the fixture interface")
                    }
                    owner.endpoints += UsbEndpointSnapshot(
                        address = u8(bytes, childOffset + 2),
                        transferType = u8(bytes, childOffset + 3) and 0x03,
                        maxPacketSize = u16(bytes, childOffset + 4),
                        interval = u8(bytes, childOffset + 6),
                    )
                }
            }
            childOffset += childLength
        }
        finishInterface(current)
        if (!hidDescriptorSeen) throw DescriptorViolation("Configuration omits its HID descriptor")
        if (childOffset != end || configuration.interfaces.size != configuration.declaredInterfaceCount) {
            throw DescriptorViolation("Configuration interface count or total length is inconsistent")
        }
        return configuration.copy(interfaces = configuration.interfaces.toMutableList())
    }

    private fun finishInterface(interfaceDescriptor: RawInterface?) {
        if (interfaceDescriptor != null && interfaceDescriptor.endpoints.size != interfaceDescriptor.declaredEndpointCount) {
            throw DescriptorViolation("Interface endpoint count does not match its descriptor")
        }
    }

    private fun descriptorLength(bytes: ByteArray, offset: Int, limit: Int): Int {
        if (offset + 2 > limit) throw DescriptorViolation("Truncated descriptor header")
        val length = u8(bytes, offset)
        if (length < 2) throw DescriptorViolation("Descriptor length must include its two-byte header")
        if (offset + length > limit) throw DescriptorViolation("Descriptor extends past its containing block")
        return length
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u16(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private data class RawDevice(
        val usbVersion: Int,
        val deviceClass: Int,
        val deviceSubclass: Int,
        val deviceProtocol: Int,
        val endpointZeroPacketSize: Int,
        val vendorId: Int,
        val productId: Int,
        val configurationCount: Int,
        val manufacturerStringIndex: Int,
        val productStringIndex: Int,
        val serialStringIndex: Int,
        val configuration: RawConfiguration? = null,
    )

    private data class RawConfiguration(
        val totalLength: Int,
        val declaredInterfaceCount: Int,
        val configurationValue: Int,
        val attributes: Int,
        val maxPower: Int,
        val interfaces: MutableList<RawInterface>,
    )

    private data class RawInterface(
        val id: Int,
        val alternateSetting: Int,
        val declaredEndpointCount: Int,
        val interfaceClass: Int,
        val interfaceSubclass: Int,
        val interfaceProtocol: Int,
        val endpoints: MutableList<UsbEndpointSnapshot>,
    )

    private val SERIAL_PATTERN = Regex("[0-9A-F]{16}")
}
