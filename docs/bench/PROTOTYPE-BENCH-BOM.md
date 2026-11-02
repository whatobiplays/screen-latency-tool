# Prototype bench procurement and budget

**Document ID:** BENCH-001\
**Status:** Draft\
**Owner:** Repository maintainers\
**Last Updated:** 2026-11-01 (America/Los_Angeles)\
**Depends On:** ARCH-001, REF-MET-001, REF-WIRE-001, SPEC-X-001, PHASE-001, ADR-0002\
**Supersedes:** None\
**Superseded By:** None

Procurement evidence is consolidated in [BENCH-003: Prototype procurement readiness](PROTOTYPE-PROCUREMENT-READINESS.md).

Readiness note: procurement approval remains blocked. This file records dated component listings for one prototype; it is not a complete checkout quote or purchase authorization. No purchase, fabrication, assembly, electrical test, optical test, USB test, or timing qualification is reported here.

Observation dates: most sourced equipment and component rows were first captured on 2026-10-31. The 30 UNVERIFIED_BEFORE_ORDER manifest rows, representing 23 unique MPNs, were checked against their current supplier listings on 2026-11-01. Those observations establish supplier catalog status and record the displayed supply. They do not establish manufacturer active-production status for every exact MPN. Linked manufacturer pages remain product references unless the page and the cited record explicitly establish a lifecycle state. The generated rows therefore remain UNVERIFIED_BEFORE_ORDER. The Phihong adapter and Beagle USB 12 were not part of that refresh and keep their 2026-10-31 observations. Prices are USD and exclude shipping, tax, and import charges. Unless a row gives a dated count or lead time, stock and delivery are unknown. Supplier quantities in this document are dated rendered-page snapshots captured through the source-review tool; anti-bot controls blocked direct browser access to the cited Newark and DigiKey pages. These snapshots are not reservations or delivery promises.

The instance-level list is hardware/prototype/BOM.csv. Its rows record function/value, reference, MPN, quantity, unit and extended price, source URL, and the 2026-10-31 observation note. Stock is not a manifest field; uncaptured counts mean unknown. The exact MPN is the default for every row. No substitutions are approved unless an existing sourcing row names an alternative and explains why. Shipping, tax, duties, and multi-vendor checkout costs remain unknown. This document records the later lifecycle check; it does not silently change the labels stored in BOM.csv.

Gate A supports provisional values by model only: 46.999988 kOhm nominal transimpedance, 1.905 MHz bandwidth, 14.335% step overshoot, 0.801 us 2% settling, 1.399 us recovery after a 40 uA overload, and 5.829 mV RMS output noise through 2 MHz. The 424 MOhm photodiode resistance is an assumption, 20-25 pF input capacitance is an unmeasured planning envelope, real display photocurrent is unknown, and comparator discrimination remains unaccepted.

## Dated purchasable-item observations

| Stage | Item / MPN or product | Qty | Package or interface | Function | Source and date | Unit / extended USD (qty 1) | Dated stock observation | Lead time / unknown | Confidence and constraints | Compatible alternative |
| --- | --- | ---: | --- | --- | --- | ---: | --- | --- | --- | --- |
| Immediate | SparkFun DEV-20360, Teensy 4.1 without Ethernet, headers installed | 1 | Teensy board with installed 2.54 mm headers and Micro-B USB device socket | Prototype controller | [SparkFun](https://www.sparkfun.com/teensy-4-1-without-ethernet-headers.html), 2026-11-01 | 33.60 | Displayed in stock, 2026-11-01 | Unknown; no delivery estimate captured | Vendor snapshot. VIN/VUSB must be inspected and separated before external 5 V and USB are used together. | No alternate selected; installed headers are part of the prototype interface. |
| Immediate | Adafruit product 239, full-size premium breadboard | 1 | Solderless breadboard, 830 tie points, 2.54 mm pitch | Low-speed digital, control, LED, and supply experiments only | [Adafruit](https://www.adafruit.com/product/239), 2026-11-01 | 5.95 | Displayed in stock, 2026-11-01 | Unknown; no delivery estimate captured | Vendor snapshot. Prohibited for USB D+/D- and the photodiode-to-TIA high-impedance junction. | No alternate selected; low-speed use only. |
| Immediate | Mean Well GST18U05-P1J, DigiKey 1866-2073-ND | 1 | External 5 V DC adapter, 3 A / 15 W, ±5% total voltage tolerance, 80 mV p-p ripple; fixed US NEMA 1-15P input; 5.5 x 2.1 mm center-positive barrel; approximately 1.2 m cable; Class II isolation | Regulated external 5 V source only | [DigiKey](https://www.digikey.com/en/products/detail/mean-well-usa-inc/GST18U05-P1J/7703631) and [Mean Well datasheet](https://www.meanwellusa.com/upload/pdf/GST18U/GST18U-SPEC.pdf), 2026-11-01 | 14.10 | 141 displayed, 2026-11-01 | 17 weeks standard manufacturer lead time observed 2026-11-01 | Vendor and manufacturer observations. The source has short-circuit, overload, and overvoltage protection. **It provides no Teensy VIN/VUSB isolation, reverse-current blocking, or bench reverse-polarity protection.** | Use either this primary row or the separate Phihong alternate row; do not add both supplies. |
| Immediate alternate | Phihong PSC15A-050-R, DigiKey 993-PSC15A-050-R-ND | 1 | External 5 V DC adapter, 5.0 V DC, 3 A / 15 W, ±5% regulation, 100 mV p-p ripple; fixed US plug; 5.5 x 2.1 mm center-positive barrel; double-insulated | Alternate regulated external 5 V source only; not included in the primary basket | [DigiKey](https://www.digikey.com/en/products/detail/phihong-usa/PSC15A-050-R/5247140) and [Phihong datasheet](https://phihong.com/wp-content/uploads/PSX15A.pdf), 2026-10-31 | 10.47 | 1,469 displayed, 2026-10-31 | 26 weeks standard manufacturer lead time observed 2026-10-31 | Vendor and manufacturer observations. The source has short-circuit, overcurrent, and overvoltage protection. **It does not supply the required VIN/VUSB isolation, reverse-current blocking, or bench reverse-polarity protection.** | Use this alternate or the separate Mean Well primary; do not add both supplies. |
| Diagnostic only, not a qualified T0 sync solution | Total Phase TP320221, Beagle USB 12 | 1 | USB 2.0 Full/Low-Speed protocol analyzer; **no external trigger/digital I/O** on USB 12; pass-through setup still needs verification | Separately priced protocol-analyzer option for nonintrusive traffic capture | [Total Phase](https://www.totalphase.com/products/beagle-usb12/), 2026-10-31; not rechecked 2026-11-01 | 495.00 | Displayed in stock, 2026-10-31; not rechecked 2026-11-01 | Unknown; no delivery estimate captured | Protocol-only diagnostic candidate: vendor confirms Full/Low-Speed monitoring, **no external I/O or trigger**, and therefore no independently demonstrated timestamp correlation to fixture ticks. | Only pursue an analyzer with a documented external timing input and a verified shared clock or trigger. No synchronized model or quote is established. |
| Deferred T0 | RIGOL DHO814 | 1 | Benchtop oscilloscope, four analog channels, four included probes | Four-channel waveform inspection | [RIGOL](https://www.rigolna.com/products/digital-oscilloscopes/dho800/dho812/), 2026-11-01 | 549.00 | Displayed in stock, 2026-11-01 | Unknown; no delivery estimate captured | Vendor snapshot. Native USB protocol decoding is not claimed. | No alternate selected; defer scope choice until the T0 measurement need is established. |

The Beagle USB 12 is **protocol diagnostic only**, excluded from the baseline. Total Phase explicitly says it **has no digital I/O port / external trigger**, so its 21 ns packet timestamps cannot independently establish the fixture-to-ACK time offset. The [manufacturer manual](https://www.totalphase.com/support/articles/200472426-beagle-protocol-analyzer-user-manual/) and [technical support response](https://www.totalphase.com/blog/2015/03/beagle-usb-protocol-analyzers-provide-external-trigger-test-usb-response/) establish this limitation. The old PhyWhisperer storefront URL failed link validation; do not treat it as a source.

### Price, stock, lead, and substitution handling

Rows in the two sourcing tables are quantity one, so unit and extended price are equal. BOM.csv carries both values for repeated MPNs. Stock is unknown unless a dated count is shown. A supplier catalog marked Active does not establish manufacturer active-production status and is not a stock count. Mean Well and Phihong lead times apply to replenishment, not displayed on-hand stock. For every vendor, shipping, tax, and duties remain unknown.

### Manifest lifecycle check, 2026-11-01

The 30 manifest rows marked UNVERIFIED_BEFORE_ORDER contain 23 unique MPNs. Each MPN had a supplier Active observation on 2026-11-01. DigiKey was the cited supplier for the distributor rows; SparkFun or Adafruit was the cited supplier for its external-kit products. That supplier status records only the catalog state on that date. It does not show that the manufacturer classifies the exact orderable part as active production, promise future availability, or reserve stock. Where a manufacturer page is linked, this review did not treat a product page alone as lifecycle evidence unless it explicitly recorded the relevant production state. None of these 30 rows is promoted from UNVERIFIED_BEFORE_ORDER. Counts and lead times were observed on 2026-11-01.

| MPN | Manifest rows | Function | 2026-11-01 unit / extended USD | 2026-11-01 supplier status and observed supply | Source |
| --- | --- | --- | ---: | --- | --- |
| RC0603FR-0718KL | R1, optical board | 3V3-to-VREF divider upper resistor | 0.11 / 0.11 | DigiKey Active; 321,535 shown | [Yageo](https://yageogroup.com/products/Resistors/part/RC0603FR-0718KL) and [DigiKey](https://www.digikey.com/en/products/detail/yageo/RC0603FR-0718KL/726989), 2026-11-01 |
| ERJ-3EKF1002V | R2, optical board | VREF-to-GND divider lower resistor | 0.10 / 0.10 | DigiKey Active; 744,247 shown; 15-week lead | [DigiKey](https://www.digikey.com/en/products/detail/panasonic-industry/ERJ-3EKF1002V/198102), 2026-11-01 |
| RC0603FR-0747KL | R3, optical board; R3, power board | TIA feedback resistor (R3, optical) and VBUS input pull-down (R3, power) | 0.10 / 0.20 | DigiKey Active; 519,895 shown; 24-week lead | [Yageo](https://yageogroup.com/products/Resistors/part/RC0603FR-0747KL) and [DigiKey](https://www.digikey.com/en/products/detail/yageo/RC0603FR-0747KL/727253), 2026-11-01 |
| RC0603FR-072M2L | R4, optical board | Comparator output-to-threshold hysteresis feedback resistor; DNP by default | 0.11 / 0.00 planned | DigiKey Active; 179,716 shown; 24-week lead | [Yageo](https://yageogroup.com/products/Resistors/part/RC0603FR-072M2L) and [DigiKey](https://www.digikey.com/en/products/detail/yageo/RC0603FR-072M2L/5282195), 2026-11-01 |
| RC0603FR-07100RL | R5, optical board | Comparator output series resistor from CMP_OUT to EDGE | 0.10 / 0.10 historical | Newark dated rendered-page snapshot: 318,908 on-hand and 85,000 incoming; DigiKey: 0 displayed and 5,000 expected 2026-11-09; Yageo's 51.29M+ aggregate is not stock | [Newark](https://www.newark.com/yageo/rc0603fr-07100rl/res-100r-1-0-1w-0603-thick-film/dp/98K7334), [Yageo](https://yageogroup.com/products/Resistors/part/RC0603FR-07100RL), and [DigiKey](https://www.digikey.com/en/products/detail/yageo/RC0603FR-07100RL/726888), 2026-11-01 |
| RC0603FR-071KL | R6, optical board; R1 and R2, power board | TIA_OUT-to-MON monitor series resistor (R6, optical); VBUS series and LED series resistors (R1 and R2, power) | 0.11 / 0.33 | DigiKey Active; 1,967,446 shown; 24-week lead | [Yageo](https://yageogroup.com/products/Resistors/part/RC0603FR-071KL) and [DigiKey](https://www.digikey.com/en/products/detail/yageo/RC0603FR-071KL/726843), 2026-11-01 |
| RC0603FR-071ML | R7, optical board | Threshold-to-VREF fallback bias resistor | 0.10 / 0.10 | DigiKey Active; 1,864,870 shown; 24-week lead | [Yageo](https://yageogroup.com/products/Resistors/part/RC0603FR-071ML) and [DigiKey](https://www.digikey.com/en/products/detail/yageo/RC0603FR-071ML/729791), 2026-11-01 |
| CL10A105KP8NNNC | C1 and C4, optical board | 1 uF VREF decoupling (C1) and 3V3-to-GND OPA320 decoupling (C4) | 0.11 / 0.22 | DigiKey Active; 269,185 shown; 61-week lead | [DigiKey](https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A105KP8NNNC/3886840), 2026-11-01 |
| CC0603CRNPO9BN2R2 | C2, optical board | 2.2 pF C0G TIA feedback capacitor from SUM to TIA_OUT | 0.10 / 0.10 | DigiKey Active; 69,116 shown; 16-week lead | [Yageo](https://yageogroup.com/products/Capacitors/part/CC0603CRNPO9BN2R2) and [DigiKey](https://www.digikey.com/en/products/detail/yageo/CC0603CRNPO9BN2R2/5195641), 2026-11-01 |
| KGM15BR71H104KT | C3 and C5, optical board; C1, power board | 100 nF 3V3-to-GND decoupling for U1/U2 and the USB buffer | 0.10 / 0.30 | DigiKey Active; displayed 0; 15-week lead. Procurement gap; no substitute selected. | [DigiKey](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046), 2026-11-01 |
| CL10A225KP8NNNC | C6, optical board | 2.2 uF comparator 3V3-to-GND decoupling | 0.10 / 0.10 | DigiKey Active; 147,544 shown; 61-week lead | [DigiKey](https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A225KP8NNNC/3886743), 2026-11-01 |
| 3296W-1-103LF | RV1, optical board | Ten-turn threshold trimmer from 3V3 to GND, wiper on THRESH | 2.39 / 2.39 | DigiKey Active; 30,067 shown; 7-week lead | [DigiKey](https://www.digikey.com/en/products/detail/bourns-inc/3296W-1-103LF/1088045), 2026-11-01 |
| TSW-104-07-G-S | J1, optical board | 3V3, GND, EDGE, and MON power/signal header | 0.38 / 0.38 | DigiKey Active; 3,224 shown; 2-week lead | [Samtec](https://www.samtec.com/products/tsw-104-07-g-s) and [DigiKey](https://www.digikey.com/en/products/detail/samtec-inc/TSW-104-07-G-S/1101323), 2026-11-01 |
| Adafruit 368 | J1, power board | 5.5 x 2.1 mm barrel-to-screw-terminal adapter for 5V_IN/GND | 2.00 / 2.00 | Supplier Active; displayed in stock | [Adafruit](https://www.adafruit.com/product/368), 2026-11-01 |
| MF-R050 | F1, power board | Resettable PTC from 5V_IN to 5V_LIMITED, 500 mA hold | 0.47 / 0.47 | DigiKey Active; 10,109 shown; 4-week lead | [DigiKey](https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965), 2026-11-01 |
| TSW-102-07-G-S | J2 and J5, power board | Protected 5V_SAFE/GND header (J2) and LED_GPIO/GND header (J5) | 0.21 / 0.42 | DigiKey Active; 73,452 shown; 2-week lead | [DigiKey](https://www.digikey.com/en/products/detail/samtec-inc/TSW-102-07-G-S/1101325), 2026-11-01 |
| TSW-103-07-G-S | J4, power board | 3V3, GND, and VBUS_PRESENT logic header | 0.30 / 0.30 | DigiKey Active; 73,433 shown; 2-week lead | [DigiKey](https://www.digikey.com/en/products/detail/samtec-inc/TSW-103-07-G-S/1101324), 2026-11-01 |
| WP7113ID | D1, power board | Red slow-status LED from LED_ANODE to GND | 0.21 / 0.21 | DigiKey Active; 102,675 shown; 6-week lead | [Kingbright](https://www.kingbrightusa.com/product.asp?catalog_name=LED&Product_ID=WP7113ID) and [DigiKey](https://www.digikey.com/en/products/detail/kingbright/WP7113ID/1747663), 2026-11-01 |
| DEV-20360 | EXT1 | Teensy 4.1 with headers | 33.60 / 33.60 | Supplier Active; displayed in stock | [SparkFun](https://www.sparkfun.com/teensy-4-1-without-ethernet-headers.html), 2026-11-01 |
| GST18U05-P1J | EXT2 | Regulated 5 V, 15 W adapter | 14.10 / 14.10 | DigiKey Active; 141 shown; 17-week lead | [DigiKey](https://www.digikey.com/en/products/detail/mean-well-usa-inc/GST18U05-P1J/7703631) and [Mean Well datasheet](https://www.meanwellusa.com/upload/pdf/GST18U/GST18U-SPEC.pdf), 2026-11-01 |
| Adafruit 239 | EXT3 | Digital breadboard | 5.95 / 5.95 | Supplier Active; displayed in stock | [Adafruit](https://www.adafruit.com/product/239), 2026-11-01 |
| Adafruit 5030 | EXT4 | USB-C host to USB-A socket adapter | 0.95 / 0.95 | Supplier Active; displayed in stock | [Adafruit](https://www.adafruit.com/product/5030), 2026-11-01 |
| Adafruit 592 | EXT5 | USB-A to Micro-B data cable | 2.95 / 2.95 | Supplier Active; displayed in stock | [Adafruit](https://www.adafruit.com/product/592), 2026-11-01 |

The entries above record dated supplier Active status against the linked supplier. Rows sourced through DigiKey say so; the SparkFun and Adafruit products use the supplier named in their source cell. Where a manufacturer link is present, the linked page supplies product or package information; the table does not infer active-production status from that link alone. None of these rows changes the generated UNVERIFIED_BEFORE_ORDER label.

### Sourcing gaps

| MPN and manifest use | Dated evidence | Required compatibility review | Disposition |
| --- | --- | --- | --- |
| RC0603FR-07100RL, R5 optical board: CMP_OUT-to-EDGE series resistor | [Newark exact-MPN page](https://www.newark.com/yageo/rc0603fr-07100rl/res-100r-1-0-1w-0603-thick-film/dp/98K7334), SKU `98K7334`, dated 2026-11-01 rendered-page snapshot: 318,908 on-hand and 85,000 incoming; cut-tape MOQ 10; $0.015 per unit, or $0.15 minimum for one line; 26-week manufacturer replenishment lead; 4-6 business-day delivery for the displayed UK stock. Anti-bot controls blocked direct browser access, so this snapshot came through the source-review tool and is not a checkout reservation. [DigiKey exact listing](https://www.digikey.com/en/products/detail/yageo/RC0603FR-07100RL/726888) separately displayed 0, with 5,000 expected incoming on 2026-11-09 and a 24-week new-replenishment lead. The [Yageo product page](https://yageogroup.com/products/Resistors/part/RC0603FR-07100RL) reported 51.29M+ across distributors; that aggregate is not a stock count or order quantity. | A candidate must match the exact 0603 land pattern, resistance, tolerance, power and voltage ratings, and temperature behavior. Review must also cover comparator output loading and the EDGE transition. | Supplier-available exact-MPN path in the dated Newark snapshot, subject to owner revalidation. No substitute is selected. Manufacturer lifecycle and electrical approval remain unresolved. |
| KGM15BR71H104KT, C3 and C5 on the optical board and C1 in the offboard/non-PCB group: 100 nF 3V3-to-GND decoupling | [DigiKey exact listing](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046), dated 2026-11-01 rendered-page snapshot: displayed 0; 4,000 expected 2027-05-17; 15-week lead. The separately priced `KGM15BR71H104KM` packaging variant showed 10,399 in stock. Neither observation is a reservation. | A candidate must match the exact 0603 land pattern where applicable, capacitance, tolerance, voltage rating, dielectric, temperature and DC-bias behavior, and the bypass requirements at each IC. C1 has no PCB footprint. | The procurement gap remains. No substitute is selected or approved. Manufacturer lifecycle and circuit-specific capacitor performance remain open. |

No alternative has been approved. The assessment below found no explicit manufacturer active-production declaration for either original MPN or for any alternative. That evidence gap prevents approval under the repository lifecycle policy. The generator and `BOM.csv` still select the original MPNs, and this assessment does not constitute substitution approval or a purchase recommendation.

### Procurement candidate assessment, 2026-11-01

The observations below were collected on 2026-11-01, with the primary review beginning at 12:09 UTC. Supplier counts and arrival estimates are dated rendered-page snapshots, not inventory audits, reservations, or delivery promises. Direct probes returned HTTP 200 for the Yageo and Kyocera manufacturer sources and HTTP 403 for the DigiKey pages. DigiKey observations came from the rendered listings and are not inferred from the failed direct probes. The Newark page blocked direct browser access; its observation comes from the rendered-page snapshot supplied for this review. Mouser direct access also failed, so this assessment does not infer availability from Mouser.

#### R5: 100 Ω from CMP_OUT to EDGE

R5 is one optical-board resistor. The generator maps it from `CMP_OUT` to `EDGE`, and its KiCad footprint uses 0.80 × 0.95 mm pads on 1.65 mm centers.

| Candidate | Manufacturer lifecycle | Verified specifications | Footprint fit | Supplier availability | Price | Engineering disposition |
| --- | --- | --- | --- | --- | --- | --- |
| `RC0603FR-07100RL` (original selection) | **Unresolved.** The [Yageo specsheet](https://yageogroup.com/component-documentation/download/specsheet/RC0603FR-07100RL?lang=en) and [Yageo RC datasheet, revision 14](https://yageogroup.com/component-documentation/download/datasheet/RC0603FR-07100RL?lang=en) identify the part but do not state active, NRND, or EOL status. The datasheet does not promise continuing production. DigiKey and Newark catalog labels are supplier metadata. | 100 Ω, ±1%, 0.1 W at 70 °C, 75 V maximum continuous working voltage, 150 V maximum over voltage, 150 V dielectric withstanding voltage, ±100 ppm/°C, −55 to +155 °C, 0603/1608 body nominally 1.6 × 0.8 × 0.45 mm. | The nominal body and terminals fit a 0603 land pattern. Final paste, placement, and reflow fit still require review of the exact manufacturer package drawing at order time. | [Newark exact page](https://www.newark.com/yageo/rc0603fr-07100rl/res-100r-1-0-1w-0603-thick-film/dp/98K7334), SKU `98K7334`, dated 2026-11-01 rendered-page snapshot: 318,908 on-hand; 85,000 incoming; CT MOQ 10; 26-week manufacturer replenishment lead; 4-6 business-day delivery for displayed UK stock. [DigiKey exact listing](https://www.digikey.com/en/products/detail/yageo/RC0603FR-07100RL/726888): `311-100HRCT-ND` and `311-100HRTR-ND`; displayed 0; 5,000 expected incoming 2026-11-09; 24-week new-replenishment lead; CT MOQ 1; standard reel package 5,000. Both supplier pages required the source-review snapshot because anti-bot controls blocked direct browser access. All counts are dated snapshots, not reservations. | Newark snapshot: $0.015 each with a ten-piece cut-tape minimum, so $0.15 for the first line. The historical input is $0.10. | Supplier-available exact-MPN path through Newark. It remains subject to manufacturer lifecycle, electrical, and owner gates and is not approval-ready. |
| `RC0603FR-10100RL` | **Unresolved.** No manufacturer page or lifecycle declaration for this exact orderable variant was established. | Same RC electrical family as the original; the `10` ordering code specifies 10-inch paper packaging instead of the original 7-inch packaging, based on the [Yageo RC datasheet](https://yageogroup.com/component-documentation/download/datasheet/RC0603FR-07100RL?lang=en). | Same 0603 body family and nominal package fit. Exact fabrication tolerance and reflow fit remain subject to the separate electrical and lifecycle review. | [DigiKey Marketplace exact-MPN page](https://www.digikey.com/en/products/detail/yageo/RC0603FR-10100RL/12756390), dated 2026-11-01 rendered-page snapshot: the page's primary In-Stock field shows 119,636 on-hand; a separate Other Suppliers on DigiKey WEC entry shows 143,970 on-hand. No incoming quantity was displayed. CT MOQ 1; TR multiple 10,000. The page is branded DigiKey Marketplace, so the primary field is not labeled as direct DigiKey stock. Anti-bot controls blocked direct browser access. | $0.11 each; +$0.01 per unit versus the $0.10 historical input. | Conditional fallback for separate review only. The primary field and separate WEC quantity are distinct dated observations and neither establishes manufacturer lifecycle or circuit compatibility. No substitution approval. |
| `RC0603FR-13100RL` | **Unresolved.** No manufacturer page or lifecycle declaration for this exact orderable variant was established. | Same RC electrical family as the original; the `13` ordering code specifies 13-inch paper packaging, based on the [Yageo RC datasheet](https://yageogroup.com/component-documentation/download/datasheet/RC0603FR-07100RL?lang=en). | Same 0603 body family and nominal package fit. Exact fabrication tolerance and reflow fit remain subject to the separate electrical and lifecycle review. | [DigiKey Marketplace exact-MPN page](https://www.digikey.com/en/products/detail/yageo/RC0603FR-13100RL/12756392), dated 2026-11-01 rendered-page snapshot: the page's primary In-Stock field shows 181,803 on-hand; a separate WEC entry shows 280,000 on-hand. No incoming quantity was displayed. CT MOQ 1; TR multiple 20,000. The page is branded DigiKey Marketplace, so the primary field is not labeled as direct DigiKey stock. Anti-bot controls blocked direct browser access. | $0.11 each; +$0.01 per unit versus the $0.10 historical input. | Conditional fallback for separate review only. The primary field and separate WEC quantity are distinct dated observations and neither establishes manufacturer lifecycle or circuit compatibility. No substitution approval. |
| `ERJ-3EKF1000V` | **Not established.** No primary manufacturer datasheet or lifecycle review was completed. | DigiKey describes a 100 Ω, ±1%, 0.1 W, 0603 part. That distributor description is not a substitute for the manufacturer specification and package drawing. | Nominal 0603 only; exact terminal dimensions, land-pattern tolerance, and reflow fit were not verified. | [DigiKey listing](https://www.digikey.com/en/products/detail/panasonic-industry/ERJ-3EKF1000V/196074): 2,396,166 displayed and $0.10 each in the 2026-11-01 snapshot. | $0.10 each; no change from the $0.10 historical input. | Withdrawn from this shortlist because it does not meet the evidence bar. Displayed stock cannot override the lifecycle policy. |

The Yageo product page displayed `51,294.5K+` across supply on 2026-11-01. That aggregate links distributors with different packaging, minimum quantities, and regions. It is not a Yageo order quantity, confirmed physical inventory, or proof of active production. The exact DigiKey listing independently displayed zero, while the exact Newark snapshot showed 318,908 and DigiKey's alternate package variants showed additional dated stock.

For R5, the DigiKey 5,000-unit date for 2026-11-09 is incoming inventory, while the 24-week figure is a new-replenishment lead time. These are different supply signals, so the document preserves both without treating either as proof of on-hand stock or active production. The Newark snapshot separately shows 318,908 on-hand and 85,000 incoming.

A like-for-like 100 Ω, 1% part would preserve the nominal series value used to isolate `CMP_OUT` from `EDGE`. The 150 V overload rating does not establish pulse-energy capability. Comparator output loading, driven-edge rise and fall behavior, ringing, and EMC effects remain unverified. Any 100 Ω orderable variant still requires manufacturer lifecycle evidence and those signal-integrity checks before approval.

#### C3, C5, and C1: 100 nF from 3V3 to GND

The optical board has two PCB-mounted 0603 capacitors, C3 and C5. Each KiCad footprint uses 0.90 × 0.95 mm pads on 1.55 mm centers. C1 is one separate 100 nF offboard/non-PCB capacitor, so its assessment concerns physical handling and assembly rather than an existing PCB land pattern.

| Candidate | Manufacturer lifecycle | Verified specifications | Footprint fit | Supplier availability | Price | Engineering disposition |
| --- | --- | --- | --- | --- | --- | --- |
| `KGM15BR71H104KT` (original selection) | **Unresolved.** The [Kyocera AVX KGM X7R datasheet, revision 13](https://datasheets.kyocera-avx.com/KGM_X7R.pdf) supplies the family specification and ordering table, but it does not state active, NRND, or EOL status for this exact MPN. DigiKey's `Active` label is supplier metadata. | 100 nF, ±10%, 50 V, X7R, −55 to +125 °C; 0603/1608 body 1.60 ±0.15 × 0.81 ±0.15 mm, maximum 0.94 mm thickness for the `T` code, terminal width 0.35 ±0.15 mm. Capacitance varies with temperature and applied voltage; exact DC-bias, ESR, ESL, and impedance curves were not established. | C3 and C5 fit the nominal 0603 land pattern. C1 requires no PCB land pattern; its mounting, insulation, and strain relief remain assembly review items. | [DigiKey exact listing](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046), 2026-11-01 rendered-page snapshot: `478-KGM15BR71H104KTCT-ND`, `478-KGM15BR71H104KTTR-ND`, and `478-KGM15BR71H104KTDKR-ND`; displayed 0; 4,000 expected 2027-05-17; 15-week lead; CT MOQ 1; reel MOQ 4,000. Counts and dates are snapshots, not reservations. | $0.10 each; no change from the $0.10 historical input for C3, C5, or C1. | Preferred exact design target, but the exact `KT` path is not currently stocked in this snapshot. Manufacturer lifecycle and circuit performance remain open. Not approval-ready. |
| `KGM15BR71H104KM` | **Unresolved.** The manufacturer ordering table identifies this package code, but no exact-MPN lifecycle declaration was found. | Same KGM X7R family as the original: 100 nF, ±10%, 50 V, X7R, 0603/1608. The final `M` replaces the original `T` 7-inch paper packaging with 13-inch paper packaging, per the [manufacturer ordering table](https://datasheets.kyocera-avx.com/KGM_X7R.pdf). The electrical part and 0603 body are unchanged by this packaging code. | C3 and C5 have the same nominal 0603 body envelope and should fit the existing land pattern on dimensional grounds. Exact DC-bias, ESR, ESL, impedance, and assembly tolerance remain unverified. C1 remains offboard and needs physical handling verification rather than a PCB change. | [DigiKey alternate-package listing](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KM/1600047), 2026-11-01 rendered-page snapshot: `478-KGM15BR71H104KMCT-ND`; 10,399 displayed; 15-week lead; CT MOQ 1; reel MOQ 15,000. The count is a dated snapshot, not a reservation. | $0.10 each; no change from the $0.10 historical input for C3, C5, or C1. | Packaging option for separate review. Stock availability does not prove lifecycle status or identical high-frequency decoupling. No substitution approval. |

The original `KT` part and the `KM` packaging variant share the same manufacturer family, electrical specification, and nominal 0603 body. That does not establish identical high-frequency decoupling because exact DC-bias and impedance evidence is incomplete. C3 and C5 sit beside the OPA320 and TLV3501, where local supply behavior depends on bias and impedance over frequency. C1 has no PCB footprint, so its review covers handling, lead or terminal preparation, strain relief, and isolation, along with electrical comparison.

#### Decision status and owner gate

1. **Preferred engineering selection:** retain `RC0603FR-07100RL` and `KGM15BR71H104KT` in the design while the lifecycle and engineering gates are completed. The dated Newark snapshot makes the exact R5 MPN supplier-available. The capacitor's exact `KT` DigiKey path showed zero, while the `KM` packaging variant showed stock. Historical component price inputs remain unchanged.
2. **Fallbacks:** the Yageo `10` and `13` package variants and Kyocera `KM` packaging variant remain candidates for separate review. All three had dated stock snapshots, but none is approval-ready because manufacturer lifecycle and circuit-specific compatibility are incomplete. No alternative has verified manufacturer active-production status.
3. **Rejected or withdrawn:** no candidate was rejected for proven electrical or dimensional incompatibility. `ERJ-3EKF1000V` is withdrawn from this shortlist because its manufacturer specification, package drawing, and lifecycle evidence are incomplete. Displayed distributor stock alone does not qualify it.
4. **Missing evidence:** an explicit manufacturer active-production status for each exact orderable MPN; exact package drawings and land-pattern comparison for off-original candidates; R5 pulse-energy, comparator loading, edge, ringing, and EMC analysis; capacitor DC-bias, ESR, ESL, and impedance behavior at each placement; and revalidation of all dated supplier snapshots before purchase. These availability gaps remain narrower than the lifecycle and engineering gaps.
5. **Owner approval required before substitution:** the owner must explicitly authorize a separate substitution review and select an exact MPN after the missing lifecycle and compatibility evidence is supplied. That decision would then require a separate implementation change to `generate.mjs`, `BOM.csv`, and any affected metadata or assembly instructions. Selection does not authorize purchase, fabrication, energizing, or physical qualification.

#### Conditional cost deltas

| Conditional component set | Component arithmetic | Delta historical component inputs | $324.70 scenario becomes | $371.84 scenario becomes |
| --- | ---: | ---: | ---: | ---: |
| Original exact MPNs at historical $0.10 inputs | $0.10 + 3 × $0.10 = $0.40 | $0.00 | $324.70 | $371.84 |
| One exact R5 through the Newark MOQ-10 line plus three $0.10 capacitors | 10 × $0.015 = $0.15; $0.15 + 3 × $0.10 = $0.45 | +$0.05 | $324.75 | $371.89 |
| One $0.11 Yageo R5 package variant plus three $0.10 original capacitors | $0.11 + 3 × $0.10 = $0.41 | +$0.01 | $324.71 | $371.85 |
| One $0.10 historical R5 input plus three $0.10 `KM` capacitors | $0.10 + 3 × $0.10 = $0.40 | $0.00 | $324.70 | $371.84 |

These rows are conditional arithmetic. The historical $324.70 and $371.84 baselines remain unchanged. No component set is procurement-ready, and displayed stock does not change either historical baseline.

The Newark unit price is $0.015, which is $0.085 below the historical $0.10 allowance for one unit. Its ten-piece cut-tape minimum makes the actual component line $0.15 for one required R5, so the one-prototype basket delta is +$0.05.

> Documentation update: The README sourcing description was corrected on 2026-11-01. `KGM15BR71H104KT` remains the selected design MPN; the 2026-11-01 DigiKey snapshot showed zero on-hand stock for that exact MPN. `KGM15BR71H104KM` remains an unapproved packaging-code alternative. Manufacturer active-production verification and procurement approval remain outstanding.

## Additional priced prototype component candidates

These manufacturer/distributor pages were retrieved on **2026-10-31**; stock counts are snapshots, not confirmed cart reservations. Quantities below are for one prototype, not five final sensors. Prices are USD single-unit or one listed pack, exclusive of tax/shipping. A listed price is **not** proof that the assembled circuit is safe or that the component will be available at checkout.

| Stage / function | Exact candidate (qty 1) | Package / pinout dependency | Source and unit / extended USD | Observed availability and uncertainty | Decision / alternative |
| --- | --- | --- | ---: | --- | --- |
| Immediate detector | ams OSRAM **SFH 203**, `Q62702P0955`, 475-SFH203-ND (1) | Through-hole radial, 2.54 mm pitch; **pad 1 anode / pad 2 cathode**, 2.0 mm project-designed copper lands and 1.0 mm drills; 40° total acceptance cone from a 20° half angle | [DigiKey](https://www.digikey.com/en/products/detail/ams-osram-usa-inc/SFH-203/2205881) **$0.84** | **Full production** per [manufacturer](https://ams-osram.com/products/photodetectors/photodiodes/osram-radial-t1-34-sfh-203); 2,056 shown at observation | **Selected, not an alternate**; contact-boot and response require characterization; the dated discontinuation notice excludes the predecessor from this design even if residual stock remains |
| Immediate TIA IC | TI **OPA320AIDBVR**, 296-29480-1-ND (1) | SOT-23-5, TI DBV pinout | [DigiKey](https://www.digikey.com/en/products/detail/texas-instruments/OPA320AIDBVR/2747411) **$3.06** | DigiKey Active; 22,286 shown; manufacturer lead time 16 weeks applies to replenishment, not stock | Confirm TI schematic and soldered package; keep Rf/Cf provisional |
| Immediate comparator IC | TI **TLV3501AIDBVR**, 296-35977-1-ND (1) | SOT-23-6, push-pull output; supply from verified 3.3 V analog rail | [DigiKey](https://www.digikey.com/en/products/detail/texas-instruments/TLV3501AIDBVR/4135047) **$4.34** | DigiKey Active; 24,864 shown | No 5 V-powered output to Teensy GPIO; verify level and startup behavior |
| Small IC solder adapters | Adafruit **1230** SOT-23-5/-6 breakout assortment (1 pack) | Bare adapter PCB only; **no** ground plane/decoupling/optical layout guarantee | [Adafruit](https://www.adafruit.com/product/1230) **$4.95** | 2 packs shown; constrained | For pinout inspection and low-speed exploratory assemblies; **not automatically an accepted TIA/photodiode carrier** |
| Soldered low-speed carrier | Adafruit **589** quarter-size Perma-Proto (1 pack of 3) | Through-hole soldered power/logic board | [Adafruit](https://www.adafruit.com/product/589) **$8.50** | 77 packs shown | For protection/logic/LED, **not** the photodiode-to-TIA high-impedance input |
| DC connector | Adafruit **368** barrel female to two-screw terminal (1) | 5.5 x 2.1 mm, center-positive; manually verify polarity | [Adafruit](https://www.adafruit.com/product/368) **$2.00** | In stock shown | Mechanical mating candidate for selected adapter |
| Reverse-polarity / reverse-current guard | Pololu **5383** ideal-diode protector (1) | 4-60 V, 12 A rating, power direction **VIN -> VOUT**, common GND; owner-soldered terminals | [Pololu](https://www.pololu.com/product/5383) **$4.95** | Supplier Active; preferred with backorders allowed; **orderable quantity / delivery not verified** | Blocks reversed external input and reverse current toward adapter; **does not** isolate Teensy VUSB from VIN and **does not** limit forward fault current. #5382 is 10 A alternative but rationed |
| Coarse fault limiter | Bourns **MF-R050**, MF-R050-ND (1) | Radial PTC, 0.5 A hold, 1 A trip at rated conditions | [DigiKey](https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965) **$0.47** | DigiKey Active; 11,271 shown | **Not adjustable precision current limit**; delayed trip (listed 4 s) and voltage drop require validation; choose trip rating after measured startup/load |
| USB VBUS input buffer | TI **SN74LVC1G17DBVRG4**, SOT-23-5 (1) | 3.3 V supplied Schmitt buffer, input tolerates 5.5 V, power-off Ioff | [DigiKey](https://www.digikey.com/en/products/detail/texas-instruments/SN74LVC1G17DBVRG4/4833886) **$0.71** | DigiKey Active, 863 shown; plain DBVR listing has conflicting stock observations | [TI datasheet](https://www.ti.com/product/SN74LVC1G17) supports Ioff/overvoltage-tolerant input; requires **own** compact solder adapter/decoupling; no connection before verifying pinout and unpowered VBUS behavior |
| Android USB-C host adapter | Adafruit **5030** USB-C plug to USB-A socket (1) | Data-capable host/accessory connection; not a USB-PD power supply | [Adafruit](https://www.adafruit.com/product/5030) **$0.95** | In stock shown | Verify **specific Android DUT** enters USB host role and supplies VBUS; not guaranteed on every handheld |
| Teensy USB device cable | Adafruit **592** USB-A to Micro-B data cable (1) | USB 2.0 cable to Teensy **device** Micro-B port | [Adafruit](https://www.adafruit.com/product/592) **$2.95** | In stock shown | Keep intact; do not use Teensy 4.1 USB-host header |
| Slow LED driver candidate | onsemi **2N7000**, 2N7000FS-ND (1) | TO-92 N-channel; Vgs/Id/Rds(on) verified for **selected LED current and 3.3 V gate** | [DigiKey](https://www.digikey.com/en/products/detail/onsemi/2N7000/244278) **$0.65** | DigiKey Active; 91,965 shown | Optional experiment only. The generated power board has no 2N7000; it drives D1 from LED_GPIO through R2. High-speed optical response remains unmeasured. |

**Note on the buffer:** The SN74LVC1G17DBVRG4 was selected because TI documents 5.5 V-tolerant inputs **and Ioff partial-power-down protection**. VIN/VUSB separation is still required. The buffer must be 3.3 V-powered, locally bypassed, and its connection to the Teensy GPIO verified; it does not make the USB host power the instrument.

**Note on soldered optics:** Buying Adafruit 1230 does not provide an accepted photodiode/TIA/comparator schematic. An owner-soldered compact carrier with the input/feedback loop physically adjacent to the op amp, an analog return, and local decoupling is still needed. A small dedicated prototype PCB is allowed **before** production PCB orders. The production assembly remains deferred. See BENCH-002.

## Cost scenarios and purchasing gates

The amounts below are USD public-list price observations from 2026-10-31 and 2026-11-01. Each input table records its observation date. These figures are planning inputs, not quotes, reservations, stock commitments, or delivered prices. Shipping, file extras, tax, duties, assembly labor, and unresolved line items are excluded unless a formula names them. Current stock remains volatile.

The [owner bench inventory update](https://github.com/whatobiplays/screen-latency-tool/issues/3#issuecomment-6554881813), dated 2026-11-01, confirms a basic digital multimeter and basic soldering equipment. It does not identify their models or establish DMM accuracy, continuity or current ranges, soldering tips, SMT rework capability, accessories, magnification, ESD protection, suitable leads, or a current-limited bench supply. Those details remain unknown. The inventory report does not establish ownership of any other bench item.

Full-purchase comparisons remain below as hypothetical replacements. The owner-incremental scenarios treat the confirmed basic DMM and basic soldering-hardware category as zero new spend. They keep the separately listed solder, flux, hand tools, heat gun, bench supply, and all other items unless ownership is specifically established. The $65.98 soldering-hardware allowance in the hypothetical set is a comparison value for an iron and its power supply, not a valuation of the owner's unlisted equipment.

[BOM.csv](../../hardware/prototype/BOM.csv) records $79.24 for the 2026-10-31 mandatory snapshot. The scenarios use the 2026-11-01 refreshed mandatory input of $79.28. The 4-cent difference is the refreshed $0.11 price for RC0603FR-0718KL (one row) and RC0603FR-071KL (three rows).

### Component and equipment inputs

| Input | Observed amount and date | Scenario use | Source |
| --- | ---: | --- | --- |
| Refreshed mandatory components | $79.28, 2026-11-01 | All scenarios | [BOM.csv](../../hardware/prototype/BOM.csv) |
| Optional second component set | $14.10, 2026-10-31 | Six-board hypothetical rows only | Adafruit [1230](https://www.adafruit.com/product/1230) at $4.95, Adafruit [589](https://www.adafruit.com/product/589) at $8.50, and onsemi [2N7000](https://www.digikey.com/en/products/detail/onsemi/2N7000/244278) at $0.65 |
| Deferred DHO814 | $549.00, 2026-11-01 | Labelled DHO what-if rows only | [RIGOL DHO814 listing](https://www.rigolna.com/products/digital-oscilloscopes/dho800/dho812/) |

The optional subtotal is $4.95 + $8.50 + $0.65 = $14.10. Together, the refreshed mandatory and optional component inputs are $79.28 + $14.10 = $93.38; only the high scenario adds both.

### PCB fabrication and assembly options

| Option | 2026-11-01 known amount | What remains outside the amount | Source |
| --- | ---: | --- | --- |
| OSH Park, one 2-layer set of three boards | 50 x 44 mm is 3.41 in2. At $5/in2, one set is $17.05. Two sets supply six boards for $34.10. | Shipping and options; board design and assembly are separate. | [OSH Park two-layer pricing](https://docs.oshpark.com/services/two-layer/) |
| JLCPCB, five 2-layer boards below 100 x 100 mm | $2 base set | Shipping, file charges, and any other checkout charge. This base set is not a quote. | [JLCPCB ordering information](https://jlcpcb.com/help/article/how-do-i-place-an-order) |
| JLCPCB Economic PCBA, quantity 3 or 5 | Fixed floor $13.29 = $8.18 setup + $1.53 stencil + $3.58 hand-soldering labor. Add $3.07 x unique extended-part count, component prices, and per-joint charges. | Components, joints, unique-part count, assembly variables, transport, and checkout charges. Total NOT_ESTIMABLE. | [JLCPCB assembly pricing](https://jlcpcb.com/help/article/pcb-assembly-price) and [prototype cost guidance](https://jlcpcb.com/blog/reduce-pcb-assembly-cost-prototype) |
| JLCPCB Standard PCBA, one board | Fixed floor $37.35 = $25.56 setup + $8.21 stencil + $3.58 hand-soldering labor | All variable charges and the complete delivered order. Total NOT_ESTIMABLE. | [JLCPCB assembly pricing](https://jlcpcb.com/help/article/pcb-assembly-price) |

The current planning basis is one OSH Park set: one populated optical board and two bare spare boards. The owner-incremental high example adds a second PCB set and a second component set to price a six-board hypothetical. It does not change the current one-board plan or authorize fabrication. The JLCPCB board base remains separate because its typical checkout and delivery charges are not established. No JLCPCB PCBA charge enters a scenario total until a design-specific quote supplies the missing variables.

### Short harness and sleeve

| Item | 2026-11-01 listing | Scenario use | Source |
| --- | ---: | --- | --- |
| Adafruit jumpers | $3.95 | One purchase line in the short-harness example | [Adafruit 1949](https://www.adafruit.com/product/1949) |
| Sullins 4-position female header | $0.43 | One purchase line in the short-harness example | [DigiKey PPPC041LFBN-RC](https://www.digikey.com/en/products/detail/sullins-connector-solutions/PPPC041LFBN-RC/810176) |
| Alpha Wire black heatshrink, 4 ft | $4.49 | One pack in the short-harness example | [DigiKey F221B1-4-BK100](https://www.digikey.com/en/products/detail/alpha-wire/F221B1-4-BK100/281675) |
| Short-harness subtotal | $8.87 | $3.95 + $0.43 + $4.49 | The three linked listings above |
| 3M opaque heatshrink sleeve | $2.42 per foot | One planning foot in each scenario | [DigiKey SFTW203-3-8-BL-SPL](https://www.digikey.com/en/products/detail/3m/SFTW203-3-8-BL-SPL/269592) |

The $8.87 harness example buys the full four-foot Alpha Wire pack. A custom bracket or optical boot remains NOT_ESTIMABLE until a STEP-based quote exists.

### Hypothetical replacement tool set

| Tool | 2026-11-01 listing | Source |
| --- | ---: | --- |
| Pinecil soldering iron | $25.99 | [Pine64 Pinecil](https://pine64.com/product/pinecil-smart-mini-portable-soldering-iron/) |
| PinePower 165 W supply | $39.99 | [Pine64 PinePower v2](https://pine64.com/product/pinepower-v2-165w-desktop-power-supply-us-version/) |
| Solder | $9.25 | [Adafruit 1886](https://www.adafruit.com/product/1886) |
| Flux | $7.95 | [Adafruit 3468](https://www.adafruit.com/product/3468) |
| Flush cutters | $7.25 | [Adafruit 152](https://www.adafruit.com/product/152) |
| Tweezers | $3.95 | [Adafruit 422](https://www.adafruit.com/product/422) |
| Wire strippers | $11.95 | [Adafruit 4747](https://www.adafruit.com/product/4747) |
| Heat gun | $42.49 | [DigiKey EC-MINI](https://www.digikey.com/en/products/detail/master-appliance-co/EC-MINI/13629006) |
| Listed tool set subtotal | $148.82 | $25.99 + $39.99 + $9.25 + $7.95 + $7.25 + $3.95 + $11.95 + $42.49 |

The scenarios separate this hypothetical set into a $65.98 replacement allowance for the confirmed basic soldering-hardware category and $82.84 for the listed items whose ownership is not established. The $65.98 is Pinecil $25.99 + PinePower $39.99. The $82.84 is solder $9.25 + flux $7.95 + flush cutters $7.25 + tweezers $3.95 + wire strippers $11.95 + heat gun $42.49. These line prices describe the hypothetical set, not the owner's actual tools.

### Measurement equipment and first-power input

| Item | 2026-11-01 listing | Source |
| --- | ---: | --- |
| Digital multimeter | $17.50 | [Adafruit 2034](https://www.adafruit.com/product/2034) |
| Owner-selected Korad KA3005PE+ bench supply | $134.24 | [DigiKey KA3005PE](https://www.digikey.com/en/products/detail/sra-soldering-products/KA3005PE/21293129) |
| Baseline meter and supply subtotal | $151.74 | $17.50 + $134.24 |
| Optional probe | $15.99 | [DigiKey UT-P04](https://www.digikey.com/en/products/detail/uni-trend-technology-us-inc/UT-P04/22256953) |
| Deferred DHO814 | $549.00 | [RIGOL DHO814 listing](https://www.rigolna.com/products/digital-oscilloscopes/dho800/dho812/) |
| Baseline switch-mode supply, optional probe, and DHO814 | $716.73 | $151.74 + $15.99 + $549.00 |

Owner selection dated 2026-11-02 UTC: Korad KA3005PE+ is `OWNER_SELECTED_MODEL / NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED`. DigiKey's $134.24 price is a dated planning observation, not live stock, a delivered quote, or purchase authorization. Before any supply purchase or current-limit setting, review the initial load budget and document a defensible current-limit method from the expected load and component limits. Then confirm the exact revision, mains setup, protective earth, output isolation, certification, leads, and measured behavior before first-power qualification. Startup and load behavior remain Stage-B measurements. The KA3005D and Siglent SPD3303X-E remain historical alternatives in [BENCH-003](PROTOTYPE-PROCUREMENT-READINESS.md); no alternative is selected. The DHO814 remains deferred.

The owner's confirmed DMM removes the hypothetical $17.50 meter from owner-incremental totals. The adjustable current-limited supply remains a prerequisite for first power and is retained in owner-incremental totals; buying it does not qualify its setpoints, protection, wiring, or the protected load. The DHO814 has four analog channels and includes four probes. The extra probe remains optional and the oscilloscope remains deferred. No listing resolves synchronized USB capture.

### Known-cost scenarios

| Scenario | Included purchases | Arithmetic | Known subtotal |
| --- | --- | --- | ---: |
| Low parts basket, not first-power ready | Mandatory manifest purchases, one OSH Park set, complete harness example, and one-foot sleeve. No soldering accessories or measurement equipment are included. | $79.28 + $17.05 + $8.87 + $2.42 = $107.62 | $107.62 |
| Low parts basket plus deferred DHO814 what-if | The low parts basket and oscilloscope. The supply prerequisite is still absent. | $107.62 + $549.00 = $656.62 | $656.62 |
| Owner-incremental first-power base | Low parts basket, current-limited bench supply, and every listed soldering item whose ownership is not established. It excludes the confirmed basic DMM and basic soldering-hardware category. | $107.62 + $134.24 + $82.84 = $324.70 | $324.70 |
| Owner-incremental first-power base plus deferred DHO814 what-if | The same owner-incremental base with the oscilloscope | $324.70 + $549.00 = $873.70 | $873.70 |
| Owner-incremental six-board hypothetical, DHO814 deferred | Mandatory manifest purchases, two OSH Park sets, harness, sleeve, bench supply, unconfirmed soldering and workshop items, optional probe, and a second component set | $79.28 + $34.10 + $8.87 + $2.42 + $134.24 + $82.84 + $15.99 + $14.10 = $371.84 | $371.84 |
| Owner-incremental six-board hypothetical plus deferred DHO814 what-if | The same six-board hypothetical basket with the oscilloscope | $371.84 + $549.00 = $920.84 | $920.84 |
| Full-purchase base, hypothetical | Low parts basket, complete replacement tool set, hypothetical DMM, and bench supply | $107.62 + $148.82 + $151.74 = $408.18 | $408.18 |
| Full-purchase base plus deferred DHO814 what-if | The hypothetical full-purchase base with the oscilloscope | $408.18 + $549.00 = $957.18 | $957.18 |
| Full-purchase six-board hypothetical, DHO814 included | Mandatory manifest purchases, two OSH Park sets, harness, sleeve, complete replacement tool set, hypothetical DMM and supply, optional probe, a second component set, and DHO814 | $79.28 + $34.10 + $8.87 + $2.42 + $148.82 + $151.74 + $15.99 + $14.10 + $549.00 = $1,004.32 | $1,004.32 |

The owner/full comparison also reconciles directly: $408.18 - $65.98 - $17.50 = $324.70 for the owner-incremental first-power base, and $1,004.32 - $65.98 - $17.50 = $920.84 for the owner-incremental six-board hypothetical with the deferred DHO814 what-if.

The $107.62 basket is not ready for first power because it omits the current-limited bench supply. The owner-incremental rows exclude only the confirmed basic DMM and basic soldering-hardware category. The $324.70 and $371.84 rows still include priced items whose ownership is not confirmed; magnification, ESD supplies, suitable test leads, and any other unlisted accessory remain outside the totals. The full-purchase rows are hypothetical comparisons and are not recommended purchases.

### Target and ceiling comparisons

These remainders use the $1,100 target and approximately $1,300 ceiling. They are arithmetic before the excluded costs above, so none establishes a fully bounded project total.

| Scenario | Known subtotal | Remaining to $1,100 | Remaining to $1,300 |
| --- | ---: | ---: | ---: |
| Low parts basket | $107.62 | $1,100 - $107.62 = $992.38 | $1,300 - $107.62 = $1,192.38 |
| Low parts basket plus deferred DHO814 | $656.62 | $1,100 - $656.62 = $443.38 | $1,300 - $656.62 = $643.38 |
| Owner-incremental first-power base | $324.70 | $1,100 - $324.70 = $775.30 | $1,300 - $324.70 = $975.30 |
| Owner-incremental first-power base plus deferred DHO814 | $873.70 | $1,100 - $873.70 = $226.30 | $1,300 - $873.70 = $426.30 |
| Owner-incremental six-board hypothetical, DHO814 deferred | $371.84 | $1,100 - $371.84 = $728.16 | $1,300 - $371.84 = $928.16 |
| Owner-incremental six-board hypothetical plus deferred DHO814 | $920.84 | $1,100 - $920.84 = $179.16 | $1,300 - $920.84 = $379.16 |
| Full-purchase base, hypothetical | $408.18 | $1,100 - $408.18 = $691.82 | $1,300 - $408.18 = $891.82 |
| Full-purchase base plus deferred DHO814 | $957.18 | $1,100 - $957.18 = $142.82 | $1,300 - $957.18 = $342.82 |
| Full-purchase high, hypothetical | $1,004.32 | $1,100 - $1,004.32 = $95.68 | $1,300 - $1,004.32 = $295.68 |

### Remaining unknowns

| Item | Status on 2026-11-01 | Effect on the budget |
| --- | --- | --- |
| JLCPCB PCBA variable charges | NOT_ESTIMABLE | The fixed floors do not include components, joints, part count, assembly variables, shipping, or file charges. |
| Custom optical bracket or boot | NOT_ESTIMABLE | No STEP-based quote exists. The provisional sleeve does not establish a finished mechanical design. |
| Synchronized USB capture | NOT_ESTIMABLE | No source documents a shared timing interface with the fixture. A protocol analyzer without that interface is not justified. |
| Shipping, file extras, tax, and duties | Unknown | Public list prices do not provide a delivered total. |
| Current-limited bench supply | Required before first power; not confirmed owned | The $134.24 candidate is present in owner-incremental totals. Purchase or ownership does not establish a safe limit or validate the power path. |
| Owner inventory | Partly confirmed | Basic DMM and basic soldering equipment are owned. Exact models and capabilities, loose solder and flux, additional hand tools, heat gun, magnification, ESD supplies, suitable leads, and synchronized capture remain unconfirmed; unpriced items cannot be counted as free. The Android DUT is recorded below. |
| Final electrical and optical acceptance | Not a purchase quote | Assembly does not establish the optical-current, noise, comparator, cable, or T0 gates. |

The total external spend remains not calculable. The $1,004.32 full-purchase high example is the largest enumerated known subtotal, not a ceiling. It leaves $95.68 to the $1,100 target on known items alone, and omitted charges can consume or exceed that amount.

The historical scenarios retain dated price inputs and are planning arithmetic, not an orderable basket. The exact R5 MPN has a supplier-available Newark snapshot, while the exact 100 nF `KT` capacitor has zero displayed stock. Both still require manufacturer lifecycle, engineering, and owner approval.

## Power protection boundary

Mean Well GST18U05-P1J is the primary adapter; Phihong PSC15A-050-R is its separately priced alternative. Both provide regulated 5 V but no isolation between Teensy VIN and VUSB. **The PJRC underside trace must be separated and inspected before USB and the external supply can be connected together.** After isolation, the Teensy is externally powered at VIN; the Android host contributes only USB signal data and VBUS presence.

A priced candidate input network is **barrel connector -> MF-R050 PTC -> Pololu 5383 ideal-diode guard -> Teensy VIN**, with common return and a separately 3.3 V-powered SN74LVC1G17 sense buffer on the USB VUSB line. This is a **topology proposal, not a validated final schematic**. The PTC is a thermally activated, delayed, coarse fault limiter (not a knob-adjustable current limit); the ideal diode protects against input reversal and backflow toward the adapter, **not** leakage through any uncut VIN/VUSB join or GPIO. See BENCH-002 for test points, powered verification, and staged release to simultaneous external 5 V plus USB. No equipment is energized or qualified by this plan.

## Lifecycle and purchasing boundary

BOM.csv marks OPA320AIDBVR, TLV3501AIDBVR, SFH 203, Pololu 5383, and SN74LVC1G17DBVRG4 as ACTIVE_VERIFIED_2026-10-31. The other 30 priced rows remain UNVERIFIED_BEFORE_ORDER in the manifest. The 2026-11-01 refresh recorded supplier Active listings for all 23 unique MPNs in those rows; it did not update BOM.csv or establish active-production status from supplier status alone. The exact lifecycle assessment and one-sensor exception are documented in [BENCH-003](PROTOTYPE-PROCUREMENT-READINESS.md). Known obsolete, discontinued, EOL, NRND, and last-time-buy parts remain excluded regardless of supplier stock. Unknown lifecycle is eligible for owner review only for screened low-risk commodity items in this one-sensor prototype; it does not clear critical optical, analog, timing, USB, or power parts. Dated distributor observations are not current-stock confirmation. Generator lifecycle fields remain unchanged, including every UNVERIFIED_BEFORE_ORDER classification.

**SFH 203** (`Q62702P0955`) replaces **SFH 203 P** (`Q62702P0942`). The ams OSRAM product page identifies `Q62702P0955` as full production. Its [2026-03-18 discontinuation notice](https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/8902/OS-PDN-2026-027_ext-revised.pdf) identifies `Q62702P0942` as discontinued and names `Q62702P0955` as the replacement. The `Q62702P0942` product page still says ordering and shipping are possible, so this document records that unresolved conflict without claiming that no orders can currently be placed. The 40-degree total acceptance angle differs from the former 150-degree part, so boot geometry and optical response still require measurement.

## Source-backed electrical gates

1. PJRC states that Teensy 4.1 VIN and VUSB are connected by default, and that external VIN with a USB cable connected can feed power toward the host. The underside pads can be separated for simultaneous external power and USB. See the [product page](https://www.pjrc.com/store/teensy41.html) and [schematic notes](https://www.pjrc.com/teensy/schematic.html).
2. PJRC warns that reverse polarity or more than 6 V can damage Teensy. The external input needs polarity inspection, current limiting, and reverse-current protection.
3. Teensy GPIO uses 3.3 V logic. Never drive it at 5 V.
4. TI specifies OPA320 at 1.8 V to 5.5 V, with local bypass capacitance of 0.001 µF to 0.1 µF and a five-pin SOT-23 version. See the [OPA320 datasheet](https://www.ti.com/lit/ds/symlink/opa320.pdf). This supports a 3.3 V investigation; it does not validate the TIA.
5. TI specifies TLV3501 at 2.7 V to 5.5 V with push-pull output; the exact orderable **TLV3501AIDBVR / SOT-23-6** is now identified. See the [manufacturer TLV3501 documentation](https://www.ti.com/product/TLV3501). Supply it from a verified 3.3 V rail when driving Teensy GPIO, and check VOH/VOL under actual load, output drive limits, startup/power-down states and decoupling. **The assembled connection is still unverified.**
6. The selected **SFH 203** (`Q62702P0955`) is full-production and active. The [manufacturer datasheet](https://look.ams-osram.com/m/67e1a90044b03ee9/original/SFH-203.pdf) gives a 20° half angle, which is a 40° total acceptance cone, and identifies the package flat as the cathode. Page 9 establishes a 2.54 mm lead pitch, 0.5 mm lead thickness, 0.7 mm anode lead dimension near the package, and 5.90 mm maximum body diameter. The project footprint matches this pitch, envelope, and polarity. The datasheet gives no land-pattern or drill recommendation, so the 2.0 mm lands and 1.0 mm drills are project-designed rather than manufacturer-endorsed. Independently verify installed cathode marking and board-side viewing orientation before any fabrication or soldering.

TI's [DEM-OPA-SOT-1A](https://www.ti.com/tool/DEM-OPA-SOT-1A) and Adafruit 1230 are real solder adapters/evaluation candidates, but **neither by itself** establishes a stable, fully decoupled photodiode/TIA/comparator front end. The owner-soldered analog PCB/copper-ground implementation must be separately reviewed and characterized.

## USB path gates

The owner-selected Android DUT is the **AYANEO Pocket AIR Mini**. Owner ADB observations from this device are preserved as reported:

- `android.hardware.usb.host`: declared
- `ro.product.model=GT78-VN`
- `ro.build.fingerprint=ARBOR/GT78-VN/GT78-VN:11/RP1A.200720.011/mp1V95182:user/release-keys`

`GT78-VN` is not unique to AYANEO because ARBOR uses the same model designation for another product. These observations support the Stage-A connector and role assessment. They do not establish successful physical USB-host operation, VBUS delivery, cable retention, enumeration, interoperability, or timing. The Adafruit **5030** USB-C-to-A adapter and **592** A-to-Micro-B data cable remain candidates for the connection to the Teensy Micro-B **device** port. Never connect the Android device to the Teensy USB host header. See [BENCH-002](PROTOTYPE-BENCH-GUIDE.md) for the staged electrical release and physical validation gate.

The oscilloscope/analyzer is measurement equipment, not evidence that T0 passed. The DHO814 has four 100 MHz analog channels (up to 312.5 MSa/s with all four active per [RIGOL datasheet](https://www.rigol.com/dam/global/downloads/brochures/en/data-sheet/oscilloscopes/DHO800_DataSheet_EN.pdf)). A proposed **scope-only** experiment probes D+ and D- plus a fixture hardware marker on a short, safe tap; save original analog traces, demonstrate a specific Full-Speed IN/DATA/ACK waveform and independently validate manual packet interpretation. This can put bus and fixture marker on the same instrument clock, but does not guarantee adequate signal fidelity or exact T0 characterization. The Beagle USB 12 has **no external I/O sync**; it can provide diagnostic packet decoding only. If scope evidence is insufficient, borrow/rent a verified synchronized USB analyzer; never infer T0 from enqueue or firmware interrupt time.

## Owner inventory and remaining blockers

The Calibrite Display Pro HL and Bambu P1S are recorded as owner-owned. On 2026-11-01, the owner also confirmed a basic DMM and basic soldering equipment; their exact models and capabilities remain unspecified. A current-limited supply, SMT rework capability, flux, soldering consumables and accessories, magnification, ESD supplies, and suitable leads have not been confirmed. Unknown inventory is not treated as available. The owner selected the AYANEO Pocket AIR Mini as the target DUT; physical USB compatibility remains unverified.

The dated parts list and scenario estimates are not order-ready procurement or quotes. PCB option selection, PCBA variable charges, the custom optical boot, harness construction, itemized owner tools and accessories, Android compatibility, external USB synchronization, delivered costs, and manufacturer lifecycle confirmation remain unresolved. No physical work or qualification is reported here.

## Technical source links

- Procurement inputs: [Issue #3 follow-up review](https://github.com/whatobiplays/screen-latency-tool/issues/3#issuecomment-6556071055) and [owner bench inventory update](https://github.com/whatobiplays/screen-latency-tool/issues/3#issuecomment-6554881813).
- SFH 203 lifecycle and package: [ams OSRAM SFH 203](https://ams-osram.com/products/photodetectors/photodiodes/osram-radial-t1-34-sfh-203), [DigiKey package drawing](https://media.digikey.com/pdf/Data%20Sheets/Osram%20PDFs/SFH_203_Ver1.3_12-23-15.pdf), and [ams OSRAM notice for the discontinued predecessor](https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/8902/OS-PDN-2026-027_ext.pdf).
- Analog parts: [TI OPA320 datasheet](https://www.ti.com/lit/ds/symlink/opa320.pdf), [TI TLV3501 documentation](https://www.ti.com/product/TLV3501), and [TI SN74LVC1G17 documentation](https://www.ti.com/product/SN74LVC1G17).
- Teensy power and USB: [PJRC Teensy 4.1 notes](https://www.pjrc.com/store/teensy41.html), [annotated pinout](https://www.pjrc.com/teensy/card11b_rev4_web.pdf), and [schematic](https://www.pjrc.com/teensy/schematic.html).
- Adapter specifications: [Mean Well GST18U datasheet](https://www.meanwellusa.com/upload/pdf/GST18U/GST18U-SPEC.pdf) and [Phihong PSX15A datasheet](https://phihong.com/wp-content/uploads/PSX15A.pdf).
- Timing equipment: [RIGOL DHO800 datasheet](https://www.rigol.com/dam/global/downloads/brochures/en/data-sheet/oscilloscopes/DHO800_DataSheet_EN.pdf), [Total Phase USB 12 manual](https://www.totalphase.com/support/articles/200472426-beagle-protocol-analyzer-user-manual/), and [external-trigger limitation](https://www.totalphase.com/blog/2015/03/beagle-usb-protocol-analyzers-provide-external-trigger-test-usb-response/).

Vendor rows carry their individual observation dates. The lifecycle refresh and refreshed supplier observations are dated 2026-11-01; the Phihong and Beagle USB 12 rows retain their 2026-10-31 observations and were not rechecked. Every listing must be rechecked before an owner-approved purchase. This document does not supersede ARCH-001, SPEC-X-001, or ADR-0002.
