#include "slt/serial_number.h"

extern "C" void slt_format_usb_serial_number(uint32_t mac0, uint32_t mac1,
                                              uint16_t serial[16]) {
  constexpr char kHexDigits[] = "0123456789ABCDEF";
  const uint32_t words[] = {mac0, mac1};
  for (uint8_t index = 0; index < 16; ++index) {
    const uint32_t word = words[index / 8];
    const uint8_t shift = static_cast<uint8_t>((7 - (index % 8)) * 4);
    serial[index] = static_cast<uint16_t>(kHexDigits[(word >> shift) & 0x0Fu]);
  }
}
