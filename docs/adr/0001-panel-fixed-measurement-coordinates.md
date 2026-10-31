# Physical-panel coordinates are authoritative

Status: Accepted (2026-10-29)

Five sensor positions and signed spatial latency gradients are defined relative to the fixed physical display, not to Android's logical content orientation. Record chassis orientation and Android's logical orientation/transform as distinct facts. A logical 90-degree rotation can be implemented without rotating the physical panel's scanout direction, so neither golden fixtures nor the analyzer may assume that a spatial gradient must rotate. The rejected alternative was to reinterpret sensor positions in orientation-relative logical coordinates; that would conflate physical scanout with software transforms and could turn a geometric observation into a false causal claim.

Consequences: comparisons preserve physical sensor identity and positional sign; synthetic reference sessions must include cases where the physical gradient stays fixed despite logical rotation, in addition to any cases where a genuine change is evidenced. The exact coordinate mapping and evidence format belong in the revised subsystem specification.
