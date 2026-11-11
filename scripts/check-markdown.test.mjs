import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';

import { validateMarkdown } from './check-markdown.mjs';

function makeRoot(t) {
  const root = mkdtempSync(join(tmpdir(), 'screen-latency-markdown-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return root;
}

function writeMarkdown(root, relativePath, contents) {
  const path = join(root, relativePath);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, contents);
}

test('validates Android and firmware documentation', (t) => {
  const root = makeRoot(t);
  writeMarkdown(root, 'android/README.md', '[missing](android-missing.md)\n');
  writeMarkdown(root, 'firmware/docs/physical-verification.md', '[missing](firmware-missing.md)\n');

  const result = validateMarkdown(root);

  assert.equal(result.files, 2);
  assert.equal(result.errors.length, 2);
  assert.match(result.errors.join('\n'), /android\/README\.md/);
  assert.match(result.errors.join('\n'), /firmware\/docs\/physical-verification\.md/);
});

test('skips generated dependencies, caches, and build directories', (t) => {
  const root = makeRoot(t);
  for (const relativePath of [
    'android/node_modules/dependency/README.md',
    'android/build/generated.md',
    'android/coverage/report.md',
    'firmware/__pycache__/generated.md',
    'firmware/cache/generated.md',
    'firmware/dist/generated.md',
    'firmware/out/generated.md',
    'firmware/target/generated.md',
    'firmware/.cache/generated.md',
    'firmware/.pio/generated.md',
    'firmware/.platformio-core/generated.md',
  ]) {
    writeMarkdown(root, relativePath, '[missing](generated-target.md)\n');
  }

  const result = validateMarkdown(root);

  assert.equal(result.files, 0);
  assert.deepEqual(result.errors, []);
});
