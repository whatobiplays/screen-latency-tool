// Validate repository-local Markdown destinations and GitHub-style heading anchors.
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

function visibleMarkdown(source, { links = false } = {}) {
  // Preserve newlines while removing multiline comments. Code spans remain in
  // headings (their rendered text contributes to a GitHub heading anchor).
  const withoutComments = source.replace(/<!--[\s\S]*?(?:-->|$)/g,
    (comment) => comment.replace(/[^\n]/g, ''));
  const originalLines = source.split(/\r?\n/);
  let fence = null;
  return withoutComments.split(/\r?\n/).map((line, index) => {
    const original = originalLines[index];
    // A fence must begin at a valid position in the original Markdown, not
    // become one merely because an earlier HTML comment was removed.
    const marker = original.match(/^ {0,3}(`{3,}|~{3,})/) &&
      line.match(/^ {0,3}(`{3,}|~{3,})/);
    if (marker) {
      if (!fence) fence = { character: marker[1][0], length: marker[1].length };
      else if (marker[1][0] === fence.character && marker[1].length >= fence.length) fence = null;
      return '';
    }
    // Preserve indented-code semantics when removing a leading HTML comment.
    if (fence || /^(?: {4}|\t)/.test(original)) return '';
    return links ? line.replace(/(`+)([^`\n]*?)\1/g, '') : line;
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
  function add(text) {
    const base = slug(text);
    if (!base) return;
    const count = duplicates.get(base) ?? 0;
    ids.add(count ? `${base}-${count}` : base);
    duplicates.set(base, count + 1);
  }
  const lines = markdown.split('\n');
  for (let index = 0; index < lines.length; index++) {
    const atx = lines[index].match(/^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$/);
    if (atx) add(atx[1]);
    else if (index + 1 < lines.length &&
             /^ {0,3}(?:=+|-+)\s*$/.test(lines[index + 1]) &&
             lines[index].trim()) add(lines[index].trim());
  }
  return ids;
}

function inlineDestinations(markdown) {
  const result = [];
  const opener = /!?\[[^\]\n]*\]\(/g;
  for (const match of markdown.matchAll(opener)) {
    let depth = 1;
    let angle = false;
    let titleEnd = null;
    let end = match.index + match[0].length;
    for (; end < markdown.length && depth; end++) {
      const char = markdown[end];
      if (char === '\\') { end++; continue; }
      if (char === '\n') break;
      if (titleEnd !== null) {
        if (char === titleEnd) titleEnd = null;
        continue;
      }
      // Quoted or parenthesized Markdown link titles start after whitespace.
      // Parentheses inside a quoted title are content, not destination nesting.
      if (!angle && depth === 1 && /[ \t]/.test(markdown[end - 1]) &&
          (char === '"' || char === "'" || char === '(')) {
        titleEnd = char === '(' ? ')' : char;
        continue;
      }
      if (char === '<' && depth === 1) angle = true;
      else if (char === '>' && angle) angle = false;
      else if (!angle && char === '(') depth++;
      else if (!angle && char === ')') depth--;
    }
    if (depth === 0) result.push({ raw: markdown.slice(match.index + match[0].length, end - 1) });
    else result.push({ error: `Unclosed Markdown link near ${markdown.slice(match.index, match.index + 55)}` });
  }
  return result;
}

function destinations(markdown) {
  return [
    ...inlineDestinations(markdown),
    ...[...markdown.matchAll(/^ {0,3}\[[^\]\n]+\]:\s*(<[^>\n]*>|\S+)/gm)]
      .map((match) => ({ raw: match[1] })),
  ];
}

function localTarget(raw) {
  const text = raw.trim();
  const angle = text.match(/^<([^>\n]*)>(?:\s+(?:"[^"]*"|'[^']*'|\([^)]*\)))?\s*$/);
  const plain = text.match(/^((?:\\.|[^\s])+?)(?:\s+(?:"[^"]*"|'[^']*'|\([^)]*\)))?\s*$/);
  const candidate = angle?.[1] ?? plain?.[1] ?? text;
  if (/^(?:[A-Za-z][A-Za-z\d+.-]*:|\/\/)/.test(candidate)) return null;
  return candidate.replace(/\\([()\\#])/g, '$1');
}

function decodeComponent(value) {
  try { return decodeURIComponent(value); }
  catch { throw Error(`Invalid URI encoding: ${value}`); }
}

export function validateMarkdown(root = defaultRoot) {
  const errors = [];
  const files = markdownFiles(root);
  const cachedHeadings = new Map();
  let links = 0;
  let anchors = 0;
  for (const file of files) {
    const raw = readFileSync(file, 'utf8');
    for (const entry of destinations(visibleMarkdown(raw, { links: true }))) {
      if (entry.error) { errors.push(`${relative(root, file)}: ${entry.error}`); continue; }
      const target = localTarget(entry.raw);
      if (target === null) continue;
      const [rawPath, rawFragment, ...extra] = target.split('#');
      if (extra.length) { errors.push(`${relative(root, file)}: invalid link fragment: ${target}`); continue; }
      let pathPart, fragment;
      try { pathPart = decodeComponent(rawPath); fragment = rawFragment ? decodeComponent(rawFragment) : rawFragment; }
      catch (error) { errors.push(`${relative(root, file)}: ${error.message}`); continue; }
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
        if (!statSync(resolved).isFile() || extname(resolved).toLowerCase() !== '.md') {
          errors.push(`${relative(root, file)}: anchor on non-Markdown target: ${target}`);
          continue;
        }
        if (!cachedHeadings.has(resolved)) {
          cachedHeadings.set(resolved, headingIds(visibleMarkdown(readFileSync(resolved, 'utf8'))));
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
