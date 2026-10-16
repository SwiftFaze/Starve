// The docs page: every code block is labelled with its language, coloured by a small highlighter
// for the two languages the guide uses (shell commands and log lines), and gets a Copy button.
// The Clipboard API only exists on secure origins, and Starve is often reached over plain HTTP on a
// home network, so a hidden textarea and execCommand('copy') stand in there. Without JavaScript the
// blocks stay plain, selectable text.
(function () {
  'use strict';

  var NAMES = { shell: 'Shell', log: 'Log' };

  // ---- Highlighting -------------------------------------------------------------------------

  function span(kind, text) {
    var node = document.createElement('span');
    node.className = 'tok-' + kind;
    node.textContent = text;
    return node;
  }

  // A URL may wrap after each slash, and nowhere else, so it is never cut or split mid-word.
  function url(text) {
    var node = document.createElement('span');
    node.className = 'tok-url';
    text.split('/').forEach(function (part, index, parts) {
      node.appendChild(document.createTextNode(part + (index < parts.length - 1 ? '/' : '')));
      if (index < parts.length - 1) node.appendChild(document.createElement('wbr'));
    });
    return node;
  }

  // One shell line, as tokens: a comment, strings, operators, URLs, flags, and the command word that
  // starts the line or follows an operator.
  var SHELL = /(#.*$)|("[^"]*"|'[^']*')|(&&|\|\||\||;|>|<|2>&1)|(https?:\/\/\S+)|(\s--?[A-Za-z][\w-]*)|(\S+)|(\s+)/g;

  function shellLine(line, out) {
    var expectCommand = true;
    var match;
    SHELL.lastIndex = 0;
    while ((match = SHELL.exec(line)) !== null) {
      if (match[1]) out.push(span('comment', match[1]));
      else if (match[2]) { out.push(span('string', match[2])); expectCommand = false; }
      else if (match[3]) { out.push(span('operator', match[3])); expectCommand = true; }
      else if (match[4]) { out.push(url(match[4])); expectCommand = false; }
      else if (match[5]) { out.push(document.createTextNode(match[5].charAt(0))); out.push(span('flag', match[5].slice(1))); }
      else if (match[6]) {
        out.push(span(expectCommand ? 'command' : 'word', match[6]));
        expectCommand = false;
      } else out.push(document.createTextNode(match[7]));
      if (match[0] === '') SHELL.lastIndex++;
    }
  }

  // A log line: the message in the text colour, the value after the last colon picked out.
  function logLine(line, out) {
    var colon = line.lastIndexOf(': ');
    if (colon < 0) { out.push(document.createTextNode(line)); return; }
    out.push(span('comment', line.slice(0, colon + 2)));
    out.push(span('string', line.slice(colon + 2)));
  }

  function highlight(code, language) {
    var lines = code.textContent.split('\n');
    var out = [];
    lines.forEach(function (line, index) {
      if (index > 0) out.push(document.createTextNode('\n'));
      if (language === 'shell') shellLine(line, out);
      else logLine(line, out);
    });
    code.replaceChildren.apply(code, out);
  }

  // ---- Copy ---------------------------------------------------------------------------------

  function fallbackCopy(text) {
    var area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    var copied = false;
    try { copied = document.execCommand('copy'); } catch (e) { copied = false; }
    area.remove();
    return copied ? Promise.resolve() : Promise.reject(new Error('copy refused'));
  }

  function copy(text) {
    if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(text);
    return fallbackCopy(text);
  }

  function say(button, label) {
    button.textContent = label;
    clearTimeout(button.reset);
    button.reset = setTimeout(function () { button.textContent = 'Copy'; }, 1600);
  }

  // The code blocks of the page on screen. It runs on load and after each htmx swap that brings a docs page in,
  // and a block already enhanced is left alone.
  function enhance() {
    document.querySelectorAll('.docs-body pre:not(.has-copy)').forEach(enhancePre);
  }

  function enhancePre(pre) {
    var code = pre.querySelector('code') || pre;
    var text = code.textContent;
    var language = (code.className.match(/language-(\w+)/) || [])[1];
    if (NAMES[language]) {
      highlight(code, language);
      var label = document.createElement('span');
      label.className = 'code-lang';
      label.textContent = NAMES[language];
      label.setAttribute('aria-hidden', 'true');
      pre.appendChild(label);
    }
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'copy';
    button.textContent = 'Copy';
    button.setAttribute('aria-label', 'Copy this ' + (language === 'log' ? 'line' : 'command'));
    button.addEventListener('click', function () {
      copy(text).then(function () { say(button, 'Copied'); }, function () { say(button, 'Select and copy'); });
    });
    pre.classList.add('has-copy');
    pre.appendChild(button);
  }

  enhance();
  document.addEventListener('htmx:afterSettle', enhance);
})();
