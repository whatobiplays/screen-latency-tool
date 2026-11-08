// Version-pinned, project-aware KiCad 10 validation on a disposable source copy.
// The authoritative KiCad sources are never passed to kicad-cli.
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const defaultRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
export const expectedIgnoredDrcCategories = Object.freeze([
  'missing_courtyard',
  'track_not_centered_on_via',
  'tuning_profile_track_geometries',
  'footprint_filters_mismatch',
  'footprint_type_mismatch',
].sort());

const necessaryFiles = Object.freeze([
  'hardware/prototype/Proto.kicad_sym',
  'hardware/prototype/optical/one-sensor.kicad_pcb',
  'hardware/prototype/optical/one-sensor.kicad_pro',
  'hardware/prototype/optical/one-sensor.kicad_sch',
  'hardware/prototype/optical/fp-lib-table',
  'hardware/prototype/optical/sym-lib-table',
  'hardware/prototype/optical/ProtoOpto.pretty',
  'hardware/prototype/power-usb/power-usb.kicad_pro',
  'hardware/prototype/power-usb/power-usb.kicad_sch',
  'hardware/prototype/power-usb/sym-lib-table',
]);

function digest(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function fileDigests(root) {
  const digests = new Map();
  function inspect(path, label) {
    if (!existsSync(path)) throw Error(`Required KiCad project input missing: ${label}`);
    if (statSync(path).isDirectory()) {
      for (const name of readdirSync(path).sort()) inspect(join(path, name), join(label, name));
    } else digests.set(label, digest(path));
  }
  for (const path of necessaryFiles) inspect(resolve(root, path), path);
  return digests;
}

function run(command, args, description, cwd) {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
  if (result.error || result.status !== 0) {
    throw Error(`${description} failed (exit ${result.status ?? 'not started'}):\n${result.error?.message ?? ''}\n${result.stdout ?? ''}\n${result.stderr ?? ''}`.trim());
  }
  return `${result.stdout ?? ''}${result.stderr ?? ''}`;
}

function cliPath(override) {
  if (override) return override;
  const bundledMac = '/Applications/KiCad/KiCad.app/Contents/MacOS/kicad-cli';
  return existsSync(bundledMac) ? bundledMac : 'kicad-cli';
}

export function validateKiCad({ root = defaultRoot, cli = cliPath(process.env.KICAD_CLI), expectedVersion = '10.0.8', keepReports = false } = {}) {
  const project = JSON.parse(readFileSync(resolve(root, 'hardware/prototype/optical/one-sensor.kicad_pro'), 'utf8'));
  const severities = project.board?.design_settings?.rule_severities;
  if (!severities || typeof severities !== 'object') throw Error('Missing optical KiCad project DRC rule severities');
  const ignored = Object.keys(severities).filter((key) => severities[key] === 'ignore').sort();
  if (JSON.stringify(ignored) !== JSON.stringify(expectedIgnoredDrcCategories)) {
    throw Error(`DRC ignore configuration changed: expected ${expectedIgnoredDrcCategories.join(', ')}, got ${ignored.join(', ')}`);
  }
  const before = fileDigests(root);
  const version = run(cli, ['version'], 'KiCad CLI version', root).trim();
  const versionToken = version.match(/(?:^|[^0-9.])([0-9]+\.[0-9]+\.[0-9]+(?:[-+.][A-Za-z0-9.-]+)?)(?=$|[^A-Za-z0-9.+-])/)?.[1];
  if (versionToken !== expectedVersion) {
    throw Error(`Expected KiCad ${expectedVersion}, got ${JSON.stringify(version)}; update the pinned validation profile deliberately`);
  }

  const scratch = mkdtempSync(join(tmpdir(), 'screen-latency-kicad-'));
  let passed = false;
  try {
    for (const source of necessaryFiles) {
      const from = resolve(root, source);
      const to = resolve(scratch, source);
      mkdirSync(dirname(to), { recursive: true });
      cpSync(from, to, { recursive: true, force: false, errorOnExist: true });
    }
    const optical = resolve(scratch, 'hardware/prototype/optical');
    const power = resolve(scratch, 'hardware/prototype/power-usb');
    const board = resolve(optical, 'one-sensor.kicad_pcb');
    const opticalReport = resolve(scratch, 'optical-drc-refilled.rpt');
    const opticalErc = resolve(scratch, 'optical-erc.rpt');
    const powerErc = resolve(scratch, 'power-erc.rpt');

    // A refilled run is new evidence; do not claim that its hash reproduces an archived non-refill result.
    run(cli, ['pcb', 'drc', '--severity-all', '--schematic-parity', '--exit-code-violations', '--refill-zones',
      '--format', 'report', '--output', opticalReport, board], 'Optical PCB DRC/parity/refill', root);
    run(cli, ['sch', 'erc', '--severity-error', '--severity-warning', '-o', opticalErc,
      resolve(optical, 'one-sensor.kicad_sch')], 'Optical schematic ERC', root);
    run(cli, ['sch', 'erc', '--severity-error', '--severity-warning', '-o', powerErc,
      resolve(power, 'power-usb.kicad_sch')], 'Power/USB schematic ERC', root);
    run(process.execPath, [resolve(root, 'hardware/prototype/erc-report-gate.mjs'), opticalErc, powerErc],
      'Complete/ERC report gate', root);
    for (const path of [opticalReport, opticalErc, powerErc]) {
      if (!existsSync(path)) throw Error(`KiCad reported success without producing ${basename(path)}`);
    }
    const after = fileDigests(root);
    for (const [path, hash] of before.entries()) {
      if (after.get(path) !== hash) throw Error(`Authoritative KiCad input changed during validation: ${path}`);
    }
    const result = { passed: true, version, ignoredDrcCategories: ignored, inputHashes: Object.fromEntries(before),
      reportHashes: { drc: digest(opticalReport), opticalErc: digest(opticalErc), powerErc: digest(powerErc) },
      preservedSource: true, reportsDirectory: keepReports ? scratch : null };
    passed = true;
    return result;
  } finally {
    if (passed && !keepReports) rmSync(scratch, { recursive: true, force: true });
    if (!passed) console.error(`KiCad failure reports / disposable project retained at ${scratch}`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = validateKiCad({ keepReports: process.env.KICAD_KEEP_REPORTS === '1' });
    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
