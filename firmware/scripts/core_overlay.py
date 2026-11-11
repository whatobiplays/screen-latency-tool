"""Apply the version-locked Teensy USB descriptor and endpoint overlay."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


def replace_exact(text: str, before: str, after: str, count: int = 1) -> str:
    actual = text.count(before)
    if actual != count:
        raise RuntimeError(
            f"Core overlay expected {count} occurrence(s), found {actual}: {before[:80]!r}"
        )
    return text.replace(before, after)


def replace_in_region(
    text: str, start: str, end: str, before: str, after: str, count: int = 1
) -> str:
    start_at = text.index(start)
    end_at = text.index(end, start_at)
    region = text[start_at:end_at]
    region = replace_exact(region, before, after, count)
    return text[:start_at] + region + text[end_at:]


def guard_fixture_other_speed_configuration(text: str) -> str:
    """Keep the unsupported other-speed entry out of the fixture lookup table."""
    entry = "\t{0x0700, 0x0000, usb_config_descriptor_12, CONFIG_DESC_SIZE},\n"
    return replace_exact(
        text,
        entry,
        "#if !defined(USB_SLT_FIXTURE)\n" + entry + "#endif\n",
    )


def transform(relative_path: str, text: str) -> str:
    if relative_path == "cores/teensy4/usb_desc.h":
        custom = """#if defined(USB_SLT_FIXTURE)
#define VENDOR_ID 0x1209
#define PRODUCT_ID 0x0001
#define BCD_DEVICE 0x0100
#define MANUFACTURER_NAME {'S','c','r','e','e','n',' ','L','a','t','e','n','c','y',' ','T','o','o','l'}
#define MANUFACTURER_NAME_LEN 19
#define PRODUCT_NAME {'S','c','r','e','e','n',' ','L','a','t','e','n','c','y',' ','F','i','x','t','u','r','e',' ','v','1'}
#define PRODUCT_NAME_LEN 25
#define EP0_SIZE 64
#define NUM_ENDPOINTS 3
#define NUM_USB_BUFFERS 8
#define NUM_INTERFACE 2
#define KEYBOARD_INTERFACE 0
#define KEYBOARD_ENDPOINT 1
#define KEYBOARD_SIZE 16
#define SLT_HID_ENDPOINT_MPS 64
#define KEYBOARD_INTERVAL 1
#define EXPERIMENTAL_INTERFACE 1
#define ENDPOINT1_CONFIG ENDPOINT_RECEIVE_UNUSED + ENDPOINT_TRANSMIT_INTERRUPT
#define ENDPOINT2_CONFIG ENDPOINT_RECEIVE_BULK + ENDPOINT_TRANSMIT_UNUSED
#define ENDPOINT3_CONFIG ENDPOINT_RECEIVE_UNUSED + ENDPOINT_TRANSMIT_BULK
#elif defined(USB_SERIAL)
"""
        return replace_exact(text, "#if defined(USB_SERIAL)\n", custom)

    if relative_path == "cores/teensy4/usb_desc.c":
        text = guard_fixture_other_speed_configuration(text)
        report = """#if defined(USB_SLT_FIXTURE)
static uint8_t slt_hid_report_desc[] = {
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x05,       // Usage (Game Pad)
        0xA1, 0x01,       // Collection (Application)
        0x05, 0x09,       // Usage Page (Button)
        0x19, 0x01,       // Usage Minimum (Button 1)
        0x29, 0x10,       // Usage Maximum (Button 16)
        0x15, 0x00,       // Logical Minimum (0)
        0x25, 0x01,       // Logical Maximum (1)
        0x75, 0x01,       // Report Size (1)
        0x95, 0x10,       // Report Count (16)
        0x81, 0x02,       // Input (Data, Variable, Absolute)
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x30,       // Usage (X)
        0x09, 0x31,       // Usage (Y)
        0x09, 0x33,       // Usage (Rx)
        0x09, 0x34,       // Usage (Ry)
        0x15, 0x81,       // Logical Minimum (-127)
        0x25, 0x7F,       // Logical Maximum (127)
        0x75, 0x08,       // Report Size (8)
        0x95, 0x04,       // Report Count (4)
        0x81, 0x02,       // Input (Data, Variable, Absolute)
        0xC0              // End Collection
};
#define SLT_KEYBOARD_REPORT_DESCRIPTOR slt_hid_report_desc
#else
#define SLT_KEYBOARD_REPORT_DESCRIPTOR keyboard_report_desc
#endif

"""
        text = replace_exact(
            text,
            "};\n#endif\n\n#ifdef KEYMEDIA_INTERFACE\n",
            "};\n#endif\n\n" + report + "#ifdef KEYMEDIA_INTERFACE\n",
        )
        text = replace_exact(
            text,
            "sizeof(keyboard_report_desc)",
            "sizeof(SLT_KEYBOARD_REPORT_DESCRIPTOR)",
            count=5,
        )
        text = replace_exact(
            text,
            "        KEYBOARD_SIZE, 0,                       // wMaxPacketSize\n",
            "#if defined(USB_SLT_FIXTURE)\n        SLT_HID_ENDPOINT_MPS, 0,               // wMaxPacketSize\n#else\n        KEYBOARD_SIZE, 0,                       // wMaxPacketSize\n#endif\n",
            count=2,
        )
        text = replace_exact(
            text,
            "{0x2200, KEYBOARD_INTERFACE, keyboard_report_desc, sizeof(SLT_KEYBOARD_REPORT_DESCRIPTOR)},",
            "{0x2200, KEYBOARD_INTERFACE, SLT_KEYBOARD_REPORT_DESCRIPTOR, sizeof(SLT_KEYBOARD_REPORT_DESCRIPTOR)},",
        )
        keyboard_subprotocol = """        0x03,                                   // bInterfaceClass (0x03 = HID)
#if defined(USB_SLT_FIXTURE)
        0x00,                                   // bInterfaceSubClass
        0x00,                                   // bInterfaceProtocol
#else
        0x01,                                   // bInterfaceSubClass (0x01 = Boot)
        0x01,                                   // bInterfaceProtocol (0x01 = Keyboard)
#endif
"""
        text = replace_exact(
            text,
            "        0x03,                                   // bInterfaceClass (0x03 = HID)\n        0x01,                                   // bInterfaceSubClass (0x01 = Boot)\n        0x01,                                   // bInterfaceProtocol (0x01 = Keyboard)\n",
            keyboard_subprotocol,
            count=2,
        )
        power = """#if defined(USB_SLT_FIXTURE)
        1,                                      // bMaxPower (2 mA)
#else
        50,                                     // bMaxPower
#endif
"""
        text = replace_exact(
            text, "        50,                                     // bMaxPower\n", power, count=2
        )

        vendor480 = """        0xFF,                                   // bInterfaceClass (Vendor)
#if defined(USB_SLT_FIXTURE)
        0x00,                                   // bInterfaceSubClass
        0x00,                                   // bInterfaceProtocol
#else
        0x6A,                                   // bInterfaceSubClass
        0xC7,                                   // bInterfaceProtocol
#endif
        0,                                      // iInterface
        7,                                      // bLength
        5,                                      // bDescriptorType
#if defined(USB_SLT_FIXTURE)
        0x02,                                   // bulk OUT endpoint
#else
        1 | 0x80,                               // bEndpointAddress
#endif
        0x02,                                   // bmAttributes (bulk)
#if defined(USB_SLT_FIXTURE)
        LSB(64), MSB(64),                       // wMaxPacketSize
#else
        LSB(512), MSB(512),                     // wMaxPacketSize
#endif
#if defined(USB_SLT_FIXTURE)
        0,                                      // bInterval
#else
        1,                                      // bInterval
#endif
        7,                                      // bLength
        5,                                      // bDescriptorType
#if defined(USB_SLT_FIXTURE)
        0x83,                                   // bulk IN endpoint
#else
        1,                                      // bEndpointAddress
#endif
        0x02,                                   // bmAttributes (bulk)
#if defined(USB_SLT_FIXTURE)
        LSB(64), MSB(64),                       // wMaxPacketSize
#else
        LSB(512), MSB(512),                     // wMaxPacketSize
#endif
#if defined(USB_SLT_FIXTURE)
        0,                                      // bInterval
#else
        1,                                      // bInterval
#endif
"""
        vendor12 = """        0xFF,                                   // bInterfaceClass (Vendor)
#if defined(USB_SLT_FIXTURE)
        0x00,                                   // bInterfaceSubClass
        0x00,                                   // bInterfaceProtocol
#else
        0x6A,                                   // bInterfaceSubClass
        0xFF,                                   // bInterfaceProtocol
#endif
        0,                                      // iInterface
        7,                                      // bLength
        5,                                      // bDescriptorType
#if defined(USB_SLT_FIXTURE)
        0x02,                                   // bulk OUT endpoint
#else
        1 | 0x80,                               // bEndpointAddress
#endif
        0x02,                                   // bmAttributes (bulk)
        LSB(64), MSB(64),                       // wMaxPacketSize
        0,                                      // bInterval
        7,                                      // bLength
        5,                                      // bDescriptorType
#if defined(USB_SLT_FIXTURE)
        0x83,                                   // bulk IN endpoint
#else
        1,                                      // bEndpointAddress
#endif
        0x02,                                   // bmAttributes (bulk)
        LSB(64), MSB(64),                       // wMaxPacketSize
        0,                                      // bInterval
"""
        region_start = "#ifdef EXPERIMENTAL_INTERFACE\n\t// configuration for 480 Mbit/sec speed"
        region_end = "#endif // EXPERIMENTAL_INTERFACE"
        text = replace_in_region(
            text,
            region_start,
            region_end,
            "        0xFF,                                   // bInterfaceClass (0xFF = Vendor)\n        0x6A,                                   // bInterfaceSubClass\n        0xC7,                                   // bInterfaceProtocol\n        0,                                      // iInterface\n        // endpoint descriptor, USB spec 9.6.6, page 269-271, Table 9-13\n        7,                                      // bLength\n        5,                                      // bDescriptorType\n        1 | 0x80,                               // bEndpointAddress\n        0x02,                                   // bmAttributes (0x02=bulk)\n        LSB(512), MSB(512),                     // wMaxPacketSize\n        1,                                      // bInterval\n        // endpoint descriptor, USB spec 9.6.6, page 269-271, Table 9-13\n        7,                                      // bLength\n        5,                                      // bDescriptorType\n        1,                                      // bEndpointAddress\n        0x02,                                   // bmAttributes (0x02=bulk)\n        LSB(512), MSB(512),                     // wMaxPacketSize\n        1,                                      // bInterval\n",
            vendor480,
        )
        text = replace_in_region(
            text,
            "#ifdef EXPERIMENTAL_INTERFACE\n\t// configuration for 12 Mbit/sec speed",
            region_end,
            "        0xFF,                                   // bInterfaceClass (0xFF = Vendor)\n        0x6A,                                   // bInterfaceSubClass\n        0xFF,                                   // bInterfaceProtocol\n        0,                                      // iInterface\n        // endpoint descriptor, USB spec 9.6.6, page 269-271, Table 9-13\n        7,                                      // bLength\n        5,                                      // bDescriptorType\n        1 | 0x80,                               // bEndpointAddress\n        0x02,                                   // bmAttributes (0x02=bulk)\n        LSB(64), MSB(64),                       // wMaxPacketSize\n        1,                                      // bInterval\n        // endpoint descriptor, USB spec 9.6.6, page 269-271, Table 9-13\n        7,                                      // bLength\n        5,                                      // bDescriptorType\n        1,                                      // bEndpointAddress\n        0x02,                                   // bmAttributes (0x02=bulk)\n        LSB(64), MSB(64),                       // wMaxPacketSize\n        1,                                      // bInterval\n",
            vendor12,
        )

        text = replace_exact(
            text,
            "#ifdef EXPERIMENTAL_INTERFACE\nstatic uint8_t microsoft_os_string_desc[] = {",
            "#if defined(EXPERIMENTAL_INTERFACE) && !defined(USB_SLT_FIXTURE)\nstatic uint8_t microsoft_os_string_desc[] = {",
        )
        text = replace_exact(
            text,
            "#ifdef EXPERIMENTAL_INTERFACE\n\t{0x03EE, 0x0000, microsoft_os_string_desc, 18},",
            "#if defined(EXPERIMENTAL_INTERFACE) && !defined(USB_SLT_FIXTURE)\n\t{0x03EE, 0x0000, microsoft_os_string_desc, 18},",
        )
        serial = """#if defined(USB_SLT_FIXTURE)
        slt_format_usb_serial_number(HW_OCOTP_MAC0, HW_OCOTP_MAC1,
                                     usb_string_serial_number_default.wString);
        usb_string_serial_number_default.bLength = 34;
#else
"""
        text = replace_exact(
            text,
            "void usb_init_serialnumber(void)\n{\n\tchar buf[11];\n\tuint32_t i, num;\n",
            "#if defined(USB_SLT_FIXTURE)\n#include \"slt/serial_number.h\"\n#endif\n\n"
            "void usb_init_serialnumber(void)\n{\n\tchar buf[11];\n\tuint32_t i, num;\n",
        )
        text = replace_exact(
            text,
            "void usb_init_serialnumber(void)\n{\n\tchar buf[11];\n\tuint32_t i, num;\n",
            "void usb_init_serialnumber(void)\n{\n\tchar buf[11];\n\tuint32_t i, num;\n" + serial,
        )
        text = replace_exact(
            text,
            "struct usb_string_descriptor_struct usb_string_serial_number_default = {\n"
            "        12,\n"
            "        3,\n"
            "        {0,0,0,0,0,0,0,0,0,0}\n"
            "};",
            "struct usb_string_descriptor_struct usb_string_serial_number_default = {\n"
            "#if defined(USB_SLT_FIXTURE)\n"
            "        34,\n"
            "#else\n"
            "        12,\n"
            "#endif\n"
            "        3,\n"
            "#if defined(USB_SLT_FIXTURE)\n"
            "        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}\n"
            "#else\n"
            "        {0,0,0,0,0,0,0,0,0,0}\n"
            "#endif\n"
            "};",
        )
        text = replace_exact(
            text,
            "\tusb_string_serial_number_default.bLength = i * 2 + 2;\n}\n",
            "\tusb_string_serial_number_default.bLength = i * 2 + 2;\n#endif\n}\n",
        )
        return text

    if relative_path == "cores/teensy4/usb.c":
        text = replace_exact(
            text,
            "\tUSB1_USBCMD = USB_USBCMD_RS;\n",
            """#if defined(USB_SLT_FIXTURE)
        uint32_t portsc = USB1_PORTSC1 &
                ~(USB_PORTSC1_OCC | USB_PORTSC1_PEC | USB_PORTSC1_CSC);
        USB1_PORTSC1 = portsc | USB_PORTSC1_PFSC;
#endif
\tUSB1_USBCMD = USB_USBCMD_RS;\n""",
        )
        text = replace_exact(
            text,
            "\t\tendpointN_notify_mask = 0;\n\t\t// TODO: Free all allocated dTDs\n",
            """\t\tendpointN_notify_mask = 0;
#if defined(USB_SLT_FIXTURE)
                usb_configuration = 0;
                uint32_t portsc = USB1_PORTSC1 &
                        ~(USB_PORTSC1_OCC | USB_PORTSC1_PEC | USB_PORTSC1_CSC);
                USB1_PORTSC1 = portsc | USB_PORTSC1_PFSC;
                slt_usb_bus_reset();
#endif
\t\t// TODO: Free all allocated dTDs
""",
        )
        text = replace_exact(
            text,
            "\t\t#if defined(ENDPOINT2_CONFIG)\n\t\tUSB1_ENDPTCTRL2 = ENDPOINT2_CONFIG;\n",
            """#if defined(USB_SLT_FIXTURE) && defined(ENDPOINT1_CONFIG)
                USB1_ENDPTCTRL1 = ENDPOINT1_CONFIG;
#endif
\t\t#if defined(ENDPOINT2_CONFIG)
\t\tUSB1_ENDPTCTRL2 = ENDPOINT2_CONFIG;
""",
        )
        text = replace_exact(
            text,
            "\t\t#if defined(KEYBOARD_INTERFACE)\n\t\tusb_keyboard_configure();\n\t\t#endif\n",
            "\t\t#if defined(KEYBOARD_INTERFACE) && !defined(USB_SLT_FIXTURE)\n\t\tusb_keyboard_configure();\n\t\t#endif\n",
        )
        text = replace_exact(
            text,
            "\t\t#if defined(EXPERIMENTAL_INTERFACE)\n\t\tmemset(endpoint_queue_head + 2, 0, sizeof(endpoint_t) * 2);",
            "#if defined(EXPERIMENTAL_INTERFACE) && !defined(USB_SLT_FIXTURE)\n\t\tmemset(endpoint_queue_head + 2, 0, sizeof(endpoint_t) * 2);",
        )
        text = replace_exact(
            text,
            "#ifdef EXPERIMENTAL_INTERFACE\n\t  case 0xF8C0: // GET_MS_DESCRIPTOR",
            "#if defined(EXPERIMENTAL_INTERFACE) && !defined(USB_SLT_FIXTURE)\n\t  case 0xF8C0: // GET_MS_DESCRIPTOR",
        )
        text = replace_exact(
            text,
            "\tif (ep < 2 || ep > NUM_ENDPOINTS) return;",
            "#if defined(USB_SLT_FIXTURE)\n\tif (ep < 1 || ep > NUM_ENDPOINTS) return;\n#else\n\tif (ep < 2 || ep > NUM_ENDPOINTS) return;\n#endif",
            count=4,
        )
        text = replace_exact(
            text,
            "\tif (endpoint_number < 2 || endpoint_number > NUM_ENDPOINTS) return;",
            "#if defined(USB_SLT_FIXTURE)\n\tif (endpoint_number < 1 || endpoint_number > NUM_ENDPOINTS) return;\n#else\n\tif (endpoint_number < 2 || endpoint_number > NUM_ENDPOINTS) return;\n#endif",
            count=2,
        )
        cancel = """#if defined(USB_SLT_FIXTURE)
uint8_t slt_usb_cancel_transfer(uint32_t endpoint_number, uint8_t transmit)
{
        if (endpoint_number < 1 || endpoint_number > NUM_ENDPOINTS) return 0;
        if (endpoint_number == 1 && !transmit) return 1;
        __disable_irq();
        const uint32_t mask = 1u << (endpoint_number + (transmit ? 16 : 0));
        const uint32_t flush_started = ARM_DWT_CYCCNT;
        USB1_ENDPTFLUSH = mask;
        while (USB1_ENDPTFLUSH & mask) {
                if ((uint32_t)(ARM_DWT_CYCCNT - flush_started) >= 6000000u) {
                        __enable_irq();
                        return 0;
                }
        }
        USB1_ENDPTCOMPLETE = mask;
        endpoint_t *queue = endpoint_queue_head + endpoint_number * 2 +
                            (transmit ? 1 : 0);
        usb_endpoint_config(queue, queue->config, queue->callback_function);
        __enable_irq();
        return 1;
}
#endif

"""
        text = replace_exact(
            text,
            "uint32_t usb_transfer_status(const transfer_t *transfer)\n{\n",
            cancel + "uint32_t usb_transfer_status(const transfer_t *transfer)\n{\n",
        )
        return text

    if relative_path == "cores/teensy4/Print.cpp":
        return replace_exact(
            text,
            "\tif (file >= 0 && file <= 2) file = (int)&Serial;\n",
            """#if defined(USB_SLT_FIXTURE)
        (void)file;
        (void)ptr;
        (void)len;
        return 0;
#else
\tif (file >= 0 && file <= 2) file = (int)&Serial;
\treturn ((class Print *)file)->write((uint8_t *)ptr, len);
#endif
""",
        )

    if relative_path == "cores/teensy4/yield.cpp":
        return replace_exact(
            text,
            "\t// USB Serial - Add hack to minimize impact...\n\tif (check_flags & YIELD_CHECK_USB_SERIAL) {\n\t\tif (Serial.available()) serialEvent();\n\t}\n",
            "#if !defined(USB_SLT_FIXTURE)\n"
            "\t// USB Serial - Add hack to minimize impact...\n"
            "\tif (check_flags & YIELD_CHECK_USB_SERIAL) {\n"
            "\t\tif (Serial.available()) serialEvent();\n"
            "\t}\n"
            "#endif\n",
        )

    if relative_path == "cores/teensy4/usb_dev.h":
        return replace_exact(
            text,
            "void usb_init_serialnumber(void);\n",
            """void usb_init_serialnumber(void);
#if defined(USB_SLT_FIXTURE)
extern volatile uint8_t usb_configuration;
uint8_t slt_usb_cancel_transfer(uint32_t endpoint_number, uint8_t transmit);
void slt_usb_bus_reset(void);
#endif
""",
        )

    raise ValueError(f"No Teensy overlay transformation for {relative_path}")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def apply(framework_dir: Path, overlay_dir: Path) -> None:
    manifest = json.loads((overlay_dir / "manifest.json").read_text())
    package = json.loads((framework_dir / "package.json").read_text())
    if package.get("name") != manifest["framework_package"]:
        raise RuntimeError("Unexpected PlatformIO Teensy framework package")
    if package.get("version") != manifest["framework_version"]:
        raise RuntimeError(
            f"Expected Teensy framework {manifest['framework_version']}, got {package.get('version')}"
        )

    for relative_path, hashes in manifest["files"].items():
        path = framework_dir / relative_path
        current = path.read_bytes()
        current_hash = sha256(current)
        if current_hash == hashes["overlay_sha256"]:
            continue
        if current_hash != hashes["upstream_sha256"]:
            raise RuntimeError(
                f"Refusing to patch unexpected Teensy core file {relative_path}: {current_hash}"
            )
        patched = transform(relative_path, current.decode("utf-8")).encode("utf-8")
        patched_hash = sha256(patched)
        if patched_hash != hashes["overlay_sha256"]:
            raise RuntimeError(
                f"Overlay output for {relative_path} is not hash-pinned: {patched_hash}"
            )
        path.write_bytes(patched)

