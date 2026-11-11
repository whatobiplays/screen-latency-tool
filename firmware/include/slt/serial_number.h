#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Encodes the two hardware MAC words as sixteen uppercase UTF-16 hex digits.
void slt_format_usb_serial_number(uint32_t mac0, uint32_t mac1,
                                  uint16_t serial[16]);

#ifdef __cplusplus
}
#endif
