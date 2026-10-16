// The player deck and the workspace. What plays sits in #dock, which htmx never swaps, so the music
// outlives every change of the sheet. On a desktop the list, a playing video and an open post are
// tiled like tmux panels: each has a title bar, rules between them resize them, dragging a title bar
// onto another panel splits it (or swaps the two), and the panel last clicked is outlined. Layout only changes
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

  function forget(key) {
    try { localStorage.removeItem(key); } catch (e) { /* storage blocked */ }
  }

  // "STV 0030" is item 30.
  function itemNumber(catalogue) { return String(parseInt(String(catalogue).replace(/\D/g, ''), 10)); }

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
    forget('starve.open.play');
    forget('starve.open.at');
    var target = document.querySelector('.item.playing .play') || document.getElementById('sheet');
    button.closest('.player').remove();
    state = null;
    delete body.dataset.playing;
    delete body.dataset.video;
    if (body.dataset.active === 'video') delete body.dataset.active;
    trackDeck();
    markPlaying();
    if (target) target.focus();
  };

  // ---- The workspace: panels tiled beside the obi, arranged by dragging their title bars ----------
  //
  // The arrangement is a tree. A leaf is a panel name; a split is { dir: 'row' | 'col', parts, sizes }, its
  // sizes fractions of its length. Dragging a title bar onto a panel splits that panel on the side it is
  // dropped (or swaps the two, dropped in the middle); dropped on an edge of the workspace, it takes that
  // whole edge. Every split has a rule to drag. Panels not on screen are left out when it is laid out.

  var NAMES = { list: 'List', video: 'Video', post: 'Post' };
  var BAR = 28;
  var STRIP = 136;
  var LIST_FOLD = 610;
  var MIN = 200;
  var EDGE = 28;
  var DEFAULT_TREE = { dir: 'row', parts: ['list', { dir: 'col', parts: ['video', 'post'], sizes: [0.5, 0.5] }], sizes: [0.5, 0.5] };

  function videoBox() { return document.querySelector('#dock .ratio-16x9'); }
  function postView() { return document.querySelector('#reader .reader-view'); }

  function element(name) {
    if (name === 'list') return document.getElementById('sheet');
    if (name === 'video') return videoBox();
    return document.getElementById('reader');
  }

  function leaves(node) {
    return typeof node === 'string' ? [node] : node.parts.reduce(function (all, part) { return all.concat(leaves(part)); }, []);
  }

  function copy(node) { return JSON.parse(JSON.stringify(node)); }

  function validTree(node) {
    var names = leaves(node).sort().join(',');
    return names === 'list,post,video';
  }

  var tree = (function () {
    try {
      var saved = JSON.parse(stored('starve.tree') || 'null');
      if (saved && validTree(saved)) return saved;
    } catch (e) { /* a broken value: start over */ }
    return copy(DEFAULT_TREE);
  })();

  function saveTree() { store('starve.tree', JSON.stringify(tree)); }

  // Only the timeline shows an open post or a video. Elsewhere (Sources) they stay alive but out of sight, so
  // music keeps playing and both are back where they were on the way back to the timeline.
  function onTimeline() { return !!document.querySelector('#sheet[data-page="timeline"]'); }

  function present() {
    if (!onTimeline()) return ['list'];
    return ['list', 'video', 'post'].filter(function (name) {
      if (name === 'list') return true;
      if (name === 'video') return !!videoBox();
      return !!postView();
    });
  }

  // The tree with only the panels on screen; a split left with one part gives way to it. Each kept split
  // remembers the full tree's split it came from and which of its parts it kept, so a resize writes back.
  function prune(node, keep) {
    if (typeof node === 'string') return keep.indexOf(node) >= 0 ? node : null;
    var parts = [];
    var sizes = [];
    var kept = [];
    node.parts.forEach(function (part, index) {
      var pruned = prune(part, keep);
      if (!pruned) return;
      parts.push(pruned);
      sizes.push(node.sizes[index]);
      kept.push(index);
    });
    if (!parts.length) return null;
    if (parts.length === 1) return parts[0];
    var total = sizes.reduce(function (a, b) { return a + b; }, 0) || 1;
    return { dir: node.dir, parts: parts, sizes: sizes.map(function (s) { return s / total; }), origin: node, kept: kept };
  }

  function area() {
    var sleeve = document.querySelector('.series-block');
    var left = sleeve ? Math.round(sleeve.getBoundingClientRect().right) : 0;
    var bar = document.getElementById('bar');
    var top = bar ? Math.max(0, Math.round(bar.getBoundingClientRect().bottom)) : 0;
    var deck = player() ? player().offsetHeight : 0;
    // The page's client width, not the window's: the scrollbar (its gutter is always kept) is not ours to tile.
    var width = document.documentElement.clientWidth;
    return { x: left, y: top, w: width - left, h: window.innerHeight - top - deck };
  }

  // Lays [node] out in [box]: panel rectangles into out.rects, one cut per rule between parts into out.cuts.
  function lay(node, box, out) {
    if (typeof node === 'string') { out.rects[node] = box; return; }
    var across = node.dir === 'row';
    var total = across ? box.w : box.h;
    var lengths = node.sizes.map(function (size) { return Math.round(total * size); });
    // A list squeezed below its fold shows its strip of catalogue numbers; a neighbour takes the room.
    if (across) {
      node.parts.forEach(function (part, index) {
        if (part !== 'list' || lengths[index] >= LIST_FOLD) return;
        var neighbour = index + 1 < lengths.length ? index + 1 : index - 1;
        lengths[neighbour] += lengths[index] - STRIP;
        lengths[index] = STRIP;
      });
    }
    lengths[lengths.length - 1] = total - lengths.slice(0, -1).reduce(function (a, b) { return a + b; }, 0);
    var at = across ? box.x : box.y;
    node.parts.forEach(function (part, index) {
      var length = lengths[index];
      lay(part, across ? { x: at, y: box.y, w: length, h: box.h } : { x: box.x, y: at, w: box.w, h: length }, out);
      at += length;
      if (index < node.parts.length - 1) {
        out.cuts.push({ node: node, index: index, across: across, at: at, start: across ? box.x : box.y, total: total,
          x: across ? at : box.x, y: across ? box.y : at, length: across ? box.h : box.w });
      }
    });
  }

  // The chrome over the tiles: a title bar and a frame per panel. It lives in the sleeve, which paints
  // above the sheet and the reader.
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

  // Closing a panel: the post closes, the video is ejected as the deck's Eject does; the list stays.
  function closeButton(name) {
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'pane-close';
    button.dataset.pane = name;
    button.setAttribute('aria-label', name === 'video' ? 'Close the video' : 'Close the post');
    button.title = 'Close';
    button.innerHTML = '<svg class="icon" viewBox="0 0 16 16" aria-hidden="true"><path d="M4 4l8 8M12 4l-8 8" ' +
      'fill="none" stroke="currentColor" stroke-width="1.6"/></svg>';
    return button;
  }

  function paneBar(holder, name) {
    var bar = holder.querySelector('.pane-bar[data-pane="' + name + '"]');
    if (bar) return bar;
    bar = document.createElement('div');
    bar.className = 'pane-bar';
    bar.dataset.pane = name;
    bar.title = 'Drag onto a panel to split it, or onto its middle to swap';
    var label = document.createElement('span');
    label.className = 'pane-name';
    label.textContent = NAMES[name];
    bar.appendChild(label);
    if (name !== 'list') bar.appendChild(closeButton(name));
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

  // Three panels need at most two rules; the page carries that many separators to place.
  var SPLITTERS = ['.splitter', '.splitter-2', '.video-splitter'];
  var cuts = [];

  function placeSplitters() {
    SPLITTERS.forEach(function (selector, index) {
      var node = document.querySelector(selector);
      if (!node) return;
      var cut = cuts[index];
      node.hidden = !cut;
      if (!cut) return;
      node.dataset.cut = String(index);
      node.setAttribute('aria-orientation', cut.across ? 'vertical' : 'horizontal');
      node.style.left = (cut.across ? cut.x - 6 : cut.x) + 'px';
      node.style.top = (cut.across ? cut.y : cut.y - 6) + 'px';
      node.style.width = cut.across ? '' : cut.length + 'px';
      node.style.height = cut.across ? cut.length + 'px' : '';
      node.setAttribute('aria-valuemin', '0');
      node.setAttribute('aria-valuemax', '100');
      node.setAttribute('aria-valuenow', String(Math.round((cut.at - cut.start) / cut.total * 100)));
    });
  }

  // A video alone in a tall panel ends under the picture, so no empty band hangs below it.
  function videoFit(name, rect) {
    if (name !== 'video') return rect;
    var fitted = Math.round(rect.w * 9 / 16) + BAR;
    return fitted < rect.h ? { x: rect.x, y: rect.y, w: rect.w, h: fitted } : rect;
  }

  var laidOut = [];

  function layout() {
    if (onTimeline()) delete body.dataset.away;
    else body.dataset.away = 'on';
    var names = present();
    var holder = document.getElementById('panes');
    if (phone() || names.length < 2) {
      delete body.dataset.split;
      delete body.dataset.list;
      ['list', 'video', 'post'].forEach(function (name) { clearRect(element(name)); });
      if (holder) holder.replaceChildren();
      cuts = [];
      placeSplitters();
      laidOut = [];
      return;
    }
    var wasStrip = body.dataset.list === 'strip';
    body.dataset.split = 'on';
    var out = { rects: {}, cuts: [] };
    lay(prune(tree, names), area(), out);
    cuts = out.cuts;
    holder = chrome();
    holder.querySelectorAll('.pane-bar, .pane-frame').forEach(function (node) {
      if (names.indexOf(node.dataset.pane) < 0) node.remove();
    });
    names.forEach(function (name) {
      var rect = videoFit(name, out.rects[name]);
      setRect(paneBar(holder, name), { x: rect.x, y: rect.y, w: rect.w, h: BAR });
      setRect(holder.querySelector('.pane-frame[data-pane="' + name + '"]'), rect);
      setRect(element(name), { x: rect.x, y: rect.y + BAR, w: rect.w, h: rect.h - BAR });
    });
    if (names.indexOf('video') < 0) clearRect(videoBox());
    if (names.indexOf('post') < 0) clearRect(element('post'));
    if (out.rects.list && out.rects.list.w <= STRIP) body.dataset.list = 'strip';
    else delete body.dataset.list;
    if (wasStrip !== (body.dataset.list === 'strip')) fold();
    if (body.dataset.active && names.indexOf(body.dataset.active) < 0) delete body.dataset.active;
    placeSplitters();
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

  document.addEventListener('click', function (event) {
    var close = event.target.closest('.pane-close');
    if (!close) return;
    if (close.dataset.pane === 'post') window.closeReader();
    else if (close.dataset.pane === 'video') {
      var eject = document.querySelector('#dock .stop');
      if (eject) window.stopPlayer(eject);
    }
  });

  // ---- Rearranging: drop a title bar on a panel's side to split it, its middle to swap, an edge to span

  // The split of the tree that holds [name] directly, with the part's index; null for the root itself.
  function parentOf(node, name) {
    if (typeof node === 'string') return null;
    for (var index = 0; index < node.parts.length; index++) {
      var part = node.parts[index];
      if (part === name) return { node: node, index: index };
      var found = parentOf(part, name);
      if (found) return found;
    }
    return null;
  }

  // Takes [name] out of the tree; a split left with one part is replaced by that part.
  function detach(name) {
    var at = parentOf(tree, name);
    if (!at) return;
    at.node.parts.splice(at.index, 1);
    at.node.sizes.splice(at.index, 1);
    var total = at.node.sizes.reduce(function (a, b) { return a + b; }, 0) || 1;
    at.node.sizes = at.node.sizes.map(function (size) { return size / total; });
    if (at.node.parts.length > 1) return;
    var only = at.node.parts[0];
    var outer = parentOfNode(tree, at.node);
    if (outer) outer.node.parts[outer.index] = only;
    else tree = typeof only === 'string' ? { dir: 'row', parts: [only], sizes: [1] } : only;
  }

  function parentOfNode(node, child) {
    if (typeof node === 'string') return null;
    for (var index = 0; index < node.parts.length; index++) {
      if (node.parts[index] === child) return { node: node, index: index };
      var found = parentOfNode(node.parts[index], child);
      if (found) return found;
    }
    return null;
  }

  var ZONES = {
    left: { dir: 'row', first: true }, right: { dir: 'row', first: false },
    top: { dir: 'col', first: true }, bottom: { dir: 'col', first: false }
  };

  function swapNames(a, b) {
    function walk(node) {
      if (typeof node === 'string') return node === a ? b : node === b ? a : node;
      node.parts = node.parts.map(walk);
      return node;
    }
    tree = walk(tree);
  }

  // Drops panel [from] onto [target] (a panel, or 'edge' for the whole workspace) in [zone].
  function drop(from, target, zone) {
    if (target !== 'edge' && zone === 'middle') { swapNames(from, target); return; }
    var side = ZONES[zone];
    detach(from);
    if (target === 'edge') {
      tree = { dir: side.dir, parts: side.first ? [from, tree] : [tree, from], sizes: side.first ? [0.35, 0.65] : [0.65, 0.35] };
      return;
    }
    var at = parentOf(tree, target);
    if (at && at.node.dir === side.dir) {
      // The same direction as the split around the target: a new part beside it, sharing its room.
      var share = at.node.sizes[at.index] / 2;
      at.node.sizes[at.index] = share;
      at.node.parts.splice(side.first ? at.index : at.index + 1, 0, from);
      at.node.sizes.splice(side.first ? at.index : at.index + 1, 0, share);
      return;
    }
    var split = { dir: side.dir, parts: side.first ? [from, target] : [target, from], sizes: [0.5, 0.5] };
    if (at) at.node.parts[at.index] = split;
    else tree = split;
  }

  function frameRect(name) {
    var frame = document.querySelector('.pane-frame[data-pane="' + name + '"]');
    return frame ? frame.getBoundingClientRect() : null;
  }

  // Where a drop at (x, y) lands: an outer edge of the workspace, or a side or the middle of a panel.
  function dropAt(x, y, from) {
    var box = area();
    var edges = { left: x - box.x, right: box.x + box.w - x, top: y - box.y, bottom: box.y + box.h - y };
    var nearest = Object.keys(edges).reduce(function (a, b) { return edges[a] <= edges[b] ? a : b; });
    if (edges[nearest] >= 0 && edges[nearest] < EDGE) return { target: 'edge', zone: nearest, box: box };
    var hit = null;
    laidOut.forEach(function (name) {
      var rect = frameRect(name);
      if (rect && x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) hit = { name: name, rect: rect };
    });
    if (!hit || hit.name === from) return null;
    var dx = (x - hit.rect.left) / hit.rect.width;
    var dy = (y - hit.rect.top) / hit.rect.height;
    if (dx > 0.3 && dx < 0.7 && dy > 0.3 && dy < 0.7) return { target: hit.name, zone: 'middle', box: hit.rect };
    var sides = { left: dx, right: 1 - dx, top: dy, bottom: 1 - dy };
    var zone = Object.keys(sides).reduce(function (a, b) { return sides[a] <= sides[b] ? a : b; });
    return { target: hit.name, zone: zone, box: hit.rect };
  }

  // The cyan preview of where the panel will land.
  function hint(spot) {
    var shade = document.getElementById('drop-hint');
    if (!spot) { if (shade) shade.hidden = true; return; }
    if (!shade) {
      shade = document.createElement('div');
      shade.id = 'drop-hint';
      shade.className = 'drop-hint';
      chrome().appendChild(shade);
    }
    var b = spot.box;
    var left = b.left !== undefined ? b.left : b.x;
    var top = b.top !== undefined ? b.top : b.y;
    var width = b.width !== undefined ? b.width : b.w;
    var height = b.height !== undefined ? b.height : b.h;
    var part = spot.target === 'edge' ? 0.35 : 0.5;
    var rect = { x: left, y: top, w: width, h: height };
    if (spot.zone === 'left') rect.w = width * part;
    if (spot.zone === 'right') { rect.x = left + width * (1 - part); rect.w = width * part; }
    if (spot.zone === 'top') rect.h = height * part;
    if (spot.zone === 'bottom') { rect.y = top + height * (1 - part); rect.h = height * part; }
    setRect(shade, { x: Math.round(rect.x), y: Math.round(rect.y), w: Math.round(rect.w), h: Math.round(rect.h) });
    shade.hidden = false;
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
      hint(dropAt(e.clientX, e.clientY, from));
    }
    function up(e) {
      bar.releasePointerCapture(event.pointerId);
      bar.removeEventListener('pointermove', move);
      bar.removeEventListener('pointerup', up);
      bar.style.transform = '';
      bar.classList.remove('lifted');
      body.classList.remove('resizing', 'dragging-pane');
      hint(null);
      if (!dragging) return;
      var spot = dropAt(e.clientX, e.clientY, from);
      if (!spot) return;
      drop(from, spot.target, spot.zone);
      saveTree();
      body.classList.add('swapping');
      layout();
      setTimeout(function () { body.classList.remove('swapping'); }, 260);
    }
    bar.addEventListener('pointermove', move);
    bar.addEventListener('pointerup', up);
  });

  // ---- Rules between panels: drag, arrow keys (32px), double click to even the split out ------------

  // Sets the rule after part [index] of a laid-out split at [position] px, keeping both parts usable, and
  // writes the sizes back to the full tree.
  function moveCut(cut, position) {
    var node = cut.node;
    var before = node.sizes.slice(0, cut.index).reduce(function (a, b) { return a + b; }, 0);
    var pair = node.sizes[cut.index] + node.sizes[cut.index + 1];
    var minA = (node.parts[cut.index] === 'list' ? STRIP : MIN) / cut.total;
    var minB = (node.parts[cut.index + 1] === 'list' ? STRIP : MIN) / cut.total;
    var size = clamp((position - cut.start) / cut.total - before, minA, pair - minB);
    node.sizes[cut.index] = size;
    node.sizes[cut.index + 1] = pair - size;
    writeBack(node);
  }

  function writeBack(node) {
    var origin = node.origin || node;
    if (!node.kept) return;
    var share = node.kept.reduce(function (sum, index) { return sum + origin.sizes[index]; }, 0);
    node.kept.forEach(function (index, part) { origin.sizes[index] = node.sizes[part] * share; });
  }

  function evenOut(cut) {
    var node = cut.node;
    node.sizes = node.sizes.map(function () { return 1 / node.sizes.length; });
    writeBack(node);
  }

  function splitterOf(target) {
    return target && target.closest && target.closest('.splitter, .splitter-2, .video-splitter');
  }

  function cutOf(splitter) { return splitter && !splitter.hidden ? cuts[Number(splitter.dataset.cut)] : null; }

  document.addEventListener('pointerdown', function (event) {
    var splitter = splitterOf(event.target);
    var cut = cutOf(splitter);
    if (!cut) return;
    event.preventDefault();
    body.classList.add('resizing');
    splitter.setPointerCapture(event.pointerId);
    function move(e) {
      moveCut(cut, cut.across ? e.clientX : e.clientY);
      layout();
      cut = cuts[Number(splitter.dataset.cut)] || cut;
    }
    function up() {
      splitter.releasePointerCapture(event.pointerId);
      splitter.removeEventListener('pointermove', move);
      splitter.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      saveTree();
    }
    splitter.addEventListener('pointermove', move);
    splitter.addEventListener('pointerup', up);
  });

  document.addEventListener('dblclick', function (event) {
    var cut = cutOf(splitterOf(event.target));
    if (!cut) return;
    evenOut(cut);
    saveTree();
    // layout() plays the fold itself, and only when the list crosses into or out of its strip.
    layout();
  });

  document.addEventListener('keydown', function (event) {
    var splitter = splitterOf(event.target);
    var cut = cutOf(splitter);
    if (!cut) return;
    var step = cut.across ? { ArrowLeft: -32, ArrowRight: 32 }[event.key] : { ArrowUp: -32, ArrowDown: 32 }[event.key];
    if (!step && (event.key === 'Home' || event.key === 'End')) {
      event.preventDefault();
      evenOut(cut);
      saveTree();
      layout();
      return;
    }
    if (!step) return;
    event.preventDefault();
    moveCut(cut, cut.at + step);
    saveTree();
    layout();
    splitter.focus();
  });

  // In the strip a row is only its number: clicking it does what the hidden title would (play or read).
  document.addEventListener('click', function (event) {
    if (body.dataset.list !== 'strip') return;
    var row = event.target.closest && event.target.closest('#sheet .item');
    if (!row || event.target.closest('a, button, form')) return;
    var title = row.querySelector('.title');
    if (title) title.click();
  });

  // Escape unfolds a list folded to its strip: the split around it is evened out.
  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape' || body.dataset.list !== 'strip') return;
    var at = parentOf(tree, 'list');
    if (!at) return;
    at.node.sizes = at.node.sizes.map(function () { return 1 / at.node.sizes.length; });
    saveTree();
    layout();
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

  var lastSaved = 0;

  // ---- After a reload: the post, the player (paused where it was) and the list's scroll come back ------

  var resumeAt = null;

  function resume(current) {
    if (resumeAt === null || current !== state) return;
    var at = resumeAt;
    resumeAt = null;
    if (at > 0) current.seek(at);
    setTimeout(function () { current.pause(); }, 300);
  }

  var pendingTop = 0;

  // The stored post and player come back where they are missing: on a reload, or on arriving at the timeline.
  function reopenStored() {
    if (!window.htmx || !onTimeline() || phone()) return;
    var post = stored('starve.open.post');
    var play = stored('starve.open.play');
    // Each request comes from its own target, so neither cancels the other.
    if (post && !postView()) {
      window.htmx.ajax('GET', '/items/' + post + '/reader', { source: reader(), target: '#reader', swap: 'innerHTML' });
    }
    if (play && !player()) {
      resumeAt = Number(stored('starve.open.at')) || 0;
      window.htmx.ajax('GET', '/items/' + play + '/player', { source: dock(), target: '#dock', swap: 'innerHTML' });
    }
  }

  function restoreView() {
    if (!window.htmx || !onTimeline() || phone()) return;
    pendingTop = Number(stored('starve.list.top')) || 0;
    reopenStored();
    if (pendingTop) setTimeout(applyTop, 0);
  }

  // The list's scroll goes back once the panels are tiled (it only scrolls on its own when they are).
  function applyTop() {
    var sheet = document.getElementById('sheet');
    if (!pendingTop || !sheet || body.dataset.split !== 'on') return;
    sheet.scrollTop = pendingTop;
    if (sheet.scrollTop > 0) pendingTop = 0;
  }

  document.addEventListener('htmx:afterSettle', function () { requestAnimationFrame(applyTop); });

  document.addEventListener('scroll', function (event) {
    if (event.target && event.target.id === 'sheet' && body.dataset.split === 'on') {
      store('starve.list.top', Math.round(event.target.scrollTop));
    }
  }, true);

  function render() {
    var current = player();
    if (!current || !state) return;
    var fraction = state.duration > 0 ? Math.min(1, state.position / state.duration) : 0;
    if (state.position > 0 && Math.abs(state.position - lastSaved) >= 2) {
      lastSaved = state.position;
      store('starve.open.at', Math.floor(state.position));
    }
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

  // The deck's play/pause button.
  document.addEventListener('click', function (event) {
    if (!event.target.closest('#dock .pause') || !state) return;
    if (state.playing) state.pause(); else state.play();
  });

  // The shield over the video keeps the pointer away from YouTube's own hover overlay:
  // a click plays or pauses, a double click goes fullscreen.
  document.addEventListener('click', function (event) {
    if (!event.target.closest('#dock .shield') || !state) return;
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
          resume(player);
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
            resume(player);
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

  // The embeds answer their parent window, this page.
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
    forget('starve.open.post');
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
    store('starve.open.post', itemNumber(view.dataset.item));
    var pane = reader();
    if (pane) pane.scrollTop = 0;
    body.classList.toggle('reading', phone());
    layout();
    activate('post');
    markRowRead(view.dataset.item);
    var close = view.querySelector('.reader-close');
    if (close && phone()) close.focus({ preventScroll: true });
  }

  // ---- A post's image, clicked, opens large over the page; a click, Escape or × closes it -------------

  function lightbox() {
    var box = document.getElementById('lightbox');
    if (box) return box;
    box = document.createElement('dialog');
    box.id = 'lightbox';
    box.className = 'lightbox';
    box.setAttribute('aria-label', 'Image');
    box.innerHTML = '<img alt=""><button type="button" class="lightbox-close" aria-label="Close the image" ' +
      'title="Close"><svg class="icon" viewBox="0 0 16 16" aria-hidden="true"><path d="M4 4l8 8M12 4l-8 8" ' +
      'fill="none" stroke="currentColor" stroke-width="1.6"/></svg></button>';
    box.addEventListener('click', function () { box.close(); });
    body.appendChild(box);
    return box;
  }

  document.addEventListener('click', function (event) {
    var image = event.target.closest && event.target.closest('.reader-body img, .reader-image');
    if (!image || !image.currentSrc) return;
    // A picture that links to a larger copy shows that copy here, without leaving the app.
    var link = image.closest('a[href]');
    var large = link && /\.(avif|gif|jpe?g|png|svg|webp)(\?.*)?$/i.test(link.href) ? link.href : image.currentSrc;
    event.preventDefault();
    var box = lightbox();
    var shown = box.querySelector('img');
    shown.src = large;
    shown.alt = image.alt || '';
    if (box.showModal) box.showModal();
  });

  // Centre mode for posts: remembered in this browser, applied to every post.
  function syncCenter() {
    var on = stored('starve.center') === 'on';
    if (on) body.dataset.readerCenter = 'on';
    else delete body.dataset.readerCenter;
    document.querySelectorAll('.center-toggle').forEach(function (button) {
      button.setAttribute('aria-pressed', String(on));
    });
  }

  document.addEventListener('click', function (event) {
    if (!event.target.closest('.center-toggle')) return;
    store('starve.center', stored('starve.center') === 'on' ? 'off' : 'on');
    syncCenter();
  });

  document.addEventListener('htmx:afterSettle', syncCenter);

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

  // Changing group (or Favourites, Later): the new list deals in row by row and the obi's name slides up.
  // Armed before the swap, so the new rows are inserted already animating from invisible and never blink.
  var dealTimer = null;

  document.addEventListener('htmx:beforeSwap', function (event) {
    var elt = event.detail && event.detail.requestConfig && event.detail.requestConfig.elt;
    if (!elt || !elt.matches || !elt.matches('.group-link, .favourites-link, .later-link')) return;
    body.classList.add('dealing');
    clearTimeout(dealTimer);
    dealTimer = setTimeout(function () { body.classList.remove('dealing'); }, 1000);
  });

  // Sources swaps its whole sheet after every change; each column keeps its scroll position across it.
  var columnScroll = null;

  document.addEventListener('htmx:beforeSwap', function () {
    var columns = document.querySelectorAll('.sources-split .column-body');
    columnScroll = columns.length ? Array.prototype.map.call(columns, function (column) { return column.scrollTop; }) : null;
  });

  document.addEventListener('htmx:afterSettle', function () {
    if (!columnScroll) return;
    var saved = columnScroll;
    columnScroll = null;
    document.querySelectorAll('.sources-split .column-body').forEach(function (column, index) {
      if (saved[index] !== undefined) column.scrollTop = saved[index];
    });
  });

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
    store('starve.open.play', itemNumber(current.dataset.item));
    body.dataset.playing = current.dataset.kind;
    var video = current.querySelector('.ratio-16x9 iframe');
    var audio = current.querySelector('.sc-frame');
    trackDeck();
    if (video) {
      body.dataset.video = 'on';
      layout();
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

  // ---- Swaps between pages (theme.js swaps the shell for non-deck pages) ---------------------

  // A shell swap takes the dock with it, so the player it held is gone: forget it. Arriving on the timeline from
  // a page without the deck brings the stored post and player back, as a page load did.
  var awayFromTimeline = !onTimeline();
  document.addEventListener('htmx:afterSettle', function () {
    if (!player()) state = null;
    var away = !onTimeline();
    if (awayFromTimeline && !away) reopenStored();
    awayFromTimeline = away;
  });

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
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', restoreView);
  else restoreView();
  syncCenter();
  trackBar();
  trackHeights();
  markPlaying();
  syncTop();
})();
