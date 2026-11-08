// Reconcile the structured prototype manifest with the active budget scenarios.
// This is arithmetic verification, not a live price, stock or procurement check.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));

export function parseCsv(source) {
  const records = [];
  let record = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    if (char === '"') {
      if (quoted && source[i + 1] === '"') { field += '"'; i++; }
      else if (quoted) quoted = false;
      else if (!field) quoted = true;
      else throw Error(`Unexpected quote at character ${i}`);
    } else if (!quoted && (char === ',' || char === '\n' || char === '\r')) {
      if (char === '\r' && source[i + 1] === '\n') i++;
      record.push(field);
      field = '';
      if (char !== ',') {
        if (record.some((value) => value !== '')) records.push(record);
        record = [];
      }
    } else field += char;
  }
  if (quoted) throw Error('Unterminated CSV quoted field');
  if (field || record.length) {
    record.push(field);
    if (record.some((value) => value !== '')) records.push(record);
  }
  return records;
}

function currencyMicros(amount, label) {
  if (!/^\d+(?:\.\d{1,6})?$/.test(amount)) throw Error(`Invalid USD ${label}: ${amount}`);
  const [whole, fraction = ''] = amount.split('.');
  return Number(whole) * 1_000_000 + Number(fraction.padEnd(6, '0'));
}

function cents(amount, label = 'amount') {
  const micros = currencyMicros(amount.replaceAll(',', ''), label);
  if (micros % 10_000) throw Error(`Not an exact cent amount (${label}): ${amount}`);
  return micros / 10_000;
}

function moneyFrom(text, label) {
  const match = text?.match(/\$([\d,]+(?:\.\d{1,2})?)/);
  if (!match) throw Error(`Missing USD in ${label}: ${text}`);
  return cents(match[1], label);
}

function sumExpression(text, label) {
  const expr = text.split('=')[0];
  const tokens = [...expr.matchAll(/([+-])?\s*\$([\d,]+(?:\.\d{1,2})?)/g)];
  if (!tokens.length) throw Error(`No arithmetic amounts in ${label}`);
  // The scenario arithmetic columns use additions. Refuse an unsupported operator.
  const stripped = expr.replace(/\$[\d,]+(?:\.\d{1,2})?/g, '').replace(/[+\s]/g, '');
  if (stripped && !/[A-Za-z()/.]/.test(stripped)) {
    throw Error(`Unrecognized scenario arithmetic in ${label}: ${expr}`);
  }
  return tokens.reduce((total, token) => total + (token[1] === '-' ? -1 : 1) * cents(token[2], label), 0);
}

function assertEqual(actual, expected, description, errors) {
  if (actual !== expected) errors.push(`${description}: expected $${(expected / 100).toFixed(2)}, got $${(actual / 100).toFixed(2)}`);
}

export function validateBom(root = defaultRoot) {
  const errors = [];
  const csvPath = resolve(root, 'hardware/prototype/BOM.csv');
  const bomDoc = readFileSync(resolve(root, 'docs/bench/PROTOTYPE-BENCH-BOM.md'), 'utf8');
  const sourcingDoc = readFileSync(resolve(root, 'docs/bench/PROTOTYPE-PROCUREMENT-READINESS.md'), 'utf8');
  const csv = parseCsv(readFileSync(csvPath, 'utf8'));
  const header = csv.shift();
  const required = ['group', 'ref', 'mpn', 'qty', 'unit_usd_snapshot', 'extended_usd_snapshot', 'dnp'];
  if (!header || required.some((column) => !header.includes(column))) {
    throw Error(`Missing BOM columns: ${required.filter((name) => !header?.includes(name)).join(', ')}`);
  }
  const index = Object.fromEntries(header.map((column, i) => [column, i]));
  let totalMicros = 0;
  const identities = new Set();
  for (const [i, row] of csv.entries()) {
    if (row.length !== header.length) { errors.push(`CSV row ${i + 2}: expected ${header.length} columns, got ${row.length}`); continue; }
    const identity = `${row[index.group]}/${row[index.ref]}`;
    if (identities.has(identity)) errors.push(`Duplicate BOM instance ${identity}`);
    identities.add(identity);
    const qty = row[index.qty];
    if (!/^\d+$/.test(qty)) { errors.push(`Non-integer quantity for ${identity}: ${qty}`); continue; }
    const dnp = row[index.dnp];
    if (dnp !== 'YES' && dnp !== 'NO') { errors.push(`Unrecognized DNP marker ${identity}`); continue; }
    try {
      const unit = currencyMicros(row[index.unit_usd_snapshot], identity + ' unit');
      const extension = currencyMicros(row[index.extended_usd_snapshot], identity + ' extension');
      const expected = dnp === 'YES' ? 0 : unit * Number(qty);
      if (extension !== expected) errors.push(`Incorrect extension for ${identity}: ${extension / 1e6} != ${expected / 1e6}`);
      totalMicros += extension;
    } catch (error) { errors.push(error.message); }
  }
  if (totalMicros % 10_000) errors.push(`Manifest subtotal is not exact cents: ${totalMicros / 1e6}`);
  const totalCents = totalMicros / 10_000;
  const manifestClaim = sourcingDoc.match(/generated BOM is \*\*\$([\d,.]+)\*\* and Strategy A is \*\*\$([\d,.]+)\*\*/);
  if (!manifestClaim) errors.push('BENCH-003 active generated/Strategy A total declaration missing');
  const sourceSubtotal = manifestClaim ? cents(manifestClaim[1]) : NaN;
  const strategyTotal = manifestClaim ? cents(manifestClaim[2]) : NaN;
  if (manifestClaim) assertEqual(totalCents, sourceSubtotal, 'BOM.csv vs BENCH-003 generated subtotal', errors);
  const strategyMatch = sourcingDoc.match(/Strategy A source snapshot is \*\*\$([\d,.]+)\*\*: \$([\d,.]+) in non-J2 lines plus[^\n]+?generated BOM total \*\*\$([\d,.]+)\*\*: \$([\d,.]+) in non-J2 lines/);
  if (!strategyMatch) errors.push('BENCH-003 active Strategy A / generated non-J2 reconciliation missing');
  else {
    const [, strategy, strategyNonJ2, generated, generatedNonJ2] = strategyMatch.map((x) => x);
    assertEqual(cents(strategy), strategyTotal, 'Strategy A source snapshot', errors);
    assertEqual(cents(generated), totalCents, 'Generated source snapshot', errors);
    assertEqual(cents(strategyNonJ2) - cents(generatedNonJ2), strategyTotal - totalCents,
      'Strategy A sourcing delta', errors);
  }

  let scenarios = 0;
  let highCase = null;
  for (const [path, body] of [['BENCH-001', bomDoc], ['BENCH-003', sourcingDoc]]) {
    let scenarioColumns = null;
    for (const line of body.split(/\r?\n/)) {
      if (!line.trim().startsWith('|')) { scenarioColumns = null; continue; }
      const fields = line.split('|').slice(1, -1).map((field) => field.trim());
      if (fields[0] === 'Scenario' && fields.includes('Arithmetic')) {
        scenarioColumns = { kind: 'known', arithmetic: fields.indexOf('Arithmetic'), total: fields.indexOf('Known subtotal'), remainder: fields.indexOf('Remainder from $1,100 target'), cap: fields.indexOf('Remainder from approximately $1,300 ceiling') };
        continue;
      }
      if (fields[0] === 'Scenario' && fields.includes('Calculation')) {
        scenarioColumns = { kind: 'sensitivity', arithmetic: fields.indexOf('Calculation'), total: fields.indexOf('Sensitivity subtotal'), remainder: fields.indexOf('Remainder from $1,100 target'), cap: fields.indexOf('Remainder from approximately $1,300 ceiling') };
        continue;
      }
      if (!scenarioColumns || /^-+$/.test(fields[0])) continue;
      const { arithmetic, total, remainder, cap } = scenarioColumns;
      if (arithmetic < 0 || total < 0 || !fields[arithmetic]?.includes('$')) continue;
      const label = `${path} / ${fields[0]}`;
      let sum, subtotal;
      try {
        sum = sumExpression(fields[arithmetic], label);
        subtotal = moneyFrom(fields[total], label);
      } catch (error) { errors.push(error.message); continue; }
      assertEqual(sum, subtotal, label + ' arithmetic', errors);
      if (remainder >= 0 && cap >= 0) {
        assertEqual(moneyFrom(fields[remainder], label), 110000 - subtotal, label + ' $1,100 headroom', errors);
        assertEqual(moneyFrom(fields[cap], label), 130000 - subtotal, label + ' $1,300 headroom', errors);
      }
      if (fields[0].startsWith('Full-purchase six-board hypothetical') && scenarioColumns.kind === 'known') {
        highCase = highCase == null ? subtotal : highCase;
        assertEqual(subtotal, highCase, 'High scenario across documents', errors);
      }
      scenarios++;
    }
  }
  if (scenarios < 10) errors.push(`Expected at least 10 active budget scenario reconciliations; saw ${scenarios}`);
  if (highCase === null) errors.push('Active full-purchase high scenario missing');
  // This exact scenario should still start from the active Strategy A basket.
  if (strategyTotal && !bomDoc.includes(`$${(strategyTotal / 100).toFixed(2)} + $34.10`)) {
    errors.push('BENCH-001 high scenario does not include current Strategy A subtotal');
  }
  return { passed: errors.length === 0, rows: csv.length, totalUsd: (totalCents / 100).toFixed(2), strategyAUsd: (strategyTotal / 100).toFixed(2), scenarioCount: scenarios, errors };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = validateBom();
    console.log(`BOM: ${result.rows} rows, generated $${result.totalUsd}, Strategy A $${result.strategyAUsd}, ${result.scenarioCount} scenario reconciliations`);
    for (const error of result.errors) console.error(error);
    if (!result.passed) process.exitCode = 1;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
