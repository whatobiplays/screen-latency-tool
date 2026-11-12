"""Deterministic tests for linker-map RAM budget validation."""

from pathlib import Path
import sys
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from check_capture_memory import (  # noqa: E402
    ARCHIVE_BYTES,
    MemoryBudget,
    Symbol,
    calculate_budget,
    parse_symbols,
    validate_budget,
)


class CaptureMemoryBudgetTests(unittest.TestCase):
    def test_parses_linker_symbols(self) -> None:
        symbols = parse_symbols(
            "20200000 00061b00 B (anonymous namespace)::capture_archive\n"
            "20010000 00001000 B _ebss\n"
        )
        self.assertEqual(symbols["(anonymous namespace)::capture_archive"].size,
                         ARCHIVE_BYTES)
        self.assertEqual(symbols["_ebss"].address, 0x20010000)

    def test_calculates_static_headroom(self) -> None:
        symbols = {
            "capture_archive": Symbol(0x20200000, ARCHIVE_BYTES),
            "hid_slots": Symbol(0x20261c00, 2048),
            "vendor_out_slot": Symbol(0x20261b80, 128),
            "vendor_in_slot": Symbol(0x20261b00, 128),
            "event_queue": Symbol(0x20008000, 960),
            "_edata": Symbol(0x20001000, 0),
            "_ebss": Symbol(0x20002000, 0),
            "_estack": Symbol(0x20078000, 0),
            "_heap_start": Symbol(0x20262440, 0),
            "_heap_end": Symbol(0x20280000, 0),
        }
        budget = calculate_budget(symbols)
        self.assertEqual(budget.ram1_headroom, 0x76000)
        self.assertEqual(budget.ram2_headroom, 0x80000 - 0x62440)
        self.assertEqual(budget.usb_dma_bytes, 2304)
        self.assertEqual(budget.ram2_used, 0x62440)
        self.assertEqual(budget.event_queue_bytes, 960)
        validate_budget(budget)

    def test_rejects_archive_outside_internal_ram2(self) -> None:
        budget = MemoryBudget(
            archive=Symbol(0x70000000, ARCHIVE_BYTES),
            usb_dma_bytes=128,
            usb_dma_symbols=(Symbol(0x20200000, 128),),
            event_queue_bytes=128,
            ram2_used=ARCHIVE_BYTES + 128,
            ram1_headroom=64 * 1024,
            ram2_headroom=64 * 1024,
        )
        with self.assertRaisesRegex(ValueError, "outside internal RAM2"):
            validate_budget(budget)

    def test_rejects_insufficient_heap_reserve(self) -> None:
        budget = MemoryBudget(
            archive=Symbol(0x20200000, ARCHIVE_BYTES),
            usb_dma_bytes=128,
            usb_dma_symbols=(Symbol(0x20261b00, 128),),
            event_queue_bytes=128,
            ram2_used=ARCHIVE_BYTES + 128,
            ram1_headroom=64 * 1024,
            ram2_headroom=1024,
        )
        with self.assertRaisesRegex(ValueError, "RAM2 heap headroom"):
            validate_budget(budget)


if __name__ == "__main__":
    unittest.main()
