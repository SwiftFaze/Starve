// The theme switch and the obi shutter. The chevron block on the obi flips between dark and
// light, remembered in this browser; the same red shutter covers theme changes and page switches.
// Loaded in <head> without defer so the saved theme is set before the first paint; with nothing
// saved, the page follows the OS.
(function () {
  'use strict';

  var root = document.documentElement;
  var KEY = 'starve.theme';

  try {
    var saved = localStorage.getItem(KEY);
    if (saved === 'dark' || saved === 'light') root.dataset.theme = saved;
  } catch (e) { /* storage blocked: follow the OS */ }

  function current() {
    if (root.dataset.theme) return root.dataset.theme;
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }

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

  // Pages: the page switch fetches the next page while the panel sweeps in, swaps the content
  // in place underneath it, updates the address bar, and sweeps out. The panel is one element
  // the whole way, so nothing blinks the way a full page load would. Modified clicks,
  // back/forward and a failed fetch fall back to an ordinary page load.
  function load(src) {
    return new Promise(function (resolve) {
      var script = document.createElement('script');
      script.src = src;
      script.onload = script.onerror = resolve;
      document.head.appendChild(script);
    });
  }

  function swap(html, href) {
    var next = new DOMParser().parseFromString(html, 'text/html');
    document.title = next.title;
    var body = document.body;
    var panel = body.querySelector('.shutter');
    Array.prototype.slice.call(body.attributes).forEach(function (a) { body.removeAttribute(a.name); });
    Array.prototype.slice.call(next.body.attributes).forEach(function (a) { body.setAttribute(a.name, a.value); });
    body.innerHTML = next.body.innerHTML;
    if (panel) body.appendChild(panel);
    history.pushState({ starve: true }, '', href);
    window.scrollTo(0, 0);
    var missing = Array.prototype.slice.call(next.head.querySelectorAll('script[src]'))
      .map(function (script) { return script.getAttribute('src'); })
      .filter(function (src) { return !document.head.querySelector('script[src="' + src + '"]'); });
    return Promise.all(missing.map(load)).then(function () {
      if (window.htmx) window.htmx.process(body);
      document.dispatchEvent(new CustomEvent('starve:page'));
      label();
    });
  }

  // The page you are already on: glide back to the top instead of reloading, or, already at
  // the top, blink the tab's marker to say "you are here".
  function here(link) {
    if (window.scrollY > 0) {
      window.scrollTo({ top: 0, behavior: still() ? 'auto' : 'smooth' });
      return;
    }
    link.classList.remove('here');
    void link.offsetWidth;
    link.classList.add('here');
    setTimeout(function () { link.classList.remove('here'); }, 700);
  }

  document.addEventListener('click', function (event) {
    var link = event.target.closest('a.tab');
    if (!link || busy) return;
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (link.href === window.location.href) {
      event.preventDefault();
      here(link);
      return;
    }
    if (still() || !window.fetch) return;
    event.preventDefault();
    busy = true;
    var href = link.getAttribute('href');
    var panel = shutter(link.textContent.trim(), '-130%', 'Page', '\u2192 ' + href);
    var page = fetch(link.href, { credentials: 'same-origin' }).then(function (response) {
      if (!response.ok) throw new Error(response.status);
      return response.text();
    });
    Promise.all([page, sweepIn(panel)])
      .then(function (done) { return swap(done[0], href); })
      .then(function () { return sweepOut(panel); })
      .then(function () { busy = false; }, function () { window.location.href = link.href; });
  });

  // Back and forward after an in-place switch load the page normally.
  window.addEventListener('popstate', function () { window.location.reload(); });

  // A page restored from the back/forward cache may still hold a panel.
  window.addEventListener('pageshow', function (event) {
    if (!event.persisted) return;
    document.querySelectorAll('.shutter').forEach(function (panel) { panel.remove(); });
    busy = false;
  });

  // The obi on the timeline is swapped by htmx, so its new switch is labelled again.
  document.addEventListener('DOMContentLoaded', label);
  document.addEventListener('htmx:afterSettle', label);
})();
