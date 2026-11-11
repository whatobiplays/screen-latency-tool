"""Check the USB descriptors stored in the compiled Teensy ELF image."""

from __future__ import annotations

import argparse
import struct
from dataclasses import dataclass
from pathlib import Path


HID_REPORT_DESCRIPTOR = bytes.fromhex(
    "05 01 09 05 A1 01 05 09 19 01 29 10 15 00 25 01 75 01 95 10 81 02 "
    "05 01 09 30 09 31 09 33 09 34 15 81 25 7F 75 08 95 04 81 02 C0"
)
HID_REPORT_DESCRIPTOR_LENGTH = 43
USB_DESCRIPTOR_LIST_ENTRY_SIZE = struct.calcsize("<HHIH2x")


@dataclass(frozen=True)
class Section:
    address: int
    offset: int
    size: int


class Elf32:
    """Read named ELF32 symbols without a third-party ELF dependency."""

    def __init__(self, path: Path) -> None:
        self.data = path.read_bytes()
        if self.data[:6] != b"\x7fELF\x01\x01":
            raise ValueError("expected a 32-bit little-endian ELF image")
        section_offset = struct.unpack_from("<I", self.data, 32)[0]
        section_entry_size, section_count = struct.unpack_from("<HH", self.data, 46)
        sections = [
            struct.unpack_from("<10I", self.data, section_offset + index * section_entry_size)
            for index in range(section_count)
        ]
        self.sections = [Section(item[3], item[4], item[5]) for item in sections]
        self.symbols: dict[str, tuple[int, int, int]] = {}

        for section in sections:
            section_type = section[1]
            if section_type not in (2, 11):  # SHT_SYMTAB and SHT_DYNSYM
                continue
            strings = sections[section[6]]
            string_data = self.data[strings[4] : strings[4] + strings[5]]
            entry_size = section[9]
            if entry_size < 16:
                raise ValueError("invalid ELF symbol entry size")
            for offset in range(section[4], section[4] + section[5], entry_size):
                name_offset, address, size, _info, _other, section_index = struct.unpack_from(
                    "<IIIBBH", self.data, offset
                )
                if name_offset == 0 or section_index >= len(self.sections):
                    continue
                end = string_data.find(b"\0", name_offset)
                name = string_data[name_offset:end].decode("ascii")
                if name:
                    self.symbols[name] = (address, size, section_index)

    def symbol(self, name: str) -> bytes:
        if name not in self.symbols:
            raise ValueError(f"compiled ELF is missing symbol {name}")
        address, size, section_index = self.symbols[name]
        section = self.sections[section_index]
        relative = address - section.address
        if relative < 0 or relative + size > section.size:
            raise ValueError(f"symbol {name} falls outside its ELF section")
        start = section.offset + relative
        return self.data[start : start + size]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def string_value(descriptor: bytes, expected: str, label: str) -> None:
    require(len(descriptor) >= 2 and descriptor[1] == 3,
            f"{label} is not a USB string descriptor")
    require(descriptor[0] == len(descriptor), f"{label} length does not match storage")
    decoded = descriptor[2:].decode("utf-16-le")
    require(decoded == expected, f"compiled {label} is {decoded!r}, expected {expected!r}")


def parse_configuration(descriptor: bytes, report_size: int) -> None:
    require(report_size == HID_REPORT_DESCRIPTOR_LENGTH,
            "compiled HID report array must be the 43-byte fixture report")
    require(len(descriptor) >= 9, "configuration descriptor is truncated")
    require(descriptor[0:2] == bytes((9, 2)), "configuration descriptor header is invalid")
    total_length = int.from_bytes(descriptor[2:4], "little")
    require(total_length == len(descriptor), "configuration total length is inconsistent")
    require(descriptor[4] == 2, "configuration must declare exactly two interfaces")
    require(descriptor[5] == 1, "configuration value must be one")
    require(descriptor[7] == 0xC0, "configuration must declare self-powered operation")
    require(descriptor[8] == 1, "configuration must declare 2 mA bus power")

    interfaces: dict[int, tuple[int, int, int, int, int]] = {}
    endpoint_addresses: set[int] = set()
    endpoints: dict[tuple[int, int], tuple[int, int, int]] = {}
    hid_report_length: int | None = None
    hid_descriptor_count = 0
    current_interface: int | None = None
    position = 0
    while position < len(descriptor):
        require(position + 2 <= len(descriptor), "descriptor header is truncated")
        length, kind = descriptor[position : position + 2]
        require(length >= 2 and position + length <= len(descriptor),
                "descriptor has an invalid length")
        item = descriptor[position : position + length]
        if kind == 4:
            require(length == 9, "unexpected interface descriptor length")
            interface_id = item[2]
            alternate = item[3]
            require(alternate == 0, "unexpected alternate interface setting")
            require(interface_id not in interfaces, "duplicate interface descriptor")
            interfaces[interface_id] = (item[4], item[5], item[6], item[7], item[8])
            current_interface = interface_id
        elif kind == 0x21:
            require(current_interface == 0 and length == 9,
                    "unexpected or malformed HID descriptor")
            hid_descriptor_count += 1
            require(hid_descriptor_count == 1,
                    "configuration must contain exactly one HID descriptor")
            require(item[2:5] == bytes((0x11, 0x01, 0)),
                    "HID descriptor version or country code is unexpected")
            require(item[5] == 1 and item[6] == 0x22,
                    "HID descriptor must contain one report descriptor")
            hid_report_length = int.from_bytes(item[7:9], "little")
        elif kind == 5:
            require(length == 7 and current_interface is not None,
                    "unexpected or malformed endpoint descriptor")
            address = item[2]
            require(address not in endpoint_addresses, "duplicate endpoint address")
            endpoint_addresses.add(address)
            endpoints[(current_interface, address)] = (
                item[3],
                int.from_bytes(item[4:6], "little"),
                item[6],
            )
        position += length

    require(interfaces == {
        0: (1, 3, 0, 0, 0),
        1: (2, 0xFF, 0, 0, 0),
    }, f"unexpected compiled interfaces: {interfaces!r}")
    require(endpoints == {
        (0, 0x81): (3, 64, 1),
        (1, 0x02): (2, 64, 0),
        (1, 0x83): (2, 64, 0),
    }, f"unexpected compiled endpoint tree: {endpoints!r}")
    require(hid_descriptor_count == 1,
            "configuration must contain exactly one HID descriptor")
    require(hid_report_length == HID_REPORT_DESCRIPTOR_LENGTH,
            "HID descriptor must declare the 43-byte fixture report")
    require(hid_report_length == report_size,
            "HID descriptor report length does not match the compiled report array")


def require_other_speed_configuration_unavailable(descriptor_table: bytes) -> None:
    """Check that the compiled lookup table cannot serve a type-7 descriptor."""
    require(
        len(descriptor_table) % USB_DESCRIPTOR_LIST_ENTRY_SIZE == 0,
        "compiled USB descriptor table has a malformed entry size",
    )
    standard_configuration_found = False
    terminator_found = False
    for offset in range(0, len(descriptor_table), USB_DESCRIPTOR_LIST_ENTRY_SIZE):
        value, index, address = struct.unpack_from("<HHI", descriptor_table, offset)
        if address == 0:
            require(value == 0 and index == 0,
                    "compiled USB descriptor table has an invalid terminator")
            require(offset + USB_DESCRIPTOR_LIST_ENTRY_SIZE == len(descriptor_table),
                    "compiled USB descriptor table has data after its terminator")
            terminator_found = True
            break
        require(value != 0x0700,
                "compiled Full-Speed-only firmware exposes an other-speed configuration")
        standard_configuration_found |= value == 0x0200

    require(terminator_found, "compiled USB descriptor table is not terminated")
    require(standard_configuration_found,
            "compiled USB descriptor table has no standard configuration")


def validate_report_descriptor(report: bytes) -> None:
    require(report == HID_REPORT_DESCRIPTOR,
            "HID report descriptor differs from the approved six-byte gamepad contract")
    report_sizes: list[tuple[int, int]] = []
    size = 0
    count = 0
    input_bits = 0
    position = 0
    while position < len(report):
        prefix = report[position]
        position += 1
        require(prefix != 0xFE, "long HID items are not expected in this report")
        encoded_size = prefix & 0x03
        item_size = 4 if encoded_size == 3 else encoded_size
        require(position + item_size <= len(report), "HID item is truncated")
        value = int.from_bytes(report[position : position + item_size], "little")
        position += item_size
        item_type = (prefix >> 2) & 0x03
        item_tag = (prefix >> 4) & 0x0F
        if item_type == 1 and item_tag == 7:
            size = value
        elif item_type == 1 and item_tag == 9:
            count = value
        elif item_type == 1 and item_tag == 8:
            raise ValueError("HID report must not contain a Report ID")
        elif item_type == 0 and item_tag == 8:
            input_bits += size * count
            report_sizes.append((size, count))

    require(report_sizes == [(1, 16), (8, 4)],
            f"unexpected HID input fields: {report_sizes!r}")
    require(input_bits == 48, f"HID input report is {input_bits} bits, not six bytes")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("elf", type=Path, help="compiled Teensy 4.1 firmware ELF")
    arguments = parser.parse_args()

    elf = Elf32(arguments.elf)
    device = elf.symbol("device_descriptor")
    require(len(device) == 18 and device[0:2] == bytes((18, 1)),
            "compiled USB device descriptor has an unexpected shape")
    require(int.from_bytes(device[2:4], "little") == 0x0200,
            "compiled USB version is not 2.0")
    require(device[4:7] == bytes((0, 0, 0)), "device class must be defined by interfaces")
    require(device[7] == 64, "USB endpoint zero must have a 64-byte packet size")
    require(int.from_bytes(device[8:10], "little") == 0x1209 and
            int.from_bytes(device[10:12], "little") == 0x0001,
            "compiled VID/PID differs from the provisional fixture identity")
    require(device[14:18] == bytes((1, 2, 3, 1)),
            "compiled device string indexes or configuration count are unexpected")

    string_value(elf.symbol("usb_string_manufacturer_name_default"),
                 "Screen Latency Tool", "manufacturer string")
    string_value(elf.symbol("usb_string_product_name_default"),
                 "Screen Latency Fixture v1", "product string")
    serial = elf.symbol("usb_string_serial_number_default")
    require(len(serial) == 34 and serial[0:2] == bytes((34, 3)),
            "serial descriptor must reserve sixteen UTF-16 code units")

    report = elf.symbol("slt_hid_report_desc")
    validate_report_descriptor(report)
    require_other_speed_configuration_unavailable(elf.symbol("usb_descriptor_list"))
    parse_configuration(elf.symbol("usb_config_descriptor_12"), len(report))
    print(
        "compiled descriptors passed: device identity, two Full-Speed interfaces, "
        "three endpoints, 64-byte packets, six-byte HID input, strings, "
        "other-speed configuration unavailable"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
