package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FixtureIdentityValidatorTest {
    @Test
    fun acceptsVariableLengthWellFormedDescriptorsAndProtectedIdentity() {
        val validated = FixtureIdentityValidator.validate(
            expectedDevice,
            descriptorBytes(includeVariableDescriptor = true),
            expectedStrings,
        )

        assertEquals(FixtureIdentityValidator.VENDOR_ID, validated.vendorId)
        assertEquals(FixtureIdentityValidator.PRODUCT_ID, validated.productId)
        assertEquals(expectedStrings.serial, validated.serial)
    }

    @Test
    fun preliminaryScreenRejectsWrongVendorOrProduct() {
        val wrongVendor = expectedDevice.copy(vendorId = 0x1234)
        val wrongProduct = expectedDevice.copy(productId = 2)

        assertThrows(FixtureIdentityViolation::class.java) { FixtureIdentityValidator.preflight(wrongVendor) }
        assertThrows(FixtureIdentityViolation::class.java) { FixtureIdentityValidator.preflight(wrongProduct) }
    }

    @Test
    fun preliminaryScreenRejectsUnexpectedInterfaceOrEndpointModels() {
        val missingVendorInterface = expectedDevice.copy(interfaces = expectedDevice.interfaces.dropLast(1))
        val wrongHidEndpoint = expectedDevice.copy(
            interfaces = expectedDevice.interfaces.map { interfaceModel ->
                if (interfaceModel.id == 0) {
                    interfaceModel.copy(endpoints = listOf(interfaceModel.endpoints.single().copy(address = 0x82)))
                } else interfaceModel
            },
        )

        assertThrows(FixtureIdentityViolation::class.java) {
            FixtureIdentityValidator.preflight(missingVendorInterface)
        }
        assertThrows(FixtureIdentityViolation::class.java) {
            FixtureIdentityValidator.preflight(wrongHidEndpoint)
        }
    }

    @Test
    fun rejectsMissingOrDuplicateHidInterfaces() {
        val hid = expectedDevice.interfaces.single { it.id == FixtureIdentityValidator.HID_INTERFACE }
        val vendor = expectedDevice.interfaces.single { it.id == FixtureIdentityValidator.VENDOR_INTERFACE }
        val missingHid = expectedDevice.copy(interfaces = listOf(vendor))
        val duplicateHid = expectedDevice.copy(interfaces = listOf(hid, hid, vendor))
        val duplicateInterfaceNumber = expectedDevice.copy(interfaces = listOf(hid, vendor.copy(id = hid.id)))

        listOf(missingHid, duplicateHid, duplicateInterfaceNumber).forEach { device ->
            assertThrows(FixtureIdentityViolation::class.java) {
                FixtureIdentityValidator.preflight(device)
            }
        }
    }

    @Test
    fun rejectsMissingOrDuplicateRawHidDescriptors() {
        val missing = TestUsbFixture.descriptorBytes(includeHidDescriptor = false)
        val duplicate = TestUsbFixture.descriptorBytes(duplicateHidDescriptor = true)

        listOf(missing, duplicate).forEach { rawDescriptors ->
            assertThrows(DescriptorViolation::class.java) {
                FixtureIdentityValidator.validate(expectedDevice, rawDescriptors, expectedStrings)
            }
        }
    }

    @Test
    fun rejectsRawHidDescriptorWithMismatchedReportLength() {
        val rawDescriptors = TestUsbFixture.descriptorBytes(hidReportLength = 63)

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, rawDescriptors, expectedStrings)
        }
    }

    @Test
    fun rejectsRawHidDescriptorWithUnexpectedSignature() {
        val rawDescriptors = TestUsbFixture.descriptorBytes(
            hidDescriptorVersion = 0x0110,
            hidDescriptorCountryCode = 1,
            hidReportDescriptorCount = 2,
            hidReportDescriptorType = 0x23,
        )

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, rawDescriptors, expectedStrings)
        }
    }

    @Test
    fun rejectsOverlongStandardConfigurationInterfaceAndEndpointDescriptors() {
        val overlongDescriptors = listOf(
            TestUsbFixture.descriptorBytes(extraDeviceDescriptorByte = true),
            TestUsbFixture.descriptorBytes(extraConfigurationDescriptorByte = true),
            TestUsbFixture.descriptorBytes(extraInterfaceDescriptorByte = true),
            TestUsbFixture.descriptorBytes(extraEndpointDescriptorByte = true),
        )

        overlongDescriptors.forEach { rawDescriptors ->
            assertThrows(DescriptorViolation::class.java) {
                FixtureIdentityValidator.validate(expectedDevice, rawDescriptors, expectedStrings)
            }
        }
    }

    @Test
    fun rejectsOverlongRawHidDescriptor() {
        val rawDescriptors = TestUsbFixture.descriptorBytes(extraHidDescriptorByte = true)

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, rawDescriptors, expectedStrings)
        }
    }

    @Test
    fun rejectsUnexpectedRawEndpointAttributeBits() {
        val invalidHidEndpoint = TestUsbFixture.descriptorBytes(hidEndpointAttributes = 0x83)
        val invalidVendorEndpoints = TestUsbFixture.descriptorBytes(vendorEndpointAttributes = 0x82)

        listOf(invalidHidEndpoint, invalidVendorEndpoints).forEach { rawDescriptors ->
            assertThrows(DescriptorViolation::class.java) {
                FixtureIdentityValidator.validate(expectedDevice, rawDescriptors, expectedStrings)
            }
        }
    }

    @Test
    fun rejectsTruncatedRawDescriptorStreams() {
        val truncated = descriptorBytes().copyOf(descriptorBytes().size - 1)

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, truncated, expectedStrings)
        }
    }

    @Test
    fun rejectsRawDeviceDescriptorThatDeclaresButOmitsItsConfiguration() {
        val deviceDescriptorOnly = descriptorBytes().copyOf(18)

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, deviceDescriptorOnly, expectedStrings)
        }
    }

    @Test
    fun rejectsDescriptorsAfterTheSingleConfiguration() {
        val withTrailingDescriptor = descriptorBytes() + byteArrayOf(2, 0x03)

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, withTrailingDescriptor, expectedStrings)
        }
    }

    @Test
    fun rejectsDescriptorLengthsBelowTheHeaderSize() {
        val malformed = descriptorBytes().also { it[it.lastIndex - 6] = 1 }

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, malformed, expectedStrings)
        }
    }

    @Test
    fun rejectsDescriptorLengthsThatRunPastTheConfiguration() {
        val malformed = descriptorBytes().also { it[it.lastIndex - 6] = 8 }

        assertThrows(DescriptorViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, malformed, expectedStrings)
        }
    }

    @Test
    fun rejectsRawIdentityThatContradictsThePublicDeviceModel() {
        val rawWithWrongProduct = descriptorBytes(productId = 2)

        assertThrows(FixtureIdentityViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, rawWithWrongProduct, expectedStrings)
        }
    }

    @Test
    fun rejectsContradictoryProtectedManufacturerProductAndSerial() {
        listOf(
            expectedStrings.copy(manufacturer = "Other manufacturer"),
            expectedStrings.copy(product = "Other product"),
            expectedStrings.copy(serial = "0123456789abcdef"),
            expectedStrings.copy(serial = "1234"),
        ).forEach { strings ->
            assertThrows(FixtureIdentityViolation::class.java) {
                FixtureIdentityValidator.validate(expectedDevice, descriptorBytes(), strings)
            }
        }
    }

    @Test
    fun rejectsPublicEndpointModelsThatContradictRawDescriptors() {
        val alteredRawDescriptors = descriptorBytes().also { it[it.size - 3] = 32 }

        assertThrows(FixtureIdentityViolation::class.java) {
            FixtureIdentityValidator.validate(expectedDevice, alteredRawDescriptors, expectedStrings)
        }
    }

    private fun descriptorBytes(
        vendorId: Int = FixtureIdentityValidator.VENDOR_ID,
        productId: Int = FixtureIdentityValidator.PRODUCT_ID,
        includeVariableDescriptor: Boolean = false,
    ): ByteArray = TestUsbFixture.descriptorBytes(vendorId, productId, includeVariableDescriptor)

    private val expectedStrings = TestUsbFixture.strings

    private val expectedDevice = TestUsbFixture.device
}
