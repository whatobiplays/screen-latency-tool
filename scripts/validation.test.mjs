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

test('Markdown checker accepts CommonMark local links and ignores non-rendered examples', (t) => {
  const root = scratch(t);
  put(root, 'docs/complex.md', '# Install `foo`\n\nRelease Notes\n-------------\n');
  put(root, 'docs/spaced name.md', '# A File\n');
  put(root, 'docs/other#file.md', '# Hash name\n');
  put(root, 'docs/file_(one).md', '# Parentheses\n');
  put(root, 'docs/UPPER.MD', '# Uppercase\n');
  put(root, 'docs/index.md', [
    '# Intro',
    '[Code](complex.md#install-foo)',
    '[Setext](complex.md#release-notes)',
    '[ref]: <spaced name.md> "title"',
    '[Angle](<spaced name.md> "title")',
    '[Hash](other%23file.md#hash-name)',
    '[Percent anchor](complex.md#release%2Dnotes)',
    '[Balanced](file_(one).md#parentheses)',
    '[Escaped](file_\\(one\\).md#parentheses)',
    '[Upper](UPPER.MD#uppercase)',
    '<!--',
    '[Ignored](missing-comment.md)',
    '-->',
    '    [Ignored](missing-indented.md)',
    '```md',
    '[Ignored](missing-fenced.md)',
    '```',
    '`[Ignored](missing-inline.md)`',
  ].join('\n'));
  const result = validateMarkdown(root);
  assert.deepEqual(result.errors, []);
  assert.equal(result.passed, true);
});

test('Markdown checker does not treat encoded fragment delimiters as raw hashes', (t) => {
  const root = scratch(t);
  put(root, 'docs/name#hash.md', '# Hash\n');
  put(root, 'docs/index.md', '[Works](name%23hash.md#hash)\n');
  assert.equal(validateMarkdown(root).passed, true);
  put(root, 'docs/index.md', '[Missing](name%23hash.md#not-found)\n');
  assert.match(validateMarkdown(root).errors.join('\n'), /missing heading/);
});

test('Markdown headings join around HTML comments and quoted titles may contain parentheses', (t) => {
  const root = scratch(t);
  put(root, 'docs/target.md', '# Target\n');
  put(root, 'docs/index.md', [
    '# Join<!-- hidden -->ed',
    '[Heading](#joined)',
    '[Double title](target.md "description ) with paren")',
    "[Single title](target.md 'description ( with paren')",
    '[Title parens](target.md (description))',
  ].join('\n') + '\n');
  assert.deepEqual(validateMarkdown(root).errors, []);
  put(root, 'docs/index.md', '[Missing](missing.md "still ) a title")\n');
  const result = validateMarkdown(root);
  assert.match(result.errors.join('\n'), /missing target/);
  assert.doesNotMatch(result.errors.join('\n'), /Unclosed Markdown link/);
});

const knownBench001 = [
  'Low parts basket, not first-power ready',
  'Low parts basket plus deferred DHO814 what-if',
  'Owner-incremental first-power base',
  'Owner-incremental first-power base plus deferred DHO814 what-if',
  'Owner-incremental six-board hypothetical, DHO814 deferred',
  'Owner-incremental six-board hypothetical plus deferred DHO814 what-if',
  'Full-purchase base, hypothetical',
  'Full-purchase base plus deferred DHO814 what-if',
  'Full-purchase six-board hypothetical, DHO814 included',
];
const knownBench003 = [
  'Low parts basket, not first-power ready',
  'Owner-incremental first-power base',
  'Owner-incremental six-board hypothetical, DHO814 deferred',
  'Owner-incremental first-power base with deferred DHO814 what-if',
  'Owner-incremental six-board hypothetical with deferred DHO814 what-if',
  'Full-purchase base, hypothetical',
  'Full-purchase six-board hypothetical with deferred DHO814 what-if',
];
function bomFixture(root) {
  put(root, 'hardware/prototype/BOM.csv',
    '"group","ref","mpn","qty","unit_usd_snapshot","extended_usd_snapshot","dnp"\n' +
    '"optical sensor","C1","TEST-1","1","1.00","1.00","NO"\n');
  put(root, 'docs/bench/PROTOTYPE-BENCH-BOM.md',
    '| Scenario | Includes | Arithmetic | Known subtotal |\n' +
    '| --- | --- | ---: | ---: |\n' +
    knownBench001.map((name) => `| ${name} | Test | $1.05 + $34.10 = $35.15 | $35.15 |`).join('\n') + '\n');
  const currentRows = knownBench003.map((name) =>
    `| ${name} | $1.05 + $34.10 | $35.15 | $1,064.85 | $1,264.85 |`);
  const sensitivityRows = knownBench003.map((name) =>
    `| ${name} | $35.15 + $0.26 | $35.41 | $1,064.59 | $1,264.59 |`);
  put(root, 'docs/bench/PROTOTYPE-PROCUREMENT-READINESS.md',
    'The generated BOM is **$1.00** and Strategy A is **$1.05**.\n' +
    'The updated Strategy A source snapshot is **$1.05**: $0.25 in non-J2 lines plus the pair. ' +
    'Strategy B and the generated BOM total **$1.00**: $0.20 in non-J2 lines.\n' +
    '| Scenario | Arithmetic | Known subtotal | Remainder from $1,100 target | Remainder from approximately $1,300 ceiling |\n' +
    '| --- | --- | ---: | ---: | ---: |\n' + currentRows.join('\n') + '\n' +
    '\n### Sensitivity\n\n' +
    '| Scenario | Calculation | Sensitivity subtotal | Remainder from $1,100 target | Remainder from approximately $1,300 ceiling |\n' +
    '| --- | --- | ---: | ---: | ---: |\n' + sensitivityRows.join('\n') + '\n');
}

test('BOM checker reconciles active source totals and all budget scenarios', (t) => {
  const root = scratch(t);
  bomFixture(root);
  const result = validateBom(root);
  assert.deepEqual(result.errors, []);
  assert.equal(result.passed, true);
  assert.equal(result.scenarioCount, 23);
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

test('BOM checker rejects malformed thousands-grouping in subtotals and formulas', (t) => {
  const root = scratch(t);
  bomFixture(root);
  const doc = join(root, 'docs/bench/PROTOTYPE-BENCH-BOM.md');
  const baseline = readFileSync(doc, 'utf8');
  for (const malformed of ['$1,2', '$12,34', '$1,23,456']) {
    writeFileSync(doc, baseline.replace(' | $35.15 |', ` | ${malformed} |`));
    assert.match(validateBom(root).errors.join('\n'), /Invalid USD/, malformed);
    writeFileSync(doc, baseline.replace('$1.05 + $34.10', `${malformed} + $34.10`));
    assert.match(validateBom(root).errors.join('\n'), /Invalid USD/, malformed);
  }
});

function kicadFixture(t) {
  const root = scratch(t);
  const expectedCategories = [
    'footprint_filters_mismatch', 'footprint_type_mismatch', 'missing_courtyard',
    'track_not_centered_on_via', 'tuning_profile_track_geometries',
  ];
  assert.deepEqual(expectedIgnoredDrcCategories, expectedCategories);
  const severities = Object.fromEntries(expectedCategories.map((name) => [name, 'ignore']));
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
import {dirname,join,relative,resolve,isAbsolute} from 'node:path';
const args=process.argv.slice(2);
const sourceRoot=${JSON.stringify(root)};
function underRoot(path){const relativePath=relative(sourceRoot,resolve(path));
 return relativePath===''||(!relativePath.startsWith('..')&&!isAbsolute(relativePath));}
if(args[0]==='version'){console.log('10.0.8');process.exit(0)}
const dest=args[args.indexOf(args.includes('--output')?'--output':'-o')+1];
if(args[0]==='pcb'){
  const board=resolve(args.at(-1));
  const project=dirname(board);
  if(underRoot(board))process.exit(2);
  if(!args.includes('--refill-zones')||!args.includes('--schematic-parity')||
     !existsSync(join(project,'one-sensor.kicad_pro'))||
     !existsSync(join(project,'fp-lib-table'))||
     !existsSync(join(project,'ProtoOpto.pretty','Photodiode_SFH203.kicad_mod')))
    process.exit(2);
  writeFileSync(dest, 'DRC test passed\\n');
}else if(args[0]==='sch'){
  const schematic=resolve(args.at(-1));
  if(underRoot(schematic)||!args.includes('--severity-error')||!args.includes('--severity-warning'))process.exit(2);
  if(dest.includes('optical-erc')&&!schematic.endsWith('optical/one-sensor.kicad_sch'))process.exit(2);
  if(dest.includes('power-erc')&&!schematic.endsWith('power-usb/power-usb.kicad_sch'))process.exit(2);
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

test('BOM checker rejects malformed amounts and unsupported arithmetic operators', (t) => {
  const root = scratch(t);
  bomFixture(root);
  const doc = join(root, 'docs/bench/PROTOTYPE-BENCH-BOM.md');
  const baseline = readFileSync(doc, 'utf8');
  for (const [from, to, pattern] of [
    ['$35.15 |', '$35.150 |', /Invalid USD/],
    ['$1.05 + $34.10', '$1.05 * label $34.10', /Unsupported operator/],
    ['$1.05 + $34.10 = $35.15', '$1.05 + $34.10 = $34.15', /Equation result disagrees/],
  ]) {
    writeFileSync(doc, baseline.replace(from, to));
    assert.match(validateBom(root).errors.join('\n'), pattern);
  }
});

test('BOM checker binds high-case basket and detects missing active scenario rows', (t) => {
  const root = scratch(t);
  bomFixture(root);
  const doc = join(root, 'docs/bench/PROTOTYPE-BENCH-BOM.md');
  const baseline = readFileSync(doc, 'utf8');
  const rows = baseline.split('\n');
  const highIndex = rows.findIndex((line) => line.includes('Full-purchase six-board hypothetical, DHO814 included'));
  rows[highIndex] = rows[highIndex].replace('$1.05 + $34.10', '$1.00 + $34.15');
  writeFileSync(doc, rows.join('\n'));
  assert.match(validateBom(root).errors.join('\n'), /high scenario must start/);
  writeFileSync(doc, baseline.replace(/^\| Full-purchase six-board hypothetical, DHO814 included.*\n/m, ''));
  assert.match(validateBom(root).errors.join('\n'), /Missing budget scenario/);
});

test('BOM checker rejects missing budget headroom columns', (t) => {
  const root = scratch(t);
  bomFixture(root);
  const doc = join(root, 'docs/bench/PROTOTYPE-PROCUREMENT-READINESS.md');
  const baseline = readFileSync(doc, 'utf8');
  writeFileSync(doc, baseline.replace(' | Remainder from $1,100 target | Remainder from approximately $1,300 ceiling |', ' |'));
  assert.match(validateBom(root).errors.join('\n'), /missing required headroom columns/);
});

test('KiCad profile refuses suffixed versions of the pinned CLI', (t) => {
  const { root, cli } = kicadFixture(t);
  const script = readFileSync(cli, 'utf8');
  for (const otherVersion of ['10.0.8.1', '10.0.8-rc1', '10.0.9']) {
    writeFileSync(cli, script.replace("console.log('10.0.8')", `console.log('${otherVersion}')`));
    assert.throws(() => validateKiCad({ root, cli }), /Expected KiCad 10\.0\.8/);
  }
});
