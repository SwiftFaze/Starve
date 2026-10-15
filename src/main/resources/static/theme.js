// The theme switch and the obi shutter. The chevron block on the obi flips between dark and
// light, remembered in this browser; the same red shutter covers theme changes and page switches.
// Loaded in <head> without defer so the saved theme is set before the first paint; with nothing
// saved, the page follows the OS.
(function () {
  'use strict';

  var root = document.documentElement;
  var KEY = 'starve.theme';

  // Scripts run: controls that only exist for pages without them (a Set group button) can step aside.
  root.classList.add('js');

  try {
    var saved = localStorage.getItem(KEY);
    if (saved === 'dark' || saved === 'light') root.dataset.theme = saved;
  } catch (e) { /* storage blocked: follow the OS */ }

  function current() {
    if (root.dataset.theme) return root.dataset.theme;
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }

  // The favicon is the obi tile in the active theme's colours, so it follows the switch too.
  function icon() {
    var link = document.getElementById('favicon');
    if (link) link.href = '/favicon-' + current() + '.svg';
  }

  icon();
  window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', icon);

  function label() {
    var next = current() === 'dark' ? 'light' : 'dark';
    document.querySelectorAll('.theme-switch').forEach(function (button) {
      button.setAttribute('aria-label', 'Switch to ' + next + ' theme');
      button.title = 'Switch to ' + next + ' theme';
    });
  }

  function apply(next) {
    root.dataset.theme = next;
    try { localStorage.setItem(KEY, next); } catch (e) { /* storage blocked: this page only */ }
    label();
    icon();
  }

  // ---- The shutter: a red obi panel with chevron edges that sweeps across the page ---------

  // Every sweep, in or out, theme or page, runs at one speed and easing with one hold between.
  var SWEEP = 1250;
  var HOLD = 400;
  var EASE = 'cubic-bezier(0.65, 0, 0.35, 1)';
  var IN = { duration: SWEEP, easing: EASE, fill: 'forwards' };
  var OUT = { duration: SWEEP, delay: HOLD, easing: EASE, fill: 'forwards' };
  var busy = false;

  function still() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches || !document.documentElement.animate;
  }

  // The panel is dressed as an obi spec sheet: a framed sheet with registration crosses,
  // corner captions, a barcode and chevrons around the label of what is switching.
  function part(parent, className, text) {
    var el = document.createElement('span');
    el.className = className;
    if (text) el.textContent = text;
    parent.appendChild(el);
    return el;
  }

  // A stable code for the barcode, the same on both sides of a page switch.
  function serial(label) {
    var sum = 0;
    for (var i = 0; i < label.length; i++) sum = (sum * 31 + label.charCodeAt(i)) % 10000;
    return label.replace(/[^A-Za-z]/g, '').slice(0, 3).toUpperCase() + '-' + String(sum).padStart(4, '0');
  }

  function shutter(label, at, kind, caption) {
    var panel = document.createElement('div');
    panel.className = 'shutter';
    panel.setAttribute('aria-hidden', 'true');
    panel.style.transform = 'translateX(' + at + ')';
    var frame = document.createElement('div');
    frame.className = 'shutter-frame';
    part(frame, 'shutter-cap tl', 'Starve');
    part(frame, 'shutter-cap tr', 'No algorithm');
    part(frame, 'shutter-cap bl', '[ ' + kind + ' ]');
    var code = part(frame, 'shutter-code br');
    part(code, 'shutter-bars');
    part(code, 'shutter-cap', serial(label));
    var core = document.createElement('div');
    core.className = 'shutter-core';
    part(core, 'shutter-label', label);
    part(core, 'shutter-rule');
    part(core, 'shutter-cap shutter-sub', caption);
    frame.appendChild(core);
    part(frame, 'shutter-chevrons');
    part(frame, 'shutter-scan');
    panel.appendChild(frame);
    document.body.appendChild(panel);
    return panel;
  }

  function sweepIn(panel) {
    return panel.animate([{ transform: 'translateX(-130%)' }, { transform: 'translateX(0)' }], IN).finished;
  }

  function sweepOut(panel, hold) {
    var timing = { duration: OUT.duration, delay: hold === undefined ? HOLD : hold, easing: EASE, fill: 'forwards' };
    return panel.animate([{ transform: 'translateX(0)' }, { transform: 'translateX(130%)' }], timing).finished
      .then(function () { panel.remove(); }, function () { panel.remove(); });
  }

  // Theme: the panel covers the page, the theme swaps underneath, the panel leaves. It is a live
  // overlay, not a snapshot, so a playing video or track carries on underneath.
  document.addEventListener('click', function (event) {
    if (!event.target.closest('.theme-switch') || busy) return;
    var next = current() === 'dark' ? 'light' : 'dark';
    if (still()) { apply(next); return; }
    busy = true;
    var panel = shutter(next, '-130%', 'Theme', 'Switching to ' + next);
    sweepIn(panel).then(function () {
      apply(next);
      return sweepOut(panel);
    }).then(function () { busy = false; }, function () { busy = false; });
  });

  // Pages: the Timeline and Sources tabs are htmx requests that swap the page around the dock,
  // so the player is never replaced and whatever plays keeps playing. The panel sweeps in first,
  // then the request goes out, then the panel sweeps out once the new page has settled. Without
  // JavaScript, with reduced motion, or after a failed request the tab is an ordinary link.
  var pending = null;

  // The page you are already on: glide back to the top instead of reloading, or, already at
  // the top, blink the tab's marker to say "you are here".
  function here(link) {
    var sheet = document.body.dataset.split === 'on' ? document.getElementById('sheet') : null;
    var scroller = sheet || document.scrollingElement || document.documentElement;
    if (scroller.scrollTop > 0) {
      scroller.scrollTo({ top: 0, behavior: still() ? 'auto' : 'smooth' });
      return;
    }
    link.classList.remove('here');
    void link.offsetWidth;
    link.classList.add('here');
    setTimeout(function () { link.classList.remove('here'); }, 700);
  }

  // Capture phase, so htmx never sees a click that is not a plain request for another page.
  document.addEventListener('click', function (event) {
    var link = event.target.closest('a.tab');
    if (!link) return;
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      event.stopPropagation();
      return;
    }
    if (busy || link.href === window.location.href) {
      event.preventDefault();
      event.stopPropagation();
      if (!busy) here(link);
    }
  }, true);

  function fallBack() {
    if (!pending) return;
    var failed = pending;
    pending = null;
    failed.panel.remove();
    busy = false;
    window.location.href = failed.href;
  }

  document.addEventListener('htmx:confirm', function (event) {
    var link = event.detail.elt;
    if (!link.matches || !link.matches('a.tab') || still()) return;
    event.preventDefault();
    busy = true;
    var panel = shutter(link.textContent.trim(), '-130%', 'Page', '\u2192 ' + link.getAttribute('href'));
    pending = { elt: link, panel: panel, href: link.href };
    sweepIn(panel).then(function () { event.detail.issueRequest(true); }, fallBack);
  });

  document.addEventListener('htmx:afterSettle', function (event) {
    var config = event.detail.requestConfig;
    if (!pending || !config || config.elt !== pending.elt) return;
    var panel = pending.panel;
    pending = null;
    window.scrollTo(0, 0);
    sweepOut(panel).then(function () { busy = false; }, function () { busy = false; });
  });

  // Back and forward between pages swapped in place: ask for the page again into the same
  // places, rather than restoring a snapshot of the whole body (which would replace the dock) or
  // reloading. htmx's own history restore is switched off for the same reason.
  var shown = window.location.pathname + window.location.search;
  var restorer = null;

  document.addEventListener('DOMContentLoaded', function () {
    if (window.htmx) window.htmx.config.historyCacheSize = 0;
  });

  document.addEventListener('htmx:pushedIntoHistory', function () {
    shown = window.location.pathname + window.location.search;
  });

  window.addEventListener('popstate', function (event) {
    event.stopImmediatePropagation();
    var now = window.location.pathname + window.location.search;
    if (now === shown) return;
    if (!window.htmx) { window.location.reload(); return; }
    shown = now;
    if (!restorer) {
      restorer = document.createElement('span');
      restorer.hidden = true;
      restorer.setAttribute('hx-select-oob', '#bar,#series,#panel');
      document.body.appendChild(restorer);
      window.htmx.process(restorer);
    }
    window.htmx.ajax('GET', now, { source: restorer, target: '#sheet', select: '#sheet', swap: 'outerHTML' });
  }, true);

  ['htmx:responseError', 'htmx:sendError', 'htmx:swapError'].forEach(function (name) {
    document.addEventListener(name, function (event) {
      if (event.detail.elt === restorer) window.location.reload();
      else if (pending && event.detail.requestConfig && event.detail.requestConfig.elt === pending.elt) fallBack();
    });
  });

  // A page restored from the back/forward cache may still hold a panel.
  window.addEventListener('pageshow', function (event) {
    if (!event.persisted) return;
    document.querySelectorAll('.shutter').forEach(function (panel) { panel.remove(); });
    pending = null;
    busy = false;
  });

  // The obi on the timeline is swapped by htmx, so its new switch is labelled again.
  document.addEventListener('DOMContentLoaded', label);
  document.addEventListener('htmx:afterSettle', label);
})();
