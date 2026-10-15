// The player deck and the workspace. What plays sits in #dock, which htmx never swaps, so the music
// outlives every change of the sheet. On a desktop the list, a playing video and an open post are
// tiled like tmux panels: each has a title bar, splitters between them resize them, dragging a title
// bar onto another panel swaps the two, and the panel last clicked is outlined. Layout only changes
// classes, data attributes and custom properties; the embed iframe is never moved in the DOM, so
// nothing restarts.
(function () {
  'use strict';

  var body = document.body;
  var YOUTUBE = 'https://www.youtube-nocookie.com';
  var SOUNDCLOUD = 'https://w.soundcloud.com';
  var PHONE = '(max-width: 60rem)';
  var state = null;

  function dock() { return document.getElementById('dock'); }
  function player() { return document.querySelector('#dock .player'); }
  function phone() { return window.matchMedia(PHONE).matches; }

  function stored(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  function store(key, value) {
    try { localStorage.setItem(key, String(value)); } catch (e) { /* storage blocked */ }
  }

  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }

  // ---- The playing row ------------------------------------------------------------------

  function markPlaying() {
    var current = player();
    document.querySelectorAll('.item.playing').forEach(function (row) {
      row.classList.remove('playing');
      var play = row.querySelector('.play');
      if (play) { play.textContent = 'Play'; play.setAttribute('aria-pressed', 'false'); }
    });
    layout();
    markDetail();
    if (!current) return;
    var row = document.querySelector('.item[data-item="' + current.dataset.item + '"]');
    if (!row) return;
    row.classList.add('playing');
    var play = row.querySelector('.play');
    if (play) { play.textContent = 'Playing'; play.setAttribute('aria-pressed', 'true'); }
  }

  // Eject removes the focused button, so focus goes back to the row it was playing.
  window.stopPlayer = function (button) {
    var target = document.querySelector('.item.playing .play') || document.getElementById('sheet');
    if (pipWindow) { var leaving = pipWindow; pipWindow = null; leaving.close(); }
    button.closest('.player').remove();
    state = null;
    delete body.dataset.playing;
    delete body.dataset.video;
    if (body.dataset.active === 'video') delete body.dataset.active;
    trackDeck();
    markPlaying();
    if (target) target.focus();
  };

  // ---- Video layout: 2 col, 3 col, bottom or picture in picture ----------------------------

  var LAYOUTS = { split: true, tri: true, bottom: true, pip: true };

  function savedLayout() {
    var value = stored('starve.layout');
    return LAYOUTS[value] ? value : 'split';
  }

  function place(where) {
    if (!LAYOUTS[where]) where = 'split';
    body.dataset.video = where;
    store('starve.layout', where);
    document.querySelectorAll('#dock .placement button').forEach(function (button) {
      button.setAttribute('aria-pressed', String(button.dataset.place === where));
    });
    if (where === 'pip') { if (!popOut()) sizePip(pipBox(), false); }
    else closePip();
    layout();
    requestAnimationFrame(tickTitle);
  }

  document.addEventListener('click', function (event) {
    var placeButton = event.target.closest('#dock .placement button');
    if (placeButton) { place(placeButton.dataset.place); return; }

    var pause = event.target.closest('#dock .pause');
    if (pause && state) { state.playing ? state.pause() : state.play(); }
  });

  // ---- The workspace: panels tiled beside the obi --------------------------------------------

  var NAMES = { list: 'List', video: 'Video', post: 'Post' };
  var BAR = 28;
  var GAP = 0;
  var STRIP = 136;
  var LIST_FOLD = 610;
  var COLUMN_MIN = 280;
  var ROW_MIN = 120;

  function videoBox() { return document.querySelector('#dock .ratio-16x9'); }
  function postView() { return document.querySelector('#reader .reader-view'); }
  function tiledVideo() { return !!videoBox() && !!body.dataset.video && body.dataset.video !== 'pip'; }

  function element(name) {
    if (name === 'list') return document.getElementById('sheet');
    if (name === 'video') return videoBox();
    return document.getElementById('reader');
  }

  // The panels in their remembered order, keeping only those on screen now.
  function order() {
    var saved = (stored('starve.panels') || '').split(',');
    var known = saved.filter(function (name) { return NAMES[name]; });
    ['list', 'video', 'post'].forEach(function (name) { if (known.indexOf(name) < 0) known.push(name); });
    return known.slice(0, 3);
  }

  function present() {
    return order().filter(function (name) {
      if (name === 'list') return true;
      if (name === 'video') return tiledVideo();
      return !!postView();
    });
  }

  function area() {
    var sleeve = document.querySelector('.series-block');
    var left = sleeve ? Math.round(sleeve.getBoundingClientRect().right) : 0;
    var bar = document.getElementById('bar');
    var top = bar ? Math.round(bar.getBoundingClientRect().bottom) : 0;
    var deck = player() ? player().offsetHeight : 0;
    return { x: left, y: Math.max(0, top), w: window.innerWidth - left, h: window.innerHeight - Math.max(0, top) - deck };
  }

  function savedNumber(key) {
    var value = Number(stored(key));
    return value > 0 ? value : null;
  }

  // The first column: the list folds to its catalogue numbers below LIST_FOLD.
  function firstWidth(name, room) {
    var saved = stored('starve.col1');
    if (name === 'list' && saved === 'strip') return STRIP;
    var width = savedNumber('starve.col1') || (name === 'list' ? 720 : Math.round(room * 0.4));
    if (name === 'list' && width < LIST_FOLD) return STRIP;
    return clamp(width, COLUMN_MIN, Math.max(COLUMN_MIN, room - COLUMN_MIN));
  }

  // Columns of panels side by side; a column may hold a stack of two.
  function columns(names, box, mode) {
    var rects = {};
    var cuts = [];
    if (names.length === 1) {
      rects[names[0]] = box;
      return { rects: rects, cuts: cuts };
    }
    var first = firstWidth(names[0], box.w);
    rects[names[0]] = { x: box.x, y: box.y, w: first, h: box.h };
    cuts.push({ kind: 'col1', x: box.x + first, y: box.y, h: box.h });
    var restX = box.x + first + GAP;
    var restW = box.w - first - GAP;
    var rest = names.slice(1);
    if (rest.length === 1) {
      rects[rest[0]] = { x: restX, y: box.y, w: restW, h: box.h };
    } else if (mode === 'tri') {
      var second = clamp(savedNumber('starve.col2') || Math.round(restW / 2), COLUMN_MIN, Math.max(COLUMN_MIN, restW - COLUMN_MIN));
      rects[rest[0]] = { x: restX, y: box.y, w: second, h: box.h };
      rects[rest[1]] = { x: restX + second + GAP, y: box.y, w: restW - second - GAP, h: box.h };
      cuts.push({ kind: 'col2', x: restX + second, y: box.y, h: box.h });
    } else {
      // A video on top of the stack defaults to its own 16:9 height, so it starts without bars.
      var natural = rest[0] === 'video' ? Math.round(restW * 9 / 16) + BAR : Math.round(box.h / 2);
      var top = clamp(savedNumber('starve.row') || natural, ROW_MIN, Math.max(ROW_MIN, box.h - ROW_MIN));
      rects[rest[0]] = { x: restX, y: box.y, w: restW, h: top };
      rects[rest[1]] = { x: restX, y: box.y + top + GAP, w: restW, h: box.h - top - GAP };
      cuts.push({ kind: 'row', x: restX, y: box.y + top, w: restW });
    }
    return { rects: rects, cuts: cuts };
  }

  function tiles(names, box) {
    var mode = tiledVideo() ? body.dataset.video : 'split';
    if (mode === 'bottom' && names.indexOf('video') >= 0) {
      var band = clamp(savedNumber('starve.band') || Math.round(Math.min(box.h * 0.45, box.w * 9 / 16 * 0.5)) + BAR,
        ROW_MIN, Math.max(ROW_MIN, box.h - ROW_MIN));
      var above = { x: box.x, y: box.y, w: box.w, h: box.h - band };
      var others = names.filter(function (name) { return name !== 'video'; });
      var placed = columns(others, above, 'split');
      placed.rects.video = { x: box.x, y: box.y + above.h, w: box.w, h: band };
      placed.cuts.push({ kind: 'band', x: box.x, y: box.y + above.h, w: box.w });
      return placed;
    }
    return columns(names, box, mode);
  }

  // The chrome drawn over the tiles: a title bar and a frame per panel. It lives in the sleeve,
  // which paints above the sheet and the reader, and below a floating video.
  function chrome() {
    var holder = document.getElementById('panes');
    if (holder) return holder;
    holder = document.createElement('div');
    holder.id = 'panes';
    holder.className = 'panes';
    var sleeve = document.querySelector('.sleeve');
    (sleeve || body).appendChild(holder);
    return holder;
  }

  var ARROW = { left: 'M10.5 3L5.5 8l5 5', right: 'M5.5 3l5 5-5 5' };

  function arrowButton(direction, name) {
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'pane-move';
    button.dataset.move = direction;
    button.dataset.pane = name;
    button.setAttribute('aria-label', 'Move ' + NAMES[name].toLowerCase() + ' panel ' + direction);
    button.title = 'Move ' + direction;
    button.innerHTML = '<svg class="icon" viewBox="0 0 16 16" aria-hidden="true"><path d="' + ARROW[direction] +
      '" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>';
    return button;
  }

  function paneBar(holder, name) {
    var bar = holder.querySelector('.pane-bar[data-pane="' + name + '"]');
    if (bar) return bar;
    bar = document.createElement('div');
    bar.className = 'pane-bar';
    bar.dataset.pane = name;
    var label = document.createElement('span');
    label.className = 'pane-name';
    label.textContent = NAMES[name];
    bar.appendChild(label);
    bar.appendChild(arrowButton('left', name));
    bar.appendChild(arrowButton('right', name));
    var frame = document.createElement('div');
    frame.className = 'pane-frame';
    frame.dataset.pane = name;
    holder.appendChild(frame);
    holder.appendChild(bar);
    return bar;
  }

  function setRect(node, rect) {
    node.style.setProperty('--px', rect.x + 'px');
    node.style.setProperty('--py', rect.y + 'px');
    node.style.setProperty('--pw', rect.w + 'px');
    node.style.setProperty('--ph', rect.h + 'px');
  }

  function clearRect(node) {
    if (!node) return;
    ['--px', '--py', '--pw', '--ph'].forEach(function (name) { node.style.removeProperty(name); });
  }

  var SPLITTERS = { col1: '.splitter', col2: '.splitter-2', row: '.video-splitter', band: '.video-splitter' };

  function placeSplitters(cuts) {
    ['.splitter', '.splitter-2', '.video-splitter'].forEach(function (selector) {
      var node = document.querySelector(selector);
      if (node) node.hidden = true;
    });
    cuts.forEach(function (cut) {
      var node = document.querySelector(SPLITTERS[cut.kind]);
      if (!node) return;
      node.hidden = false;
      node.dataset.cut = cut.kind;
      var across = cut.kind === 'row' || cut.kind === 'band';
      node.setAttribute('aria-orientation', across ? 'horizontal' : 'vertical');
      node.style.left = (across ? cut.x : cut.x - 6) + 'px';
      node.style.top = (across ? cut.y - 6 : cut.y) + 'px';
      node.style.width = across ? cut.w + 'px' : '';
      node.style.height = across ? '' : cut.h + 'px';
      var value = cut.kind === 'col1' ? cut.x - area().x : cut.kind === 'col2' ? cut.x : cut.y;
      node.setAttribute('aria-valuenow', String(Math.round(value)));
      if (cut.kind === 'col1' && body.dataset.list === 'strip') node.setAttribute('aria-valuetext', 'List folded to catalogue numbers');
      else node.removeAttribute('aria-valuetext');
    });
  }

  var laidOut = [];

  // A video alone in a tall column ends under the picture, so no empty band hangs below it.
  function videoFit(name, rect) {
    if (name !== 'video') return rect;
    var fitted = Math.round(rect.w * 9 / 16) + BAR;
    return fitted < rect.h ? { x: rect.x, y: rect.y, w: rect.w, h: fitted } : rect;
  }

  function layout() {
    var names = present();
    var holder = document.getElementById('panes');
    if (phone() || names.length < 2) {
      delete body.dataset.split;
      delete body.dataset.list;
      ['list', 'video', 'post'].forEach(function (name) { clearRect(element(name)); });
      if (holder) holder.replaceChildren();
      placeSplitters([]);
      laidOut = [];
      return;
    }
    var wasStrip = body.dataset.list === 'strip';
    body.dataset.split = 'on';
    var placed = tiles(names, area());
    holder = chrome();
    holder.querySelectorAll('.pane-bar, .pane-frame').forEach(function (node) {
      if (names.indexOf(node.dataset.pane) < 0) node.remove();
    });
    names.forEach(function (name, index) {
      var rect = videoFit(name, placed.rects[name]);
      var bar = paneBar(holder, name);
      setRect(bar, { x: rect.x, y: rect.y, w: rect.w, h: BAR });
      setRect(holder.querySelector('.pane-frame[data-pane="' + name + '"]'), rect);
      bar.querySelector('[data-move="left"]').disabled = index === 0;
      bar.querySelector('[data-move="right"]').disabled = index === names.length - 1;
      setRect(element(name), { x: rect.x, y: rect.y + BAR, w: rect.w, h: rect.h - BAR });
    });
    if (names.indexOf('video') < 0) clearRect(videoBox());
    if (names.indexOf('post') < 0) clearRect(element('post'));
    var listRect = placed.rects.list;
    if (listRect && listRect.w <= STRIP) body.dataset.list = 'strip';
    else delete body.dataset.list;
    if (wasStrip !== (body.dataset.list === 'strip')) fold();
    if (body.dataset.active && names.indexOf(body.dataset.active) < 0) delete body.dataset.active;
    placeSplitters(placed.cuts);
    laidOut = names;
  }

  // Crossing into or out of the strip plays the fold, even mid-drag.
  var foldTimer = null;

  function fold() {
    body.classList.remove('folding');
    void body.offsetWidth;
    body.classList.add('folding');
    clearTimeout(foldTimer);
    foldTimer = setTimeout(function () { body.classList.remove('folding'); }, 320);
  }

  // ---- Which panel is active: the one last clicked or focused gets the red outline --------------

  function paneAt(target) {
    if (!target || !target.closest) return null;
    var bar = target.closest('.pane-bar');
    if (bar) return bar.dataset.pane;
    if (target.closest('#sheet')) return 'list';
    if (target.closest('#reader')) return 'post';
    if (target.closest('#dock .ratio-16x9')) return 'video';
    return null;
  }

  function activate(name) {
    if (!name || body.dataset.split !== 'on' || laidOut.indexOf(name) < 0) return;
    body.dataset.active = name;
  }

  document.addEventListener('pointerdown', function (event) { activate(paneAt(event.target)); }, true);
  document.addEventListener('focusin', function (event) { activate(paneAt(event.target)); });

  // ---- Swapping panels: the arrow buttons, or dragging a title bar onto another panel ----------

  function swap(a, b) {
    var names = order();
    var i = names.indexOf(a);
    var j = names.indexOf(b);
    if (i < 0 || j < 0 || i === j) return;
    names[i] = b;
    names[j] = a;
    store('starve.panels', names.join(','));
    body.classList.add('swapping');
    layout();
    setTimeout(function () { body.classList.remove('swapping'); }, 260);
  }

  document.addEventListener('click', function (event) {
    var move = event.target.closest('.pane-move');
    if (!move) return;
    var names = laidOut;
    var index = names.indexOf(move.dataset.pane);
    var other = names[index + (move.dataset.move === 'left' ? -1 : 1)];
    if (other) swap(move.dataset.pane, other);
    var again = document.querySelector('.pane-bar[data-pane="' + move.dataset.pane + '"] [data-move="' + move.dataset.move + '"]');
    if (again && !again.disabled) again.focus();
    else if (again) again.parentElement.querySelector('.pane-move:not([disabled])').focus();
  });

  function paneUnder(x, y) {
    var hit = null;
    document.querySelectorAll('.pane-frame').forEach(function (frame) {
      var box = frame.getBoundingClientRect();
      if (x >= box.left && x <= box.right && y >= box.top && y <= box.bottom) hit = frame.dataset.pane;
    });
    return hit;
  }

  document.addEventListener('pointerdown', function (event) {
    var bar = event.target.closest('.pane-bar');
    if (!bar || event.target.closest('button') || event.button !== 0) return;
    event.preventDefault();
    var from = bar.dataset.pane;
    var startX = event.clientX;
    var startY = event.clientY;
    var dragging = false;
    bar.setPointerCapture(event.pointerId);
    function move(e) {
      if (!dragging && Math.abs(e.clientX - startX) + Math.abs(e.clientY - startY) < 6) return;
      if (!dragging) { dragging = true; body.classList.add('resizing', 'dragging-pane'); bar.classList.add('lifted'); }
      bar.style.transform = 'translate(' + (e.clientX - startX) + 'px,' + (e.clientY - startY) + 'px)';
      var over = paneUnder(e.clientX, e.clientY);
      document.querySelectorAll('.pane-frame').forEach(function (frame) {
        frame.classList.toggle('target', dragging && over === frame.dataset.pane && over !== from);
      });
    }
    function up(e) {
      bar.releasePointerCapture(event.pointerId);
      bar.removeEventListener('pointermove', move);
      bar.removeEventListener('pointerup', up);
      bar.style.transform = '';
      bar.classList.remove('lifted');
      body.classList.remove('resizing', 'dragging-pane');
      document.querySelectorAll('.pane-frame.target').forEach(function (frame) { frame.classList.remove('target'); });
      if (!dragging) return;
      var over = paneUnder(e.clientX, e.clientY);
      if (over && over !== from) swap(from, over);
    }
    bar.addEventListener('pointermove', move);
    bar.addEventListener('pointerup', up);
  });

  // ---- Splitters: drag, or arrow keys; a double click resets ---------------------------------

  function resize(kind, position, remember) {
    var box = area();
    if (kind === 'col1') {
      var width = position - box.x;
      var first = laidOut[0];
      var value = first === 'list' && width < LIST_FOLD ? 'strip' : Math.round(Math.max(first === 'list' ? LIST_FOLD : COLUMN_MIN, width));
      store('starve.col1', value);
    } else if (kind === 'col2') {
      var col1 = document.querySelector('.splitter');
      var start = col1 ? col1.getBoundingClientRect().left + 6 : box.x;
      store('starve.col2', Math.round(Math.max(COLUMN_MIN, position - start)));
    } else if (kind === 'row') {
      store('starve.row', Math.round(Math.max(ROW_MIN, position - box.y)));
    } else if (kind === 'band') {
      store('starve.band', Math.round(Math.max(ROW_MIN, box.y + box.h - position)));
    }
    layout();
    void remember;
  }

  function splitterOf(target) {
    return target && target.closest && target.closest('.splitter, .splitter-2, .video-splitter');
  }

  document.addEventListener('pointerdown', function (event) {
    var splitter = splitterOf(event.target);
    if (!splitter || splitter.hidden) return;
    event.preventDefault();
    var kind = splitter.dataset.cut;
    var across = kind === 'row' || kind === 'band';
    body.classList.add('resizing');
    splitter.setPointerCapture(event.pointerId);
    function move(e) { resize(kind, across ? e.clientY : e.clientX, false); }
    function up(e) {
      splitter.releasePointerCapture(event.pointerId);
      splitter.removeEventListener('pointermove', move);
      splitter.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      resize(kind, across ? e.clientY : e.clientX, true);
    }
    splitter.addEventListener('pointermove', move);
    splitter.addEventListener('pointerup', up);
  });

  var DEFAULT_KEYS = { col1: 'starve.col1', col2: 'starve.col2', row: 'starve.row', band: 'starve.band' };

  document.addEventListener('dblclick', function (event) {
    var splitter = splitterOf(event.target);
    if (!splitter || splitter.hidden) return;
    try { localStorage.removeItem(DEFAULT_KEYS[splitter.dataset.cut]); } catch (e) { /* storage blocked */ }
    fold();
    layout();
  });

  document.addEventListener('keydown', function (event) {
    var splitter = splitterOf(event.target);
    if (!splitter || splitter.hidden) return;
    var kind = splitter.dataset.cut;
    var across = kind === 'row' || kind === 'band';
    var step = across ? { ArrowUp: -32, ArrowDown: 32 }[event.key] : { ArrowLeft: -32, ArrowRight: 32 }[event.key];
    if (!step) return;
    event.preventDefault();
    var box = splitter.getBoundingClientRect();
    var at = across ? box.top + 6 : box.left + 6;
    if (kind === 'col1' && body.dataset.list === 'strip' && step > 0) at = area().x + LIST_FOLD;
    resize(kind, at + step, true);
    splitter.focus();
  });

  // Escape unfolds a list folded to its strip.
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && body.dataset.list === 'strip' && laidOut[0] === 'list') {
      store('starve.col1', LIST_FOLD + 180);
      fold();
      layout();
    }
  });

  // ---- Picture in picture: a floating window, dragged by the video, resized by the grip ---------

  var PIP = { min: 240, max: 720, start: 360 };

  function pipBox() {
    var saved = {};
    try { saved = JSON.parse(stored('starve.pip') || '{}') || {}; } catch (e) { saved = {}; }
    return { right: saved.right === undefined ? 20 : saved.right, bottom: saved.bottom === undefined ? 16 : saved.bottom, w: saved.w || PIP.start };
  }

  function sizePip(box, remember) {
    var w = Math.round(clamp(box.w, PIP.min, Math.min(PIP.max, window.innerWidth - 40)));
    var h = Math.round(w * 9 / 16);
    var deck = player() ? player().offsetHeight : 0;
    var right = Math.round(clamp(box.right, 0, window.innerWidth - w));
    var bottom = Math.round(clamp(box.bottom, 0, window.innerHeight - deck - h - 8));
    body.style.setProperty('--pip-w', w + 'px');
    body.style.setProperty('--pip-right', right + 'px');
    body.style.setProperty('--pip-bottom', bottom + 'px');
    if (remember) store('starve.pip', JSON.stringify({ right: right, bottom: bottom, w: w }));
    return { right: right, bottom: bottom, w: w };
  }

  // The grip at the top-left corner resizes, keeping the bottom-right corner where it is.
  document.addEventListener('pointerdown', function (event) {
    var grip = event.target.closest('#dock .grip');
    if (!grip || body.dataset.video !== 'pip') return;
    event.preventDefault();
    var start = pipBox();
    var startX = event.clientX;
    body.classList.add('resizing');
    grip.setPointerCapture(event.pointerId);
    function move(e) { sizePip({ right: start.right, bottom: start.bottom, w: start.w + (startX - e.clientX) }, false); }
    function up(e) {
      grip.releasePointerCapture(event.pointerId);
      grip.removeEventListener('pointermove', move);
      grip.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      sizePip({ right: start.right, bottom: start.bottom, w: start.w + (startX - e.clientX) }, true);
    }
    grip.addEventListener('pointermove', move);
    grip.addEventListener('pointerup', up);
  });

  document.addEventListener('keydown', function (event) {
    var grip = event.target.closest && event.target.closest('#dock .grip');
    if (!grip || body.dataset.video !== 'pip') return;
    var step = { ArrowLeft: 32, ArrowUp: 32, ArrowRight: -32, ArrowDown: -32 }[event.key];
    if (!step) return;
    event.preventDefault();
    var box = pipBox();
    sizePip({ right: box.right, bottom: box.bottom, w: box.w + step }, true);
  });

  // ---- Picture in picture outside the browser (Document Picture-in-Picture, Chrome and Edge) ------

  // The video's box moves into an always-on-top window of its own. Moving an iframe into another
  // document reloads it, so the position and play state are carried over and restored once the
  // embed is ready again. Closing that window brings the video back into the page, in 2 col.
  // Without the API (Firefox, Safari) PiP stays the floating window inside the page.
  var pipWindow = null;
  var pipHome = null;

  function carryOver() {
    if (!state) return;
    state.resumeAt = state.position;
    state.resumePlaying = state.playing;
  }

  function popOut() {
    if (!window.documentPictureInPicture || phone() || !videoBox() || !state) return false;
    if (pipWindow) return true;
    var width = pipBox().w;
    window.documentPictureInPicture.requestWindow({ width: width, height: Math.round(width * 9 / 16) })
      .then(function (win) {
        pipWindow = win;
        win.document.title = 'Starve';
        win.document.body.className = 'pip-window';
        var style = win.document.createElement('style');
        style.textContent = 'html,body{margin:0;height:100%;background:#000}iframe{display:block;width:100%;height:100%;border:0}';
        win.document.head.appendChild(style);
        win.addEventListener('pagehide', popBack);
        // The video goes into a same-origin page inside the window, never into the window's own
        // about:blank document: there the embed would load with no Referer, and YouTube refuses it.
        var host = win.document.createElement('iframe');
        host.src = window.location.origin + '/pip.html';
        host.title = 'Picture in picture';
        host.addEventListener('load', function () {
          var box = videoBox();
          if (!box || pipWindow !== win) { win.close(); return; }
          var page = host.contentWindow;
          pipHome = { parent: box.parentNode, next: box.nextSibling, box: box };
          carryOver();
          page.document.body.appendChild(page.document.adoptNode(box));
          page.addEventListener('message', receiveMessage);
          page.document.addEventListener('click', function (event) {
            if (event.target.closest('.shield') && state) state.playing ? state.pause() : state.play();
          });
          layout();
        }, { once: true });
        win.document.body.appendChild(host);
      }, function () { sizePip(pipBox(), false); });
    return true;
  }

  function popBack() {
    var win = pipWindow;
    pipWindow = null;
    if (!win || !pipHome) return;
    // The box itself is held on to: by the time the window reports it is closing, the page that hosted
    // the video may already be gone, so it cannot be looked up there.
    var box = pipHome ? pipHome.box : null;
    if (box && pipHome.parent.isConnected) {
      carryOver();
      pipHome.parent.insertBefore(document.adoptNode(box), pipHome.next && pipHome.next.parentNode === pipHome.parent ? pipHome.next : null);
    }
    pipHome = null;
    if (body.dataset.video === 'pip' && videoBox()) place('split');
  }

  function closePip() {
    if (pipWindow) pipWindow.close();
  }

  // Dragging the floating video moves it; a click without a drag still plays or pauses.
  var draggedPip = false;

  document.addEventListener('pointerdown', function (event) {
    var shield = event.target.closest('#dock .shield');
    if (!shield || body.dataset.video !== 'pip' || phone() || event.button !== 0) return;
    var start = pipBox();
    var startX = event.clientX;
    var startY = event.clientY;
    draggedPip = false;
    shield.setPointerCapture(event.pointerId);
    function move(e) {
      if (!draggedPip && Math.abs(e.clientX - startX) + Math.abs(e.clientY - startY) < 6) return;
      if (!draggedPip) { draggedPip = true; body.classList.add('resizing'); }
      sizePip({ right: start.right - (e.clientX - startX), bottom: start.bottom - (e.clientY - startY), w: start.w }, false);
    }
    function up(e) {
      shield.releasePointerCapture(event.pointerId);
      shield.removeEventListener('pointermove', move);
      shield.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      if (draggedPip) sizePip({ right: start.right - (e.clientX - startX), bottom: start.bottom - (e.clientY - startY), w: start.w }, true);
    }
    shield.addEventListener('pointermove', move);
    shield.addEventListener('pointerup', up);
  });

  // ---- Transport ------------------------------------------------------------------------

  function clock(seconds) {
    if (!isFinite(seconds) || seconds < 0) return '--:--';
    var s = Math.floor(seconds % 60);
    var m = Math.floor(seconds / 60) % 60;
    var h = Math.floor(seconds / 3600);
    var mm = h ? String(m).padStart(2, '0') : String(m);
    return (h ? h + ':' : '') + mm + ':' + String(s).padStart(2, '0');
  }

  function render() {
    var current = player();
    if (!current || !state) return;
    var fraction = state.duration > 0 ? Math.min(1, state.position / state.duration) : 0;
    current.querySelector('.wave').style.setProperty('--progress', String(fraction));
    current.querySelector('.elapsed').textContent = clock(state.position);
    current.querySelector('.total').textContent = clock(state.duration);
    var seek = current.querySelector('.seek');
    if (!seek.matches(':active')) seek.value = String(Math.round(fraction * 1000));
    seek.setAttribute('aria-valuetext', clock(state.position) + ' of ' + clock(state.duration));
    var pause = current.querySelector('.pause');
    pause.setAttribute('aria-label', state.playing ? 'Pause' : 'Play');
    current.classList.toggle('paused', !state.playing);
    markDetail();
  }

  function enable() {
    var current = player();
    if (!current) return;
    current.querySelector('.pause').disabled = false;
    current.querySelector('.seek').disabled = false;
  }

  // Volume is a per-viewer convenience, remembered in this browser when storage allows.
  function savedVolume() {
    try {
      var stored = localStorage.getItem('starve.volume');
      if (stored !== null && !isNaN(Number(stored))) return Number(stored);
    } catch (e) { /* storage blocked */ }
    return 80;
  }

  function applyVolume(level) {
    var input = document.querySelector('#dock .level');
    if (input) {
      input.value = String(level);
      input.style.setProperty('--level', String(level / 100));
    }
    if (state) state.volume(level);
  }

  document.addEventListener('input', function (event) {
    if (!event.target.matches('#dock .level')) return;
    var level = Number(event.target.value);
    try { localStorage.setItem('starve.volume', String(level)); } catch (e) { /* storage blocked */ }
    applyVolume(level);
  });

  // Dragging the waveform only moves the playhead; the player seeks once, on release, so
  // YouTube is not sent into a buffering state (and its overlay) on every pixel.
  var seeking = false;

  document.addEventListener('input', function (event) {
    if (!event.target.matches('#dock .seek') || !state || !(state.duration > 0)) return;
    seeking = true;
    state.position = Number(event.target.value) / 1000 * state.duration;
    render();
  });

  document.addEventListener('change', function (event) {
    if (!event.target.matches('#dock .seek') || !state || !(state.duration > 0)) return;
    seeking = false;
    state.position = Number(event.target.value) / 1000 * state.duration;
    state.seek(state.position);
    render();
  });

  // The shield over the video keeps the pointer away from YouTube's own hover overlay:
  // a click plays or pauses, a double click goes fullscreen.
  document.addEventListener('click', function (event) {
    if (!event.target.closest('#dock .shield') || !state) return;
    if (draggedPip) { draggedPip = false; return; }
    state.playing ? state.pause() : state.play();
  });

  document.addEventListener('dblclick', function (event) {
    var shield = event.target.closest('#dock .shield');
    if (!shield) return;
    var box = shield.parentElement;
    if (document.fullscreenElement) document.exitFullscreen();
    else if (box.requestFullscreen) box.requestFullscreen();
  });

  function post(frame, origin, message) {
    if (frame && frame.contentWindow) frame.contentWindow.postMessage(JSON.stringify(message), origin);
  }

  function youtube(frame) {
    var command = function (func, args) { post(frame, YOUTUBE, { event: 'command', func: func, args: args || [] }); };
    var player = {
      origin: YOUTUBE, frame: frame, playing: false, position: 0, duration: 0,
      play: function () { command('playVideo'); },
      pause: function () { command('pauseVideo'); },
      seek: function (seconds) { command('seekTo', [seconds, true]); },
      volume: function (level) {
        command('setVolume', [level]);
        command(level > 0 ? 'unMute' : 'mute');
      },
      receive: function (data) {
        var info = data.info;
        if (data.event === 'onReady') {
          player.volume(savedVolume());
          // A moved iframe reloads: pick up where it was, playing or paused as it was.
          if (player.resumeAt !== undefined) {
            command('seekTo', [player.resumeAt, true]);
            command(player.resumePlaying ? 'playVideo' : 'pauseVideo');
            delete player.resumeAt;
          }
        }
        if (!info || typeof info !== 'object') return;
        if (typeof info.currentTime === 'number' && !seeking) player.position = info.currentTime;
        if (typeof info.duration === 'number') player.duration = info.duration;
        if (typeof info.playerState === 'number') player.playing = info.playerState === 1 || info.playerState === 3;
        enable();
        render();
      }
    };
    // The iframe API only reports once asked to; ask until it answers.
    var tries = 0;
    var listen = setInterval(function () {
      if (state !== player || player.duration > 0 || ++tries > 20) { clearInterval(listen); return; }
      post(frame, YOUTUBE, { event: 'listening', id: 'starve', channel: 'widget' });
    }, 500);
    return player;
  }

  function soundcloud(frame) {
    var call = function (method, value) {
      var message = { method: method };
      if (value !== undefined) message.value = value;
      post(frame, SOUNDCLOUD, message);
    };
    var player = {
      origin: SOUNDCLOUD, frame: frame, playing: false, position: 0, duration: 0,
      play: function () { call('play'); },
      pause: function () { call('pause'); },
      seek: function (seconds) { call('seekTo', seconds * 1000); },
      volume: function (level) { call('setVolume', level); },
      receive: function (data) {
        switch (data.method) {
          case 'ready':
            ['playProgress', 'play', 'pause', 'finish'].forEach(function (name) { call('addEventListener', name); });
            call('getDuration');
            call('getCurrentSound');
            player.volume(savedVolume());
            enable();
            break;
          case 'getDuration':
            player.duration = (data.value || 0) / 1000;
            break;
          case 'getCurrentSound':
            if (data.value && data.value.waveform_url) realWaveform(data.value.waveform_url);
            if (data.value && data.value.duration) player.duration = data.value.duration / 1000;
            break;
          case 'playProgress':
            player.playing = true;
            if (data.value && !seeking) player.position = (data.value.currentPosition || 0) / 1000;
            break;
          case 'play': player.playing = true; break;
          case 'pause': player.playing = false; break;
          case 'finish': player.playing = false; player.position = player.duration; break;
        }
        render();
      }
    };
    // Without the widget's handshake there is no transport: show SoundCloud's own player instead.
    setTimeout(function () {
      if (state === player && !(player.duration > 0)) frame.closest('.player').classList.add('native');
    }, 8000);
    return player;
  }

  // SoundCloud publishes each track's peaks; draw them over the stand-in when the browser may.
  function realWaveform(url) {
    fetch(url.replace(/\.png(\?.*)?$/, '.json'))
      .then(function (response) { return response.ok ? response.json() : Promise.reject(response.status); })
      .then(function (data) {
        var samples = data && data.samples;
        if (!samples || !samples.length) return;
        var peak = Math.max.apply(null, samples) || 1;
        document.querySelectorAll('#dock .bars').forEach(function (svg) {
          var rects = svg.querySelectorAll('rect');
          rects.forEach(function (rect, bar) {
            var from = Math.floor(bar / rects.length * samples.length);
            var to = Math.max(from + 1, Math.floor((bar + 1) / rects.length * samples.length));
            var top = Math.max.apply(null, samples.slice(from, to));
            var height = Math.max(8, Math.round(top / peak * 100));
            rect.setAttribute('y', String((100 - height) / 2));
            rect.setAttribute('height', String(height));
          });
        });
      })
      .catch(function () { /* the seeded waveform stays */ });
  }

  // The embeds answer their parent window: this page, or the picture-in-picture window while the
  // video has popped out, which forwards to the same handler.
  function receiveMessage(event) {
    if (!state || event.origin !== state.origin || event.source !== state.frame.contentWindow) return;
    var data = event.data;
    if (typeof data === 'string') {
      try { data = JSON.parse(data); } catch (e) { return; }
    }
    if (data && typeof data === 'object') state.receive(data);
  }

  window.addEventListener('message', receiveMessage);

  // ---- A title too long for the deck scrolls as a ticker ------------------------------

  function tickTitle() {
    var title = document.querySelector('#dock .now-title');
    if (!title) return;
    var text = title.querySelector('.ticker');
    if (!text) {
      text = document.createElement('span');
      text.className = 'ticker';
      text.textContent = title.textContent;
      title.textContent = '';
      title.appendChild(text);
      title.title = text.textContent;
    }
    var overflow = text.scrollWidth - title.clientWidth;
    title.classList.toggle('ticking', overflow > 4);
    title.style.setProperty('--shift', -Math.max(0, overflow) + 'px');
    title.style.setProperty('--tick', Math.max(6, overflow / 30 + 4) + 's');
  }

  window.addEventListener('resize', tickTitle);

  // ---- The reader: a post or a detail view in its own panel -------------------------------------

  function reader() { return document.getElementById('reader'); }

  function emptyReader() {
    var pane = reader();
    if (pane) pane.replaceChildren();
    body.classList.remove('reading');
    layout();
  }

  // Close empties the reader only; a video stays where it is. On a phone the list comes back
  // exactly where it was left.
  window.closeReader = function () {
    var item = postView() ? postView().dataset.item : null;
    emptyReader();
    var row = item ? document.querySelector('.item[data-item="' + item + '"] .title') : null;
    if (row && phone()) row.focus({ preventScroll: true });
    else {
      var sheet = document.getElementById('sheet');
      if (sheet) sheet.focus({ preventScroll: true });
    }
  };

  // Opening a post marks its row read (the server already did) and makes the reader the active panel.
  function readerOpened() {
    var view = postView();
    if (!view) return;
    var pane = reader();
    if (pane) pane.scrollTop = 0;
    body.classList.toggle('reading', phone());
    layout();
    activate('post');
    markRowRead(view.dataset.item);
    var close = view.querySelector('.reader-close');
    if (close && phone()) close.focus({ preventScroll: true });
  }

  function markRowRead(itemId) {
    var row = document.querySelector('.item[data-item="' + itemId + '"]');
    if (!row) return;
    row.classList.add('read');
    var markRead = row.querySelector('form[action$="/read"]');
    if (markRead) markRead.remove();
  }

  // ---- Phones: the reader's head returns when scrolling up; a button goes back to the top --------

  var lastReaderTop = 0;

  document.addEventListener('scroll', function (event) {
    var pane = reader();
    if (event.target !== pane || !postView()) return;
    var top = pane.scrollTop;
    var head = postView().querySelector('.reader-head');
    if (head) head.classList.toggle('tucked', phone() && top > lastReaderTop && top > 120);
    lastReaderTop = top;
    syncTop();
  }, true);

  window.addEventListener('scroll', syncTop, { passive: true });

  function scroller() {
    if (body.classList.contains('reading') && postView()) return reader();
    if (body.dataset.split === 'on') return document.getElementById('sheet');
    return null;
  }

  function topButton() {
    var button = document.getElementById('to-top');
    if (button) return button;
    button = document.createElement('button');
    button.type = 'button';
    button.id = 'to-top';
    button.className = 'to-top';
    button.setAttribute('aria-label', 'Back to the top');
    button.title = 'Back to the top';
    button.innerHTML = '<svg class="icon" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2.5l5.5 6H9.5v5h-3v-5h-4z"/></svg>';
    body.appendChild(button);
    return button;
  }

  function syncTop() {
    if (!phone()) { var old = document.getElementById('to-top'); if (old) old.hidden = true; return; }
    var pane = scroller();
    var top = pane ? pane.scrollTop : window.scrollY;
    topButton().hidden = top < window.innerHeight * 0.8;
  }

  document.addEventListener('click', function (event) {
    if (!event.target.closest('#to-top')) return;
    var pane = scroller();
    var smooth = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
    if (pane) pane.scrollTo({ top: 0, behavior: smooth });
    else window.scrollTo({ top: 0, behavior: smooth });
    if (postView()) {
      var head = postView().querySelector('.reader-head');
      if (head) head.classList.remove('tucked');
    }
  });

  // ---- Marking read: the rows strike through before the server's answer replaces them -----------

  document.addEventListener('htmx:beforeRequest', function (event) {
    var form = event.detail.elt;
    if (!form || !form.matches) return;
    if (form.matches('form.read-all')) {
      var sheet = document.getElementById('sheet');
      if (sheet) {
        sheet.querySelectorAll('.item:not(.read)').forEach(function (row, index) {
          row.style.setProperty('--stagger', Math.min(index, 12) * 22 + 'ms');
          row.classList.add('clearing');
        });
      }
    } else if (form.matches('form[action$="/read"]')) {
      var row = form.closest('.item');
      if (row) row.classList.add('clearing');
    }
  });

  // ---- The detail view: its control reads Pause while its item plays in the deck ----------

  // A detail view is keyed to its item, so it can tell whether that item is the one in the deck.
  function markDetail() {
    var current = player();
    var id = current ? current.dataset.item : null;
    document.querySelectorAll('#reader .detail-view').forEach(function (view) {
      var inDeck = id !== null && view.dataset.item === id;
      var playing = inDeck && !!state && state.playing;
      view.classList.toggle('current', inDeck);
      var play = view.querySelector('.detail-play');
      if (!play) return;
      var label = playing ? 'Pause' : 'Play';
      if (play.textContent !== label) play.textContent = label;
      play.setAttribute('aria-pressed', String(playing));
    });
  }

  // While its item is in the deck the control pauses or resumes it; htmx leaves that click alone.
  document.addEventListener('click', function (event) {
    var play = event.target.closest('#reader .detail-play');
    if (!play || !play.closest('.current') || !state) return;
    if (state.playing) state.pause(); else state.play();
  });

  // Whether the pane already shows the detail view or post of this item.
  function readerShows(id) {
    var view = document.querySelector('#reader .reader-view');
    return !!view && view.dataset.item === id;
  }

  // A failed request leaves the rows as they were.
  document.addEventListener('htmx:afterRequest', function (event) {
    if (event.detail.successful) return;
    document.querySelectorAll('.item.clearing').forEach(function (row) { row.classList.remove('clearing'); });
  });

  // ---- A new player arrives -------------------------------------------------------------

  function start() {
    var current = player();
    state = null;
    if (!current) return;
    body.dataset.playing = current.dataset.kind;
    var video = current.querySelector('.ratio-16x9 iframe');
    var audio = current.querySelector('.sc-frame');
    trackDeck();
    if (video) {
      place(savedLayout());
      activate('video');
      state = youtube(video);
      video.addEventListener('load', function () { post(video, YOUTUBE, { event: 'listening', id: 'starve', channel: 'widget' }); });
    } else {
      delete body.dataset.video;
      layout();
      if (audio) state = soundcloud(audio);
    }
    applyVolume(savedVolume());
    tickTitle();
  }

  document.addEventListener('htmx:afterSwap', function (event) {
    if (event.detail && event.detail.target === dock()) start();
    if (event.detail && event.detail.target === reader()) readerOpened();
  });
  document.addEventListener('htmx:afterSettle', markPlaying);

  // ---- Measured heights -----------------------------------------------------------------

  // The deck and the bar are measured so the page keeps their room free and the obi sits
  // between them; the bar grows when the group tabs wrap.
  function trackDeck() {
    var deck = player();
    body.style.setProperty('--deck-h', deck ? deck.offsetHeight + 'px' : '0px');
  }

  var observer = null;

  function trackBar() {
    var bar = document.getElementById('bar');
    if (bar) body.style.setProperty('--bar-h', bar.offsetHeight + 'px');
  }

  var pending = 0;

  function relayout() {
    if (pending) return;
    pending = requestAnimationFrame(function () {
      pending = 0;
      trackBar();
      trackDeck();
      layout();
      if (body.dataset.video === 'pip') sizePip(pipBox(), false);
      syncTop();
    });
  }

  function trackHeights() {
    if (!window.ResizeObserver) return;
    if (observer) observer.disconnect();
    observer = new ResizeObserver(relayout);
    var target = dock();
    if (target) observer.observe(target);
    var bar = document.getElementById('bar');
    if (bar) observer.observe(bar);
  }

  // A page tab swaps the bar in place (the dock and the deck stay), and the bar may change height.
  document.addEventListener('htmx:afterSettle', function () {
    trackHeights();
    relayout();
  });

  // Every htmx request carries the CSRF token from the XSRF-TOKEN cookie, as the X-CSRF-TOKEN header.
  document.addEventListener('htmx:configRequest', function (event) {
    var token = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    if (token) event.detail.headers['X-CSRF-TOKEN'] = token[1];
  });

  window.addEventListener('resize', relayout);
  trackBar();
  trackHeights();
  markPlaying();
  syncTop();
})();
