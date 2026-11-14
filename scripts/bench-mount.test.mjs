import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { clearanceBudget } from './check-bench-mount.mjs';
const source = JSON.parse(readFileSync(new URL('../hardware/prototype/optical/bench-mount.json', import.meta.url), 'utf8'));
test('proposed gap preserves reserve without claiming physical fit', () => {
  const result = clearanceBudget(source);
  assert.equal(result.nominalGap, 5);
  assert.ok(Math.abs(result.designLowerBound - 2.6) < 1e-9);
  assert.ok(Math.abs(result.indicatedMinimum - 2.1) < 1e-9);
  assert.equal(result.physicalFit, 'UNVERIFIED');
});
test('movement exercised in a minimum-gap measurement is not subtracted twice', () => {
  const result = clearanceBudget(source, ['setup_repeatability', 'fixture_approach', 'cable_approach']);
  assert.equal(result.indicatedMinimum, 0.7);
});
test('a gap that can close fails the design calculation', () => {
  const design = structuredClone(source);
  design.allowances.fixture_approach = 4;
  assert.throws(() => clearanceBudget(design), /consume/);
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
