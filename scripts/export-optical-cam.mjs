// Optical-only CAM preparation. Every KiCad write targets a disposable project.
// Passing these checks prepares files; fabrication and physical use need owner release.
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, realpathSync,
  statSync, unlinkSync, writeFileSync } from 'node:fs';
import { basename, dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { expectedIgnoredDrcCategories } from './check-kicad.mjs';
import { parseCsv } from './check-bom.mjs';

const defaultRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const expectedVersion = '10.0.8';
const approvedBoardSha256 = '3f7ee13263f27410885c2ea0af3558b2fb27d6718b94dacfc55aec67fd4bbe88';
const nativeInputs = [
  'hardware/prototype/Proto.kicad_sym',
  'hardware/prototype/BOM.csv',
  'hardware/prototype/optical/one-sensor.kicad_pcb',
  'hardware/prototype/optical/one-sensor.kicad_pro',
  'hardware/prototype/optical/one-sensor.kicad_sch',
  'hardware/prototype/optical/fp-lib-table',
  'hardware/prototype/optical/sym-lib-table',
  'hardware/prototype/optical/ProtoOpto.pretty',
  'hardware/prototype/power-usb/power-usb.kicad_pro',
  'hardware/prototype/power-usb/power-usb.kicad_sch',
  'hardware/prototype/power-usb/sym-lib-table',
  'hardware/prototype/README.md',
  'hardware/prototype/optical/README.md',
  'docs/bench/PROTOTYPE-BENCH-GUIDE.md',
];
const layers = {
  'F_Cu': 'Copper,L1,Top', 'B_Cu': 'Copper,L2,Bot',
  'F_Mask': 'Soldermask,Top', 'B_Mask': 'Soldermask,Bot',
  'F_Silkscreen': 'Legend,Top', 'B_Silkscreen': 'Legend,Bot', 'Edge_Cuts': 'Profile,NP',
};
const ercIgnored = ['single_global_label', 'four_way_junction', 'simulation_model_issue', 'footprint_filter'].sort();
const hash = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');
const json = (path) => JSON.parse(readFileSync(path, 'utf8'));
const writeJson = (path, value) => writeFileSync(path, JSON.stringify(value, null, 2) + '\n');

function filesBelow(directory) {
  return readdirSync(directory).sort().flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? filesBelow(path) : [path];
  });
}

function inputHashes(root) {
  return Object.fromEntries(nativeInputs.flatMap((name) => {
    const path = join(root, name);
    if (!existsSync(path)) throw Error('Missing authoritative input: ' + name);
    return (statSync(path).isDirectory() ? filesBelow(path) : [path])
      .map((file) => [relative(root, file), hash(file)]);
  }));
}

function below(parent, child) {
  const name = relative(parent, child);
  return name === '' || (!name.startsWith('..' + '/') && name !== '..' && !isAbsolute(name));
}

// Resolve existing ancestors too, so an output symlink cannot write into the source tree.
function canonicalDestination(path) {
  if (existsSync(path)) return realpathSync(path);
  return join(canonicalDestination(dirname(path)), basename(path));
}

function validateReport(path, type, ignored, clean = true) {
  const data = json(path);
  if (data.kicad_version !== expectedVersion) throw Error('Unexpected report KiCad version: ' + path);
  if (!['error', 'warning', 'exclusion'].every((s) => data.included_severities?.includes(s))) {
    throw Error('Incomplete report severities: ' + path);
  }
  const keys = data.ignored_checks?.map((v) => v.key).sort();
  if (JSON.stringify(keys) !== JSON.stringify([...ignored].sort())) throw Error('Unaccounted ignored checks: ' + path);
  let findings;
  if (type === 'drc') {
    for (const key of ['violations', 'unconnected_items', 'schematic_parity']) {
      if (!Array.isArray(data[key])) throw Error('Incomplete DRC report: missing ' + key);
    }
    findings = [...data.violations, ...data.unconnected_items, ...data.schematic_parity];
  } else {
    if (!Array.isArray(data.sheets) || !data.sheets.length ||
        data.sheets.some((sheet) => !Array.isArray(sheet.violations))) throw Error('Incomplete ERC sheets');
    findings = data.sheets.flatMap((sheet) => sheet.violations);
  }
  if (clean && findings.length) throw Error(type.toUpperCase() + ' report contains findings: ' + path);
  return { ignoredChecks: keys, findings: findings.length };
}

function validateArtwork(directory) {
  const names = Object.keys(layers).map((layer) => 'one-sensor-' + layer + '.gbr');
  for (const [layer, expected] of Object.entries(layers)) {
    const path = join(directory, 'one-sensor-' + layer + '.gbr');
    if (!existsSync(path)) throw Error('Missing CAM layer: ' + layer);
    const text = readFileSync(path, 'utf8');
    if (!text.includes('%TF.FileFunction,' + expected + '*%') ||
        !text.includes('%MOMM*%') || !text.includes('%FSLAX46Y46*%') || !text.includes('M02*')) {
      throw Error('Invalid Gerber layer/FileFunction/units: ' + layer);
    }
  }
  const drill = join(directory, 'one-sensor.drl');
  if (!existsSync(drill)) throw Error('Missing Excellon drill file');
  const text = readFileSync(drill, 'utf8');
  if (!/^M48$/m.test(text) || !/^METRIC(?:,|$)/m.test(text) ||
      !/^T\d+C\d/m.test(text) || !/^X[-.\d]+Y[-.\d]+$/m.test(text) || !/^M30$/m.test(text)) {
    throw Error('Invalid Excellon drill header, tools or coordinates');
  }
  names.push('one-sensor.drl');
  if (JSON.stringify(readdirSync(directory).sort()) !== JSON.stringify(names.sort())) {
    throw Error('Unexpected files in fabrication directory');
  }
  return names;
}

// Each stage consumes the same copied project and returns its evidence identity.
function validateSnapshot({ root, review, native, reports, board, run, cli }) {
  cpSync(board, join(review, 'source-board.kicad_pcb'));
  const [header, ...rows] = parseCsv(readFileSync(join(root, 'hardware/prototype/BOM.csv'), 'utf8'));
  const groupColumn = header.indexOf('group');
  if (groupColumn < 0) throw Error('BOM has no group column');
  const opticalRows = rows.filter((row) => row[groupColumn] === 'optical sensor');
  if (opticalRows.length !== 23) throw Error('Expected 23 optical BOM positions including DNP and bare pads');
  const quote = (value) => '"' + value.replaceAll('"', '""') + '"';
  writeFileSync(join(review, 'optical-bom.csv'), [header, ...opticalRows]
    .map((row) => row.map(quote).join(',')).join('\n') + '\n');
  const pro = json(board.replace('.kicad_pcb', '.kicad_pro'));
  const ignored = Object.entries(pro.board.design_settings.rule_severities)
    .filter(([, severity]) => severity === 'ignore').map(([key]) => key).sort();
  if (JSON.stringify(ignored) !== JSON.stringify(expectedIgnoredDrcCategories)) throw Error('DRC ignore configuration changed');
  const drcArgs = ['pcb', 'drc', '--severity-all', '--severity-exclusions', '--schematic-parity',
    '--all-track-errors', '--exit-code-violations', '--format', 'json'];
  const first = join(reports, 'optical-drc-saved.json');
  run(cli, [...drcArgs, '--refill-zones', '--save-board', '--output', first, board], 'DRC saved zone refill', native);
  validateReport(first, 'drc', ignored);
  const savedHash = hash(board);
  const second = join(reports, 'optical-drc-revalidated.json');
  run(cli, [...drcArgs, '--output', second, board], 'Saved-board revalidation DRC', native);
  validateReport(second, 'drc', ignored);
  if (hash(board) !== savedHash) throw Error('Saved board snapshot changed during revalidation');
  for (const [name, relativePath] of [
    ['optical', 'optical/one-sensor.kicad_sch'], ['power-usb', 'power-usb/power-usb.kicad_sch'],
  ]) {
    const path = join(reports, name + '-erc.json');
    run(cli, ['sch', 'erc', '--severity-all', '--severity-exclusions', '--exit-code-violations',
      '--format', 'json', '--output', path, join(native, 'hardware/prototype', relativePath)], name + ' ERC', native);
    validateReport(path, 'erc', ercIgnored);
  }

  // Check saved copper under a stricter profile, without another refill or source edit.
  const diagnostic = join(review, 'diagnostic');
  cpSync(native, diagnostic, { recursive: true });
  const diagnosticBoard = join(diagnostic, relative(native, board));
  const projectPath = diagnosticBoard.replace('.kicad_pcb', '.kicad_pro');
  const project = json(projectPath);
  for (const key of ignored) project.board.design_settings.rule_severities[key] = 'warning';
  const floors = { min_clearance: 0.1524, min_connection: 0.1524, min_track_width: 0.1524,
    min_via_annular_width: 0.127, min_via_diameter: 0.508, min_through_hole_diameter: 0.254,
    min_copper_edge_clearance: 0.381 };
  for (const [key, value] of Object.entries(floors)) {
    project.board.design_settings.rules[key] = Math.max(project.board.design_settings.rules[key] ?? 0, value);
  }
  writeJson(projectPath, project);
  const diagnosticReport = join(reports, 'oshpark-diagnostic-drc.json');
  run(cli, [...drcArgs, '--output', diagnosticReport, diagnosticBoard], 'OSH Park diagnostic DRC', diagnostic, [0, 8]);
  const diagnosticSummary = validateReport(diagnosticReport, 'drc', [], false);
  if (hash(diagnosticBoard) !== savedHash) throw Error('Diagnostic board snapshot changed');
  return { savedHash, ignored, projectPath, diagnosticSummary };
}

function exportSnapshot({ native, board, review, cam, run, cli }) {
  run(cli, ['pcb', 'export', 'gerbers', '--layers', 'F.Cu,B.Cu,F.Mask,B.Mask,F.SilkS,B.SilkS,Edge.Cuts',
    '--common-layers', '', '--precision', '6', '--no-protel-ext', '--subtract-soldermask',
    '--output', cam + '/', board], 'Gerber X2 export', native);
  run(cli, ['pcb', 'export', 'drill', '--format', 'excellon', '--drill-origin', 'absolute',
    '--excellon-units', 'mm', '--excellon-zeros-format', 'decimal', '--excellon-oval-format', 'alternate',
    '--output', cam + '/', board], 'Excellon export', native);
  const job = join(cam, 'one-sensor-job.gbrjob');
  if (existsSync(job)) { cpSync(job, join(review, basename(job))); unlinkSync(job); }
  const camFiles = validateArtwork(cam);
  run(cli, ['sch', 'export', 'pdf', '--output', join(review, 'optical-schematic.pdf'),
    join(native, 'hardware/prototype/optical/one-sensor.kicad_sch')], 'Schematic PDF', native);
  for (const side of ['front', 'back']) {
    const layerList = side === 'front' ? 'F.Fab,F.SilkS,Edge.Cuts' : 'B.Fab,B.SilkS,Edge.Cuts';
    run(cli, ['pcb', 'export', 'pdf', '--mode-single', '--layers', layerList,
      '--sketch-pads-on-fab-layers', '--crossout-DNP-footprints-on-fab-layers',
      ...(side === 'back' ? ['--mirror'] : []),
      '--output', join(review, 'assembly-' + side + '.pdf'), board], 'Assembly ' + side + ' PDF', native);
  }
  run(cli, ['pcb', 'export', 'pos', '--format', 'csv', '--units', 'mm',
    '--output', join(review, 'reference-positions.csv'), board], 'Reference positions', native);
  for (const name of ['optical-schematic.pdf', 'assembly-front.pdf', 'assembly-back.pdf', 'reference-positions.csv']) {
    if (!existsSync(join(review, name)) || !statSync(join(review, name)).size) throw Error('Missing review output: ' + name);
  }
  return camFiles;
}

function packageSnapshot({ root, outDir, review, cam, board, before, run, commands, savedHash, ignored, projectPath, diagnosticSummary, camFiles }) {
  writeJson(join(review, 'commands.json'), commands);
  const git = (args) => {
    const result = spawnSync('git', ['-C', root, ...args], { encoding: 'utf8' });
    return result.status === 0 ? result.stdout.trim() : null;
  };
  const manifest = {
    formatVersion: 1, purpose: 'provisional one-sensor evaluation PCB; no fabrication or physical-use authorization',
    source: { commit: git(['rev-parse', 'HEAD']), branch: git(['branch', '--show-current']),
      status: git(['status', '--porcelain=v1', '--untracked-files=all']) },
    tools: { kicad: expectedVersion, node: process.version, exporterSha256: hash(fileURLToPath(import.meta.url)) },
    inputHashes: before, preservedSource: true,
    exportBoard: { path: relative(outDir, board), sha256: savedHash },
    revalidatedBoardSha256: savedHash, exportedBoardSha256: savedHash,
    ignoredDrcCategories: ignored, ignoredErcCategories: ercIgnored,
    diagnostic: { profile: relative(outDir, projectPath), ...diagnosticSummary, disposition: 'manual review required' },
    construction: { copperLayers: 2, nominalThicknessMm: 1.6, outlineMm: [50, 44] },
    camFiles, settings: { gerber: 'X2, mm 4.6, absolute origin, no mirroring, no common layers',
      drill: 'Excellon, mm decimal, absolute origin, alternate slots, combined PTH/NPTH',
      paste: 'omitted: hand soldering without stencil', npth: 'current source has no NPTH' },
    files: Object.fromEntries([...filesBelow(review), ...filesBelow(cam)]
      .map((path) => [relative(outDir, path), { sha256: hash(path), bytes: statSync(path).size }])),
  };
  writeJson(join(outDir, 'manifest.json'), manifest);
  run('zip', ['-X', '-q', '-r', join(outDir, 'fabrication.zip'), '.'], 'Fabrication ZIP', cam);
  run('zip', ['-X', '-q', '-r', join(outDir, 'review.zip'), 'manifest.json', 'review'], 'Review ZIP', outDir);
  writeFileSync(join(outDir, 'SHA256SUMS'), ['manifest.json', 'fabrication.zip', 'review.zip']
    .map((name) => hash(join(outDir, name)) + '  ' + name).join('\n') + '\n');
  return manifest;
}

/**
 * Export and package the approved one-sensor design. outDir must be new and outside root.
 * Nominal ERC/DRC failures stop packaging. Diagnostic rule findings are retained
 * for human disposition and never interpreted as fabrication approval.
 */
export function exportOpticalCam({ root = defaultRoot, outDir,
  cli = process.env.KICAD_CLI || '/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli' } = {}) {
  root = realpathSync(root);
  if (!outDir) throw Error('Specify --out-dir outside the source checkout');
  outDir = canonicalDestination(resolve(outDir));
  if (below(root, outDir) || below(outDir, root)) throw Error('Output must be outside and not overlap the source');
  if (existsSync(outDir)) throw Error('Output directory already exists: ' + outDir);
  const before = inputHashes(root);
  if (before['hardware/prototype/optical/one-sensor.kicad_pcb'] !== approvedBoardSha256) {
    throw Error('Native board differs from the approved geometry; review the new design before updating its pin');
  }
  const commands = [];
  const portable = (value) => value.replaceAll(outDir, '${RUN}').replaceAll(root, '${SOURCE}');
  function run(command, args, description, cwd = root, allowed = [0]) {
    const result = spawnSync(command, args, { cwd, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
    commands.push({ command, args: args.map(portable), cwd: portable(cwd), description,
      exitCode: result.status, stdout: portable(result.stdout ?? ''), stderr: portable(result.stderr ?? '') });
    if (result.error || !allowed.includes(result.status)) {
      throw Error(description + ' failed (exit ' + result.status + '): ' +
        (result.error?.message ?? '') + (result.stdout ?? '') + (result.stderr ?? ''));
    }
    return (result.stdout ?? '').trim();
  }
  if (run(cli, ['version'], 'KiCad version') !== expectedVersion) throw Error('Expected KiCad 10.0.8');
  mkdirSync(outDir, { recursive: true });
  const review = join(outDir, 'review'), native = join(review, 'native');
  const reports = join(review, 'reports'), cam = join(outDir, 'fabrication');
  for (const directory of [native, reports, cam]) mkdirSync(directory, { recursive: true });
  const board = join(native, 'hardware/prototype/optical/one-sensor.kicad_pcb');
  let complete = false;
  let operationError;
  try {
    for (const name of nativeInputs) {
      mkdirSync(dirname(join(native, name)), { recursive: true });
      cpSync(join(root, name), join(native, name), { recursive: true, errorOnExist: true, force: false });
    }
    const evidence = validateSnapshot({ root, review, native, reports, board, run, cli });
    const { savedHash } = evidence;
    const camFiles = exportSnapshot({ native, board, review, cam, run, cli });
    if (hash(board) !== savedHash) throw Error('Saved board snapshot changed during export');
    if (JSON.stringify(inputHashes(root)) !== JSON.stringify(before)) throw Error('Authoritative input changed during export');
    const manifest = packageSnapshot({ root, outDir, review, cam, board, before, run, commands, camFiles, ...evidence });
    complete = true;
    return manifest;
  } catch (error) {
    operationError = error;
    throw error;
  } finally {
    // A missing or unreadable input is also failed verification. Clean archives
    // before reporting it, rather than letting the hash exception bypass cleanup.
    let preserved = false;
    let verificationError;
    try { preserved = JSON.stringify(inputHashes(root)) === JSON.stringify(before); }
    catch (error) { verificationError = error; }
    const cleanupErrors = [];
    if (!complete || !preserved) {
      for (const name of ['fabrication.zip', 'review.zip', 'SHA256SUMS']) {
        const path = join(outDir, name);
        try { unlinkSync(path); }
        catch (error) { if (error.code !== 'ENOENT') cleanupErrors.push(error); }
      }
    }
    if (cleanupErrors.length) {
      const original = operationError ? '; original failure: ' + operationError.message : '';
      throw new AggregateError([operationError, verificationError, ...cleanupErrors].filter(Boolean),
        'Failed to remove output archives; remaining artifacts are not releasable' + original);
    }
    if (!preserved) {
      const original = operationError ? '; original failure: ' + operationError.message : '';
      throw new AggregateError([verificationError, operationError].filter(Boolean),
        'Authoritative input changed or became unavailable during CAM workflow' + original);
    }
  }
}

function withoutCreationTime(text) {
  return text
    .replace(/^(%TF\.CreationDate,)\d{4}-\d\d-\d\dT[0-9:+Z.-]+(\*%)$/gm, '$1<TIMESTAMP>$2')
    .replace(/^(G04 Created by KiCad \(PCBNEW [^)]+\) date )\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\*$/gm, '$1<TIMESTAMP>*')
    .replace(/^(; DRILL file KiCad [\d.]+ date )\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d$/gm, '$1<TIMESTAMP>')
    .replace(/^(; #@! TF\.CreationDate,)\d{4}-\d\d-\d\dT[0-9:+Z.-]+$/gm, '$1<TIMESTAMP>');
}

/** Compare fabrication payloads directly, without trusting recorded manifest hashes. */
export function compareCam(first, second) {
  const a = join(resolve(first), 'fabrication'), b = join(resolve(second), 'fabrication');
  validateArtwork(a); validateArtwork(b);
  const left = readdirSync(a).sort(), right = readdirSync(b).sort();
  const timestampOnly = [], different = [];
  for (const name of new Set([...left, ...right])) {
    if (!left.includes(name) || !right.includes(name)) { different.push(name); continue; }
    const before = readFileSync(join(a, name), 'utf8'), after = readFileSync(join(b, name), 'utf8');
    if (before === after) continue;
    if (withoutCreationTime(before) === withoutCreationTime(after)) timestampOnly.push(name);
    else different.push(name);
  }
  return { sameArtwork: different.length === 0, timestampOnly, different };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2);
    if (args.length === 3 && args[0] === '--compare') {
      const result = compareCam(args[1], args[2]); console.log(JSON.stringify(result, null, 2));
      if (!result.sameArtwork) process.exitCode = 1;
    } else if (args.length === 2 && args[0] === '--out-dir') {
      const result = exportOpticalCam({ outDir: args[1] });
      console.log(JSON.stringify({ output: resolve(args[1]), source: result.source,
        exportBoard: result.exportBoard, preservedSource: result.preservedSource }, null, 2));
    } else throw Error('Usage: node scripts/export-optical-cam.mjs --out-dir NEW_EXTERNAL_DIRECTORY | --compare RUN1 RUN2');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
