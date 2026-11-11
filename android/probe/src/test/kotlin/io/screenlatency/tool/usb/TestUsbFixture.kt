package io.screenlatency.tool.usb

internal object TestUsbFixture {
    val strings = ProtectedUsbStrings(
        manufacturer = "Screen Latency Tool",
        product = "Screen Latency Fixture v1",
        serial = "0123456789ABCDEF",
    )

    val device = UsbDeviceSnapshot(
        deviceName = "/dev/bus/usb/001/002",
        vendorId = FixtureIdentityValidator.VENDOR_ID,
        productId = FixtureIdentityValidator.PRODUCT_ID,
        configurationCount = 1,
        interfaces = listOf(
            UsbInterfaceSnapshot(
                id = 0,
                alternateSetting = 0,
                interfaceClass = 3,
                interfaceSubclass = 0,
                interfaceProtocol = 0,
                endpoints = listOf(UsbEndpointSnapshot(0x81, 3, 64, 1)),
            ),
            UsbInterfaceSnapshot(
                id = 1,
                alternateSetting = 0,
                interfaceClass = 0xFF,
                interfaceSubclass = 0,
                interfaceProtocol = 0,
                endpoints = listOf(
                    UsbEndpointSnapshot(0x02, 2, 64, 0),
                    UsbEndpointSnapshot(0x83, 2, 64, 0),
                ),
            ),
        ),
    )

    fun descriptorBytes(
        vendorId: Int = FixtureIdentityValidator.VENDOR_ID,
        productId: Int = FixtureIdentityValidator.PRODUCT_ID,
        includeVariableDescriptor: Boolean = false,
        includeHidDescriptor: Boolean = true,
        duplicateHidDescriptor: Boolean = false,
        hidReportLength: Int = 43,
        hidDescriptorVersion: Int = 0x0111,
        hidDescriptorCountryCode: Int = 0,
        hidReportDescriptorCount: Int = 1,
        hidReportDescriptorType: Int = 0x22,
        extraDeviceDescriptorByte: Boolean = false,
        extraConfigurationDescriptorByte: Boolean = false,
        extraInterfaceDescriptorByte: Boolean = false,
        extraHidDescriptorByte: Boolean = false,
        extraEndpointDescriptorByte: Boolean = false,
        hidEndpointAttributes: Int = 3,
        vendorEndpointAttributes: Int = 2,
    ): ByteArray {
        val deviceDescriptor = withExtraByte(
            bytes(18, 1, 0x00, 0x02, 0, 0, 0, 64) +
                u16Bytes(vendorId) + u16Bytes(productId) + bytes(0x00, 0x01, 1, 2, 3, 1),
            extraDeviceDescriptorByte,
        )
        val hidInterface = withExtraByte(bytes(9, 4, 0, 0, 1, 3, 0, 0, 0), extraInterfaceDescriptorByte)
        val hidDescriptor = withExtraByte(
            bytes(
                9,
                0x21,
                hidDescriptorVersion and 0xFF,
                hidDescriptorVersion ushr 8,
                hidDescriptorCountryCode,
                hidReportDescriptorCount,
                hidReportDescriptorType,
                hidReportLength and 0xFF,
                hidReportLength ushr 8,
            ),
            extraHidDescriptorByte,
        )
        val hidDescriptorTree = if (!includeHidDescriptor) {
            byteArrayOf()
        } else if (duplicateHidDescriptor) {
            hidDescriptor + hidDescriptor
        } else {
            hidDescriptor
        }
        val hidEndpoint = withExtraByte(bytes(7, 5, 0x81, hidEndpointAttributes, 64, 0, 1), extraEndpointDescriptorByte)
        val vendorInterface = bytes(9, 4, 1, 0, 2, 0xFF, 0, 0, 0)
        val variableDescriptor = if (includeVariableDescriptor) bytes(5, 0x24, 0x11, 0x22, 0x33) else byteArrayOf()
        val bulkOut = bytes(7, 5, 0x02, vendorEndpointAttributes, 64, 0, 0)
        val bulkIn = bytes(7, 5, 0x83, vendorEndpointAttributes, 64, 0, 0)
        val body = hidInterface + hidDescriptorTree + hidEndpoint + vendorInterface + variableDescriptor + bulkOut + bulkIn
        val configurationHeader = withExtraByte(
            bytes(9, 2, 0, 0, 2, 1, 0, 0xC0, 1),
            extraConfigurationDescriptorByte,
        )
        val totalLength = configurationHeader.size + body.size
        configurationHeader[2] = totalLength.toByte()
        configurationHeader[3] = (totalLength ushr 8).toByte()
        val configuration = configurationHeader + body
        return deviceDescriptor + configuration
    }

    private fun withExtraByte(descriptor: ByteArray, includeExtraByte: Boolean): ByteArray {
        if (!includeExtraByte) return descriptor
        return descriptor.copyOf(descriptor.size + 1).also {
            it[0] = (descriptor[0] + 1).toByte()
        }
    }

    private fun bytes(vararg values: Int): ByteArray = values.map(Int::toByte).toByteArray()

    private fun u16Bytes(value: Int) = bytes(value and 0xFF, value ushr 8)
}
