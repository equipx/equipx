// EquipX 3D explorer. Kotlin -> JS: loadModel, selectPart, focusPart, resetCamera.
// JS -> Kotlin (window.EquipX): onHotspotTap(partId), onModelLoaded(), onModelError(message).
(function () {
  'use strict';

  // Hex string: three.js Color.set() treats it as sRGB and converts to linear (a byte/255 array would be taken as linear).
  var ACCENT = '#2383E2';
  var mv = document.getElementById('mv');
  var hotspots = {};           // partId -> {position:[x,y,z], number, el}
  var originalColors = {};     // material index -> original baseColorFactor
  var modelReady = false;
  var pendingSelect = null;
  var pendingFocus = null;
  var initialOrbit = null;
  var selectedId = null;
  var lastFocusId = null;
  var modelLoaded = false;      // <model-viewer> 'load' fired
  var hotspotsSettled = false;  // hotspots fetch finished (success or failure)

  function bridge() { return window.EquipX; }
  function call(name, arg) {
    try {
      var b = bridge();
      if (b && b[name]) { arg === undefined ? b[name]() : b[name](arg); }
    } catch (e) { /* ignore */ }
  }
  function fail(msg) { call('onModelError', String(msg)); }

  window.addEventListener('error', function (e) { if (modelReady) return; fail(e && e.message ? e.message : 'script error'); });

  function clearHotspots() {
    Object.keys(hotspots).forEach(function (id) {
      var el = hotspots[id].el;
      if (el && el.parentNode) el.parentNode.removeChild(el);
    });
    hotspots = {};
  }

  function fmtPos(p) { return p[0] + 'm ' + p[1] + 'm ' + p[2] + 'm'; }
  function fmtNormal(n) { return n[0] + ' ' + n[1] + ' ' + n[2]; }

  function buildHotspots(data) {
    clearHotspots();
    (data.hotspots || []).forEach(function (h) {
      var el = document.createElement('button');
      el.className = 'hotspot';
      el.slot = 'hotspot-' + h.partId;
      el.textContent = String(h.number);
      el.setAttribute('data-position', fmtPos(h.position));
      if (h.normal) el.setAttribute('data-normal', fmtNormal(h.normal));
      el.setAttribute('data-visibility-attribute', 'visible');
      el.setAttribute('aria-label', 'Part ' + h.number);
      el.addEventListener('click', function (ev) {
        ev.stopPropagation();
        call('onHotspotTap', h.partId);
      });
      mv.appendChild(el);
      hotspots[h.partId] = { position: h.position, number: h.number, el: el };
    });
    // Hotspots may arrive after the model: re-apply selection badge and replay the last focus.
    if (modelReady) {
      applySelection(selectedId);
      if (lastFocusId) window.focusPart(lastFocusId);
    }
  }

  function captureColors() {
    originalColors = {};
    var mats = (mv.model && mv.model.materials) || [];
    mats.forEach(function (m, i) {
      try { originalColors[i] = m.pbrMetallicRoughness.baseColorFactor.slice(); } catch (e) { /* ignore */ }
    });
  }

  function findMaterialIndex(partId) {
    var mats = (mv.model && mv.model.materials) || [];
    for (var i = 0; i < mats.length; i++) {
      if (mats[i].name === partId) return i;
    }
    return -1;
  }

  function applySelection(partId) {
    var mats = (mv.model && mv.model.materials) || [];
    var target = partId ? findMaterialIndex(partId) : -1;
    mats.forEach(function (m, i) {
      try {
        var color = (i === target) ? ACCENT : originalColors[i];
        if (color) m.pbrMetallicRoughness.setBaseColorFactor(color);
      } catch (e) { /* ignore */ }
    });
    Object.keys(hotspots).forEach(function (id) {
      hotspots[id].el.classList.toggle('selected', id === partId);
    });
  }

  // Lit skips a property assignment equal to the previous value, but the user may have moved the
  // camera since. Assign an equivalent (extra trailing space) string first so the real value is a change.
  function setAlways(prop, value) {
    if (mv[prop] === value) mv[prop] = value + ' ';
    mv[prop] = value;
  }

  window.selectPart = function (partId) {
    selectedId = partId || null;
    if (!modelReady) { pendingSelect = { id: selectedId }; return; }
    applySelection(selectedId);
  };

  window.focusPart = function (partId) {
    lastFocusId = partId || null;
    if (!modelReady) { pendingFocus = partId; return; }
    var h = hotspots[partId];
    if (!h) return;
    setAlways('cameraTarget', fmtPos(h.position));
  };

  window.resetCamera = function () {
    setAlways('cameraTarget', 'auto auto auto');
    setAlways('cameraOrbit', initialOrbit || 'auto auto auto');
    setAlways('fieldOfView', 'auto');
  };

  mv.addEventListener('load', function () {
    modelReady = true;
    captureColors();
    if (pendingSelect) { var p = pendingSelect; pendingSelect = null; window.selectPart(p.id); }
    else if (selectedId) { applySelection(selectedId); }
    if (pendingFocus) { var f = pendingFocus; pendingFocus = null; window.focusPart(f); }
    modelLoaded = true;
    maybeNotifyLoaded();
  });

  var notified = false;
  function maybeNotifyLoaded() {
    if (notified || !modelLoaded || !hotspotsSettled) return;
    notified = true;
    call('onModelLoaded');
  }

  mv.addEventListener('error', function (ev) {
    var d = ev && ev.detail;
    fail((d && (d.sourceError && d.sourceError.message || d.type)) || 'Could not load the 3D model');
  });

  window.loadModel = function (glbUrl, hotspotsUrl, cameraOrbit) {
    modelReady = false;
    modelLoaded = false;
    hotspotsSettled = false;
    notified = false;
    originalColors = {};
    clearHotspots();
    initialOrbit = cameraOrbit || null;
    if (cameraOrbit) mv.cameraOrbit = cameraOrbit;
    mv.cameraTarget = 'auto auto auto';

    var hotspotsPromise = hotspotsUrl
      ? fetch(hotspotsUrl).then(function (r) {
          if (!r.ok) throw new Error('Hotspots not found (' + r.status + ')');
          return r.json();
        })
      : Promise.resolve({ hotspots: [] });

    hotspotsPromise.then(buildHotspots).catch(function (e) {
      // Model can still be shown without hotspots.
      console.warn('hotspots: ' + e);
    }).then(function () { hotspotsSettled = true; maybeNotifyLoaded(); });

    fetch(glbUrl, { method: 'HEAD' }).then(function (r) {
      if (!r.ok) throw new Error('Model file not found (' + r.status + ')');
      mv.removeAttribute('src');
      mv.src = null;
      // Re-assigning the same URL is a no-op in Lit; clear first and set on the next frame.
      requestAnimationFrame(function () { mv.src = glbUrl; });
    }).catch(function (e) { fail(e && e.message ? e.message : e); });
  };

  // Standalone use (desktop browser): explorer.html?device=<id> loads ../models/<id>.glb
  try {
    var dev = new URLSearchParams(location.search).get('device');
    if (dev && /^[a-z0-9_]+$/.test(dev)) {
      window.loadModel('../models/' + dev + '.glb', '../models/' + dev + '.hotspots.json', null);
    }
  } catch (e) { /* ignore */ }
})();
