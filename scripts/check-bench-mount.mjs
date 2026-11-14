// Check a proposed clearance budget. A valid calculation is not a measured fit.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

export function clearanceBudget(design, exercised = []) {
  const a = design.allowances;
  const keys = ['height_setting', 'shroud_manufacturing', 'setup_repeatability',
    'fixture_approach', 'cable_approach', 'dut_approach', 'gap_measurement_uncertainty'];
  // A separate device-motion allowance prevents a rigid stand from hiding
  // upward movement of the display in its cradle. Require it even when zero.
  const geometry = design.geometry;
  for (const key of keys) {
    if (!Number.isFinite(a[key]) || a[key] < 0) throw new Error(`Invalid allowance: ${key}`);
  }
  for (const key of ['board_front_above_glass', 'shroud_projection_from_board', 'maximum_component_and_cable_projection']) {
    if (!Number.isFinite(geometry[key]) || geometry[key] <= 0) throw new Error(`Invalid geometry: ${key}`);
  }
  if (!Number.isFinite(design.positive_reserve) || design.positive_reserve <= 0) throw new Error('A positive reserve is required');
  const movement = ['setup_repeatability', 'fixture_approach', 'cable_approach', 'dut_approach'];
  if (new Set(exercised).size !== exercised.length || exercised.some(key => !movement.includes(key))) {
    throw new Error('Only documented movement allowances may be exercised');
  }
  const projection = Math.max(geometry.shroud_projection_from_board, geometry.maximum_component_and_cable_projection);
  const nominalGap = geometry.board_front_above_glass - projection;
  const designLowerBound = nominalGap - keys.reduce((sum, key) => sum + a[key], 0);
  const indicatedMinimum = a.gap_measurement_uncertainty + design.positive_reserve +
    movement.filter(key => !exercised.includes(key)).reduce((sum, key) => sum + a[key], 0);
  if (designLowerBound < design.positive_reserve) throw new Error('Clearance allocations consume the positive reserve');
  return { units: design.units, nominalGap, designLowerBound, indicatedMinimum,
    reserve: design.positive_reserve, physicalFit: 'UNVERIFIED',
    basis: 'Proposed allocations must be supported by the required evidence before release' };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const path = new URL('../hardware/prototype/optical/bench-mount.json', import.meta.url);
  console.log(JSON.stringify(clearanceBudget(JSON.parse(readFileSync(path, 'utf8'))), null, 2));
}
