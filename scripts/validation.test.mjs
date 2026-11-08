import assert from 'node:assert/strict';
import { chmodSync, existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { validateMarkdown } from './check-markdown.mjs';
import { parseCsv, validateBom } from './check-bom.mjs';
import { expectedIgnoredDrcCategories, validateKiCad } from './check-kicad.mjs';

function scratch(t) {
  const root = mkdtempSync(join(tmpdir(), 'slt-validation-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return root;
}
function put(root, path, body) {
  const output = join(root, path);
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, body);
  return output;
}

test('CSV parser preserves quoted commas and doubled quote characters', () => {
  assert.deepEqual(parseCsv('"name","note"\n"A","one, two and ""three"""\n'), [
    ['name', 'note'], ['A', 'one, two and "three"'],
  ]);
});

test('Markdown link checker handles valid anchors and rejects broken local paths', (t) => {
  const root = scratch(t);
  put(root, 'docs/index.md', '# Start\n[Read](other.md#operating-rules)\n[Current](#start)\n');
  put(root, 'docs/other.md', '# Operating Rules\n');
  assert.equal(validateMarkdown(root).passed, true);
  put(root, 'docs/index.md', '# Start\n[Read](other.md#nonexistent)\n');
  assert.match(validateMarkdown(root).errors.join('\n'), /missing heading/);
  put(root, 'docs/index.md', '# Start\n[Read](missing.md)\n');
  assert.match(validateMarkdown(root).errors.join('\n'), /missing target/);
});

function bomFixture(root) {
  put(root, 'hardware/prototype/BOM.csv',
    '"group","ref","mpn","qty","unit_usd_snapshot","extended_usd_snapshot","dnp"\n' +
    '"optical sensor","C1","TEST-1","1","1.00","1.00","NO"\n');
  put(root, 'docs/bench/PROTOTYPE-BENCH-BOM.md',
    '| Scenario | Includes | Arithmetic | Known subtotal |\n' +
    '| --- | --- | ---: | ---: |\n' +
    '| Full-purchase six-board hypothetical | Test | $1.05 + $34.10 = $35.15 | $35.15 |\n');
  const lines = [];
  for (let i = 0; i < 10; i++) {
    const low = i === 0 ? 'Full-purchase six-board hypothetical' : `Scenario ${i}`;
    lines.push(`| ${low} | $1.05 + $34.10 | $35.15 | $1,064.85 | $1,264.85 |`);
  }
  put(root, 'docs/bench/PROTOTYPE-PROCUREMENT-READINESS.md',
    'The generated BOM is **$1.00** and Strategy A is **$1.05**.\n' +
    'The updated Strategy A source snapshot is **$1.05**: $0.25 in non-J2 lines plus the pair. ' +
    'Strategy B and the generated BOM total **$1.00**: $0.20 in non-J2 lines.\n' +
    '| Scenario | Arithmetic | Known subtotal | Remainder from $1,100 target | Remainder from approximately $1,300 ceiling |\n' +
    '| --- | --- | ---: | ---: | ---: |\n' + lines.join('\n') + '\n');
}

test('BOM checker reconciles active source totals and all budget scenarios', (t) => {
  const root = scratch(t);
  bomFixture(root);
  const result = validateBom(root);
  assert.deepEqual(result.errors, []);
  assert.equal(result.passed, true);
  assert.equal(result.scenarioCount, 11);
});

test('BOM checker fails when a manifest line extension changes without its unit price', (t) => {
  const root = scratch(t);
  bomFixture(root);
  put(root, 'hardware/prototype/BOM.csv',
    '"group","ref","mpn","qty","unit_usd_snapshot","extended_usd_snapshot","dnp"\n' +
    '"optical sensor","C1","TEST-1","1","1.00","1.01","NO"\n');
  const result = validateBom(root);
  assert.equal(result.passed, false);
  assert.match(result.errors.join('\n'), /Incorrect extension|generated subtotal/);
});

function kicadFixture(t) {
  const root = scratch(t);
  const severities = Object.fromEntries(expectedIgnoredDrcCategories.map((name) => [name, 'ignore']));
  put(root, 'hardware/prototype/optical/one-sensor.kicad_pro', JSON.stringify({ board: { design_settings: { rule_severities: severities } } }));
  const files = [
    'hardware/prototype/Proto.kicad_sym', 'hardware/prototype/optical/one-sensor.kicad_pcb',
    'hardware/prototype/optical/one-sensor.kicad_sch', 'hardware/prototype/optical/fp-lib-table',
    'hardware/prototype/optical/sym-lib-table',
    'hardware/prototype/optical/ProtoOpto.pretty/Photodiode_SFH203.kicad_mod',
    'hardware/prototype/power-usb/power-usb.kicad_pro',
    'hardware/prototype/power-usb/power-usb.kicad_sch',
    'hardware/prototype/power-usb/sym-lib-table',
  ];
  for (const path of files) put(root, path, `test source ${path}\n`);
  put(root, 'hardware/prototype/erc-report-gate.mjs',
    'import {readFileSync} from "node:fs";\n' +
    'for(const p of process.argv.slice(2)){if(!readFileSync(p,"utf8").includes("Errors 0  Warnings 0"))process.exit(1)}\n');
  const cli = put(root, 'fake-kicad-cli', `#!/usr/bin/env node
import {existsSync,writeFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
const args=process.argv.slice(2);
if(args[0]==='version'){console.log('10.0.8');process.exit(0)}
const dest=args[args.indexOf(args.includes('--output')?'--output':'-o')+1];
if(args[0]==='pcb'){
  const project=dirname(args.at(-1));
  if(!args.includes('--refill-zones')||!args.includes('--schematic-parity')||
     !existsSync(join(project,'one-sensor.kicad_pro'))||
     !existsSync(join(project,'fp-lib-table'))||
     !existsSync(join(project,'ProtoOpto.pretty','Photodiode_SFH203.kicad_mod')))
    process.exit(2);
  writeFileSync(dest, 'DRC test passed\\n');
}else if(args[0]==='sch'){
  writeFileSync(dest,'ERC report (test, Encoding UTF8)\\nReport includes: Errors, Warnings\\n ** ERC messages: 0  Errors 0  Warnings 0\\n');
}else process.exit(3);
`);
  chmodSync(cli, 0o755);
  return { root, cli };
}

test('KiCad project profile validates a disposable project and never changes the authoritative PCB', (t) => {
  const { root, cli } = kicadFixture(t);
  const board = join(root, 'hardware/prototype/optical/one-sensor.kicad_pcb');
  const before = readFileSync(board);
  const result = validateKiCad({ root, cli });
  assert.equal(result.passed, true);
  assert.equal(result.preservedSource, true);
  assert.deepEqual(readFileSync(board), before);
});

test('KiCad project profile fails closed on changed Ignore categories', (t) => {
  const { root, cli } = kicadFixture(t);
  put(root, 'hardware/prototype/optical/one-sensor.kicad_pro', JSON.stringify({ board: { design_settings: { rule_severities: {} } } }));
  assert.throws(() => validateKiCad({ root, cli }), /DRC ignore configuration changed/);
});
