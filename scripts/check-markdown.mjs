// Fail closed on broken repository-local Markdown links and heading anchors.
// External URLs are intentionally not fetched: CI must remain deterministic/offline.
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));

function markdownFiles(root) {
  const found = [];
  function visit(path) {
    if (!existsSync(path)) return;
    for (const entry of readdirSync(path, { withFileTypes: true })) {
      if (entry.name.startsWith('.')) continue;
      const target = join(path, entry.name);
      if (entry.isDirectory()) visit(target);
      else if (entry.isFile() && extname(entry.name).toLowerCase() === '.md') found.push(target);
    }
  }
  visit(join(root, 'docs'));
  visit(join(root, 'hardware', 'prototype'));
  for (const name of ['AGENTS.md', 'GLOSSARY.md', 'README.md']) {
    const path = join(root, name);
    if (existsSync(path)) found.push(path);
  }
  return [...new Set(found)].sort();
}

function markdownText(text) {
  let fence = null;
  return text.split(/\r?\n/).map((line) => {
    const marker = line.match(/^\s*(`{3,}|~{3,})/);
    if (marker) {
      if (!fence) fence = { character: marker[1][0], length: marker[1].length };
      else if (marker[1][0] === fence.character && marker[1].length >= fence.length) fence = null;
      return '';
    }
    if (fence) return '';
    return line.replace(/<!--.*?-->/g, '').replace(/`[^`]*`/g, '');
  }).join('\n');
}

function slug(text) {
  return text
    .replace(/<[^>]*>/g, '')
    .replace(/!?\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/[`*_~]/g, '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N} _-]/gu, '')
    .trim()
    .replace(/\s/g, '-');
}

function headingIds(markdown) {
  const ids = new Set();
  const duplicates = new Map();
  for (const match of markdown.matchAll(/^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/gm)) {
    const base = slug(match[1]);
    if (!base) continue;
    const count = duplicates.get(base) ?? 0;
    ids.add(count ? `${base}-${count}` : base);
    duplicates.set(base, count + 1);
  }
  return ids;
}

function localTarget(raw) {
  const text = raw.trim().replace(/^<|>$/g, '');
  const candidate = text.match(/^(\S+?)(?:\s+['"].*['"])?$/)?.[1] ?? text;
  if (/^(?:[A-Za-z][A-Za-z\d+.-]*:|\/\/)/.test(candidate)) return null;
  if (candidate.startsWith('data:')) return null;
  try { return decodeURIComponent(candidate); }
  catch { return candidate; }
}

export function validateMarkdown(root = defaultRoot) {
  const errors = [];
  const files = markdownFiles(root);
  const cachedHeadings = new Map();
  let links = 0;
  let anchors = 0;
  for (const file of files) {
    const text = markdownText(readFileSync(file, 'utf8'));
    const destinations = [
      ...text.matchAll(/!?\[[^\]\n]*\]\((<[^>]*>|[^)\n]+)\)/g),
      ...text.matchAll(/^\s*\[[^\]]+\]:\s*(\S+)/gm),
    ];
    for (const match of destinations) {
      const target = localTarget(match[1]);
      if (target === null) continue;
      const [pathPart, fragment, ...extra] = target.split('#');
      if (extra.length) { errors.push(`${relative(root, file)}: invalid link fragment: ${target}`); continue; }
      if (pathPart.startsWith('/')) { errors.push(`${relative(root, file)}: absolute local link: ${target}`); continue; }
      const resolved = pathPart ? resolve(dirname(file), pathPart) : file;
      if (relative(root, resolved).split(sep)[0] === '..' || isAbsolute(relative(root, resolved))) {
        errors.push(`${relative(root, file)}: link escapes repository: ${target}`);
        continue;
      }
      links++;
      if (!existsSync(resolved)) { errors.push(`${relative(root, file)}: missing target ${target}`); continue; }
      if (fragment) {
        anchors++;
        if (!statSync(resolved).isFile() || extname(resolved) !== '.md') {
          errors.push(`${relative(root, file)}: anchor on non-Markdown target: ${target}`);
          continue;
        }
        if (!cachedHeadings.has(resolved)) {
          cachedHeadings.set(resolved, headingIds(markdownText(readFileSync(resolved, 'utf8'))));
        }
        if (!cachedHeadings.get(resolved).has(fragment.toLowerCase())) {
          errors.push(`${relative(root, file)}: missing heading #${fragment} in ${relative(root, resolved)}`);
        }
      }
    }
  }
  return { passed: errors.length === 0, files: files.length, links, anchors, errors };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = validateMarkdown();
  console.log(`Markdown: ${result.files} files, ${result.links} local links, ${result.anchors} anchors, ${result.errors.length} errors`);
  for (const error of result.errors) console.error(error);
  if (!result.passed) process.exitCode = 1;
}
