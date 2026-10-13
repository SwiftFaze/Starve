// The player deck: what plays sits in #dock, which htmx never swaps, so the music outlives every
// change of the sheet. Placement, the list strip and the transport only change classes and data
// attributes; the embed iframe is never moved in the DOM, so nothing restarts.
(function () {
  'use strict';

  var body = document.body;
  var YOUTUBE = 'https://www.youtube-nocookie.com';
  var SOUNDCLOUD = 'https://w.soundcloud.com';
  var state = null;

  function dock() { return document.getElementById('dock'); }
  function player() { return document.querySelector('#dock .player'); }

  // ---- The playing row ------------------------------------------------------------------

  function markPlaying() {
    var current = player();
    document.querySelectorAll('.item.playing').forEach(function (row) {
      row.classList.remove('playing');
      var play = row.querySelector('.play');
      if (play) { play.textContent = 'Play'; play.setAttribute('aria-pressed', 'false'); }
    });
    syncSplitter();
    if (!current) return;
    var row = document.querySelector('.item[data-item="' + current.dataset.item + '"]');
    if (!row) return;
    row.classList.add('playing');
    var play = row.querySelector('.play');
    if (play) { play.textContent = 'Playing'; play.setAttribute('aria-pressed', 'true'); }
  }

  // Stop removes the focused button, so focus goes back to the row it was playing.
  window.stopPlayer = function (button) {
    var target = document.querySelector('.item.playing .play') || document.getElementById('sheet');
    button.closest('.player').remove();
    state = null;
    delete body.dataset.playing;
    delete body.dataset.video;
    delete body.dataset.list;
    markPlaying();
    trackDeck();
    if (target) target.focus();
  };

  // ---- Placement: pane, mini or deck ----------------------------------------------------

  function place(where) {
    body.dataset.video = where;
    syncSplitter();
    if (SIZES[where]) sizeVideo(where, videoWidth(where), false);
    requestAnimationFrame(tickTitle);
    document.querySelectorAll('#dock .placement button').forEach(function (button) {
      button.setAttribute('aria-pressed', String(button.dataset.place === where));
    });
    if (where === 'pane' && !window.matchMedia('(max-width: 60rem)').matches) sizeList(savedList(), false);
    else delete body.dataset.list;
    syncSplitter();
    if (where === 'pane') applyVideoHeight();
  }

  document.addEventListener('click', function (event) {
    var placeButton = event.target.closest('#dock .placement button');
    if (placeButton) { place(placeButton.dataset.place); return; }


    var pause = event.target.closest('#dock .pause');
    if (pause && state) { state.playing ? state.pause() : state.play(); }
  });

  // ---- The list beside the pane: drag its edge to resize; narrow enough, it folds to a strip

  // Below this width the rows would restack (the sheet's 34rem container query plus its
  // padding), so beside the video the list folds straight to its catalogue numbers instead.
  var STRIP_BELOW = 610;
  var LIST_MIN = 610;
  var LIST_DEFAULT = 720;

  function listLeft() {
    var sheet = document.getElementById('sheet');
    return sheet ? sheet.getBoundingClientRect().left : 0;
  }

  function maxList() { return Math.max(LIST_MIN, window.innerWidth - listLeft() - 480); }

  function savedList() {
    try {
      var stored = localStorage.getItem('starve.list');
      if (stored === 'strip') return 'strip';
      if (stored && !isNaN(Number(stored))) return Number(stored);
    } catch (e) { /* storage blocked */ }
    return LIST_DEFAULT;
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

  function sizeList(width, remember) {
    var wasStrip = body.dataset.list === 'strip';
    var value;
    if (width === 'strip' || width < STRIP_BELOW) {
      body.dataset.list = 'strip';
      body.style.removeProperty('--list-w');
      value = 'strip';
    } else {
      delete body.dataset.list;
      value = Math.round(Math.max(LIST_MIN, Math.min(maxList(), width)));
      body.style.setProperty('--list-w', value + 'px');
    }
    if (remember) {
      try { localStorage.setItem('starve.list', String(value)); } catch (e) { /* storage blocked */ }
    }
    if (wasStrip !== (body.dataset.list === 'strip')) fold();
    syncSplitter();
  }

  // The list and a pane share the window when a video sits in the pane or a post is open.
  function syncSplit() {
    if (body.dataset.video === 'pane' || document.querySelector('#reader .reader-view')) body.dataset.split = 'on';
    else delete body.dataset.split;
  }

  function syncSplitter() {
    syncSplit();
    var splitter = document.querySelector('.splitter');
    if (!splitter) return;
    var strip = body.dataset.list === 'strip';
    var sheet = document.getElementById('sheet');
    var width = sheet ? Math.round(sheet.getBoundingClientRect().width) : 0;
    splitter.setAttribute('aria-valuenow', String(strip ? 0 : width));
    splitter.setAttribute('aria-valuetext', strip ? 'List folded to catalogue numbers' : width + ' pixels');
  }

  document.addEventListener('pointerdown', function (event) {
    var splitter = event.target.closest('.splitter');
    if (!splitter || body.dataset.split !== 'on') return;
    event.preventDefault();
    var left = listLeft();
    body.classList.add('resizing');
    splitter.setPointerCapture(event.pointerId);
    function move(e) { sizeList(e.clientX - left, false); }
    function up(e) {
      splitter.releasePointerCapture(event.pointerId);
      splitter.removeEventListener('pointermove', move);
      splitter.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      sizeList(e.clientX - left, true);
    }
    splitter.addEventListener('pointermove', move);
    splitter.addEventListener('pointerup', up);
  });

  document.addEventListener('dblclick', function (event) {
    if (!event.target.closest('.splitter') || body.dataset.split !== 'on') return;
    fold();
    sizeList(LIST_DEFAULT, true);
  });

  document.addEventListener('keydown', function (event) {
    if (!event.target.matches || !event.target.matches('.splitter')) return;
    var step = { ArrowLeft: -32, ArrowRight: 32 }[event.key];
    if (!step) return;
    event.preventDefault();
    var current = body.dataset.list === 'strip' ? STRIP_BELOW - 1 : document.getElementById('sheet').getBoundingClientRect().width;
    sizeList(current < STRIP_BELOW && step > 0 ? LIST_MIN : current + step, true);
  });

  // ---- Video size in mini and deck ----------------------------------------------------

  // Each placement keeps its own width (16:9 sets the height); dragging the grip at the box's
  // top-left corner, or the arrow keys on it, changes it, and the browser remembers it.
  var SIZES = { mini: { min: 240, max: 720, start: 320 }, deck: { min: 288, max: 640, start: 384 } };

  function videoWidth(where) {
    var limits = SIZES[where];
    var width = limits.start;
    try {
      var stored = Number(localStorage.getItem('starve.video.' + where));
      if (stored) width = stored;
    } catch (e) { /* storage blocked */ }
    return Math.max(limits.min, Math.min(limits.max, Math.min(width, window.innerWidth - 160)));
  }

  function sizeVideo(where, width, remember) {
    if (!SIZES[where]) return;
    var limits = SIZES[where];
    var clamped = Math.round(Math.max(limits.min, Math.min(limits.max, Math.min(width, window.innerWidth - 160))));
    body.style.setProperty('--' + where + '-w', clamped + 'px');
    if (remember) {
      try { localStorage.setItem('starve.video.' + where, String(clamped)); } catch (e) { /* storage blocked */ }
    }
    trackDeck();
  }

  document.addEventListener('pointerdown', function (event) {
    var grip = event.target.closest('#dock .grip');
    var where = body.dataset.video;
    if (!grip || !SIZES[where]) return;
    event.preventDefault();
    var box = grip.parentElement;
    var startX = event.clientX;
    var startWidth = box.getBoundingClientRect().width;
    body.classList.add('resizing');
    grip.setPointerCapture(event.pointerId);
    function move(e) { sizeVideo(where, startWidth + (startX - e.clientX), false); }
    function up(e) {
      grip.releasePointerCapture(event.pointerId);
      grip.removeEventListener('pointermove', move);
      grip.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      sizeVideo(where, startWidth + (startX - e.clientX), true);
    }
    grip.addEventListener('pointermove', move);
    grip.addEventListener('pointerup', up);
  });

  document.addEventListener('keydown', function (event) {
    var grip = event.target.closest && event.target.closest('#dock .grip');
    var where = body.dataset.video;
    if (!grip || !SIZES[where]) return;
    var step = { ArrowLeft: 32, ArrowUp: 32, ArrowRight: -32, ArrowDown: -32 }[event.key];
    if (!step) return;
    event.preventDefault();
    sizeVideo(where, grip.parentElement.getBoundingClientRect().width + step, true);
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
    if (event.target.closest('#dock .shield') && state) state.playing ? state.pause() : state.play();
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
        if (data.event === 'onReady') player.volume(savedVolume());
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

  window.addEventListener('message', function (event) {
    if (!state || event.origin !== state.origin || event.source !== state.frame.contentWindow) return;
    var data = event.data;
    if (typeof data === 'string') {
      try { data = JSON.parse(data); } catch (e) { return; }
    }
    if (data && typeof data === 'object') state.receive(data);
  });

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

  // ---- The reader: a blog post beside the list --------------------------------------------

  function reader() { return document.getElementById('reader'); }

  function emptyReader() {
    var pane = reader();
    if (pane) pane.replaceChildren();
    syncSplitter();
  }

  // Close empties the pane only; a video stays where it is.
  window.closeReader = function () {
    emptyReader();
    var sheet = document.getElementById('sheet');
    if (sheet) sheet.focus();
  };

  // Opening a post marks its row read (the server already did). A video keeps its placement.
  function readerOpened() {
    var view = document.querySelector('#reader .reader-view');
    if (!view) return;
    layoutForReader();
    markRowRead(view.dataset.item);
    if (window.matchMedia('(max-width: 60rem)').matches) view.scrollIntoView({ block: 'start' });
  }

  function layoutForReader() {
    // The split layout must apply before the list is measured, or the sheet is still at its old left edge.
    syncSplitter();
    // Without a pane video the reader opens the list at its saved width, as a pane video would.
    if (body.dataset.video !== 'pane' && !window.matchMedia('(max-width: 60rem)').matches) sizeList(savedList(), false);
    applyVideoHeight();
  }

  function markRowRead(itemId) {
    var row = document.querySelector('.item[data-item="' + itemId + '"]');
    if (!row) return;
    row.classList.add('read');
    var markRead = row.querySelector('form[action$="/read"]');
    if (markRead) markRead.remove();
  }

  // ---- The video above the post: drag its lower edge to resize (a pane video with a post open) --

  var VIDEO_MIN = 120;
  var READER_MIN = 200;

  function videoOverPost() {
    return body.dataset.video === 'pane' && !!document.querySelector('#reader .reader-view') &&
      !window.matchMedia('(max-width: 60rem)').matches;
  }

  function pinnedVideo() { return document.querySelector('.ratio-16x9'); }

  // The most the video may grow: the post keeps READER_MIN pixels between the video and the deck.
  function videoMax() {
    var bar = document.getElementById('bar');
    var deck = parseFloat(getComputedStyle(body).getPropertyValue('--deck-h')) || 0;
    return Math.max(VIDEO_MIN, window.innerHeight - (bar ? bar.offsetHeight : 0) - deck - 96 - READER_MIN);
  }

  // The height is passed in when known: the video animates its height, so a measured value lags.
  function syncVideoSplitter(known) {
    var splitter = document.querySelector('.video-splitter');
    var video = pinnedVideo();
    if (!splitter || !video) return;
    var height = known === undefined ? Math.round(video.getBoundingClientRect().height) : known;
    splitter.setAttribute('aria-valuenow', String(height));
    splitter.setAttribute('aria-valuetext', height + ' pixels');
  }

  // The dragged height, or the 16:9 height the video is drawn at while no height has been set.
  function currentVideoHeight() {
    return parseFloat(body.style.getPropertyValue('--video-h')) || pinnedVideo().getBoundingClientRect().height;
  }

  function sizeVideoHeight(height, remember) {
    var value = Math.round(Math.max(VIDEO_MIN, Math.min(videoMax(), height)));
    body.style.setProperty('--video-h', value + 'px');
    if (remember) {
      try { localStorage.setItem('starve.videoh', String(value)); } catch (e) { /* storage blocked */ }
    }
    syncVideoSplitter(value);
  }

  // Restores the saved height while a pane video and a post are shown; otherwise the 16:9 default.
  function applyVideoHeight() {
    var stored = NaN;
    try { stored = Number(localStorage.getItem('starve.videoh')); } catch (e) { /* storage blocked */ }
    if (videoOverPost() && stored) sizeVideoHeight(stored, false);
    else body.style.removeProperty('--video-h');
    syncVideoSplitter();
  }

  document.addEventListener('pointerdown', function (event) {
    var splitter = event.target.closest && event.target.closest('.video-splitter');
    if (!splitter || !videoOverPost()) return;
    event.preventDefault();
    var top = pinnedVideo().getBoundingClientRect().top;
    body.classList.add('resizing');
    splitter.setPointerCapture(event.pointerId);
    function move(e) { sizeVideoHeight(e.clientY - top, false); }
    function up(e) {
      splitter.releasePointerCapture(event.pointerId);
      splitter.removeEventListener('pointermove', move);
      splitter.removeEventListener('pointerup', up);
      body.classList.remove('resizing');
      sizeVideoHeight(e.clientY - top, true);
    }
    splitter.addEventListener('pointermove', move);
    splitter.addEventListener('pointerup', up);
  });

  document.addEventListener('keydown', function (event) {
    if (!event.target.matches || !event.target.matches('.video-splitter') || !videoOverPost()) return;
    var step = { ArrowUp: -32, ArrowDown: 32 }[event.key];
    if (!step) return;
    event.preventDefault();
    sizeVideoHeight(currentVideoHeight() + step, true);
  });

  // ---- A new player arrives -------------------------------------------------------------

  function start() {
    var current = player();
    state = null;
    if (!current) return;
    body.dataset.playing = current.dataset.kind;
    var video = current.querySelector('.ratio-16x9 iframe');
    var audio = current.querySelector('.sc-frame');
    if (video) {
      emptyReader();
      place('pane');
      state = youtube(video);
      video.addEventListener('load', function () { post(video, YOUTUBE, { event: 'listening', id: 'starve', channel: 'widget' }); });
    } else {
      delete body.dataset.video;
      delete body.dataset.list;
      if (audio) state = soundcloud(audio);
    }
    syncSplitter();
    applyVolume(savedVolume());
    trackDeck();
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

  // The list starts where the sheet does: after the obi, and on Sources after the tool panel too.
  function trackList() {
    body.style.setProperty('--list-x', Math.round(listLeft()) + 'px');
  }

  function trackHeights() {
    if (!window.ResizeObserver) return;
    if (observer) observer.disconnect();
    observer = new ResizeObserver(function () {
      trackBar();
      trackDeck();
      trackList();
    });
    observer.observe(body);
    var target = dock();
    if (target) observer.observe(target);
    var sleeve = document.querySelector('.sleeve');
    if (sleeve) observer.observe(sleeve);
  }

  // A page tab swaps the bar in place (the dock and the deck stay), and the bar may change height.
  document.addEventListener('htmx:afterSettle', trackBar);

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && body.dataset.list === 'strip') sizeList(LIST_MIN + 180, true);
  });

  trackHeights();
  markPlaying();
})();
