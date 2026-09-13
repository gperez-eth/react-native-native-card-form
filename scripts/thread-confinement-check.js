#!/usr/bin/env node
/**
 * `CardSessionRegistry` (Android) carries no lock at all — see its own header
 * for why: it is confined to the main thread, the same design the iOS twin
 * has always used. That confinement has exactly two load-bearing halves, and
 * this script asserts both without needing a device or a build:
 *
 *   1. `CardSessionRegistry.kt` stays lock-free. A `@Synchronized` or
 *      `synchronized(` reappearing there is the signal that someone is
 *      re-deriving the manual "carve the Fabric-reaching call out of the
 *      locked section" fix this design replaced — which this repo's own
 *      history shows gets it wrong three times before it's right.
 *   2. Every `AsyncFunction` declared directly in `MackenrowNativeCardModule`
 *      (i.e. everything that can reach the registry from outside the
 *      `View{}` block, which Expo's own `ViewDefinitionBuilder` already
 *      confines to main on its own) is chained to `.runOnQueue(Queues.MAIN)`.
 *      A `Function(...)` (synchronous, not async) declared there is refused
 *      outright: a sync function can't be marshalled to a queue at all, so
 *      one that touched the registry would be exactly the thread `ensure()`
 *      used to run on before `ensureSession` became async.
 *
 * Like this package's other checks (`validation-fixtures.js`,
 * `tarball-audit.js`), it reads the source, never adivina: a shape it does
 * not recognise is a FAIL naming what it expected, not a skip.
 */

const fs = require('fs');
const path = require('path');

const REGISTRY_PATH = path.join(
  __dirname,
  '../android/src/main/java/com/mackenrow/nativecard/CardSessionRegistry.kt'
);
const MODULE_PATH = path.join(
  __dirname,
  '../android/src/main/java/com/mackenrow/nativecard/MackenrowNativeCardModule.kt'
);

let failures = 0;
function fail(message) {
  failures += 1;
  console.error(`✗ ${message}`);
}
function ok(message) {
  console.log(`✓ ${message}`);
}

/** Strips `//` and `/* … *​/` comments so a mention inside prose (this
 *  file's own header, or CardSessionRegistry's) never counts as code —
 *  the same distinction CLAUDE.md's "invariant 12" makes for its own
 *  checkers: a centinela that flags its own documentation gets ignored. */
function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '');
}

/** Finds the `{ … }` immediately following `startIndex` (skipping
 *  whitespace) and returns the index just past its closing brace, tracking
 *  nesting depth so a lambda containing its own `{ }` blocks resolves
 *  correctly. Returns null if no balanced block is found. */
function matchBraceBlock(source, startIndex) {
  let i = startIndex;
  while (i < source.length && /\s/.test(source[i])) i += 1;
  if (source[i] !== '{') return null;
  let depth = 0;
  for (; i < source.length; i += 1) {
    if (source[i] === '{') depth += 1;
    else if (source[i] === '}') {
      depth -= 1;
      if (depth === 0) return i + 1;
    }
  }
  return null;
}

function checkRegistry() {
  if (!fs.existsSync(REGISTRY_PATH)) {
    fail(`CardSessionRegistry.kt not found at ${REGISTRY_PATH}`);
    return;
  }
  const code = stripComments(fs.readFileSync(REGISTRY_PATH, 'utf8'));
  if (/@Synchronized/.test(code)) {
    fail('CardSessionRegistry.kt has a `@Synchronized` — the registry is meant to be lock-free (main-thread-only); see the file\'s own header.');
    return;
  }
  if (/\bsynchronized\s*\(/.test(code)) {
    fail('CardSessionRegistry.kt has a `synchronized(` block — the registry is meant to be lock-free (main-thread-only); see the file\'s own header.');
    return;
  }
  ok('CardSessionRegistry.kt has no lock of any kind.');
}

function checkModule() {
  if (!fs.existsSync(MODULE_PATH)) {
    fail(`MackenrowNativeCardModule.kt not found at ${MODULE_PATH}`);
    return;
  }
  const raw = fs.readFileSync(MODULE_PATH, 'utf8');
  const code = stripComments(raw);

  const viewIndex = code.indexOf('View(');
  if (viewIndex === -1) {
    fail('MackenrowNativeCardModule.kt has no `View(` block — expected shape not found, refusing to guess where the registry-touching functions end.');
    return;
  }
  // Only what's declared BEFORE the View{} block can reach the registry
  // directly; Prop/Function calls inside View{} are auto-confined to main
  // by expo-modules-core's own ViewDefinitionBuilder.
  const before = code.slice(0, viewIndex);

  if (/\bFunction\s*\(/.test(before)) {
    fail('MackenrowNativeCardModule.kt declares a synchronous `Function(...)` outside the View{} block — a sync function cannot be routed to `Queues.MAIN`, so it would touch the (now lock-free) registry from whatever thread called it. Convert it to an `AsyncFunction(...).runOnQueue(Queues.MAIN)`, the way `ensureSession` was.');
  }

  const names = [];
  let cursor = 0;
  for (;;) {
    const match = /AsyncFunction\s*\(\s*"([^"]+)"/.exec(before.slice(cursor));
    if (!match) break;
    const nameStart = cursor + match.index;
    const name = match[1];
    const parenEnd = before.indexOf(')', nameStart);
    if (parenEnd === -1) {
      fail(`AsyncFunction("${name}") has an unterminated argument list — refusing to guess where its lambda starts.`);
      break;
    }
    const blockEnd = matchBraceBlock(before, parenEnd + 1);
    if (blockEnd === null) {
      fail(`AsyncFunction("${name}")'s lambda body is not a balanced { … } block — refusing to guess where it ends.`);
      break;
    }
    const after = before.slice(blockEnd).replace(/^\s+/, '');
    if (!after.startsWith('.runOnQueue(Queues.MAIN)')) {
      fail(`AsyncFunction("${name}") is missing \`.runOnQueue(Queues.MAIN)\` right after its closing brace — it will run on the background \`modulesQueue\` instead, which is exactly the class of bug this file's own header documents.`);
    } else {
      names.push(name);
    }
    cursor = blockEnd;
  }

  if (names.length === 0) {
    fail('Found zero `AsyncFunction(...).runOnQueue(Queues.MAIN)` declarations before the View{} block — expected at least ensureSession/tokenize/reset/focus/disposeSession.');
    return;
  }
  ok(`${names.length} AsyncFunction declaration(s) confined to Queues.MAIN: ${names.join(', ')}.`);
}

checkRegistry();
checkModule();

if (failures > 0) {
  console.error(`\n${failures} check(s) failed.`);
  process.exit(1);
}
console.log('\nAll thread-confinement checks passed.');
