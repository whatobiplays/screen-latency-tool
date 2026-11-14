import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import manualPatterns from '../hardware/prototype/optical/manual-patterns.js';

const viewport = { width: 1000, height: 800, scale: 1 };
function fixture() {
  let clock = 0;
  const session = manualPatterns.createSession({
    sessionId: 'test-session', startedAt: '2026-11-13T00:00:00.000Z', now: () => clock,
  });
  return { session, advance(ms) { clock += ms; } };
}
function enter(session) {
  assert.equal(session.requestSetup(), true);
  session.fullscreenChanged(true, viewport);
}
function pointer(x, extra = {}) {
  return { pointerId: 1, clientX: x, clientY: 300, button: 0, isPrimary: true,
    timeStamp: 100, ...extra };
}
function tap(session, x, extra = {}) {
  session.pointerDown(pointer(x, extra));
  return session.pointerUp(pointer(x, { ...extra, timeStamp: (extra.timeStamp ?? 100) + 80 }));
}
function requests(session) {
  return session.exportDiagnostics().events.filter(event => event.type === 'state-request');
}

test('fullscreen requires explicit setup and starts each segment at BLACK', () => {
  const { session } = fixture();
  session.fullscreenChanged(true, viewport);
  assert.equal(tap(session, 999), null);
  session.fullscreenChanged(false, viewport);
  enter(session);
  assert.equal(session.state.requestedState, 'BLACK');
  assert.equal(tap(session, 999), '#FFFFFF');
  session.fullscreenChanged(false, viewport);
  enter(session);
  assert.equal(session.state.requestedState, 'BLACK');
  session.fullscreenChanged(false, viewport);
  assert.deepEqual(requests(session).map(event => event.state), ['BLACK', 'WHITE', 'BLACK']);
});

test('outer quarters select colors directly; center and outside coordinates do nothing', () => {
  const { session } = fixture();
  enter(session);
  for (const [x, expected] of [[750, '#FFFFFF'], [999, '#FFFFFF'], [0, '#000000'],
    [249, '#000000'], [250, null], [500, null], [749, null], [-1, null], [1000, null]]) {
    assert.equal(tap(session, x), expected, `x=${x}`);
  }
  assert.equal(tap(session, 999, { clientY: -1 }), null);
  session.fullscreenChanged(false, viewport);
  assert.deepEqual(requests(session).map(event => event.color),
    ['#000000', '#FFFFFF', '#FFFFFF', '#000000', '#000000']);
});

test('dragging out and back, long touches and zone crossings never select a state', () => {
  const { session } = fixture();
  enter(session);
  session.pointerDown(pointer(980));
  session.pointerMove(pointer(900, { timeStamp: 150 }));
  session.pointerMove(pointer(980, { timeStamp: 160 }));
  assert.equal(session.pointerUp(pointer(980, { timeStamp: 180 })), null);
  session.pointerDown(pointer(980));
  assert.equal(session.pointerUp(pointer(980, { timeStamp: 601 })), null);
  session.pointerDown(pointer(748));
  assert.equal(session.pointerUp(pointer(752, { timeStamp: 180 })), null);
  assert.equal(session.state.requestedState, 'BLACK');
  session.fullscreenChanged(false, viewport);
  assert.equal(requests(session).length, 1);
});

test('small touch jitter is accepted, while cancellation, secondary buttons and multitouch are rejected', () => {
  const { session } = fixture();
  enter(session);
  session.pointerDown(pointer(980));
  session.pointerMove(pointer(984, { clientY: 303 }));
  assert.equal(session.pointerUp(pointer(984, { clientY: 303, timeStamp: 200 })), '#FFFFFF');
  assert.equal(tap(session, 0, { button: 2 }), null);
  assert.equal(tap(session, 0, { isPrimary: false }), null);
  session.pointerDown(pointer(0));
  session.pointerCancel(pointer(0));
  assert.equal(session.pointerUp(pointer(0)), null);
  session.pointerDown(pointer(0));
  session.pointerDown(pointer(10, { pointerId: 2, isPrimary: false }));
  assert.equal(session.pointerUp(pointer(0, { timeStamp: 180 })), null);
  session.pointerUp(pointer(10, { pointerId: 2, isPrimary: false }));
  assert.equal(tap(session, 0), '#000000');
});

for (const reason of ['background', 'focus-lost', 'pagehide', 'resize', 'fullscreen-exit']) {
  test(`${reason} invalidates the segment and requires fresh setup`, () => {
    const { session } = fixture();
    enter(session);
    tap(session, 999);
    session.pointerDown(pointer(0));
    if (reason === 'fullscreen-exit') session.fullscreenChanged(false, viewport);
    else if (reason === 'resize') session.resize({ ...viewport, width: 900 });
    else session.interrupt(reason);
    assert.equal(session.state.phase, 'invalid');
    assert.equal(session.pointerUp(pointer(0, { timeStamp: 180 })), null);
    assert.equal(tap(session, 0), null);
    session.fullscreenChanged(true, viewport);
    assert.equal(session.state.phase, 'invalid');
    session.fullscreenChanged(false, viewport);
    const log = session.exportDiagnostics();
    assert.equal(log.segments[0].invalidReasons.includes(reason), true);
    enter(session);
    assert.equal(session.state.segmentId, 2);
    assert.equal(session.state.requestedState, 'BLACK');
  });
}

test('same-size resize is harmless; scale changes invalidate; idle time never changes the pattern', () => {
  const { session, advance } = fixture();
  enter(session);
  tap(session, 999);
  session.resize({ ...viewport });
  advance(60_000);
  assert.equal(session.state.phase, 'active');
  assert.equal(session.state.requestedState, 'WHITE');
  session.resize({ ...viewport, scale: 1.1 });
  assert.equal(session.state.phase, 'invalid');
});

test('failed or interrupted pending fullscreen setup cannot activate late', () => {
  const { session } = fixture();
  session.requestSetup();
  session.interrupt('background');
  session.fullscreenChanged(true, viewport);
  assert.equal(tap(session, 999), null);
  session.fullscreenChanged(false, viewport);
  session.requestSetup();
  session.setupFailed();
  session.fullscreenChanged(true, viewport);
  assert.equal(session.state.phase, 'invalid');
});

test('export is blocked throughout fullscreen and contains only request and lifecycle diagnostics', () => {
  const { session, advance } = fixture();
  enter(session);
  assert.throws(() => session.exportDiagnostics(), /fullscreen/i);
  advance(2000);
  tap(session, 999);
  session.interrupt('background');
  assert.throws(() => session.exportDiagnostics(), /fullscreen/i);
  session.fullscreenChanged(false, viewport);
  const log = session.exportDiagnostics();
  assert.equal(log.identity.id, 'slt-manual-optical-patterns');
  assert.equal(log.identity.version, '1.0.0');
  assert.equal(log.sessionId, 'test-session');
  assert.equal(log.opticalResult, 'NOT_EVALUATED');
  assert.equal(log.usbProbeSession, 'independent');
  assert.match(log.clock, /diagnostic/i);
  assert.deepEqual(log.events.map(event => event.sequence), log.events.map((_, i) => i + 1));
  assert.deepEqual(log.events.filter(event => event.type === 'state-request').map(event =>
    [event.state, event.color, event.elapsedMs]), [['BLACK', '#000000', 0], ['WHITE', '#FFFFFF', 2000]]);
  assert.equal(log.events.some(event => 'clientX' in event || 'clientY' in event), false);
  assert.ok(log.events.every(event => ['setup-request', 'segment-start', 'state-request',
    'segment-invalidated'].includes(event.type)));
  assert.equal(JSON.stringify(log).includes('latencyMs'), false);
  log.events[0].type = 'changed-by-consumer';
  log.segments[0].invalidReasons.length = 0;
  const again = session.exportDiagnostics();
  assert.notEqual(again.events[0].type, 'changed-by-consumer');
  assert.ok(again.segments[0].invalidReasons.length > 0);
});

test('page has only a local script dependency and a blank fullscreen surface', () => {
  const html = readFileSync(new URL('../hardware/prototype/optical/manual-patterns.html', import.meta.url), 'utf8');
  assert.match(html, /<div id="pattern"[^>]*><\/div>/);
  assert.deepEqual([...html.matchAll(/(?:src|href)="([^"]+)"/g)].map(match => match[1]), ['./manual-patterns.js']);
  assert.match(html, /-webkit-tap-highlight-color:\s*transparent/);
  assert.doesNotMatch(html, /@keyframes|\banimation\s*:|\btransition\s*:/);
});

// Event targets stand in for browser APIs; state selection and logging use the shipped script.
function pageFixture({ rejectFullscreen = false, rejectExit = false } = {}) {
  class Element extends EventTarget {
    hidden = false;
    disabled = false;
    style = {};
    textContent = '';
    setPointerCapture() {}
  }
  const elements = Object.fromEntries(['pattern', 'start', 'export', 'status', 'download', 'identity']
    .map(id => [id, new Element()]));
  const document = Object.assign(new EventTarget(), { fullscreenEnabled: true,
    fullscreenElement: null, visibilityState: 'visible', hasFocus: () => true,
    getElementById: id => elements[id] });
  let savedBlob;
  const window = Object.assign(new EventTarget(), { innerWidth: 1000, innerHeight: 800,
    PointerEvent: Event, performance: { now: () => 0 }, crypto: { randomUUID: () => 'ui-session' },
    visualViewport: Object.assign(new EventTarget(), { width: 1000, height: 800, scale: 1,
      offsetLeft: 0, offsetTop: 0 }), Blob,
    URL: { createObjectURL(blob) { savedBlob = blob; return 'blob:diagnostic-test'; }, revokeObjectURL() {} } });
  elements.pattern.requestFullscreen = () => {
    if (rejectFullscreen) return Promise.reject(new Error('fullscreen denied'));
    document.fullscreenElement = elements.pattern;
    document.dispatchEvent(new Event('fullscreenchange'));
    return Promise.resolve();
  };
  document.exitFullscreen = () => {
    if (rejectExit) return Promise.reject(new Error('exit denied'));
    document.fullscreenElement = null;
    document.dispatchEvent(new Event('fullscreenchange'));
    return Promise.resolve();
  };
  function dispatch(target, type, fields = {}) {
    target.dispatchEvent(Object.assign(new Event(type, { cancelable: true }), fields));
  }
  manualPatterns.mount(document, window);
  return { document, window, elements, dispatch,
    start() { dispatch(elements.start, 'click'); },
    async exportLog() {
      dispatch(elements.export, 'click');
      return savedBlob ? JSON.parse(await savedBlob.text()) : null;
    } };
}

test('browser adapter paints on edge taps and exposes export only outside fullscreen', async () => {
  const page = pageFixture();
  page.start();
  assert.equal(page.elements.pattern.hidden, false);
  assert.equal(page.elements.pattern.style.backgroundColor, '#000000');
  assert.equal(page.elements.export.disabled, true);
  assert.equal(await page.exportLog(), null);
  // Event.timeStamp is supplied by Event itself, just as in a browser.
  page.dispatch(page.elements.pattern, 'pointerdown', { pointerId: 1, clientX: 990,
    clientY: 200, isPrimary: true, button: 0 });
  page.dispatch(page.elements.pattern, 'pointerup', { pointerId: 1, clientX: 990,
    clientY: 200, isPrimary: true, button: 0 });
  assert.equal(page.elements.pattern.style.backgroundColor, '#FFFFFF');
  page.dispatch(page.elements.pattern, 'pointermove', { pointerId: 1, clientX: 0, clientY: 200 });
  page.dispatch(page.window, 'keydown', { key: 'ArrowLeft' });
  assert.equal(page.elements.pattern.style.backgroundColor, '#FFFFFF');
  await page.document.exitFullscreen();
  assert.equal(page.elements.pattern.hidden, true);
  assert.equal(page.elements.export.disabled, false);
  const log = await page.exportLog();
  assert.deepEqual(log.events.filter(event => event.type === 'state-request').map(event => event.state), ['BLACK', 'WHITE']);
  assert.equal(page.elements.download.hidden, false);
  assert.match(page.elements.download.download, /ui-session.*\.json$/);
});

test('browser adapter rejects denied fullscreen setup without state requests', async () => {
  const page = pageFixture({ rejectFullscreen: true });
  page.start();
  await Promise.resolve();
  assert.equal(page.elements.pattern.hidden, true);
  assert.equal(page.elements.start.disabled, false);
  const log = await page.exportLog();
  assert.equal(log.events.some(event => event.type === 'state-request'), false);
  assert.equal(log.events.at(-1).reason, 'fullscreen-setup-failed');
});

for (const reason of ['background', 'resize', 'focus-lost', 'pagehide']) {
  test(`browser adapter locks ${reason} interruptions even if fullscreen exit fails`, async () => {
    const page = pageFixture({ rejectExit: true });
    page.start();
    if (reason === 'background') {
      page.document.visibilityState = 'hidden';
      page.dispatch(page.document, 'visibilitychange');
      page.document.visibilityState = 'visible';
      page.dispatch(page.document, 'visibilitychange');
    } else if (reason === 'resize') {
      page.window.visualViewport.width = 900;
      page.dispatch(page.window.visualViewport, 'resize');
    } else page.dispatch(page.window, reason === 'focus-lost' ? 'blur' : 'pagehide');
    await Promise.resolve();
    page.dispatch(page.elements.pattern, 'pointerdown', { pointerId: 1, clientX: 990,
      clientY: 200, isPrimary: true, button: 0 });
    page.dispatch(page.elements.pattern, 'pointerup', { pointerId: 1, clientX: 990,
      clientY: 200, isPrimary: true, button: 0 });
    assert.equal(page.elements.pattern.style.backgroundColor, '#000000');
    assert.equal(await page.exportLog(), null);
    page.document.fullscreenElement = null;
    page.dispatch(page.document, 'fullscreenchange');
    const log = await page.exportLog();
    assert.equal(log.segments[0].invalid, true);
    assert.ok(log.segments[0].invalidReasons.includes(reason));
    assert.equal(log.events.filter(event => event.type === 'state-request').length, 1);
  });
}
