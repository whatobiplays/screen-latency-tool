import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync, readFileSync, writeFileSync, chmodSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { createHash } from 'node:crypto';
import { exportOpticalCam, compareCam } from './export-optical-cam.mjs';

const repository = resolve(import.meta.dirname, '..');
const sha = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');

function fixture(t, fault = '') {
  const temporary = mkdtempSync(join(tmpdir(), 'optical-cam-test-'));
  t.after(() => rmSync(temporary, { recursive: true, force: true }));
  const root = join(temporary, 'source');
  mkdirSync(join(root, 'hardware'), { recursive: true });
  cpSync(join(repository, 'hardware/prototype'), join(root, 'hardware/prototype'), { recursive: true });
  mkdirSync(join(root, 'docs/bench'), { recursive: true });
  cpSync(join(repository, 'docs/bench/PROTOTYPE-BENCH-GUIDE.md'), join(root, 'docs/bench/PROTOTYPE-BENCH-GUIDE.md'));
  const cli = join(temporary, 'fake-kicad.cjs');
  writeFileSync(cli, `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path');
const args = process.argv.slice(2), fault = ${JSON.stringify(fault)};
if(args[0] === 'version') { console.log(fault === 'version' ? '10.0.9' : '10.0.8'); process.exit(0); }
const input = args.at(-1), output = args[args.indexOf('--output') + 1];
const ignored = ['missing_courtyard','track_not_centered_on_via','tuning_profile_track_geometries','footprint_filters_mismatch','footprint_type_mismatch'];
const report = {kicad_version:'10.0.8', included_severities:['error','warning','exclusion']};
if(args[0] === 'pcb' && args[1] === 'drc') {
  const project = JSON.parse(fs.readFileSync(input.replace('.kicad_pcb','.kicad_pro')));
  report.ignored_checks = ignored.filter(k => project.board.design_settings.rule_severities[k] === 'ignore').map(key => ({key}));
  report.violations = []; report.unconnected_items = []; report.schematic_parity = [];
  if(args.includes('--save-board') && fault !== 'no-save') fs.appendFileSync(input, '\\n; saved refill snapshot\\n');
  if(!args.includes('--refill-zones') && !fs.readFileSync(input,'utf8').includes('; saved refill snapshot')) process.exit(3);
  if(fault === 'drc') report.violations.push({type:'clearance',severity:'error'});
  if(fault === 'missing-parity') delete report.schematic_parity;
  if(fault === 'source-mutation') fs.appendFileSync(${JSON.stringify(join(root, 'hardware/prototype/optical/one-sensor.kicad_pcb'))},'\\nchanged');
  fs.writeFileSync(output, JSON.stringify(report));
  process.exit(report.violations.length ? 8 : 0);
}
if(args[0] === 'sch' && args[1] === 'erc') {
  report.ignored_checks = ['single_global_label','four_way_junction','simulation_model_issue','footprint_filter'].map(key => ({key}));
  report.sheets = [{path:'/',violations:[]}];
  if(fault === 'filtered-erc') report.included_severities = ['error'];
  fs.writeFileSync(output, JSON.stringify(report)); process.exit(0);
}
if(args[0] === 'sch' && args[1] === 'export') { fs.writeFileSync(output,'%PDF-1.4 fixture'); process.exit(0); }
if(args[0] !== 'pcb' || args[1] !== 'export') process.exit(2);
if(!fs.readFileSync(input,'utf8').includes('; saved refill snapshot')) process.exit(3);
if(args[2] === 'gerbers') {
 fs.mkdirSync(output,{recursive:true});
 for(const [layer,fn] of [['F_Cu','Copper,L1,Top'],['B_Cu','Copper,L2,Bot'],['F_Mask','Soldermask,Top'],['B_Mask','Soldermask,Bot'],['F_Silkscreen','Legend,Top'],['B_Silkscreen','Legend,Bot'],['Edge_Cuts','Profile,NP']]) {
  if(fault === 'missing-layer' && layer === 'B_Mask') continue;
  const functionName = fault === 'wrong-layer' && layer === 'F_Cu' ? 'Copper,L2,Bot' : fn;
  fs.writeFileSync(path.join(output,'one-sensor-'+layer+'.gbr'),'%FSLAX46Y46*%\\n%MOMM*%\\n%TF.CreationDate,2026-11-13T00:00:00Z*%\\n%TF.FileFunction,'+functionName+'*%\\nM02*\\n');
 }
 if(fault === 'export-mutation') fs.appendFileSync(input,'\\nchanged during plot');
} else if(args[2] === 'drill') {
 fs.writeFileSync(path.join(output,'one-sensor.drl'),fault === 'drill' ? 'M48\\nM30\\n' : 'M48\\nMETRIC\\nT1C0.300\\n%\\nG90\\nT1\\nX100.0Y-100.0\\nM30\\n');
} else if(args[2] === 'pdf') {
 fs.writeFileSync(output,'%PDF-1.4 fixture');
} else if(args[2] === 'pos') fs.writeFileSync(output,'Ref,Val,Package,PosX,PosY,Rot,Side\\nU1,OPA320,SOT23,1,2,0,top\\n');
else process.exit(2);
`);
  chmodSync(cli, 0o755);
  return { root, cli, outDir: join(temporary, 'run'), temporary };
}

test('exports the saved and revalidated board while preserving authoritative inputs', (t) => {
  const options = fixture(t);
  const board = join(options.root, 'hardware/prototype/optical/one-sensor.kicad_pcb');
  const before = sha(board);
  const result = exportOpticalCam(options);
  assert.equal(sha(board), before);
  assert.equal(result.inputHashes['hardware/prototype/optical/one-sensor.kicad_pcb'], before);
  const saved = join(options.outDir, result.exportBoard.path);
  assert.match(readFileSync(saved, 'utf8'), /saved refill snapshot/);
  assert.equal(result.exportBoard.sha256, sha(saved));
  assert.notEqual(result.exportBoard.sha256, before);
  assert.equal(result.revalidatedBoardSha256, result.exportBoard.sha256);
  assert.equal(result.exportedBoardSha256, result.exportBoard.sha256);
  assert.equal(result.camFiles.length, 8);
  assert.ok(existsSync(join(options.outDir, 'fabrication.zip')));
  assert.ok(existsSync(join(options.outDir, 'review.zip')));
  assert.ok(existsSync(join(options.outDir, 'SHA256SUMS')));
  assert.ok(existsSync(join(options.outDir, 'review/optical-bom.csv')));
  assert.ok(readFileSync(join(options.outDir, 'review/optical-bom.csv'), 'utf8').includes('"R4"'));
});

for (const [fault, reason] of [
  ['version', /10\.0\.8/], ['no-save', /revalidat|saved/i], ['drc', /DRC/i],
  ['missing-parity', /schematic_parity/], ['filtered-erc', /severit/i],
  ['missing-layer', /B_Mask|layer/i], ['wrong-layer', /FileFunction|layer/i],
  ['drill', /Excellon|drill/i], ['source-mutation', /authoritative|input changed/i],
  ['export-mutation', /snapshot.*changed|board.*changed/i],
]) {
  test(`refuses a releasable package for ${fault}`, (t) => {
    const options = fixture(t, fault);
    assert.throws(() => exportOpticalCam(options), reason);
    assert.equal(existsSync(join(options.outDir, 'fabrication.zip')), false);
  });
}

test('refuses existing destinations and source/output overlap without overwriting files', (t) => {
  const options = fixture(t);
  mkdirSync(options.outDir); writeFileSync(join(options.outDir, 'keep'), 'valuable');
  assert.throws(() => exportOpticalCam(options), /exist/i);
  assert.equal(readFileSync(join(options.outDir, 'keep'), 'utf8'), 'valuable');
  assert.throws(() => exportOpticalCam({ ...options, outDir: join(options.root, 'cam') }), /outside|overlap/i);
});

test('compares repeated CAM exports allowing only recognized creation timestamps', (t) => {
  const options = fixture(t);
  exportOpticalCam(options);
  const second = join(options.temporary, 'second');
  exportOpticalCam({ ...options, outDir: second });
  const file = join(second, 'fabrication/one-sensor-F_Cu.gbr');
  writeFileSync(file, readFileSync(file, 'utf8').replace('2026-11-13T00:00:00Z', '2026-11-14T00:00:00Z'));
  const comparison = compareCam(options.outDir, second);
  assert.equal(comparison.sameArtwork, true);
  assert.deepEqual(comparison.timestampOnly, ['one-sensor-F_Cu.gbr']);
  writeFileSync(file, readFileSync(file, 'utf8').replace('M02*', 'X999Y999D03*\nM02*'));
  assert.equal(compareCam(options.outDir, second).sameArtwork, false);
});

test('comparison rejects a missing layer even if both runs lost that layer', (t) => {
  const options = fixture(t);
  const first = join(options.temporary, 'first'), second = join(options.temporary, 'second');
  mkdirSync(join(first, 'fabrication'), { recursive: true });
  mkdirSync(join(second, 'fabrication'), { recursive: true });
  assert.throws(() => compareCam(first, second), /Missing CAM layer/);
});

test('removes incomplete archives when the second archive command fails', (t) => {
  const options = fixture(t);
  const bin = join(options.temporary, 'bin'); mkdirSync(bin);
  const fakeZip = join(bin, 'zip');
  writeFileSync(fakeZip, '#!/usr/bin/env node\n' +
    'const a=process.argv.slice(2); if(a.some(v=>v.endsWith("review.zip")))process.exit(1);\n' +
    'const r=require("node:child_process").spawnSync("/usr/bin/zip",a,{stdio:"inherit"});process.exit(r.status);\n');
  chmodSync(fakeZip, 0o755);
  const originalPath = process.env.PATH;
  try {
    process.env.PATH = bin + ':' + originalPath;
    assert.throws(() => exportOpticalCam(options), /Review ZIP/);
    assert.equal(existsSync(join(options.outDir, 'fabrication.zip')), false);
    assert.equal(existsSync(join(options.outDir, 'SHA256SUMS')), false);
  } finally { process.env.PATH = originalPath; }
});

for (const failZip of [false, true]) test('removes archives if an input disappears, ZIP failure=' + failZip, (t) => {
  const options = fixture(t);
  const bin = join(options.temporary, 'bin'); mkdirSync(bin);
  const fakeZip = join(bin, 'zip');
  const bom = join(options.root, 'hardware/prototype/BOM.csv');
  writeFileSync(fakeZip, '#!/usr/bin/env node\n' +
    'const a=process.argv.slice(2),r=require("node:child_process").spawnSync("/usr/bin/zip",a,{stdio:"inherit"});\n' +
    'if(r.status===0 && a.some(v=>v.endsWith("review.zip")))require("node:fs").unlinkSync(' + JSON.stringify(bom) + ');\n' +
    'process.exit(' + JSON.stringify(failZip) + ' && a.some(v=>v.endsWith("review.zip")) ? 1 : r.status);\n');
  chmodSync(fakeZip, 0o755);
  const originalPath = process.env.PATH;
  try {
    process.env.PATH = bin + ':' + originalPath;
    assert.throws(() => exportOpticalCam(options), (error) => {
      assert.match(error.message, /authoritative input/i);
      if (failZip) assert.match(error.message, /Review ZIP/);
      return true;
    });
    for (const name of ['fabrication.zip', 'review.zip', 'SHA256SUMS']) {
      assert.equal(existsSync(join(options.outDir, name)), false, name + ' survived a failed verification');
    }
  } finally { process.env.PATH = originalPath; }
});

for (const mode of [0o500, 0o000]) {
test('retains original and cleanup failures with output permissions ' + mode.toString(8), (t) => {
  const options = fixture(t);
  const bin = join(options.temporary, 'bin'); mkdirSync(bin);
  const fakeZip = join(bin, 'zip');
  writeFileSync(fakeZip, '#!/usr/bin/env node\n' +
    'const a=process.argv.slice(2),r=require("node:child_process").spawnSync("/usr/bin/zip",a,{stdio:"inherit"});\n' +
    'if(a.some(v=>v.endsWith("review.zip"))){require("node:fs").chmodSync(' + JSON.stringify(options.outDir) + ',' + mode + ');process.exit(1)}\n' +
    'process.exit(r.status);\n');
  chmodSync(fakeZip, 0o755);
  const originalPath = process.env.PATH;
  try {
    process.env.PATH = bin + ':' + originalPath;
    assert.throws(() => exportOpticalCam(options), (error) => {
      assert.match(error.message, /Review ZIP/);
      assert.match(error.message, /cleanup|remove/i);
      assert.ok(error instanceof AggregateError);
      assert.ok(error.errors.some((cause) => /Review ZIP/.test(cause.message)));
      assert.equal(error.errors.filter((cause) => cause.code === 'EACCES').length, mode === 0 ? 3 : 2);
      return true;
    });
  } finally {
    process.env.PATH = originalPath;
    if (existsSync(options.outDir)) chmodSync(options.outDir, 0o700);
  }
});
}
