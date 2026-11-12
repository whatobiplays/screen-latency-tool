"""Check the Issue #5 archive and static RAM reserves from the linked image."""

from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


ARCHIVE_BYTES = 128 + 5 * 10_000 * 8
RAM2_START = 0x20200000
RAM2_END = RAM2_START + 512 * 1024
MIN_RAM1_STACK_RESERVE = 32 * 1024
MIN_RAM2_HEAP_RESERVE = 32 * 1024


@dataclass(frozen=True)
class Symbol:
    address: int
    size: int


@dataclass(frozen=True)
class MemoryBudget:
    archive: Symbol
    usb_dma_bytes: int
    usb_dma_symbols: tuple[Symbol, ...]
    event_queue_bytes: int
    ram2_used: int
    ram1_headroom: int
    ram2_headroom: int


def parse_symbols(output: str) -> dict[str, Symbol]:
    symbols: dict[str, Symbol] = {}
    for line in output.splitlines():
        match = re.match(
            r"^\s*([0-9a-fA-F]+)\s+(?:([0-9a-fA-F]+)\s+)?[A-Za-z]\s+(.+?)\s*$",
            line,
        )
        if match:
            symbols[match.group(3)] = Symbol(
                int(match.group(1), 16),
                int(match.group(2), 16) if match.group(2) else 0,
            )
    return symbols


def find_symbol(symbols: dict[str, Symbol], suffix: str) -> Symbol:
    matches = [symbol for name, symbol in symbols.items() if name.endswith(suffix)]
    if len(matches) != 1:
        raise ValueError(f"expected one linker symbol ending in {suffix!r}, found {len(matches)}")
    return matches[0]


def calculate_budget(symbols: dict[str, Symbol]) -> MemoryBudget:
    archive = find_symbol(symbols, "capture_archive")
    dma_symbols = (
        find_symbol(symbols, "hid_slots"),
        find_symbol(symbols, "vendor_out_slot"),
        find_symbol(symbols, "vendor_in_slot"),
    )
    event_queue = find_symbol(symbols, "event_queue")
    data_end = max(
        find_symbol(symbols, "_edata").address,
        find_symbol(symbols, "_ebss").address,
    )
    stack_top = find_symbol(symbols, "_estack").address
    heap_start = find_symbol(symbols, "_heap_start").address
    heap_end = find_symbol(symbols, "_heap_end").address
    return MemoryBudget(
        archive=archive,
        usb_dma_bytes=sum(symbol.size for symbol in dma_symbols),
        usb_dma_symbols=dma_symbols,
        event_queue_bytes=event_queue.size,
        ram2_used=heap_start - RAM2_START,
        ram1_headroom=stack_top - data_end,
        ram2_headroom=heap_end - heap_start,
    )


def validate_budget(budget: MemoryBudget) -> None:
    if budget.archive.size != ARCHIVE_BYTES:
        raise ValueError(
            f"capture archive is {budget.archive.size} bytes; expected {ARCHIVE_BYTES}"
        )
    archive_end = budget.archive.address + budget.archive.size
    if not (RAM2_START <= budget.archive.address < archive_end <= RAM2_END):
        raise ValueError("capture archive is outside internal RAM2 / DMAMEM")
    if any(
        not (RAM2_START <= symbol.address < symbol.address + symbol.size <= RAM2_END)
        for symbol in budget.usb_dma_symbols
    ):
        raise ValueError("USB DMA slots are outside internal RAM2 / DMAMEM")
    if budget.usb_dma_bytes <= 0 or budget.event_queue_bytes <= 0:
        raise ValueError("USB DMA slots and fixed event ring must be present in the map")
    if budget.ram1_headroom < MIN_RAM1_STACK_RESERVE:
        raise ValueError(
            f"RAM1 stack headroom {budget.ram1_headroom} is below "
            f"{MIN_RAM1_STACK_RESERVE} bytes"
        )
    if budget.ram2_headroom < MIN_RAM2_HEAP_RESERVE:
        raise ValueError(
            f"RAM2 heap headroom {budget.ram2_headroom} is below "
            f"{MIN_RAM2_HEAP_RESERVE} bytes"
        )


def find_nm(firmware_root: Path) -> Path:
    tool = firmware_root / ".platformio-core/packages/toolchain-gccarmnoneeabi-teensy/bin/arm-none-eabi-nm"
    if tool.is_file():
        return tool
    raise FileNotFoundError(f"could not find checkout-local arm-none-eabi-nm: {tool}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-dir", type=Path, required=True)
    parser.add_argument("--elf", type=Path, required=True)
    args = parser.parse_args()

    map_path = args.elf.with_suffix(".map")
    if not map_path.is_file():
        raise FileNotFoundError(f"linker map is missing: {map_path}")
    firmware_root = args.build_dir.resolve().parents[2]
    nm = find_nm(firmware_root)
    output = subprocess.run(
        [str(nm), "-S", "-n", "-C", str(args.elf)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    budget = calculate_budget(parse_symbols(output))
    validate_budget(budget)

    print(f"Linker map: {map_path}")
    print(f"Capture archive: {budget.archive.size} B at 0x{budget.archive.address:08x}")
    print(f"USB DMA slots: {budget.usb_dma_bytes} B in RAM2")
    other_dma_bytes = budget.ram2_used - budget.archive.size - budget.usb_dma_bytes
    print(f"Other DMA data and linker alignment: {other_dma_bytes} B")
    print(f"RAM2 static allocation: {budget.ram2_used} B of 524288 B")
    print(f"RAM2 heap headroom: {budget.ram2_headroom} B")
    print(f"RAM2 headroom after {MIN_RAM2_HEAP_RESERVE} B heap reserve: "
          f"{budget.ram2_headroom - MIN_RAM2_HEAP_RESERVE} B")
    print(f"RAM1 fixed event ring: {budget.event_queue_bytes} B")
    print(f"RAM1 stack headroom: {budget.ram1_headroom} B")
    print(f"RAM1 headroom after {MIN_RAM1_STACK_RESERVE} B stack reserve: "
          f"{budget.ram1_headroom - MIN_RAM1_STACK_RESERVE} B")
    print("Memory budget passed; runtime peak stack and heap use are not measured by the map.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
