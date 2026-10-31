# Screen Latency Measurement Glossary

**Document ID:** GLOSSARY-001  
**Status:** Ready for Implementation

This glossary defines measurement and orientation terms used consistently across the fixture, Android application, qualification procedures, and analyzer.

## Display geometry

**Physical-panel coordinates**:
Normalized positions within the active illuminated display rectangle, fixed to the panel's physical reference edges rather than Android's currently rendered orientation.
_Avoid_: Logical screen coordinates, orientation-relative sensor coordinates

**Active display rectangle**:
The physical pixel-emitting display area used to place sensors, excluding non-emitting bezels or chassis geometry.

**Chassis orientation**:
The physical orientation of the handheld body relative to the fixture and laboratory reference axes, independent of Android's requested content orientation.

**Logical display orientation**:
The orientation in which Android presents application content (such as portrait or landscape), which may differ from the physical panel or chassis orientation.

**Physical scanout gradient**:
The signed difference in observed photon-transition times between fixed sensor positions on the panel. A software content rotation does not by itself establish that this gradient must rotate.

## Measurement

**HID-completion-to-photon latency**:
Elapsed time from the fixture's successfully completed measured USB HID IN transaction to the sensor's calibrated display-luminance threshold crossing. It intentionally excludes controller scanning, debounce, and pre-completion USB polling.

**Center T50 latency**:
HID-completion-to-photon latency measured at the physical-panel center using the calibrated 50-percent luminance crossing.

**Same-device rotation effect**:
The difference in center T50 latency between matched logical-orientation conditions on the same device. Attribution of a specific display subsystem as the cause requires independent diagnostic evidence.
_Avoid_: Cross-device rotation overhead

**Practical-effect threshold**:
A predeclared difference magnitude used to distinguish practically meaningful orientation effects from small effects; statistical detectability and practical relevance are separate judgments.

**ABBA orientation comparison**:
A counterbalanced sequence of matched logical portrait, logical landscape, logical landscape, and logical portrait acquisition blocks with sensors fixed to the physical panel, intended to reduce confounding from approximately monotonic time drift.
