"""Exercise the compiled-descriptor parser against the fixture USB contract."""

from __future__ import annotations

import unittest

import check_compiled_descriptors
import core_overlay
from check_compiled_descriptors import parse_configuration, validate_report_descriptor


REPORT_DESCRIPTOR = bytes((
    0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x05, 0x09, 0x19, 0x01, 0x29,
    0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10, 0x81, 0x02,
    0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x33, 0x09, 0x34, 0x15,
    0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x04, 0x81, 0x02, 0xC0,
))


def endpoint(address: int, attributes: int, interval: int) -> bytes:
    return bytes((7, 5, address, attributes, 64, 0, interval))


def configuration(
    hid_endpoint: bytes,
    vendor_endpoints: tuple[bytes, bytes],
    *,
    report_length: int = len(REPORT_DESCRIPTOR),
    hid_descriptor_count: int = 1,
) -> bytes:
    descriptors = [
        bytes((9, 2, 0, 0, 2, 1, 0, 0xC0, 1)),
        bytes((9, 4, 0, 0, 1, 3, 0, 0, 0)),
    ]
    descriptors.extend(
        bytes((9, 0x21, 0x11, 0x01, 0, 1, 0x22, report_length, 0))
        for _ in range(hid_descriptor_count)
    )
    descriptors.extend((
        hid_endpoint,
        bytes((9, 4, 1, 0, 2, 0xFF, 0, 0, 0)),
        *vendor_endpoints,
    ))
    total_length = sum(map(len, descriptors))
    descriptors[0] = bytes((9, 2, total_length & 0xFF, total_length >> 8, 2, 1, 0, 0xC0, 1))
    return b"".join(descriptors)


class CompiledDescriptorParserTest(unittest.TestCase):
    def test_verifier_accepts_a_descriptor_table_without_other_speed_config(self) -> None:
        verify = getattr(
            check_compiled_descriptors,
            "require_other_speed_configuration_unavailable",
            None,
        )
        self.assertTrue(
            callable(verify),
            "compiled descriptor verifier must check other-speed availability",
        )
        descriptor_table = bytes((
            0x00, 0x02, 0x00, 0x00, 0x01, 0x10, 0x00, 0x00,
            0x09, 0x00, 0x00, 0x00,
            *([0] * 12),
        ))

        verify(descriptor_table)

    def test_verifier_rejects_an_other_speed_configuration_entry(self) -> None:
        verify = getattr(
            check_compiled_descriptors,
            "require_other_speed_configuration_unavailable",
            None,
        )
        self.assertTrue(
            callable(verify),
            "compiled descriptor verifier must check other-speed availability",
        )
        descriptor_table = bytes((
            0x00, 0x07, 0x00, 0x00, 0x01, 0x10, 0x00, 0x00,
            0x09, 0x00, 0x00, 0x00,
            *([0] * 12),
        ))

        with self.assertRaisesRegex(ValueError, "other-speed configuration"):
            verify(descriptor_table)

    def test_overlay_excludes_fixture_other_speed_configuration_entry(self) -> None:
        guard = getattr(core_overlay, "guard_fixture_other_speed_configuration", None)
        self.assertTrue(
            callable(guard),
            "core overlay must exclude the fixture's unsupported other-speed entry",
        )
        entry = (
            "\t{0x0700, 0x0000, usb_config_descriptor_12, CONFIG_DESC_SIZE},\n"
        )

        guarded_entry = guard(entry)

        self.assertIn("#if !defined(USB_SLT_FIXTURE)", guarded_entry)
        self.assertIn(entry.rstrip(), guarded_entry)

    def test_accepts_the_fixture_report_and_endpoint_contract(self) -> None:
        descriptor = configuration(
            endpoint(0x81, 0x03, 1),
            (endpoint(0x02, 0x02, 0), endpoint(0x83, 0x02, 0)),
        )

        parse_configuration(descriptor, report_size=len(REPORT_DESCRIPTOR))
        validate_report_descriptor(REPORT_DESCRIPTOR)

    def test_rejects_endpoint_addresses_assigned_to_the_wrong_interfaces(self) -> None:
        descriptor = configuration(
            endpoint(0x02, 0x02, 0),
            (endpoint(0x81, 0x03, 1), endpoint(0x83, 0x02, 0)),
        )

        with self.assertRaisesRegex(ValueError, "unexpected compiled endpoint tree"):
            parse_configuration(descriptor, report_size=len(REPORT_DESCRIPTOR))

    def test_rejects_noncontract_hid_report_lengths(self) -> None:
        descriptor = configuration(
            endpoint(0x81, 0x03, 1),
            (endpoint(0x02, 0x02, 0), endpoint(0x83, 0x02, 0)),
            report_length=48,
        )

        with self.assertRaisesRegex(ValueError, "43-byte fixture report"):
            parse_configuration(descriptor, report_size=48)

    def test_rejects_missing_or_duplicate_hid_descriptors(self) -> None:
        endpoints = (endpoint(0x02, 0x02, 0), endpoint(0x83, 0x02, 0))
        for count in (0, 2):
            with self.subTest(hid_descriptor_count=count):
                descriptor = configuration(
                    endpoint(0x81, 0x03, 1),
                    endpoints,
                    hid_descriptor_count=count,
                )
                with self.assertRaisesRegex(ValueError, "exactly one HID descriptor"):
                    parse_configuration(descriptor, report_size=len(REPORT_DESCRIPTOR))

    def test_rejects_reserved_endpoint_attribute_bits(self) -> None:
        invalid_endpoints = (
            (endpoint(0x81, 0x83, 1), (endpoint(0x02, 0x02, 0), endpoint(0x83, 0x02, 0))),
            (endpoint(0x81, 0x03, 1), (endpoint(0x02, 0x82, 0), endpoint(0x83, 0x02, 0))),
        )
        for hid_endpoint, vendor_endpoints in invalid_endpoints:
            with self.subTest(hid=hid_endpoint[3], vendor=vendor_endpoints[0][3]):
                descriptor = configuration(hid_endpoint, vendor_endpoints)
                with self.assertRaisesRegex(ValueError, "unexpected compiled endpoint tree"):
                    parse_configuration(descriptor, report_size=len(REPORT_DESCRIPTOR))

    def test_rejects_report_descriptors_with_wrong_usages_ranges_or_flags(self) -> None:
        invalid_bytes = (3, 11, 25, 33, 35, 21)
        replacements = (0x04, 0x0F, 0x31, 0x80, 0x7E, 0x03)
        for index, replacement in zip(invalid_bytes, replacements, strict=True):
            report = bytearray(REPORT_DESCRIPTOR)
            report[index] = replacement
            with self.subTest(index=index, replacement=replacement):
                with self.assertRaisesRegex(ValueError, "approved six-byte gamepad contract"):
                    validate_report_descriptor(bytes(report))

    def test_rejects_report_descriptor_with_wrong_length(self) -> None:
        with self.assertRaisesRegex(ValueError, "approved six-byte gamepad contract"):
            validate_report_descriptor(REPORT_DESCRIPTOR[:-1])


if __name__ == "__main__":
    unittest.main()
