# Prototype procurement readiness

Document ID: BENCH-003

Status: CONDITIONALLY_READY_WITH_OPEN_EVIDENCE

Owner: Repository maintainers

Last Updated: 2026-11-01 (America/Los_Angeles)

Depends On: ARCH-001, SPEC-X-001, PHASE-001, ADR-0002, BENCH-001, BENCH-002

Supersedes: None

Superseded By: None

Procurement status: CONDITIONALLY_READY_WITH_OPEN_EVIDENCE

This note consolidates purchasing evidence for one low-cost, one-sensor Teensy 4.1 prototype bench. It records dated observations and open gates. It does not authorize a purchase or report assembly, energization, USB synchronization, or qualification. The five-channel production fixture remains outside this basket.

The purchase basis is one routed 50 mm by 44 mm two-layer optical board design and one OSH Park set containing three physical boards. One optical board is populated. The other two are bare spares. The bench also uses one Teensy 4.1 and an offboard breadboard power and USB assembly.

The generated manifest has 41 modeled rows. It contains 28 unique MPN identities, 27 purchased MPNs, 34 required populated units, one DNP resistor, and six non-purchasable PCB or test-pad rows. The R5 cut-tape minimum raises the priced basket to 43 billed pieces. R4 remains DNP and has $0 planned spend.

Strategy A gives a line-supported component estimate of $79.80 for one prototype. It uses dated mixed supplier offers, including an indexed Mouser observation for the selected capacitor and two separate, unconfirmed Newark inventory crawls for R5. It excludes shipping, tax, tariffs, duty, PCB fabrication, PCBA, mechanics, harness design work, and unpriced equipment. It is not a delivered quote.

The original 23-MPN lifecycle audit remains in section 2. It did not establish exact-SKU manufacturer lifecycle status for DEV-20360; PJRC's manufacturing transition is family-level evidence only. All 23 original MPNs therefore remain MANUFACTURER_STATUS_UNVERIFIED. Five additional mandatory MPNs have exact manufacturer lifecycle evidence. The purchased 27-MPN basket has five exact-MPN statuses and 22 unverified statuses; none is cleared from lifecycle evidence alone. No generated lifecycle field has been changed.

## 1. Board and manifest quantities

| Purchase unit | Quantity | Scope |
| --- | ---: | --- |
| One-sensor routed two-layer PCB design | 1 design | The 1.60 mm board outline is 50 mm by 44 mm. It is the feasibility board, not the five-sensor array. |
| OSH Park two-layer set | 1 set, 3 physical boards | The published planning amount is $17.05 at 3.41 square inches per board and $5 per square inch for one set. |
| Populated optical assemblies | 1 assembly | The manifest supplies one reference-designator set. No second component set is allocated to either bare spare. |
| Teensy 4.1 controller | 1 | SparkFun DEV-20360, Teensy 4.1 without Ethernet and with headers installed. |
| Power and USB interface | Offboard assembly | The manifest identifies these parts as offboard/non-PCB. They mount through the breadboard and wiring. |
| Five-channel production boards | 0 | Deferred production fixture work. |

The $17.05 board amount excludes shipping and fabrication options. It is a planning amount, not a fabrication quote. BENCH-001 uses one set in its low and base scenarios and two sets in its high example. This readiness note uses one populated assembly unless the owner changes that plan.

## 2. Original lifecycle audit and added verified lines

The lifecycle observations in the first table were accessed on 2026-11-01. Supplier evidence was also observed on 2026-11-01, except for the Newark R5 page crawl retrieved on 2026-11-02 UTC. The R5 row identifies both dated inventory captures; neither was live confirmed. ACTIVE_PRODUCTION_CONFIRMED requires primary manufacturer evidence for the exact orderable MPN or an exact order code that maps to it; a normalized base code or broad family notice is insufficient. MANUFACTURER_STATUS_UNVERIFIED means the reviewed source established identity or specifications, but did not establish manufacturer lifecycle for the exact orderable MPN.

| MPN | Function, reference, and quantity | Supplier evidence and dated availability | Manufacturer evidence and lifecycle status |
| --- | --- | --- | --- |
| RC0603FR-0718KL | R1, optical board; 3V3-to-VREF divider upper resistor; 1 | [DigiKey 311-18.0KHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-0718KL/726989) Active; 321,535 shown; 24-week lead | [Yageo RC_L product reference](https://yageogroup.com/products/Resistors/part/RC0603FR-0718KL) identifies the 18 kOhm, 0603, 1 percent part. The reviewed documentation has no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| ERJ-3EKF1002V | R2, optical board; VREF-to-GND divider lower resistor; 1 | [DigiKey P10.0KHCT-ND](https://www.digikey.com/en/products/detail/panasonic-industry/ERJ-3EKF1002V/198102) Active; 744,247 shown; 15-week lead | [Panasonic product page](https://industrial.panasonic.com/ww/products/pt/general-purpose-chip-resistors/models/ERJ3EKF1002V) and [series datasheet](https://industrial.panasonic.com/cdbs/www-data/pdf/RDA0000/AOA0000C304.pdf) identify the 10 kOhm, 0603, 1 percent, 0.1 W part. The series document does not state active production, NRND, or EOL for this exact orderable MPN. MANUFACTURER_STATUS_UNVERIFIED. |
| RC0603FR-0747KL | R3, optical board, TIA feedback resistor; R3, power board, VBUS input pull-down; 2 total | [DigiKey 311-47.0KHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-0747KL/727253) Active; 519,895 shown; 24-week lead | [Yageo MPN specification](https://yageogroup.com/component-documentation/download/specsheet/RC0603FR-0747KL) identifies the 47 kOhm, 0603, 1 percent part. The specification and series datasheet contain no lifecycle field. MANUFACTURER_STATUS_UNVERIFIED. |
| RC0603FR-072M2L | R4, optical board; comparator hysteresis feedback resistor; 1 DNP | [DigiKey YAG3329CT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-072M2L/5282195) Active; 179,716 shown; 24-week lead | [Yageo MPN specification](https://yageogroup.com/component-documentation/download/specsheet/RC0603FR-072M2L) identifies the 2.2 MOhm, 0603, 1 percent part. The reviewed specification contains no lifecycle declaration. The DNP default removes planned spend, not the exact-MPN lifecycle requirement. MANUFACTURER_STATUS_UNVERIFIED. |
| RC0603FR-07100RL | R5, optical board; comparator output series resistor from CMP_OUT to EDGE; 1 | [Newark 75X0141, cut tape 98K7334](https://www.newark.com/yageo/rc0603fr-07100rl/res-100r-1-0-1w-0603-thick-film/dp/75X0141): a separate indexed page crawl retrieved 2026-11-02 UTC displayed 1,864,999 listed in stock and 640,000 incoming. An indexed source-review snapshot dated 2026-11-01 displayed 318,908 on hand and 85,000 incoming. Neither capture was live confirmed; neither is current-stock confirmation or a reservation. No standalone raw capture files are retained, so these remain documented indexed observations only. Cut-tape MOQ 10; the page lists a 26-week replenishment lead. [DigiKey 311-100HRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-07100RL/726888): 0 shown, 5,000 expected 2026-11-09, and a 24-week replenishment lead. | [Yageo MPN specification](https://yageogroup.com/component-documentation/download/specsheet/RC0603FR-07100RL?lang=en) identifies the 100 Ohm, 0603, 1 percent part. The reviewed manufacturer documents do not state active production, NRND, or EOL. The Yageo aggregate supplier quantity is not a physical stock count. MANUFACTURER_STATUS_UNVERIFIED. |
| RC0603FR-071KL | R6, optical board, TIA_OUT-to-MON monitor series resistor; R1 and R2, power board, VBUS and LED series resistors; 3 total | [DigiKey 311-1.00KHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-071KL/726843) Active; 1,967,446 shown; 24-week lead | [Yageo MPN specification](https://yageogroup.com/component-documentation/download/specsheet/RC0603FR-071KL) identifies the 1 kOhm, 0603, 1 percent part. The reviewed documentation contains no lifecycle field. MANUFACTURER_STATUS_UNVERIFIED. |
| RC0603FR-071ML | R7, optical board; threshold fallback bias resistor from THRESH to VREF; 1 | [DigiKey 311-1.00MHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-071ML/729791) Active; 1,864,870 shown; 24-week lead | [Yageo MPN specification](https://yageogroup.com/component-documentation/download/specsheet/RC0603FR-071ML) identifies the 1 MOhm, 0603, 1 percent part. The reviewed documentation contains no lifecycle field. MANUFACTURER_STATUS_UNVERIFIED. |
| CL10A105KP8NNNC | C1 and C4, optical board; VREF and OPA320 3V3 decoupling; 2 | [DigiKey 1276-1182-1-ND](https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A105KP8NNNC/3886840) Active; 269,185 shown; 61-week lead | [Samsung MLCC product database](https://product.samsungsem.com/mlcc/basic-search.do) records the normalized base code CL10A105KP8NNN as Mass Production. It does not provide a separate lifecycle record for the exact final C packaged suffix. The exact orderable MPN remains MANUFACTURER_STATUS_UNVERIFIED. |
| CC0603CRNPO9BN2R2 | C2, optical board; 2.2 pF C0G TIA feedback capacitor from SUM to TIA_OUT; 1 | [DigiKey 311-1739-1-ND](https://www.digikey.com/en/products/detail/yageo/CC0603CRNPO9BN2R2/5195641) Active; 69,116 shown; 16-week lead | [Yageo MPN specification](https://yageogroup.com/component-documentation/download/specsheet/CC0603CRNPO9BN2R2) identifies the 2.2 pF, 50 V, C0G, 0603 part. The reviewed documentation has no lifecycle field. MANUFACTURER_STATUS_UNVERIFIED. |
| KGM15BR71H104KT | C3 and C5, optical board; C1, offboard power group; 100 nF decoupling; 3 | [DigiKey 478-KGM15BR71H104KTCT-ND](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046) Active label; displayed 0; 4,000 expected 2027-05-17; 15-week lead | [Kyocera AVX KGM X7R ordering document](https://datasheets.kyocera-avx.com/KGM_X7R.pdf) identifies the family, electrical class, and T packaging code. It does not state lifecycle status for the exact KT MPN. MANUFACTURER_STATUS_UNVERIFIED. |
| CL10A225KP8NNNC | C6, optical board; 2.2 uF comparator 3V3 decoupling; 1 | [DigiKey 1276-1085-1-ND](https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A225KP8NNNC/3886743) Active; 147,544 shown; 61-week lead | [Samsung MLCC product database](https://product.samsungsem.com/mlcc/basic-search.do) records the normalized base code CL10A225KP8NNN as Mass Production. It does not provide a separate lifecycle record for the exact final C packaged suffix. The exact orderable MPN remains MANUFACTURER_STATUS_UNVERIFIED. |
| 3296W-1-103LF | RV1, optical board; ten-turn threshold trimmer from 3V3 to GND with wiper on THRESH; 1 | [DigiKey 3296W-103LF-ND](https://www.digikey.com/en/products/detail/bourns-inc/3296W-1-103LF/1088045) Active; 30,067 shown; 7-week lead | [Bourns product page](https://www.bourns.com/products/trimpot-trimming-potentiometers/product/3296W-1-103LF) and [3296 datasheet](https://www.bourns.com/docs/Product-Datasheets/3296.pdf) establish the exact series and terminal style. The reviewed documents contain no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| TSW-104-07-G-S | J1, optical board; 3V3, GND, EDGE, and MON header; 1 | [DigiKey 612-TSW-104-07-G-S-ND](https://www.digikey.com/en/products/detail/samtec-inc/TSW-104-07-G-S/1101323) Active; 3,224 shown; 2-week lead | [Samtec product page](https://www.samtec.com/products/tsw-104-07-g-s) and [TSW catalog](https://suddendocs.samtec.com/catalog_english/tsw_th.pdf) establish exact identity and family specifications. The reviewed sources contain no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| Adafruit 368 | J1, power board; 5.5 mm by 2.1 mm barrel-to-screw-terminal adapter; 1 | [Adafruit product 368](https://www.adafruit.com/product/368) shows In stock; count not shown | The brand-owner page establishes the female DC power adapter identity. It does not state a production lifecycle class. MANUFACTURER_STATUS_UNVERIFIED. |
| MF-R050 | F1, power board; 0.5 A hold, 1 A trip resettable PTC between 5V_IN and 5V_LIMITED; 1 | [DigiKey MF-R050-ND](https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965) Active; 10,109 shown; 4-week lead | [Bourns MF-R datasheet](https://www.bourns.com/docs/Product-Datasheets/MF-R.pdf) identifies the 60 V, 40 A, 0.50 A hold-current, 1.00 A trip-current part. The reviewed manufacturer document contains no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| TSW-102-07-G-S | J2, power board, protected 5V_SAFE/GND header; J5, LED_GPIO/GND header; 2 total | [DigiKey 612-TSW-102-07-G-S-ND](https://www.digikey.com/en/products/detail/samtec-inc/TSW-102-07-G-S/1101325) Active; 73,452 shown; 2-week lead | [Samtec product page](https://www.samtec.com/products/tsw-102-07-g-s) and [TSW catalog](https://suddendocs.samtec.com/catalog_english/tsw_th.pdf) establish exact identity and family specifications. The reviewed sources contain no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| TSW-103-07-G-S | J4, power board; 3V3, GND, and VBUS_PRESENT header; 1 | [DigiKey 612-TSW-103-07-G-S-ND](https://www.digikey.com/en/products/detail/samtec-inc/TSW-103-07-G-S/1101324) Active; 73,433 shown; 2-week lead | [Samtec product page](https://www.samtec.com/products/tsw-103-07-g-s) and [TSW catalog](https://suddendocs.samtec.com/catalog_english/tsw_th.pdf) establish exact identity and family specifications. The reviewed sources contain no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| WP7113ID | D1, power board; red slow-status LED; 1 | [DigiKey 754-1264-ND](https://www.digikey.com/en/products/detail/kingbright/WP7113ID/1747663) Active; 102,675 shown; 6-week lead | [Kingbright product page](https://www.kingbrightusa.com/product.asp?catalog_name=LED&Product_ID=WP7113ID) identifies the exact LED. The reviewed manufacturer source contains no lifecycle declaration. MANUFACTURER_STATUS_UNVERIFIED. |
| DEV-20360 | EXT1, Teensy 4.1 without Ethernet and with headers; 1 | [SparkFun DEV-20360](https://www.sparkfun.com/teensy-4-1-without-ethernet-headers.html) showed In stock on 2026-11-01; count not shown. This is a dated supplier observation, not live confirmation. | The [PJRC manufacturing transition notice dated 2025-03-13](https://www.pjrc.com/2025/03/) supports production of the Teensy 4.1 family, but does not establish lifecycle class for the exact DEV-20360 SKU. MANUFACTURER_STATUS_UNVERIFIED. |
| GST18U05-P1J | EXT2, regulated 5 V, 15 W external adapter; 1 | [DigiKey 1866-2073-ND](https://www.digikey.com/en/products/detail/mean-well-usa-inc/GST18U05-P1J/7703631) Active; 141 shown; 17-week lead | [Mean Well datasheet](https://www.meanwellusa.com/upload/pdf/GST18U/GST18U-SPEC.pdf) identifies the exact regulated source. The reviewed source does not state a manufacturer lifecycle class. MANUFACTURER_STATUS_UNVERIFIED. |
| Adafruit 239 | EXT3, full-size premium breadboard, 830 tie points, 2.54 mm pitch; 1 | [Adafruit product 239](https://www.adafruit.com/product/239) shows In stock; count not shown | The brand-owner page establishes exact product identity. It does not state a production lifecycle class. MANUFACTURER_STATUS_UNVERIFIED. |
| Adafruit 5030 | EXT4, USB-A socket to USB-C plug host adapter; 1 | [Adafruit product 5030](https://www.adafruit.com/product/5030) shows In stock; count not shown | The brand-owner page establishes exact product identity and interface. It does not state a production lifecycle class. MANUFACTURER_STATUS_UNVERIFIED. |
| Adafruit 592 | EXT5, USB-A to Micro-B data cable, 3 ft; 1 | [Adafruit product 592](https://www.adafruit.com/product/592) shows In stock; count not shown | The brand-owner page establishes exact cable identity. It does not state a production lifecycle class. MANUFACTURER_STATUS_UNVERIFIED. |

The table covers all 30 UNVERIFIED_BEFORE_ORDER rows, grouped into 23 unique MPNs. The repeated MPNs account for 12 of the 30 placements. The other 18 MPNs occur once. No reviewed PCN, EOL, NRND, or last-time-buy notice was obtained for the unresolved MPNs. Absence of a notice is not evidence of active production.

The two Samsung base-part results require a packaging qualifier. Samsung records CL10A105KP8NNN and CL10A225KP8NNN as Mass Production, then normalizes away the final packaging C when displaying the orderable code. The exact BOM MPNs ending in C remain MANUFACTURER_STATUS_UNVERIFIED until the manufacturer confirms that suffix.

Five mandatory lines have exact manufacturer lifecycle evidence outside the 23-MPN refresh. Distributor figures below are dated 2026-11-02 UTC observations, not live stock confirmation or reservation; they establish the recorded offer evidence, not manufacturer lifecycle.

| MPN and references | Manufacturer lifecycle evidence | Dated supplier offer |
| --- | --- | --- |
| OPA320AIDBVR, U1 optical; 1 | [Texas Instruments part detail](https://www.ti.com/product/OPA320/part-details/OPA320AIDBVR) marks this exact code ACTIVE and in Production. | [DigiKey](https://www.digikey.com/en/products/detail/texas-instruments/OPA320AIDBVR/2747429): $2.88, 705 shown, MOQ 1. |
| TLV3501AIDBVR, U2 optical; 1 | [Texas Instruments part detail](https://www.ti.com/product/TLV3501/part-details/TLV3501AIDBVR) marks this exact code ACTIVE. | [DigiKey](https://www.digikey.com/en/products/detail/texas-instruments/TLV3501AIDBVR/1669420): $4.92, 24,779 shown, MOQ 1. |
| SN74LVC1G17DBVRG4, U1 power/USB; 1 | [Texas Instruments part detail](https://www.ti.com/product/SN74LVC1G17/part-details/SN74LVC1G17DBVRG4) marks this exact code ACTIVE. | [DigiKey](https://www.digikey.com/en/products/detail/texas-instruments/SN74LVC1G17DBVRG4/4833886): $0.71, 2,102 shown, MOQ 1. |
| SFH 203, D1 optical; 1 | [ams OSRAM product page](https://ams-osram.com/products/photodetectors/photodiodes/osram-radial-t1-34-sfh-203) lists ordering code Q62702P0955 as Full production. | [DigiKey](https://www.digikey.com/en/products/detail/ams-osram-usa-inc/SFH-203/2205881): $0.84, 2,056 shown, MOQ 1. |
| Pololu 5383, M1 power/USB; 1 | [Pololu product page](https://www.pololu.com/product/5383) marks the item Active and Preferred. | Pololu direct: $4.95, MOQ 1. No stock count is shown; backorders are allowed. |

These five manufacturer statuses remain separate from distributor availability. The selected KGM15BR71H104KT lifecycle status remains unverified.

## 3. Complete mandatory component basket

The table consolidates repeated exact MPNs. Required quantity is the manifest population for one prototype. Billed quantity applies package minimums. R4 stays DNP with no planned spend. The six pad rows require no purchased part and are therefore absent from this table.

| MPN and exact offer | Manifest references | Need | Bill | MOQ or pack | Line USD | Observed | Evidence and status |
| --- | --- | ---: | ---: | --- | ---: | --- | --- |
| [OPA320AIDBVR, DigiKey 296-29480-1-ND](https://www.digikey.com/en/products/detail/texas-instruments/OPA320AIDBVR/2747429) | U1 optical | 1 | 1 | CT MOQ 1 | $2.88 | 2026-11-02 UTC | Dated DigiKey snapshot: 705 shown, not live confirmed; TI exact-code Active/Production. |
| [TLV3501AIDBVR, DigiKey 296-35977-1-ND](https://www.digikey.com/en/products/detail/texas-instruments/TLV3501AIDBVR/1669420) | U2 optical | 1 | 1 | CT MOQ 1 | $4.92 | 2026-11-02 UTC | Dated DigiKey snapshot: 24,779 shown, not live confirmed; TI exact-code Active. |
| [SFH 203, DigiKey 475-SFH203-ND](https://www.digikey.com/en/products/detail/ams-osram-usa-inc/SFH-203/2205881) | D1 optical | 1 | 1 | Bulk MOQ 1 | $0.84 | 2026-11-02 UTC | Dated DigiKey snapshot: 2,056 shown, not live confirmed; ams OSRAM Full production for ordering code Q62702P0955. |
| [RC0603FR-0718KL, DigiKey 311-18.0KHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-0718KL/726989) | R1 optical | 1 | 1 | CT MOQ 1 | $0.11 | 2026-11-01 | Dated distributor price. Exact-MPN manufacturer lifecycle unverified. |
| [ERJ-3EKF1002V, DigiKey P10.0KHCT-ND](https://www.digikey.com/en/products/detail/panasonic-industry/ERJ-3EKF1002V/198102) | R2 optical | 1 | 1 | CT MOQ 1 | $0.10 | 2026-11-01 | Dated distributor price. Exact-MPN manufacturer lifecycle unverified. |
| [RC0603FR-0747KL, DigiKey 311-47.0KHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-0747KL/727253) | R3 optical; R3 power | 2 | 2 | CT MOQ 1 | $0.20 | 2026-11-01 | Dated distributor price; $0.10 each. Manufacturer lifecycle unverified. |
| [RC0603FR-072M2L, DigiKey YAG3329CT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-072M2L/5282195) | R4 optical, DNP | 1 DNP | 0 | No spend | $0.00 | 2026-11-01 | DNP; excluded from billed pieces. |
| [RC0603FR-07100RL, Newark 75X0141, CT 98K7334](https://www.newark.com/yageo/rc0603fr-07100rl/res-100r-1-0-1w-0603-thick-film/dp/75X0141) | R5 optical | 1 | 10 | CT MOQ 10 | $0.15 | Price/MOQ reviewed 2026-11-02 UTC; inventory crawls dated 2026-11-02 UTC and 2026-11-01 | $0.015 each. The two indexed stock figures were not live confirmed and are not current-stock claims. Manufacturer lifecycle unverified. |
| [RC0603FR-071KL, DigiKey 311-1.00KHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-071KL/726843) | R6 optical; R1 and R2 power | 3 | 3 | CT MOQ 1 | $0.33 | 2026-11-01 | Dated distributor price; $0.11 each. Manufacturer lifecycle unverified. |
| [RC0603FR-071ML, DigiKey 311-1.00MHRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-071ML/729791) | R7 optical | 1 | 1 | CT MOQ 1 | $0.10 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [CL10A105KP8NNNC, DigiKey 1276-1182-1-ND](https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A105KP8NNNC/3886840) | C1 and C4 optical | 2 | 2 | CT MOQ 1 | $0.22 | 2026-11-01 | Dated distributor price; $0.11 each. Exact packaged suffix unverified. |
| [CC0603CRNPO9BN2R2, DigiKey 311-1739-1-ND](https://www.digikey.com/en/products/detail/yageo/CC0603CRNPO9BN2R2/5195641) | C2 optical | 1 | 1 | CT MOQ 1 | $0.10 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [KGM15BR71H104KT, Mouser 581-KGM15BR71H104KT](https://www.mouser.com/en/ProductDetail/KYOCERA-AVX/KGM15BR71H104KT?qs=Jm2GQyTW%2FbhrbR7gNqPCtw%3D%3D) | C3 and C5 optical; C1 power/USB | 3 | 3 | CT MOQ 1 indexed | $0.30 | 2026-11-02 UTC | Indexed listing showed 643,103, $0.10 each. Direct page returned HTTP 403. Live stock, price, and checkout unverified; low-confidence lead only. |
| [CL10A225KP8NNNC, DigiKey 1276-1085-1-ND](https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A225KP8NNNC/3886743) | C6 optical | 1 | 1 | CT MOQ 1 | $0.10 | 2026-11-01 | Dated distributor price. Exact packaged suffix unverified. |
| [3296W-1-103LF, DigiKey 3296W-103LF-ND](https://www.digikey.com/en/products/detail/bourns-inc/3296W-1-103LF/1088045) | RV1 optical | 1 | 1 | Bulk MOQ 1 | $2.53 | 2026-11-02 UTC | Dated page-price snapshot; not live confirmed. Manufacturer lifecycle unverified. |
| [TSW-104-07-G-S, DigiKey 612-TSW-104-07-G-S-ND](https://www.digikey.com/en/products/detail/samtec-inc/TSW-104-07-G-S/1101323) | J1 optical | 1 | 1 | Bulk MOQ 1 | $0.38 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [Adafruit 368](https://www.adafruit.com/product/368) | J1 power | 1 | 1 | MOQ 1 | $2.00 | 2026-11-01 | Brand-owner offer showed In stock; count not shown. Lifecycle class not stated. |
| [MF-R050, DigiKey MF-R050-ND](https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965) | F1 power | 1 | 1 | Bulk MOQ 1 | $0.47 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [Pololu 5383](https://www.pololu.com/product/5383) | M1 power/USB | 1 | 1 | MOQ 1 | $4.95 | 2026-11-02 UTC | Direct manufacturer offer; stock count not shown; backorders allowed. Marked Active and Preferred. |
| [TSW-102-07-G-S, DigiKey 612-TSW-102-07-G-S-ND](https://www.digikey.com/en/products/detail/samtec-inc/TSW-102-07-G-S/1101325) | J2 and J5 power | 2 | 2 | Bulk MOQ 1 | $0.38 | 2026-11-01 | Dated distributor price; $0.19 each. Manufacturer lifecycle unverified. |
| [SN74LVC1G17DBVRG4, DigiKey 296-37652-1-ND](https://www.digikey.com/en/products/detail/texas-instruments/SN74LVC1G17DBVRG4/4833886) | U1 power/USB | 1 | 1 | CT MOQ 1 | $0.71 | 2026-11-02 UTC | Dated DigiKey snapshot: 2,102 shown, not live confirmed; TI exact-code Active. |
| [TSW-103-07-G-S, DigiKey 612-TSW-103-07-G-S-ND](https://www.digikey.com/en/products/detail/samtec-inc/TSW-103-07-G-S/1101324) | J4 power | 1 | 1 | Bulk MOQ 1 | $0.27 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [WP7113ID, DigiKey 754-1264-ND](https://www.digikey.com/en/products/detail/kingbright/WP7113ID/1747663) | D1 power | 1 | 1 | Bulk MOQ 1 | $0.21 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [SparkFun DEV-20360](https://www.sparkfun.com/teensy-4-1-without-ethernet-headers.html) | EXT1 | 1 | 1 | MOQ 1 | $33.60 | 2026-11-01 | Brand-owner offer showed In stock; count not shown. PJRC supports active production for the Teensy 4.1 family. |
| [GST18U05-P1J, DigiKey 1866-2073-ND](https://www.digikey.com/en/products/detail/mean-well-usa-inc/GST18U05-P1J/7703631) | EXT2 | 1 | 1 | Box MOQ 1 | $14.10 | 2026-11-01 | Dated distributor price. Manufacturer lifecycle unverified. |
| [Adafruit 239](https://www.adafruit.com/product/239) | EXT3 | 1 | 1 | MOQ 1 | $5.95 | 2026-11-01 | Brand-owner offer showed In stock; count not shown. Lifecycle class not stated. |
| [Adafruit 5030](https://www.adafruit.com/product/5030) | EXT4 | 1 | 1 | MOQ 1 | $0.95 | 2026-11-01 | Brand-owner offer showed In stock; count not shown. Lifecycle class not stated. |
| [Adafruit 592](https://www.adafruit.com/product/592) | EXT5 | 1 | 1 | MOQ 1 | $2.95 | 2026-11-01 | Brand-owner offer showed In stock; count not shown. Lifecycle class not stated. |
| Total | 28 unique identities, 27 purchased MPNs | 34 populated | 43 billed | Six pad rows add $0 | $79.80 | Mixed dates above | Component estimate only. Shipping, tax, tariffs, duty, PCB, PCBA, mechanics, and equipment excluded. |

The original 21-MPN subset was $64.91 on 2026-11-01. The verified 3296W page update adds $0.14, making that subset $65.05. Five lifecycle-verified lines add $14.30. R5 adds $0.15 through its ten-piece minimum, and the three selected capacitors add $0.30 through the indexed Mouser observation. The line-supported total is:

$65.05 + $14.30 + $0.15 + $0.30 = $79.80.

The $64.91 figure remains useful as the dated narrow basket. It covers 21 original MPNs across 26 manifest rows and 25 purchasable units. It is not the complete mandatory basket.

## 4. R5 and selected capacitor supply

R5 remains RC0603FR-07100RL. The selected capacitor remains KGM15BR71H104KT in C3 and C5 on the optical board and C1 in the offboard group. These are the design selections. The offers below do not establish lifecycle status, approve a substitution, or reserve stock.

| Item and exact offer | Design quantity and placement | Dated offer and availability | Disposition |
| --- | --- | --- | --- |
| [RC0603FR-07100RL, Newark 75X0141, cut tape 98K7334](https://www.newark.com/yageo/rc0603fr-07100rl/res-100r-1-0-1w-0603-thick-film/dp/75X0141) | R5 optical; 1 | Price/MOQ reviewed 2026-11-02 UTC: CT MOQ 10 at $0.015 each, $0.15 minimum. Separate indexed inventory captures: the 2026-11-02 UTC page crawl displayed 1,864,999 listed in stock and 640,000 incoming; the 2026-11-01 source-review snapshot displayed 318,908 on hand and 85,000 incoming. Neither was live confirmed or reserved, and neither figure should be treated as current stock. No standalone raw capture files are retained; these are documented indexed observations only. The page lists a 26-week manufacturer lead. | Strategy A line, subject to fresh stock and checkout checks. Manufacturer lifecycle remains unverified. |
| [RC0603FR-07100RL, DigiKey 311-100HRCT-ND](https://www.digikey.com/en/products/detail/yageo/RC0603FR-07100RL/726888) | R5 optical; 1 | Dated observation: $0.015 each, CT MOQ 1, zero shown, 5,000 expected 2026-11-09, 24-week replenishment lead. | Strategy B line. Incoming stock is not on-hand stock. |
| [KGM15BR71H104KT, Mouser 581-KGM15BR71H104KT](https://www.mouser.com/en/ProductDetail/KYOCERA-AVX/KGM15BR71H104KT?qs=Jm2GQyTW%2FbhrbR7gNqPCtw%3D%3D) | C3 and C5 optical; C1 power/USB; 3 total | 2026-11-02 UTC: indexed listing showed 643,103 available, CT MOQ 1, $0.10 each, $0.30 for three. A direct request returned HTTP 403, so live stock, checkout price, and delivery remain unverified. | Conditional preferred exact-MPN lead. Recheck live availability and obtain lifecycle and owner approval before ordering. |
| [KGM15BR71H104KT, DigiKey 478-KGM15BR71H104KTCT-ND](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046) | C3 and C5 optical; C1 power/USB; 3 total | Dated observation: CT MOQ 1 at $0.10 each, $0.30 for three. Zero shown; 4,000 expected 2027-05-17; 15-week lead. | Exact-MPN restock path if the owner accepts the wait. Not immediately available. |
| [KGM15BR71H104KT, Future Electronics](https://www.futureelectronics.com/p/passives--capacitors--ceramic-capacitors--multilayer-ceramic-capacitors/kgm15br71h104kt-kyocera-avx-1183764) | C3 and C5 optical; C1 power/USB; 3 total | Dated observation: 632,000 global and US distribution stock; factory stock 0; MOQ 4,000 at $0.0414 each; $165.60 for one pack; 14-week lead. | Distribution stock is not a factory inventory claim. The pack is far above the three-unit need. |
| [Newark 54K2412, manufacturer part 06035C104KAT2A](https://www.newark.com/kyocera-avx/06035c104kat2a/cap-mlcc-smd-0-1uf-50v-x7r/dp/54K2412) | Alias lead only; no approved BOM use | 2026-11-02 UTC page identified the part as "Also Known As KGM15BR71H104KT." CT MOQ 1 at $0.234 each, or $0.702 for three. The page says available to order or notify when back in stock, so it is not presently in stock. The page shows a 10-week manufacturer lead while product data shows 13 weeks. | Alias evidence only. Do not approve a substitution or change the selected MPN without manufacturer and owner confirmation. |
| [KGM15BR71H104KM, DigiKey](https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KM/1600047) | No approved BOM use | Dated observation: 10,399 shown; CT MOQ 1 at $0.10; reel MOQ 15,000; 15-week lead. | Packaging-code alternative only. Unapproved and excluded from the basket. |

The KM ordering code specifies 13-inch paper packaging where KT specifies 7-inch paper packaging. That packaging difference does not supply the missing exact-MPN lifecycle evidence or approve a design change. Follow-on engineering would still need to confirm package handling, DC-bias behavior, high-frequency decoupling, assembly process, and owner approval before any MPN change.

Newark and Mouser both link legacy manufacturer code 06035C104KAT2A to the selected MPN. That cross-reference is useful for procurement research. It does not establish that Newark's alias can replace KGM15BR71H104KT in this design.

Within this evidence, the preferred selected-capacitor lead is the exact-MPN Mouser offer after a successful live recheck. It remains conditional on manufacturer lifecycle evidence and owner approval. A DigiKey restock wait is the next exact-MPN path. The Future Electronics pack is an expensive availability option, and the Newark alias and KM packaging code remain unapproved.

## 5. Supplier strategies and cost reconciliation

Strategy A preserves exact MPNs and the dated line sources. Strategy B reduces the number of suppliers by moving R5 and KT to DigiKey. Its $79.67 total uses the listed one-piece R5 price; Strategy A is $79.80 because Newark's cut-tape minimum bills ten pieces. Both exact DigiKey lines were out of stock on their observation dates, so Strategy B cannot be fulfilled from the recorded offers. Neither strategy is a delivered quote.

| Strategy | Supplier subtotals | Supplier orders | Component total | Practical condition |
| --- | --- | ---: | ---: | --- |
| A: Newark R5 and indexed Mouser KT | DigiKey $28.95; Adafruit $11.85; SparkFun $33.60; Pololu $4.95; Newark $0.15; Mouser $0.30 | 6 | $79.80 | The exact-MPN capacitor lead is indexed and blocked by HTTP 403. Newark R5 stock was not live confirmed. Fresh stock, checkout, lifecycle, and owner checks remain. |
| B: DigiKey-forward | DigiKey $29.27; Adafruit $11.85; SparkFun $33.60; Pololu $4.95 | 4 | $79.67 | Consumes fewer supplier orders. DigiKey R5 is expected 2026-11-09 and KT is expected 2027-05-17. This is not immediately fulfillable. |
| Secondary: Mouser-indexed pooling | OPA320 indexed at $2.90; TLV3501 indexed between $4.57 and $4.83; SFH 203 US figure $0.66 from an aggregator; SN74 at $0.71; KT at $0.30 | Not established | Not a quote | Mouser live access was unavailable. Indexed prices conflict for TLV3501. This strategy is secondary and cannot be represented as confirmed. |

The historical BENCH-001 reference is $79.28: $79.24 for the raw mandatory manifest plus a $0.04 resistor update. The historical itemized lines actually sum to $79.26: $64.91 for the original 21-MPN subset, $13.90 for five active parts, $0.15 for the Newark R5 minimum, and $0.30 for three selected KT capacitors. That is a $0.02 discrepancy in the historical reference. It is recorded here without changing the historical scenario figure.

The current line-supported bridge comes from the historical itemized lines. Active-part prices moved from $13.90 to $14.30, a net increase of $0.40. The 3296W trimmer moved from $2.39 to $2.53, an increase of $0.14. The current total is therefore $79.26 + $0.40 + $0.14 = $79.80. It is $0.52 above the historical $79.28 reference because of the current line updates and the historical two-cent mismatch.

The $0.26 adjustment has no assigned MPN, quantity, supplier, or priced manifest row in the available run record. Adding it arithmetically to the $79.80 row-supported subtotal gives $80.06; this subtotal-plus-variance sensitivity does not identify a purchasable basket and is not included in the purchase subtotal. The $0.26 remains unallocated. Section 7 shows its arithmetic effect on scenario totals and remainders. This variance is separate from the historical $79.28 reference and its $0.02 line-sum discrepancy. Shipping, tax, tariffs, duty, PCBA, PCB fabrication and assembly, mechanics, harness design, USB synchronization, and unpriced equipment remain separate or UNKNOWN.

## 6. Bench supply and first-power prerequisite

The owner has no adjustable, current-limited bench DC supply. The fixed 5 V adapter and MF-R050 PTC do not replace one. Before any supply purchase or current-limit setting, review an initial load budget and document a defensible current-limit method based on expected load and component limits. No numeric current limit is assigned. The load budget and current-limit method remain unreviewed; startup and load behavior remain unmeasured until controlled Stage-B commissioning.

| Candidate | Offer and dated availability | Electrical, control, and protection evidence | Qualification still required |
| --- | --- | --- | --- |
| Korad KA3005PE+, `OWNER_SELECTED_MODEL` | [DigiKey](https://www.digikey.com/en/products/detail/sra-soldering-products/KA3005PE/21293129): $134.24, 73 shown, one-unit minimum, dated observation 2026-11-02 UTC; the count is not live confirmed or reserved. [SRA Soldering Products](https://sra-solder.com/korad-ka3005e-precision-variable-adjustable-30v-5a-150w-dc-linear-power-supply-digital-regulated-lab-grade-with-wattage-display-1): $149.00, dated listing observation 2026-11-02 UTC, not live confirmed. DigiKey US shipping starts at $4.99; actual shipping, tax, and tariffs require checkout. | Single output, 0 to 30 V, 0 to 5 A, 150 W. Linear supply with CV/CC, output enable, OVP, OCP, overload protection, and reverse-polarity protection. Setup and readback resolution are 10 mV and 1 mA. Rated ripple is at or below 2 mV RMS in CV and 3 mA RMS in CC. [Manual](https://storage.tameson.com/asset/Products/Manuals/EN/korad_ka3005p_manual.pdf) and [Korad family page](https://www.koradtechnology.com/product/83.html). | `NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED`. The manual requires protective earth and documents selectable mains voltages. Reviewed material does not provide an output-to-earth isolation rating or exact-model NRTL certificate; output leads are not confirmed in the package. Before energizing, verify the supply revision, mains selector, fuse and cord, protective earth, output isolation, certification, included accessories, and lead suitability. Measure startup and load behavior only during controlled Stage-B commissioning after the applicable checks. These reviews and measurements remain uncompleted. |
| Korad KA3005D | [DigiKey](https://www.digikey.com/en/products/detail/sra-soldering-products/KA3005D/10708362): $104.14, 190 shown, one-unit minimum, observed 2026-11-02 UTC. | Single output, 0 to 30 V, 0 to 5 A, CV/CC and output enable. Korad's [KA-series specifications](https://www.koradtechnology.com/product/83.html) list 10 mV voltage and 1 mA current setup and readback resolution for the KA3005D and describe settable OVP/OCP. The reviewed manufacturer specification does not state readback accuracy. The D model has no USB or RS232 programming. Ripple is at or below 2 mV RMS in CV and 3 mA RMS in CC. [Manual](https://mm.digikey.com/Volume0/opasdata/d220001/medias/docus/4151/KA3000_6000_Series_Man.pdf). | The reviewed material does not provide an output-to-earth isolation rating or exact-model certification evidence. Output leads are not confirmed included. Choose this if remote programming is unnecessary. |
| Siglent SPD3303X-E | [TestEquity](https://www.testequity.com/brand/Siglent/category/All/33118-1-SPD3303X-E): $459.00 and 145 shown, observed 2026-11-02 UTC. TEquipment listed it in stock with advertised free contiguous-US shipping over $99 under the observed offer. Confirm checkout terms. | Three isolated outputs: CH1 and CH2 at 0 to 32 V and 0 to 3.2 A, plus CH3 at 2.5, 3.3, or 5 V and 0 to 3.2 A, with 220 W total. CV/CC, per-channel output control or all-channel enable, series and parallel modes, short and overload protection, USB and LAN. Siglent's [datasheet](https://siglentna.com/wp-content/uploads/dlm_uploads/2017/10/SPD3303X_DataSheet_DS0503X-E02C.pdf) lists 4-digit voltage and 3-digit current displays, 10 mV/10 mA resolution, and readback accuracy of ±(0.5% of reading + 2 digits) for both voltage and current. The [Quick Start Guide](https://siglentna.com/wp-content/uploads/dlm_uploads/2017/10/SPD3303X_QuickStart_QS0503X-E01B-1.pdf) identifies separate set and readback values on the front-panel display. Ripple is at or below 1 mV RMS in CV and 3 mA RMS in CC. | Datasheet lists two sets of output test cords, a power cord, a USB cable, a quick-start guide, and a calibration certificate. Check termination compatibility. Exact-model NRTL certification remains unconfirmed. The 10 mA current step is coarser than the Korad options. |

The owner selected the KA3005PE+ for planning. Its status is `OWNER_SELECTED_MODEL / NOT_PURCHASED / NOT_FIRST_POWER_QUALIFIED`; the $134.24 DigiKey figure is a dated planning price, not a live quote or purchase. The KA3005D and SPD3303X-E remain historical alternatives, not replacement selections. Keep the DHO814 deferred.

No exact KA3005PE+ Amazon US listing was found that also had a verifiable seller, model identity, and delivered terms. Amazon is not recommended from the available evidence.

Before first power, complete the initial load-budget review and document a defensible current-limit method. Then verify the manual and nameplate, mains selector, protective earth, cord and fuse, output isolation, lead insulation and polarity, measurement reference, and staged-start checks in BENCH-002. Do not energize until these checks are complete. Measure startup and load behavior only during controlled Stage-B commissioning. The load-budget and current-limit reviews, pre-energization verification, and Stage-B startup/load measurements remain uncompleted.

The RIGOL DHO814 remains deferred. The Adafruit 5030 adapter and 592 cable do not establish Android USB host mode, VBUS behavior, enumeration, or synchronized capture. The Beagle USB 12 remains excluded because its listed interface cannot independently correlate fixture time with USB ACK.

## 7. Owner inventory and cost scenarios

The owner has confirmed ownership of a basic digital multimeter and basic soldering equipment. Exact models, precision, soldering tips, SMT rework capability, accessories, and magnification remain unspecified. The owner has also confirmed that an adjustable, current-limited bench supply is absent.

The owner has not confirmed flux, tweezers, cutters, a heat gun, magnification, ESD equipment, specialized probes, or suitable test leads. No unconfirmed item is treated as owned. The full-purchase rows below remain hypothetical comparisons from BENCH-001. The owner-incremental rows remove only the confirmed basic DMM and basic soldering-equipment categories. All scenarios use Strategy A's $79.80 row-supported basket; Strategy B's out-of-stock $79.67 option is not used in these totals.

| Scenario | Arithmetic | Known subtotal | Remainder from $1,100 target | Remainder from approximately $1,300 ceiling |
| --- | ---: | ---: | ---: | ---: |
| Low parts basket, not first-power ready | $79.80 + $17.05 + $8.87 + $2.42 | $108.14 | $991.86 | $1,191.86 |
| Owner-incremental first-power base | $79.80 components + $17.05 PCB set + $8.87 harness example + $2.42 heatshrink + $134.24 Korad PE+ + $82.84 unconfirmed tool allowance | $325.22 | $774.78 | $974.78 |
| Owner-incremental high, DHO814 deferred | $79.80 + $34.10 + $8.87 + $2.42 + $134.24 + $82.84 + $15.99 + $14.10 | $372.36 | $727.64 | $927.64 |
| Owner-incremental first-power base with deferred DHO814 what-if | $325.22 + $549.00 | $874.22 | $225.78 | $425.78 |
| Owner-incremental high with deferred DHO814 what-if | $372.36 + $549.00 | $921.36 | $178.64 | $378.64 |
| Full-purchase base, hypothetical | $108.14 + $148.82 + $151.74 | $408.70 | $691.30 | $891.30 |
| Full-purchase high with deferred DHO814 what-if, hypothetical | $79.80 + $34.10 + $8.87 + $2.42 + $148.82 + $151.74 + $15.99 + $14.10 + $549.00 | $1,004.84 | $95.16 | $295.16 |

### Sensitivity to the unallocated $0.26

Each value below adds the unallocated $0.26 once to the corresponding row-supported subtotal and subtracts it from both budget remainders. The sensitivity does not assign the variance to an MPN, supplier, or quantity, and it does not change the purchase subtotal.

| Scenario | Sensitivity subtotal | Remainder from $1,100 target | Remainder from approximately $1,300 ceiling |
| --- | ---: | ---: | ---: |
| Low parts basket, not first-power ready | $108.40 | $991.60 | $1,191.60 |
| Owner-incremental first-power base | $325.48 | $774.52 | $974.52 |
| Owner-incremental high, DHO814 deferred | $372.62 | $727.38 | $927.38 |
| Owner-incremental first-power base with deferred DHO814 what-if | $874.48 | $225.52 | $425.52 |
| Owner-incremental high with deferred DHO814 what-if | $921.62 | $178.38 | $378.38 |
| Full-purchase base, hypothetical | $408.96 | $691.04 | $891.04 |
| Full-purchase high with deferred DHO814 what-if, hypothetical | $1,005.10 | $94.90 | $294.90 |

The $82.84 unconfirmed soldering and workshop allowance is solder at $9.25, flux at $7.95, flush cutters at $7.25, tweezers at $3.95, wire strippers at $11.95, and a heat gun at $42.49. The $148.82 full replacement tool set also includes a $25.99 Pinecil and a $39.99 PinePower supply. Those are comparison inputs for the owner-incremental and full-purchase examples. They do not imply that the owner has those tools.

The component evidence in sections 3 to 5 is the complete mandatory basket. BENCH-001 scenarios also include planning amounts for the PCB set, harness example, equipment, and the hypothetical tool allowance. Shipping, tax, tariffs, duty, PCBA, mechanics, USB synchronization, and final engineering qualification remain outside every known subtotal.

## 8. Assembly, fabrication, and system dependencies

1. PCB fabrication is estimated at $17.05 for one OSH Park two-layer set of three boards. Shipping and fabrication options are unknown. No Gerbers or fabrication order are approved.

2. One populated assembly is intended. A design-specific PCBA quote remains unavailable. The confirmed soldering equipment does not establish SMT rework capability, magnification, flux, cleaning materials, or suitable tips.

3. The power and USB circuitry is offboard. The short-harness example is $8.87: Adafruit jumpers at $3.95, a Sullins header at $0.43, and an Alpha Wire heatshrink pack at $4.49. One planning foot of opaque heatshrink is $2.42. Actual harness construction, retention, insulation, and connector choice remain unresolved.

4. The routed board has no mounting holes. It needs an external edge clamp or bracket and an opaque contact boot. Board edges, photodiode aperture alignment, boot clearance, and display contact remain unverified. No finished mechanical design or STEP-based quote exists, so mechanics cost is UNKNOWN.

5. The owner-selected Android device is the AYANEO Pocket AIR Mini. Its Android build declares `android.hardware.usb.host`, while AYANEO documents a USB-C port for charging and data and lists USB 2.0 connectivity. Adafruit 5030 and Adafruit 592 form the design-stage candidate connector chain to the Teensy 4.1 main USB device port. Host-role switching, VBUS, retention, interoperability, and enumeration still require the physical Stage-B test plan.

6. No synchronized USB capture source is established. The deferred DHO814 does not by itself solve shared-clock or trigger correlation. The Beagle USB 12 remains excluded.

7. First power requires the current-limited supply, a reviewed load budget, polarity and protection checks, physical VIN/VUSB separation, and staged measurements. None of those checks has been performed.

8. Shipping destination, tax status, tariff treatment, and duty responsibility remain unknown. Supplier prices are not delivered prices.

## 9. Procurement gates and owner decisions

1. Resolve exact-MPN lifecycle evidence for critical functions before procurement. The one-sensor exception may cover only the low-risk commodity items marked eligible in section 13. Do not change generated BOM lifecycle metadata; retain every UNVERIFIED_BEFORE_ORDER classification while evidence is unresolved.

2. Recheck exact-MPN KGM15BR71H104KT availability at Mouser after the HTTP 403 block. Decide whether to accept the DigiKey restock wait, use the conditional Mouser lead, or approve the Future Electronics 4,000-unit pack. KM and the Newark alias remain unapproved.

3. Recheck R5 stock at Newark or choose the DigiKey incoming date. Neither exact R5 path is qualified for ordering by the dated evidence alone.

4. Choose Strategy A or Strategy B after stock and checkout revalidation. Confirm shipping destination, tax treatment, and delivered cost. Duty and import costs remain UNKNOWN until a seller confirms them.

5. Before any supply purchase or current-limit setting, review the initial load budget and document a defensible current-limit method. Before energizing, verify the supply revision, manual and nameplate, mains selector, protective earth, cord and fuse, output isolation, lead insulation and polarity, certification, included leads, measurement reference, and staged-start checks in BENCH-002. Measure startup and load behavior only during controlled Stage-B commissioning. These reviews, verifications, and measurements remain uncompleted.

6. Decide whether one optical board will be populated and the other two remain bare spares. This choice affects component quantities, assembly charges, and physical-fit work.

7. Select PCBA or owner assembly after obtaining a design-specific quote and confirming the available soldering capability. No PCBA cost is included.

8. Finish the mechanical retention, opaque boot, physical-fit, and harness design. The current board and subassemblies do not establish a safe display-contact fixture.

9. The owner has selected the AYANEO Pocket AIR Mini and supplied exact ADB build evidence plus a declared USB-host feature. Review the documented 5030-to-592-to-Teensy main-device-port topology under the caveats in section 13 and resolve any concrete design-stage fit, role, or data-path issue. Actual host behavior, VBUS, connector retention, interoperability, power safety, and empirical enumeration are Stage-B checks; empirical enumeration alone cannot block Stage A. Define the synchronized USB capture method before purchasing the deferred oscilloscope.

These gates prevent a complete purchase recommendation. The mandatory component basket and both supplier strategies are documented, but lifecycle, stock, first-power, mechanical, USB, assembly, and delivered-cost evidence remain open.

## 10. Unsent manufacturer inquiry

Status: UNSENT. Do not send until the owner authorizes contact. Use one inquiry per recipient and include only that recipient's exact MPNs below.

Subject: Lifecycle status request for exact prototype MPNs

Hello,

Please confirm the current lifecycle classification for the exact orderable MPNs listed below. For each MPN, state whether it is in active production, not recommended for new designs, end-of-life or discontinued, or under a last-time-buy notice. Please provide the date of the status and any applicable product-change, obsolescence, or discontinuation notice.

These parts are being evaluated for a low-cost one-sensor hardware feasibility bench. This request does not authorize a substitution or an order.

MPNs:

[Paste the recipient's exact MPNs from the table below.]

Regards,

[Owner name]

| Manufacturer | Exact MPNs to request |
| --- | --- |
| Yageo | RC0603FR-0718KL; RC0603FR-0747KL; RC0603FR-072M2L; RC0603FR-07100RL; RC0603FR-071KL; RC0603FR-071ML; CC0603CRNPO9BN2R2 |
| Panasonic Industry | ERJ-3EKF1002V |
| Samsung Electro-Mechanics | CL10A105KP8NNNC; CL10A225KP8NNNC. Request separate confirmation of the final C packaged suffixes after the database shows base codes CL10A105KP8NNN and CL10A225KP8NNN as Mass Production. |
| Kyocera AVX | KGM15BR71H104KT. Ask whether 06035C104KAT2A is a legacy alias and whether the exact KT ordering code remains in active production. |
| Bourns | 3296W-1-103LF; MF-R050 |
| Samtec | TSW-104-07-G-S; TSW-102-07-G-S; TSW-103-07-G-S |
| Kingbright | WP7113ID |
| Mean Well | GST18U05-P1J |
| Adafruit | 368; 239; 5030; 592 |
| PJRC or SparkFun | DEV-20360; request the exact-SKU lifecycle class, not family-level production status |

DEV-20360 remains in the lifecycle inquiry because the PJRC manufacturing transition establishes family-level production, not lifecycle class for the exact SKU. Ask PJRC or SparkFun to confirm DEV-20360 directly. The five exact-status MPNs are omitted because their linked manufacturer pages already establish lifecycle evidence. Keep this inquiry UNSENT unless the owner authorizes contact; a distributor reply alone does not close a manufacturer lifecycle gate.

## 11. Evidence for the original seven acceptance criteria

| Original criterion | Evidence in this note |
| --- | --- |
| TAC-1: Lifecycle coverage and classifications | Section 2 covers all 30 UNVERIFIED_BEFORE_ORDER rows and 23 original MPNs. It preserves exact-MPN classifications, the two Samsung packaging qualifiers, the DEV-20360 family-level evidence without assigning an exact-SKU lifecycle, and the five additional lines with exact manufacturer sources. It keeps distributor stock and labels separate from lifecycle. |
| TAC-2: R5 and selected capacitor sourcing | Sections 2 and 4 date the separate Newark R5 indexed inventory captures and state that neither was live confirmed. Section 4 also records the DigiKey offer, exact-MPN Mouser KT lead and its HTTP 403 limit, DigiKey restock, Future pack, Newark 06035C104KAT2A alias evidence, and unapproved KM packaging option. |
| TAC-3: Bench supply and owner inventory | Section 6 starts with the confirmed absence of an adjustable, current-limited supply. Sections 6 and 7 compare the KA3005PE+, KA3005D, and SPD3303X-E and preserve the limits of confirmed owner equipment. |
| TAC-4: Complete basket and supplier comparison | Section 3 lists all 27 purchased MPNs, 34 required populated units, R4 DNP, package minimums, 43 billed pieces, offer links, observation dates, and the $79.80 line total. Section 5 compares Strategy A, Strategy B, and the unconfirmed Mouser pooling option. |
| TAC-5: Cost reconciliation and unknown charges | Sections 5 and 7 preserve the $79.26 historical line sum, the $79.28 reference, and their $0.02 discrepancy; they keep $79.80 as the row-supported purchase subtotal and show the unallocated $0.26 only as a separate scenario sensitivity. Delivery, fabrication, assembly, mechanics, USB, and equipment costs remain outside the component sum. |
| TAC-6: Safety and authorization boundaries | Sections 6, 8, and 9 require load-budget review, mains and earth checks, lead and polarity review, staged first power, and owner decisions. No purchase, substitution, fabrication, or energization is authorized. |
| TAC-7: Documentation integrity and scoped validation | This policy amendment is limited to the four authorized documents. It preserves the 18 non-target dirty baseline paths and prior Delegation v3 records. Focused checks cover Markdown structure, links, manifest quantities, row-supported and sensitivity arithmetic, cross-document consistency, MPN eligibility, and baseline hashes. ERC, DRC, SPICE, and hardware tests are outside this documentation-only change. |

## 12. Sources, dates, evidence boundary, and validation

Most original component prices and supplier counts in sections 2 and 3 were observed on 2026-11-01. The five added mandatory lines, the 3296W price, the Mouser capacitor indexed evidence, the Newark alias page, and the bench-supply offers were observed on 2026-11-02 UTC. Newark's separate R5 indexed observations dated 2026-11-02 UTC and 2026-11-01 displayed 1,864,999 plus 640,000 incoming and 318,908 plus 85,000 incoming, respectively. Neither capture was live confirmed or reserved. No standalone raw capture files are retained; these figures are documented indexed observations only, not current-stock claims. The selected capacitor Mouser page returned HTTP 403, so its indexed availability and price are low-confidence leads.

A distributor Active label, an In stock display, an indexed quantity, or a product page without a lifecycle statement does not establish manufacturer production status. The manufacturer links in section 2 establish status only where their text says so. No missing PCN or EOL notice is treated as evidence of active production.

The [board-set planning amount](https://docs.oshpark.com/services/two-layer/) and dimensions are recorded in BENCH-001. The [RIGOL DHO814](https://www.rigolna.com/products/digital-oscilloscopes/dho800/dho812/) remains a deferred equipment reference. The current bench BOM remains [BENCH-001](PROTOTYPE-BENCH-BOM.md), and the staged procedures remain [BENCH-002](PROTOTYPE-BENCH-GUIDE.md).

This amendment applies the policy in SPEC-X-001 and is summarized in BENCH-001 and BENCH-002. It preserves the historical supplier observations, original Issue #3 acceptance criteria, DNP R4, generator lifecycle metadata, and prior Delegation v3 runs and attestations. The four authorized documentation paths are the only source paths changed; the other 18 dirty baseline paths remain protected.

Focused validation checks Markdown tables and whitespace, local and external links, exact manifest counts and quantities, the $79.80 arithmetic, scenario remainders, the separate $0.26 sensitivity, agreement across the four documents and manifest, the 27-MPN eligibility assessment, and preservation of the 18 non-target baseline paths. ERC, DRC, SPICE, and hardware tests are not rerun for this documentation-only amendment.

No purchase, cart, fabrication, assembly, energization, seller contact, manufacturer inquiry, substitution approval, or qualification result is recorded here.

Procurement status: CONDITIONALLY_READY_WITH_OPEN_EVIDENCE.

## 13. Risk-based lifecycle and one-sensor eligibility assessment

The owner decision for Issue #3 permits risk-based purchase-review eligibility for qualifying low-risk commodity parts in a one-sensor feasibility prototype. It selects the Korad KA3005PE+ model without authorizing purchase or first power; production requirements remain stricter. The independent review identified the circular physical-test gate and calls for the sequential milestones PRE_PURCHASE_REVIEW, ASSEMBLY_FIRST_POWER_RELEASE, and MEASUREMENT_PRODUCTION_QUALIFICATION. The Issue #3 decision remains open.

### Sequential purchase, release, and qualification stages

1. **PRE_PURCHASE_REVIEW (Stage A):** The owner reviews each exact MPN for eligibility to enter one-sensor purchase review. A REVIEW disposition allows that review to proceed; it never auto-approves an item or authorizes an order, substitution, fabrication, or first power. Stage A is blocked only by a concrete pre-purchase safety, compatibility, lifecycle, sourcing, or risk gap. Checks that require receiving, assembling, or measuring a part belong downstream.
2. **ASSEMBLY_FIRST_POWER_RELEASE (Stage B):** After separate purchase authorization and receipt, verify the exact parts, assemble the prototype, and complete the reviewed safety checks. First power requires a rated, adjustable, current-limited bench source and every applicable power and signal check. Stage B has not cleared any part.
3. **MEASUREMENT_PRODUCTION_QUALIFICATION (Stage C):** Begin characterization only after Stage B release. One-sensor measurements support feasibility only. Production qualification has separate requirements for the five-channel fixture; no one-sensor exception satisfies them.

The basket contains 27 purchased MPNs: 16 REVIEW and 11 HOLD at Stage A. REVIEW means the owner may assess the exact line under its stated conditions; it is not purchase permission. All Stage-B and Stage-C evidence remains unperformed.

### Pocket AIR Mini Stage-A evidence

The owner selected an AYANEO Pocket AIR Mini for the one-sensor prototype. Owner ADB observations from the device are preserved here as reported:

- Selected DUT: AYANEO Pocket AIR Mini
- `android.hardware.usb.host`: declared
- `ro.product.model=GT78-VN`
- `ro.build.fingerprint=ARBOR/GT78-VN/GT78-VN:11/RP1A.200720.011/mp1V95182:user/release-keys`

`GT78-VN` is not unique to AYANEO because ARBOR uses the same designation for another product. Together with the Android feature declaration and AYANEO's documented ports, these observations support a Stage-A connector and topology review. They do not prove that the physical USB-C port switches to host mode, supplies VBUS, enumerates the Teensy, retains the connection, or meets electrical or timing requirements.

The design-stage candidate chain is:

```text
AYANEO Pocket AIR Mini USB-C charging/data port
-> Adafruit 5030, USB-C male to USB-A female
-> Adafruit 592, USB-A male to USB Micro-B male
-> Teensy 4.1 main USB device port
```

Do not connect the Pocket AIR Mini to the Teensy USB host header. The [AYANEO Pocket AIR Mini user manual](https://ayaneo-1305909189.cos.accelerate.myqcloud.com/ayaneo_com/download/2025/AYANEO-Pocket-AIR-Mini_User%20Manual_EN_20251028.pdf) identifies a USB-C port that supports charging and data transfer. The [AYANEO product page](https://www.ayaneo.jp/ayaneo-pocket-air-mini) lists a Type-C port, PD charging, and USB 2.0. [Adafruit 5030](https://www.adafruit.com/product/5030) identifies a USB-A socket to USB-C plug adapter and notes its USB 2.x scope and missing Type-C power negotiation pins. [Adafruit 592](https://www.adafruit.com/product/592) identifies a USB-A to USB Micro-B data cable. [PJRC's Teensy 4.1 page](https://www.pjrc.com/store/teensy41.html) distinguishes the main USB device port from the separate USB host port.

These sources support connector identity and the intended device-port connection. Runtime host switching, VBUS delivery, enumeration, actual speed, retention, interoperability, data flow, and timing accuracy remain unverified and belong to Stage B or Stage C. Purchasing remains subject to owner review and exact-product checkout validation.

Apply these rules:

1. Known obsolete, discontinued, EOL, NRND, or last-time-buy status means EXCLUDED, regardless of supplier stock.
2. Exact-MPN manufacturer lifecycle evidence passes only the lifecycle screen; it does not establish source confidence, technical fit, risk acceptance, or eligibility.
3. Unknown lifecycle blocks a critical function when a material pre-purchase lifecycle, source, compatibility, or risk gap remains. Physical tests that require receipt or assembly do not create a Stage-A blocker by themselves.
4. Unknown lifecycle may be accepted for a qualifying low-risk commodity part only under the owner-approved one-sensor exception. The lifecycle classification remains unverified, and the exception does not apply to five-channel production.
5. Distributor Active or In stock labels, counts, and family-level lifecycle statements do not override exact-MPN status. Dated counts are snapshots, not live stock confirmation or a reservation.
6. Every line remains subject to owner review. A REVIEW disposition grants no order, substitution, assembly, or energization authority.

The supplier observations below retain the dates and availability confidence recorded in sections 2 through 6. Prices and counts are historical snapshots, not current-stock claims or reservations. Supplier confidence describes that evidence, not manufacturer lifecycle.

| Exact MPN / references | Function and criticality | Manufacturer lifecycle | Dated supplier evidence and confidence | Stage A: owner purchase review | Stage B: receipt, assembly, first power | Stage C: characterization and qualification |
| --- | --- | --- | --- | --- | --- | --- |
| OPA320AIDBVR / U1 optical | Critical: TIA gain, stability, and analog signal quality | TI exact code ACTIVE / In Production | DigiKey snapshot 2026-11-02 UTC: $2.88; 705 shown. Dated, not live confirmed. | REVIEW — eligible for owner purchase review only. Exact TI lifecycle evidence is present; the owner still reviews this exact line. | Verify DBV-5 identity and orientation, bypass placement, and assembly. | Measure TIA stability, noise, saturation, and pulse response; verify the actual Rf and Cf values. |
| TLV3501AIDBVR / U2 optical | Critical: comparator threshold and capture edge timing | TI exact code ACTIVE | DigiKey snapshot 2026-11-02 UTC: $4.92; 24,779 shown. Dated, not live confirmed. | REVIEW — eligible for owner purchase review only. Exact TI lifecycle evidence is present; review the documented 3.3 V use. | Verify DBV-6 pinout, grounded SHDN, and 3.3 V output. Keep the output disconnected from Teensy GPIO until these checks pass. | Measure thresholds, output loading, and jitter. |
| SFH 203 / D1 optical | Critical: optical input and measured transition | ams OSRAM Full Production for order code Q62702P0955 | DigiKey snapshot 2026-11-02 UTC: $0.84; 2,056 shown. Dated, not live confirmed. | REVIEW — eligible for owner purchase review only for exact order code Q62702P0955. SFH 203 P remains prohibited. | Verify code, package orientation, polarity, and fit. | Measure photocurrent and dark/bright response; verify the 40-degree boot. |
| RC0603FR-0718KL / R1 optical | Critical: upper leg of the optical VREF divider | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 321,535 shown; 24-week lead. Dated supplier evidence. | REVIEW — eligible only under the owner-approved one-sensor commodity-passive exception; exact-MPN lifecycle remains unverified. | Verify exact value, package, and every listed placement. | Check divider performance, threshold behavior, and usable range. |
| ERJ-3EKF1002V / R2 optical | Critical: lower leg of the optical VREF divider | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 744,247 shown; 15-week lead. Dated supplier evidence. | REVIEW — eligible only under the owner-approved one-sensor commodity-passive exception; exact-MPN lifecycle remains unverified. | Verify exact value, package, and every listed placement. | Check divider performance, threshold behavior, and usable range. |
| RC0603FR-0747KL / R3 optical and R3 power | Critical: TIA feedback and USB VBUS pull-down | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 519,895 shown; 24-week lead. Dated supplier evidence. | REVIEW — eligible only under the owner-approved one-sensor commodity-passive exception; exact-MPN lifecycle remains unverified. | Verify exact value, package, and both optical and power-board placements. | Check TIA performance and the VBUS pull-down behavior within the one-sensor design. |
| RC0603FR-07100RL / R5 optical | Critical: CMP_OUT-to-EDGE capture path | Exact-MPN lifecycle unverified | Conflicting dated snapshots: Newark indexed captures on 2026-11-01 and 2026-11-02 UTC; DigiKey showed 0 and 5,000 expected 2026-11-09. Newark CT MOQ 10. Low availability confidence; none is live confirmed. | REVIEW — conditional on owner review under the approved one-sensor commodity-passive exception. No independent design-stage blocker is evidenced. The dated availability observations remain low-confidence; exact-MPN lifecycle remains unverified. Before purchase, verify fresh exact-MPN on-hand stock, MOQ, checkout terms, and seller identity. Do not substitute. | Verify the exact part and CMP_OUT-to-EDGE placement. | Measure edge loading, rise and fall behavior, and emissions. |
| RC0603FR-071KL / R6 optical, R1 and R2 power | Critical: analog monitor, USB VBUS series path, and LED series path | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 1,967,446 shown; 24-week lead. Dated supplier evidence. | REVIEW — eligible only under the owner-approved one-sensor commodity-passive exception; exact-MPN lifecycle remains unverified. | Verify exact value, package, and all monitor, VBUS, and LED placements. | Check monitor loading, VBUS behavior, and LED current within the one-sensor design. |
| RC0603FR-071ML / R7 optical | Critical: threshold-to-VREF fallback bias | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 1,864,870 shown; 24-week lead. Dated supplier evidence. | REVIEW — eligible only under the owner-approved one-sensor commodity-passive exception; exact-MPN lifecycle remains unverified. | Verify exact value, package, and threshold-bias placement. | Check threshold performance and usable range. |
| CL10A105KP8NNNC / C1 and C4 optical | Critical: VREF and OPA320 supply decoupling | Exact packaged C suffix unverified; Samsung Mass Production applies to normalized base code CL10A105KP8NNN only | DigiKey snapshot 2026-11-01: 269,185 shown; 61-week lead. Dated supplier evidence. | HOLD — Stage A is blocked because lifecycle for the final C suffix and its exact voltage and dielectric ratings are not evidenced. Confirm the exact suffix rating before review. | Verify exact identity and C1/C4 placement. | Measure rail stability. |
| CC0603CRNPO9BN2R2 / C2 optical | Critical: TIA feedback capacitance and stability | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 69,116 shown; 16-week lead. Dated supplier evidence. | REVIEW — eligible only under the owner-approved one-sensor commodity-passive exception; exact-MPN lifecycle remains unverified. | Verify exact 2.2 pF C0G value, package, and SUM-to-TIA_OUT placement. | Check TIA stability, noise, and pulse response with the actual feedback network. |
| KGM15BR71H104KT / C3, C5 optical; C1 power | Critical: analog IC and USB buffer decoupling | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01 showed 0; 4,000 expected 2027-05-17; 15-week lead. Low availability confidence; not a reservation. | HOLD — Stage A is blocked by exact-MPN DigiKey zero/incoming availability, a Mouser page returning HTTP 403, and unverified lifecycle. Recheck the exact source or accept the wait or pack; no KM substitution. | Verify exact identity and bypass placement. | Measure rail noise and stability. |
| CL10A225KP8NNNC / C6 optical | Critical: comparator supply decoupling | Exact packaged C suffix unverified; Samsung Mass Production applies to normalized base code CL10A225KP8NNN only | DigiKey snapshot 2026-11-01: 147,544 shown; 61-week lead. Dated supplier evidence. | HOLD — Stage A is blocked because lifecycle for the final C suffix and its exact voltage and dielectric ratings are not evidenced. Confirm the exact suffix rating before review. | Verify exact identity and C6 placement. | Measure comparator-rail stability. |
| 3296W-1-103LF / RV1 optical | Critical: comparator threshold adjustment | Exact-MPN lifecycle unverified | DigiKey 2026-11-01 availability snapshot: 30,067 shown; 7-week lead. Price snapshot $2.53 dated 2026-11-02 UTC; not live confirmed. | HOLD — Stage A is blocked because lifecycle and fit of the routed footprint to the trimmer terminals are not evidenced. Resolve fit and risk before review. | Verify identity, terminal fit, adjustment direction, and range. | Calibrate the threshold. |
| MF-R050 / F1 power | Critical: input fault protection | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 10,109 shown; 4-week lead. Dated supplier evidence. | HOLD — Stage A is blocked by unverified lifecycle and an unresolved load/fault budget. This slow PTC is not a first-power current limiter; resolve the budget and independent current limiting. | Verify placement. First power requires a separate reviewed, current-limited source. | Check protection coordination, load, and temperature. |
| GST18U05-P1J / EXT2 | Critical: external 5 V power | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 141 shown; 17-week lead. Dated supplier evidence. | HOLD — Stage A is blocked by unverified exact-MPN lifecycle and unresolved load fit. Fixed 5 V/3 A output does not provide an adjustable first-power limit; do not use it for first power. | Verify nameplate and polarity. | Measure the loaded rail only after safe bring-up. |
| SN74LVC1G17DBVRG4 / U1 power/USB | Critical: VBUS sensing and partial-power-down boundary | TI exact code ACTIVE | DigiKey snapshot 2026-11-02 UTC: $0.71; 2,102 shown. Dated, not live confirmed. | REVIEW — eligible for owner purchase review only in the documented 3.3 V supply and resistor context. Never connect raw VBUS to a GPIO. | Check isolated VBUS and backfeed behavior. | Check hot-plug and partial-power host behavior. |
| Pololu 5383 / M1 power | Critical: reverse-polarity and reverse-current protection | Manufacturer product page: Active and Preferred | Dated direct listing 2026-11-02 UTC: $4.95; no stock count; backorders allowed. No live inventory confirmation. | REVIEW — eligible for owner purchase review only for reverse-polarity and reverse-current protection. It does not limit current, isolate USB, or protect against overvoltage. Recheck availability before any order decision. | Verify orientation and the protected power path. | Measure voltage drop, loaded behavior, and fault behavior. |
| TSW-104-07-G-S / J1 optical | Critical: EDGE timing signal and analog MON connection | Exact-MPN lifecycle unverified; Samtec lifecycle not established | DigiKey snapshot 2026-11-01: 3,224 shown; 2-week lead. Dated supplier evidence. | HOLD — Stage A is blocked because lifecycle and EDGE/MON contact-current ratings are not evidenced. Verify the rating before review. | Verify pin mapping, retention, and continuity. | Check edge and MON behavior, including cable effects. |
| TSW-102-07-G-S / J2 power and J5 LED | Critical, using the more stringent J2 protected 5V_SAFE/GND role | Exact-MPN lifecycle unverified; Samtec lifecycle not established | DigiKey snapshot 2026-11-01: 73,452 shown; 2-week lead. Dated supplier evidence. | HOLD — Stage A is blocked because lifecycle and J2 contact rating for the load are not evidenced. Resolve the rating before review. | Verify pinout, placement, and continuity. | Measure loaded voltage drop and temperature; check the LED path. |
| TSW-103-07-G-S / J4 power | Critical: VBUS_PRESENT and logic header | Exact-MPN lifecycle unverified; Samtec lifecycle not established | DigiKey snapshot 2026-11-01: 73,433 shown; 2-week lead. Dated supplier evidence. | HOLD — Stage A is blocked because lifecycle and VBUS/logic contact ratings are not evidenced. Verify the ratings before review. | Verify pinout and continuity. | Check VBUS detection and partial-power-down behavior. |
| Adafruit 368 / J1 power | Critical: barrel-to-terminal 5 V input | Exact-product lifecycle unverified | Adafruit product observation 2026-11-01: listed in stock; count not shown. Dated, not live confirmed. | HOLD — Stage A is blocked because the adapter's terminal current rating has not been established as adequate for the load. | With power disconnected, verify polarity, wiring, and the PTC path. | Measure voltage drop and temperature. |
| WP7113ID / D1 power | Low risk: slow status LED; not a measurement or timing element | Exact-MPN lifecycle unverified | DigiKey snapshot 2026-11-01: 102,675 shown; 6-week lead. Dated supplier evidence. | REVIEW — eligible under the existing narrow one-sensor slow-status LED exception only. | Verify polarity and current with the listed 1 kOhm resistor. | Use as an indicator or stimulus only; never use it for timing. |
| DEV-20360 / EXT1 | Critical: Teensy controller, USB device, and power boundary | Exact-SKU lifecycle unverified; PJRC evidence supports family production only | SparkFun observation 2026-11-01: listed in stock; count not shown. Dated, not live confirmed. | HOLD — Stage A is blocked because lifecycle for this exact SKU is unverified; PJRC family production evidence does not resolve it. Obtain exact-SKU evidence before review. No manufacturer contact is authorized. | Verify exact SKU and header configuration, VIN/VUSB separation, and current-limited power setup. | Check the target USB device path and measurement behavior. |
| Adafruit 239 / EXT3 | Low risk: low-speed control, LED, and supply breadboard use | Exact-product lifecycle unverified | Adafruit product observation 2026-11-01: listed in stock; count not shown. Dated, not live confirmed. | REVIEW — eligible under the existing narrow one-sensor low-speed breadboard exception only. | Check breadboard condition and wiring. | Use only for low-speed controls or an LED. It cannot qualify the optical input or production fixture. Exclude USB D+/D- and the photodiode/TIA input. |
| Adafruit 5030 / EXT4 | Critical: Android USB host and 5 V role-dependent adapter | Exact-product lifecycle unverified | Adafruit product observation 2026-11-01: listed in stock; count not shown. Dated, not live confirmed. | REVIEW — conditional Stage-A eligibility for the owner-selected Pocket AIR Mini through the documented 5030-to-592-to-Teensy main-device-port topology. Exact-product lifecycle remains unverified. Before checkout, revalidate the exact product and seller, current availability, current specifications, and manufacturer lifecycle status. Runtime host switching, VBUS, retention, interoperability, enumeration, data flow, speed, and power safety remain Stage-B or Stage-C checks. No substitution or purchase approval. | During controlled Stage-B commissioning, verify socket and plug orientation and retention, the main USB device port connection, exclusion of the Teensy host header, host switching, VBUS and backfeed, interoperability, and empirical enumeration after power-safety checks. | Collect USB and timing evidence. |
| Adafruit 592 / EXT5 | Critical: USB data path to Teensy device port | Exact-product lifecycle unverified | Adafruit product observation 2026-11-01: listed in stock; count not shown. Dated, not live confirmed. | REVIEW — conditional Stage-A eligibility for the documented Pocket AIR Mini connector and main USB device-port path. Exact-product lifecycle remains unverified. Before checkout, revalidate the exact product and seller, current availability, current specifications, and manufacturer lifecycle status. Runtime host switching, VBUS, retention, interoperability, enumeration, data flow, speed, and power safety remain Stage-B or Stage-C checks. No substitution or purchase approval. | During controlled Stage-B commissioning, verify the cable data path and retention, the main USB device port connection, exclusion of the Teensy host header, host switching, VBUS and backfeed, interoperability, and empirical enumeration after power-safety checks. | Collect USB and timing evidence. |

The table covers all 27 purchased MPNs. Five have exact manufacturer lifecycle evidence: OPA320AIDBVR, TLV3501AIDBVR, SFH 203 order code Q62702P0955, SN74LVC1G17DBVRG4, and Pololu 5383. The other 22 remain lifecycle-unverified. Samsung Mass Production records apply only to the normalized base codes, not the exact CL10 suffixes. DEV-20360 remains unverified at the exact-SKU level. A supplier listing or verified lifecycle status does not automatically clear any line.

The current status is Stage-A review eligibility only. All Stage-B receipt, assembly, and first-power checks and all Stage-C measurements remain unperformed. The one-sensor exception does not qualify the five-channel production fixture. SFH 203 P remains prohibited. R4, RC0603FR-072M2L, remains DNP with zero planned purchase quantity and unverified lifecycle; it is not one of the 27 purchased MPNs. No replacement MPN is selected or approved. The overall readiness status remains CONDITIONALLY_READY_WITH_OPEN_EVIDENCE. No purchase or first-power action is authorized.
