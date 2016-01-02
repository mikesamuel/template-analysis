var init = (function () {
function init() {
  console.time('init');
  console.log('start init');
  // Allow all to overlap so that abbreviating doesn't cause a ridiculous
  // number of text reflows.
  var parseLog = $1('#parse-log');
  addClass(parseLog, 'overlap-all');

  console.time('abbrev');
  abbrev($1('#grammar'));
  console.timeEnd('abbrev');
  console.time('markMultilineOrs');
  markMultilineOrs();
  console.timeEnd('markMultilineOrs');
  console.time('logEntry');
  logEntry.init();
  console.timeEnd('logEntry');
  console.log('finish init');
  console.timeEnd('init');
}


function $(selector, optSource) {
  var all = (optSource || document).querySelectorAll(selector);
  var arr = [];
  for (var i = 0, n = all.length; i < n; ++i) {
    arr[i] = all[i];
  }
  return arr;
}

function $1(selector, optSource) {
  return (optSource || document).querySelector(selector);
}

function addClass(el, cn) {
  if (!el) { return; }
  el.className += ' ' + cn;
}

function removeClass(el, cn) {
  if (!el) { return; }
  var cls = el.className.split(/\s+/);
  el.className = cls.filter(function (x) { return x !== cn; }).join(' ');
}

function hasClass(el, cn) {
  return el.className.split(/\s+/).indexOf(cn) >= 0;
}


function calculateLineHeight(element) {
  if (element.hasAttribute('data-line-height')) {
    return +element.getAttribute('data-line-height');
  }

  // Courtesy http://stackoverflow.com/a/18430767/20394
  var lineHeight = parseInt(
      document.defaultView.getComputedStyle(element, null)
      .getPropertyValue('line-height'),
      10);
  var clone;
  var singleLineHeight;
  var doubleLineHeight;

  if (isNaN(lineHeight)) {
    clone = element.cloneNode();
    clone.innerHTML = '<br>';
    element.appendChild(clone);
    singleLineHeight = clone.offsetHeight;
    clone.innerHTML = '<br><br>';
    doubleLineHeight = clone.offsetHeight;
    element.removeChild(clone);
    lineHeight = doubleLineHeight - singleLineHeight;
  }

  element.setAttribute('data-line-height', lineHeight);
  return lineHeight;
}

function fitsOnOneLine(el) {
  var hgt = el.offsetHeight;
  var lnHgt = calculateLineHeight(el) || 1;
  return !((hgt / lnHgt) > 1.5);
}


function abbrev(container) {
  var abvs = $('.abv', container).slice(), abv;
  var i, n = abvs.length;

  // Add a wrapper around the abbreviable elements so that we can make the
  // abbreviated content invisible and still have a place for a visible
  // replacement.
  console.time('abbrev-step0');
  for (i = 0; i < n; ++i) {
    abv = abvs[i];
    var wrapper = document.createElement('span');
    wrapper.className = 'abv-wrapper';
    var diaresis = document.createElement('span');
    diaresis.className = 'diaresis';
    diaresis.appendChild(document.createTextNode('\u2026'));
    abv.parentNode.insertBefore(wrapper, abv);
    wrapper.appendChild(abv);
    wrapper.appendChild(diaresis);
  }
  console.timeEnd('abbrev-step0');

  // Annotate elements with index in array while we build a tree.
  console.time('abbrev-step1');
  for (i = 0; i < n; ++i) {
    abvs[i].setAttribute('data-abv-idx', '' + i);
  }
  console.timeEnd('abbrev-step1');

  console.time('abbrev-step2');
  // Now build tree relationships.
  for (i = 0; i < n; ++i) {
    abv = abvs[i];
    var parent = null;
    for (var p = abv.parentNode; p && p.nodeType === 1; p = p.parentNode) {
      if (hasClass(p, 'abv')) {
        parent = p;
        break;
      }
    }
    abvs[i] = {
      el: abv,
      parent: parent && abvs[parent.getAttribute('data-abv-idx')],
      children: []
    };
  }
  console.timeEnd('abbrev-step2');
  console.time('abbrev-step3');
  // Build child lists.
  for (i = 0; i < n; ++i) {
    abv = abvs[i];
    abv.el.removeAttribute('data-abv-idx');
    if (abv.parent) { abv.parent.children.push(abv); }
  }
  console.timeEnd('abbrev-step3');
  console.time('abbrev-step4');
  // Figure out the depth of nodes
  function depthOf(abv) {
    if ('depth' in abv) { return abv.depth; }
    var d = abv.parent ? 1 + depthOf(abv.parent) : 0;
    abv.depth = d;
    return d;
  }
  for (i = 0; i < n; ++i) {
    depthOf(abvs[i]);
  }
  console.timeEnd('abbrev-step4');

  // Sort abvs by depth and walk right to left figuring out whether abbreviating
  // frees up enough space so that a parent will fit on one line.
  console.time('abbrev-step5');
  abvs.sort(function (a, b) { return a.depth - b.depth; });
  console.timeEnd('abbrev-step5');

  console.time('abbrev-step6');
  for (i = n; --i >= 0;) {
    abv = abvs[i];
    var ancestorNeedsAbbreviation = null;
    for (var anc = abv.parent; anc; anc = anc.parent) {
      if (!fitsOnOneLine(anc.el)) {
        ancestorNeedsAbbreviation = anc;
        break;
      }
    }
    var overflowers = [];
    if (ancestorNeedsAbbreviation) {
      overflowers.push(ancestorNeedsAbbreviation.el);
      var offsetWidth = abv.el.offsetWidth;
      var offsetHeight = abv.el.offsetHeight;
      addClass(abv.el.parentNode, 'abbreviated');
    }
    for (var j = overflowers.length; --j >= 0;) {
      var overflower = overflowers[j];
      if (!hasClass(overflower, 'overflows')) {
        addClass(overflower, 'overflows');
      }
    }
  }
  console.timeEnd('abbrev-step6');
}


function markMultilineOrs() {
  $('.or.detail\\:long').forEach(function (or) {
    if (hasClass(or.lastChild, 'empty')) {
      // Skip x* and x?
      return;
    }
    or.style.float = 'left';
    if (!fitsOnOneLine(or)) {
      addClass(or, 'multiline');
    }
    or.style.float = '';
  });
}


var logEntry = (function () {
  var logEntries = null;
  var currentLogEntryIndex = -1;
  var counter;

  function setCurrent(newIndex) {
    var currentLogEntry = logEntries[currentLogEntryIndex];
    if (currentLogEntry) {
      removeClass(currentLogEntry, 'current');
    }
    currentLogEntryIndex = newIndex;
    counter.innerText = counter.textContent =
        (newIndex+1) + '/' + logEntries.length;
    currentLogEntry = logEntries[currentLogEntryIndex];
    if (currentLogEntry) {
      addClass(currentLogEntry, 'current');
      if (!hasClass(currentLogEntry, 'abbrev-run')) {
	addClass(currentLogEntry, 'abbrev-run');
	abbrev(currentLogEntry);
      }
    }
  }
  function adjustCurrent(delta) {
    var newIndex = currentLogEntryIndex + delta;
    setCurrent(Math.min(Math.max(0, newIndex), logEntries.length - 1));
  }
  function init() {
    var parseLog = $1('#parse-log');
    removeClass(parseLog, 'overlap-all');

    logEntries = $('#parse-log > li.entry');
    addClass(parseLog, 'slideshow');  // Hides all entries but .current.
    counter = document.createElement('tt');

    function makeButton(text, onClick) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.appendChild(document.createTextNode(text));
      btn.addEventListener('click', onClick);
      return btn;
    }

    var buttons = document.createElement('div');
    buttons.appendChild(makeButton('|\u21c7', setCurrent.bind(null, 0)));
    buttons.appendChild(makeButton('\u21c7', adjustCurrent.bind(null, -10)));
    buttons.appendChild(makeButton('\u2190', adjustCurrent.bind(null, -1)));
    buttons.appendChild(makeButton('\u2192', adjustCurrent.bind(null, +1)));
    buttons.appendChild(makeButton('\u21c9', adjustCurrent.bind(null, +10)));
    buttons.appendChild(makeButton('\u21c9|',
                                   setCurrent.bind(null, logEntries.length-1)));
    buttons.appendChild(counter);
    parseLog.parentNode.insertBefore(buttons, parseLog);

    setCurrent(0);
  }
  return { setCurrent: setCurrent, init: init };
}());


  return init;
}());
