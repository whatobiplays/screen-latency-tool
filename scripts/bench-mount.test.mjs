import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { clearanceBudget } from './check-bench-mount.mjs';
const source = JSON.parse(readFileSync(new URL('../hardware/prototype/optical/bench-mount.json', import.meta.url), 'utf8'));
test('proposed gap preserves reserve without claiming physical fit', () => {
  const result = clearanceBudget(source);
  assert.equal(result.nominalGap, 15);
  assert.ok(Math.abs(result.designLowerBound - 6.45) < 1e-9);
  assert.ok(Math.abs(result.indicatedMinimum - 10.25) < 1e-9);
  assert.equal(result.physicalFit, 'UNVERIFIED');
});
test('movement exercised in a minimum-gap measurement is not subtracted twice', () => {
  const result = clearanceBudget(source, ['setup_repeatability', 'fixture_approach', 'cable_approach', 'dut_approach']);
  assert.equal(result.indicatedMinimum, 4);
});
test('a gap that can close fails the design calculation', () => {
  const design = structuredClone(source);
  design.allowances.fixture_approach = 12;
  assert.throws(() => clearanceBudget(design), /consume/);
});
test('upward device motion can consume clearance even with a rigid stand', () => {
  const design = structuredClone(source);
  design.allowances.fixture_approach = 0;
  design.allowances.dut_approach = design.geometry.board_front_above_glass;
  assert.throws(() => clearanceBudget(design), /consume/);
});
test('a measured device-motion minimum is not charged twice', () => {
  const design = structuredClone(source);
  design.allowances.dut_approach = 0.1;
  const staticMinimum = clearanceBudget(design).indicatedMinimum;
  const exercisedMinimum = clearanceBudget(design, ['dut_approach']).indicatedMinimum;
  assert.ok(Math.abs(staticMinimum - exercisedMinimum - 0.1) < 1e-9);
  design.allowances.dut_approach = -0.1;
  assert.throws(() => clearanceBudget(design), /Invalid allowance: dut_approach/);
  delete design.allowances.dut_approach;
  assert.throws(() => clearanceBudget(design), /Invalid allowance: dut_approach/);
});
test('unknown, negative and duplicate allowances cannot manufacture clearance', () => {
  for (const value of [-1, null, '0.5']) {
    const design = structuredClone(source);
    design.allowances.cable_approach = value;
    assert.throws(() => clearanceBudget(design), /Invalid/);
  }
  assert.throws(() => clearanceBudget(source, ['shroud_manufacturing']), /documented/);
  assert.throws(() => clearanceBudget(source, ['fixture_approach', 'fixture_approach']), /documented/);
});
