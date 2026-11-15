# Approved prototype orders

## 1. Authority and quantities

The [approved basket](https://github.com/whatobiplays/screen-latency-tool/issues/38#issuecomment-6875730408), [G1 approval](https://github.com/whatobiplays/screen-latency-tool/issues/38#issuecomment-6877786990) and [final amendment](https://github.com/whatobiplays/screen-latency-tool/issues/38#issuecomment-6878303558) authorize the exact goods below. The accepted merchandise subtotal is $1,067.01. Routine shipping, taxes, duties, conversion and seller fees need no further ceiling or price approval. BESSEY CM20 ×2 is approved without more clamp measurements. Received installation fit and stability remain G2 checks.

These decisions supersede the earlier delivered-total approval condition and pre-purchase CM20 measurement condition. The [subsequent owner allocation amendment](https://github.com/whatobiplays/screen-latency-tool/issues/38#issuecomment-6888174426) removes the meter and heat-shrink from DigiKey: the owner supplies existing tubing and sources the meter separately. The earlier proposals remain historical evidence. Other substitutions, added quantities and service upgrades require their own authority.

The original packet defines 59 merchandise roles. The twelve current seller CSVs contain 56 purchase lines covering 57 roles; role 40 is allocated to owner-supplied heat-shrink, and role 47 to the owner's separate meter purchase. The core remains **132 purchased units for 33 installed units across 27 identities**; the 99 additional units are the approved inexpensive component spares. Support packages are not multiplied by the PCB order. Adafruit 1606 occurs in two approved roles and is ordered as two single boards, one for the power carrier and one for the source-only fixture. The PCB order is one three-board set, separate from the merchandise CSVs.

Each CSV gives the exact identity, seller code, purchase quantity, package, product URL, dated availability, variant checks and remaining fulfillment checks. A package quantity is the number of packages to buy. Do not reinterpret it as installed pieces or add generic spares. Public stock observations are dated 2026-11-14 UTC and are not reservations. Operational order confirmations and shipment status belong in [Issue #38](https://github.com/whatobiplays/screen-latency-tool/issues/38).

### 1.1. Owner-supplied insulation and separate meter

Role 40's Alpha Wire `F221B1/8 BK100` / DigiKey `A018B-4-ND` four-foot purchase is excluded under the owner amendment. The owner-supplied tubing is an allocation for the insulation requirement, not an assertion of the same MPN or qualified equivalence. At G2, establish its available length, expanded/recovered dimensions, condition and suitability for the actual terminated harness and required duty. Preserve the assembly procedure's joint coverage, insulation and strain-relief requirements. Inadequate material stops that use until corrected; it does not block unrelated purchases.

Role 47 is excluded from both DigiKey order files. The owner chooses the separate retail source. Match its exact model and accessories to the selected MM420 requirement or apply a separately reviewed owner amendment before use. Issue #38 owns the offer/model decision, checkout status and confirmation. An Amazon ASIN or a similar meter title does not establish model equivalence.

## 2. Seller checkout instructions

| Seller / manifest | Exact checkout action | Dated evidence and order preflight |
| --- | --- | --- |
| [DigiKey](digikey.csv), 35 lines | Open [cart](https://www.digikey.com/ordering/shoppingcart?lang=en) and inspect existing contents before adding anything. For an empty cart, import [fast-add CSV](digikey-fastadd.csv), mapping Quantity, DigiKey Part Number and Customer Reference; or paste quantity/comma/part-number lines into Bulk Add. | The retained 35-line subset has 33 Immediate lines in the dated source observation. The red and black Remington 25-ft wire units show approximately eight days, shipped by Remington. Verify actual address serviceability and dispatch at checkout. |
| [Mouser](mouser.csv), 1 line | Use `595-OPA320AIDBVR` ×1, cut tape. Inspect the [cart](https://www.mouser.com/en/Cart/) before adding anything. | The 2026-11-14 browser observation resolved the earlier retrieval timeout: stock 94, minimum/multiple 1. Verify actual destination acceptance at checkout. |
| [Newark](newark.csv), 1 line | Add `98K7334` quantity 25, loose cut tape. Do not select `75X0141` full reel or `98K7334RL` re-reel. | 280,297 shown, minimum/multiple 1; UK stock via Newark US, displayed 4–6 business days. Confirm address acceptance at checkout. |
| [TME](tme.csv), 1 line | Add `B4B-PH-K-S` quantity 1 using the corrected exact product URL in the manifest. | 41,918 shown, minimum/multiple 1. The manufacturer's 1,000-piece carton is not the seller's individual order minimum. Confirm ship-to acceptance. |
| [SparkFun](sparkfun.csv), 1 line | Add `DEV-20360` quantity 1, Teensy 4.1 **without Ethernet, with headers**. | Exact offer in stock. Do not select the Ethernet variant. Actual received hardware revision and U4 marking remain G2 evidence. The two pigtail pairs stay in DigiKey's order. |
| [Adafruit](adafruit.csv), 5 order lines | Add product IDs `3878` ×1, `1606` ×2, `1230` ×1, `1311` ×1 and `3299` ×1. | Exact offers in stock; 38 of item 1606 shown. These are six approved role rows consolidated to five order lines. Confirm quantities and destination at checkout. |
| [Pololu](pololu.csv), 1 line | Add item `5383` quantity 1. | 105 shown. Preserve this selected module; an unavailable exact offer requires an owner-reviewed amendment, not a silent substitute. |
| [Comet](comet.csv), 2 lines | Add `5521133` / `XT30U-F.G.Y` ×1 and `5521134` / `XT30U-M.G.Y` ×1. Use [Comet login](https://store.comet.bg/en/Login/) when required. Do not submit this seller’s order until US destination acceptance and single-item minimum/multiple are verified. | Sofia stock 1,078 female / 204 male; Plovdiv zero. Generic foreign-delivery terms do not establish US fulfillment. Only Comet submission depends on those checks; independent seller orders can proceed. |
| [eLabBay](elabbay.csv), 1 line | Select **Bare PCB Kit**, variant `45425432579`, SKU `USB-uBM-uBF-V1A-1`, quantity 1. | 80 shown, minimum/increment 1; public storefront metadata includes US. Do not select the soldered variant. Confirm address at checkout. |
| [RIGOL North America](rigol.csv), 1 line | Select `DHO804` quantity 1, four-channel 70 MHz model with four included PVP3150 probes. Decline model upgrades and extra probes. | Exact model row says In Stock / Add to Cart. Confirm bundle and actual destination. DHO804 selection does not physically qualify any measurement. |
| [Prusa](prusa.csv), 1 line | Add **Prusament PETG Jet Black 1kg**, `IDF17558 / IDS3449`, quantity **2 spools**. | In stock, preparation estimate 4–7 business days. Preserve material, color, diameter and spool size; no printing authorization follows. |
| [Home Depot](home-depot.csv), 6 lines | Add Everbilt `836781` ×6 two-packs, `836761` ×4 two-packs, `836731` ×3 two-packs, `860728` ×5 five-packs, `802448` ×8 five-packs; BESSEY `CM20` ×2. Use exact Internet IDs in the manifest. | Exact models and package sizes confirmed. Localized stock, package allocation and fulfillment require the selected store/address. CM20 needs no additional pre-purchase fit measurement. |

DigiKey's instrument line is **KA3005PE+ ×1**. The meter remains a separate **Klein MM420 ×1** requirement, sourced by the owner; it must not be re-added to DigiKey. Verify the separate offer against the selected model and included leads, thermocouple/adapter and batteries. A different meter requires a reviewed owner amendment. Keep the plus supply model. Verify the delivered mains configuration and included leads during incoming inspection, before any later physical use. The source-only fixture order remains NKK S1A ×1, Bourns PWR221T-30-R100F ×1, Yageo SQP10AJB-1R ×2 and the second Adafruit 1606. Buying them does not complete its guarded construction or qualify S0.

The approved 100 Ω load is `SQP10AJB-100R` ×1 at the original product URL. DigiKey's actual code is **`100W-10-ND`**; the older `10W-100-ND` label was transposed. This corrects the seller code for the same goods.

## 3. Optical PCB upload and checkout

Use the retained Issue #43 archive without regenerating or editing the PCB:

```text
/Users/daniel/.codex/qualifications/screen-latency-tool-issue43/deliverables/e34ec363c875/issue43-fabrication.zip
SHA-256 312b53282a2be2014daf101d50c063a38e5aee1a2211ef96162ce579b5dcf86b
```

The archive CRC and eight file hashes were verified before upload. The [OSH Park upload](https://oshpark.com/uploads/sddlOXkg) detected **50.0 ×44.0 mm**, two layers. The dated preview/cart inspection confirmed **three boards**, standard classic two-layer purple service, with no selected upgrades. Actual order confirmations and live checkout status are maintained in Issue #38.

| Preview layer | Exact archive file |
| --- | --- |
| Board outline | `one-sensor-Edge_Cuts.gbr` |
| Drills | `one-sensor.drl` |
| Top / bottom copper | `one-sensor-F_Cu.gbr` / `one-sensor-B_Cu.gbr` |
| Top / bottom mask | `one-sensor-F_Mask.gbr` / `one-sensor-B_Mask.gbr` |
| Top / bottom silkscreen | `one-sensor-F_Silkscreen.gbr` / `one-sensor-B_Silkscreen.gbr` |

The preview showed a closed rectangular outline, nonblank registered copper/mask/silk renderings, a mirrored bottom view, and the expected through-hole/drill pattern. All eight filenames were assigned to their corresponding layers. Retain the [approved J1 evaluation fit exception and possible silkscreen clipping](../optical/fabrication-review.md); preview approval does not qualify received hole dimensions or connector fit.

For a new checkout, upload the exact archive, verify the dimensions and mappings above, choose one standard three-board set and decline upgrades. Check Issue #38 for existing orders before submitting another order. Provide shipping details and payment only through the seller checkout. Stop an incorrect outline, missing/reassigned layer or drill preview. Record a placed order only after its confirmation is visible.

## 4. Incoming records and separate physical gates

For each completed order, record seller, confirmation reference, date, exact quantities and any dispatch/tracking evidence in Issue #38. Keep addresses, payment details and private account information out of repository and public issue records. Record backorders or partial fulfillment explicitly; never infer purchase from a cart, preview or approval.

On receipt, reconcile each package against its manifest, count exact usable parts, retain seller labels and record damaged, wrong or missing goods. Inspect model/variant, connector polarity and package contents at G2 under the assembly procedure. Actual stand fit, secure clamp bearing, noncontact clearance, wiring and insulation checks remain outstanding.

G1 procurement authorization permits these goods and PCB checkout only. It authorizes no stand printing, assembly, flashing, connection or energization. `INITIAL_CURRENT_LIMIT=UNSET`. G2, S0, G3, G4 and PAC-4 through PAC-6 retain their physical evidence and owner authorization requirements. PAC-1's procurement authority and executable order package are available; receipts and actual fit remain unverified. PAC-3's design-stage fabrication preflight is documented; paid fabrication, received board fit and assembly evidence remain separate obligations. Issue #47 retains its full live PAC wording.
