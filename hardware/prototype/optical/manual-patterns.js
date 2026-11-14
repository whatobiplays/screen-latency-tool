/* A classic script keeps the local page usable through file:// as well as HTTP. */
(function () {
  'use strict';

  const identity = Object.freeze({ id: 'slt-manual-optical-patterns', version: '1.0.0' });
  const colors = { BLACK: '#000000', WHITE: '#FFFFFF' };
  // Measure tap duration from pointer timestamps; no timer advances the pattern.
  const maxTapMs = 500;
  const maxMovementPx = 10;

  function geometry(viewport) {
    return { width: viewport.width, height: viewport.height, scale: viewport.scale ?? 1,
      visualWidth: viewport.visualWidth ?? viewport.width,
      visualHeight: viewport.visualHeight ?? viewport.height,
      offsetLeft: viewport.offsetLeft ?? 0, offsetTop: viewport.offsetTop ?? 0 };
  }

  /** Records requested colors and browser interruptions, with no optical timing inference. */
  function createSession({ sessionId, startedAt, now }) {
    const origin = now();
    const events = [];
    const segments = [];
    const pointers = new Set();
    let phase = 'idle';
    let fullscreen = false;
    let requestedState = null;
    let viewport = null;
    let segment = null;
    let gesture = null;

    function record(type, fields = {}) {
      events.push({ sequence: events.length + 1, segmentId: segment?.id ?? null,
        elapsedMs: now() - origin, type, ...fields });
    }
    function clearGesture() { gesture = null; pointers.clear(); }
    function requestState(state, source) {
      requestedState = state;
      record('state-request', { state, color: colors[state], source });
      return colors[state];
    }
    function interrupt(reason) {
      clearGesture();
      if (phase === 'pending') {
        phase = 'invalid';
        record('setup-invalidated', { reason });
      } else if (segment && !segment.invalidReasons.includes(reason)) {
        phase = 'invalid';
        segment.invalidReasons.push(reason);
        record('segment-invalidated', { reason });
      }
    }
    function region(event) {
      const { clientX: x, clientY: y } = event;
      if (!Number.isFinite(x) || !Number.isFinite(y) || x < 0 || y < 0 ||
          x >= viewport.width || y >= viewport.height) return null;
      if (x < viewport.width / 4) return 'BLACK';
      return x >= viewport.width * 3 / 4 ? 'WHITE' : null;
    }
    function moved(event) {
      return !Number.isFinite(event.clientX) || !Number.isFinite(event.clientY) ||
        Math.hypot(event.clientX - gesture.x, event.clientY - gesture.y) > maxMovementPx;
    }

    return {
      get state() { return { phase, fullscreen, requestedState, segmentId: segment?.id ?? null }; },
      requestSetup() {
        if (fullscreen || phase === 'pending' || phase === 'active') return false;
        phase = 'pending';
        requestedState = null;
        segment = null;
        clearGesture();
        record('setup-request');
        return true;
      },
      setupFailed() { if (phase === 'pending') interrupt('fullscreen-setup-failed'); },
      fullscreenChanged(ownFullscreen, size) {
        fullscreen = ownFullscreen;
        clearGesture();
        if (!fullscreen) {
          if (segment) interrupt('fullscreen-exit');
          return;
        }
        if (phase !== 'pending') {
          if (phase !== 'active') phase = 'invalid';
          return;
        }
        viewport = geometry(size);
        if (!(viewport.width > 0 && viewport.height > 0)) {
          interrupt('invalid-viewport');
          return;
        }
        segment = { id: segments.length + 1, invalidReasons: [] };
        segments.push(segment);
        phase = 'active';
        record('segment-start');
        requestState('BLACK', 'fullscreen-setup');
      },
      interrupt,
      resize(size) {
        if (phase !== 'active') return;
        const next = geometry(size);
        if (Object.keys(viewport).some(key => viewport[key] !== next[key])) interrupt('resize');
      },
      pointerDown(event) {
        if (phase !== 'active' || !fullscreen) return;
        pointers.add(event.pointerId);
        if (pointers.size !== 1 || !event.isPrimary || event.button !== 0) {
          gesture = null;
          return;
        }
        gesture = { id: event.pointerId, x: event.clientX, y: event.clientY,
          time: event.timeStamp, state: region(event), dragged: false };
      },
      pointerMove(event) {
        // Retain excursions so dragging out and back cannot become a tap.
        if (gesture?.id === event.pointerId && moved(event)) gesture.dragged = true;
      },
      pointerCancel(event) {
        pointers.delete(event.pointerId);
        gesture = null;
      },
      pointerUp(event) {
        pointers.delete(event.pointerId);
        if (!gesture || gesture.id !== event.pointerId) return null;
        const candidate = gesture;
        const dragged = candidate.dragged || moved(event);
        gesture = null;
        const duration = event.timeStamp - candidate.time;
        if (phase !== 'active' || !fullscreen || !event.isPrimary || event.button !== 0 ||
            dragged || !Number.isFinite(duration) || duration < 0 || duration > maxTapMs ||
            !candidate.state || region(event) !== candidate.state) return null;
        return requestState(candidate.state, 'edge-tap');
      },
      exportDiagnostics() {
        if (fullscreen || phase === 'pending') throw new Error('Exit fullscreen before exporting diagnostics.');
        return { identity: { ...identity }, sessionId, startedAt,
          clock: 'Page monotonic elapsed milliseconds; request diagnostics only.',
          evidenceScope: 'Requested colors and browser lifecycle only. No measured display luminance or latency.',
          opticalResult: 'NOT_EVALUATED', usbProbeSession: 'independent',
          segments: segments.map(item => ({ id: item.id, invalid: item.invalidReasons.length > 0,
            invalidReasons: [...item.invalidReasons] })),
          events: events.map(event => ({ ...event })) };
      },
    };
  }

  /** Keeps all setup and export controls outside the empty fullscreen element. */
  function mount(document, window) {
    const pattern = document.getElementById('pattern');
    const start = document.getElementById('start');
    const exportButton = document.getElementById('export');
    const status = document.getElementById('status');
    const download = document.getElementById('download');
    const sessionId = window.crypto?.randomUUID?.() ?? `manual-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const session = createSession({ sessionId, startedAt: new Date().toISOString(), now: () => window.performance.now() });
    document.getElementById('identity').textContent = `${identity.id} v${identity.version}`;
    const supported = typeof pattern.requestFullscreen === 'function' &&
      document.fullscreenEnabled && typeof window.PointerEvent === 'function';
    let exitPending = false;
    let downloadUrl = null;

    function size() {
      const visual = window.visualViewport;
      return { width: window.innerWidth, height: window.innerHeight,
        visualWidth: visual?.width ?? window.innerWidth,
        visualHeight: visual?.height ?? window.innerHeight,
        scale: visual?.scale ?? 1, offsetLeft: visual?.offsetLeft ?? 0, offsetTop: visual?.offsetTop ?? 0 };
    }
    function render(message) {
      const state = session.state;
      start.disabled = !supported || !!document.fullscreenElement || state.phase === 'pending';
      exportButton.disabled = !!document.fullscreenElement || state.phase === 'pending';
      if (!document.fullscreenElement && state.phase !== 'pending') pattern.hidden = true;
      status.textContent = message ?? (state.phase === 'invalid'
        ? 'Segment invalidated. Export diagnostics, then start a fresh fullscreen setup at BLACK.'
        : state.phase === 'active' ? 'Fullscreen segment active. Exit fullscreen to export diagnostics.'
          : state.phase === 'pending' ? 'Waiting for fullscreen setup.'
            : 'Ready for fullscreen setup.');
    }
    function exitIfInvalid() {
      if (session.state.phase !== 'invalid' || document.fullscreenElement !== pattern || exitPending) return;
      exitPending = true;
      Promise.resolve().then(() => document.exitFullscreen()).catch(() => {
        render('Segment invalidated. Use the browser fullscreen exit, then start a fresh setup.');
      }).finally(() => { exitPending = false; });
    }
    function interrupt(reason) {
      session.interrupt(reason);
      render();
      exitIfInvalid();
    }
    function checkEnvironment() {
      if (document.fullscreenElement !== pattern) session.fullscreenChanged(false, size());
      if (document.visibilityState !== 'visible') session.interrupt('background');
      if (!document.hasFocus()) session.interrupt('focus-lost');
      session.resize(size());
      render();
      exitIfInvalid();
    }

    start.addEventListener('click', () => {
      if (!supported || document.visibilityState !== 'visible' || !document.hasFocus() ||
          document.fullscreenElement || !session.requestSetup()) return;
      download.hidden = true;
      pattern.style.backgroundColor = '#000000';
      pattern.hidden = false;
      render();
      try {
        Promise.resolve(pattern.requestFullscreen({ navigationUI: 'hide' })).catch(() => {
          session.setupFailed();
          render('Fullscreen setup failed. Start again when fullscreen is available.');
        });
      } catch {
        session.setupFailed();
        render('Fullscreen setup failed. Start again when fullscreen is available.');
      }
    });
    document.addEventListener('fullscreenchange', () => {
      session.fullscreenChanged(document.fullscreenElement === pattern, size());
      checkEnvironment();
    });
    document.addEventListener('fullscreenerror', () => { session.setupFailed(); render(); });
    document.addEventListener('visibilitychange', checkEnvironment);
    window.addEventListener('blur', () => interrupt('focus-lost'));
    window.addEventListener('pagehide', () => interrupt('pagehide'));
    window.addEventListener('pageshow', checkEnvironment);
    window.addEventListener('resize', checkEnvironment);
    window.visualViewport?.addEventListener('resize', checkEnvironment);
    window.visualViewport?.addEventListener('scroll', checkEnvironment);
    window.screen?.orientation?.addEventListener('change', () => interrupt('resize'));
    for (const [type, method] of [['pointerdown', 'pointerDown'], ['pointermove', 'pointerMove'],
      ['pointerup', 'pointerUp'], ['pointercancel', 'pointerCancel'], ['lostpointercapture', 'pointerCancel']]) {
      pattern.addEventListener(type, event => {
        event.preventDefault();
        checkEnvironment();
        if (type === 'pointermove') {
          for (const point of event.getCoalescedEvents?.() ?? []) session.pointerMove(point);
        }
        const color = session[method](event);
        if (color) pattern.style.backgroundColor = color;
        if (type === 'pointerdown' && session.state.phase === 'active') {
          try { pattern.setPointerCapture(event.pointerId); } catch { session.pointerCancel(event); }
        }
      });
    }
    for (const type of ['contextmenu', 'dblclick']) pattern.addEventListener(type, event => event.preventDefault());
    exportButton.addEventListener('click', () => {
      if (document.fullscreenElement || session.state.phase === 'pending') return;
      const json = JSON.stringify(session.exportDiagnostics(), null, 2);
      if (downloadUrl) window.URL.revokeObjectURL(downloadUrl);
      downloadUrl = window.URL.createObjectURL(new window.Blob([json], { type: 'application/json' }));
      download.href = downloadUrl;
      download.download = `manual-patterns-${sessionId}.json`;
      download.hidden = false;
      render('Diagnostics ready. Save the JSON file using the download link.');
    });
    render(supported ? undefined : 'This browser needs the Fullscreen API and Pointer Events. Fullscreen setup is unavailable.');
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = { createSession, mount };
  else mount(document, window);
}());
