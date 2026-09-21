/*
 * AA Browser Shields: document-start cosmetic filtering.
 *
 * Runs in every frame before any page script. It asks the native bridge for the page's hide
 * selectors, applies them through a constructed stylesheet before the first paint, then watches the
 * DOM for classes and ids so generic (domain-less) rules can be fetched only for tokens that
 * actually appear on the page. All work is batched, throttled and capped so heavy pages stay cheap.
 */
(() => {
  try {
    if (globalThis.__aabrowserCosmeticInit) return;
    globalThis.__aabrowserCosmeticInit = true;

    const bridge = globalThis.__aabrowserScriptletBridge;
    if (!bridge || typeof bridge.cosmeticInit !== 'function') return;

    const resolveHref = () => {
      const protocol = location.protocol;
      if (protocol === 'http:' || protocol === 'https:') return location.href;
      // about:blank / about:srcdoc frames inherit their parent's origin, so use the parent's page.
      if (protocol === 'about:' && window !== window.parent) {
        try {
          const parentHref = window.parent.location.href;
          if (/^https?:/i.test(parentHref)) return parentHref;
        } catch (_) { /* cross-origin parent: nothing to inherit */ }
      }
      return null;
    };

    const href = resolveHref();
    if (!href) return;

    const rawInit = bridge.cosmeticInit(href);
    if (!rawInit) return;
    const init = JSON.parse(rawInit);
    if (!init) return;

    const CHUNK = 64;
    const RULE_SUFFIX = '{display:none!important}';
    let sheet = null;
    let styleElement = null;
    const pendingCss = [];

    const attachSheet = () => {
      if (sheet) {
        try {
          const adopted = document.adoptedStyleSheets;
          if (adopted && adopted.indexOf(sheet) === -1) document.adoptedStyleSheets = [...adopted, sheet];
        } catch (_) { /* ignore */ }
        return;
      }
      if (styleElement && !styleElement.isConnected) {
        const parent = document.head || document.documentElement;
        if (parent) parent.appendChild(styleElement);
      }
    };

    const ensureSink = () => {
      if (sheet || styleElement) { attachSheet(); return true; }
      try {
        const candidate = new CSSStyleSheet();
        document.adoptedStyleSheets = [...document.adoptedStyleSheets, candidate];
        sheet = candidate;
        return true;
      } catch (_) { /* older engines: fall back to a <style> element */ }
      const parent = document.head || document.documentElement;
      if (!parent) return false;
      styleElement = document.createElement('style');
      styleElement.setAttribute('data-aabrowser-shields', '');
      parent.appendChild(styleElement);
      return true;
    };

    const insertCss = (css) => {
      if (sheet) {
        sheet.insertRule(css, sheet.cssRules.length);
      } else {
        styleElement.appendChild(document.createTextNode(css));
      }
    };

    const flushPendingCss = () => {
      if (!ensureSink()) return;
      while (pendingCss.length) insertCss(pendingCss.shift());
    };

    const addSelectors = (selectors) => {
      if (!Array.isArray(selectors) || selectors.length === 0) return;
      for (let start = 0; start < selectors.length; start += CHUNK) {
        const chunk = selectors.slice(start, start + CHUNK);
        const rule = chunk.join(',') + RULE_SUFFIX;
        if (!ensureSink()) { pendingCss.push(rule); continue; }
        try {
          insertCss(rule);
        } catch (_) {
          // One invalid selector voids a grouped rule; retry each on its own.
          for (const selector of chunk) {
            try { insertCss(selector + RULE_SUFFIX); } catch (__) { /* skip the bad one */ }
          }
        }
      }
    };

    addSelectors(init.s);
    addSelectors(init.o);
    if (pendingCss.length) {
      document.addEventListener('DOMContentLoaded', flushPendingCss, { once: true });
    }

    if (!init.g || typeof bridge.genericSelectors !== 'function') return;

    const TOKEN = /^[\w-]{1,128}$/;
    const MAX_TOKENS = 20000;
    const MAX_FLUSHES = 20;
    const BATCH = 512;
    const FLUSH_DELAY_MS = 150;
    const seenClasses = new Set();
    const seenIds = new Set();
    let pendingClasses = [];
    let pendingIds = [];
    let tokens = 0;
    let flushes = 0;
    let stopped = false;
    let timer = null;
    let observer = null;

    const stop = () => {
      stopped = true;
      if (observer) { try { observer.disconnect(); } catch (_) { /* ignore */ } }
      if (timer !== null) { clearTimeout(timer); timer = null; }
    };

    const collect = (element) => {
      if (stopped || !element || element.nodeType !== 1) return;
      const id = element.id;
      if (typeof id === 'string' && id && !seenIds.has(id) && TOKEN.test(id)) {
        seenIds.add(id);
        pendingIds.push(id);
        tokens++;
      }
      const classes = element.classList;
      if (classes) {
        for (let i = 0; i < classes.length; i++) {
          const name = classes[i];
          if (!seenClasses.has(name) && TOKEN.test(name)) {
            seenClasses.add(name);
            pendingClasses.push(name);
            tokens++;
          }
        }
      }
      if (tokens > MAX_TOKENS) stop();
    };

    const collectTree = (root) => {
      if (stopped || !root || root.nodeType !== 1) return;
      collect(root);
      if (typeof root.querySelectorAll !== 'function') return;
      const descendants = root.querySelectorAll('[class],[id]');
      for (let i = 0; i < descendants.length && !stopped; i++) collect(descendants[i]);
    };

    const flush = () => {
      timer = null;
      if (stopped || (pendingClasses.length === 0 && pendingIds.length === 0)) return;
      const classes = pendingClasses.splice(0, BATCH);
      const ids = pendingIds.splice(0, BATCH);
      flushes++;
      let response = '';
      try { response = bridge.genericSelectors(href, JSON.stringify(classes), JSON.stringify(ids)); } catch (_) { /* ignore */ }
      if (response) {
        try { addSelectors(JSON.parse(response)); } catch (_) { /* ignore */ }
      }
      if (flushes >= MAX_FLUSHES) { stop(); return; }
      schedule();
    };

    const schedule = () => {
      if (stopped || timer !== null) return;
      if (pendingClasses.length === 0 && pendingIds.length === 0) return;
      timer = setTimeout(flush, FLUSH_DELAY_MS);
    };

    const harvest = () => {
      collectTree(document.documentElement);
      schedule();
    };

    observer = new MutationObserver((records) => {
      for (const record of records) {
        if (stopped) break;
        if (record.type === 'attributes') {
          collect(record.target);
        } else {
          const added = record.addedNodes;
          for (let i = 0; i < added.length && !stopped; i++) collectTree(added[i]);
        }
      }
      schedule();
    });
    observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });

    harvest();
    document.addEventListener('DOMContentLoaded', harvest, { once: true });
    window.addEventListener('load', harvest, { once: true });
  } catch (_) {
    /* Shields must never break a page. */
  }
})();
