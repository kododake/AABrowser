/*
 * Exercises app/src/main/assets/adblock/cosmetic-runtime.js in jsdom with a stub native bridge.
 * Run with: NODE_PATH=<dir containing node_modules with jsdom> node scripts/test_cosmetic_runtime.cjs
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

let JSDOM;
try {
    ({ JSDOM } = require('jsdom'));
} catch (_) {
    console.error('jsdom is required: npm install jsdom, then set NODE_PATH to its node_modules directory');
    process.exit(2);
}

const source = fs.readFileSync(path.join(__dirname, '..', 'app/src/main/assets/adblock/cosmetic-runtime.js'), 'utf8');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function makeBridge(init, generic) {
    const calls = { init: [], generic: [] };
    return {
        calls,
        bridge: {
            cosmeticInit(href) { calls.init.push(href); return init ? JSON.stringify(init) : ''; },
            genericSelectors(href, classesJson, idsJson) {
                const classes = JSON.parse(classesJson);
                const ids = JSON.parse(idsJson);
                calls.generic.push({ href, classes, ids });
                return generic ? JSON.stringify(generic(classes, ids)) : '';
            },
        },
    };
}

function load(html, url, bridge) {
    const dom = new JSDOM(html, { url, runScripts: 'outside-only', pretendToBeVisual: true });
    dom.window.__aabrowserScriptletBridge = bridge;
    dom.window.eval(source);
    return dom;
}

function hiddenCss(dom) {
    const doc = dom.window.document;
    const parts = [];
    for (const style of doc.querySelectorAll('style[data-aabrowser-shields]')) parts.push(style.textContent);
    for (const sheet of doc.adoptedStyleSheets || []) {
        for (const rule of sheet.cssRules) parts.push(rule.cssText);
    }
    return parts.join('\n');
}

async function testNonHttpPagesNeverTouchTheBridge() {
    const { bridge, calls } = makeBridge({ s: ['.x'], o: [], g: true });
    load('<html><body></body></html>', 'file:///android_asset/error.html?failedUrl=x', bridge);
    load('<html><body></body></html>', 'about:blank', bridge);
    await sleep(50);
    assert.strictEqual(calls.init.length, 0, 'bridge must not be called for non-http documents');
}

async function testSpecificAndOtherSelectorsApplyImmediately() {
    const { bridge, calls } = makeBridge({ s: ['.sponsor', '#promo'], o: ['iframe[src*="doubleclick"]'], g: false });
    const dom = load('<html><head></head><body><div class="sponsor"></div></body></html>', 'https://news.test/article?x=1', bridge);
    assert.deepStrictEqual(calls.init, ['https://news.test/article?x=1']);
    const css = hiddenCss(dom);
    assert(css.includes('.sponsor'), 'specific selector applied');
    assert(css.includes('#promo'), 'specific selector applied');
    assert(css.includes('iframe[src*="doubleclick"]'), 'other generic selector applied');
    assert(css.includes('display:none'), 'rules hide elements');
    await sleep(250);
    assert.strictEqual(calls.generic.length, 0, 'no generic lookups when g=false');
}

async function testGenericTokensAreHarvestedDedupedAndApplied() {
    const { bridge, calls } = makeBridge({ s: [], o: [], g: true }, (classes, ids) => {
        const out = [];
        if (classes.includes('ad-banner')) out.push('.ad-banner');
        if (ids.includes('sidebar-ad')) out.push('#sidebar-ad');
        return out;
    });
    const dom = load('<html><head></head><body><div class="ad-banner keep"></div><p id="intro"></p></body></html>', 'https://site.test/', bridge);
    await sleep(300);
    assert.strictEqual(calls.generic.length, 1, 'initial harvest flushed once');
    assert.deepStrictEqual([...calls.generic[0].classes].sort(), ['ad-banner', 'keep']);
    assert.deepStrictEqual(calls.generic[0].ids, ['intro']);
    assert(hiddenCss(dom).includes('.ad-banner'), 'generic rule applied');

    const doc = dom.window.document;
    const late = doc.createElement('div');
    late.className = 'ad-banner';
    late.innerHTML = '<span id="sidebar-ad" class="late"></span>';
    doc.body.appendChild(late);
    doc.body.setAttribute('class', 'keep dark');
    await sleep(300);
    assert.strictEqual(calls.generic.length, 2, 'mutations flushed in one batch');
    assert.deepStrictEqual([...calls.generic[1].classes].sort(), ['dark', 'late'], 'already-seen tokens are not resent');
    assert.deepStrictEqual(calls.generic[1].ids, ['sidebar-ad']);
    assert(hiddenCss(dom).includes('#sidebar-ad'), 'late generic rule applied');
}

async function testInvalidSelectorDoesNotVoidTheChunk() {
    const { bridge } = makeBridge({ s: ['.good', ':::bad(', '.also-good'], o: [], g: false });
    const dom = load('<html><head></head><body></body></html>', 'https://site.test/', bridge);
    const css = hiddenCss(dom);
    assert(css.includes('.good') && css.includes('.also-good'), 'valid selectors survive an invalid neighbour');
}

async function testFlushCapStopsObserving() {
    let responses = 0;
    const { bridge, calls } = makeBridge({ s: [], o: [], g: true }, () => { responses++; return []; });
    const dom = load('<html><head></head><body></body></html>', 'https://site.test/', bridge);
    const doc = dom.window.document;
    for (let i = 0; i < 30; i++) {
        const el = doc.createElement('div');
        el.className = 'c' + i;
        doc.body.appendChild(el);
        await sleep(180);
    }
    const before = calls.generic.length;
    assert(before <= 20, 'at most 20 bridge round-trips per page');
    const el = doc.createElement('div');
    el.className = 'after-cap';
    doc.body.appendChild(el);
    await sleep(300);
    assert.strictEqual(calls.generic.length, before, 'observer disconnected after the cap');
}

(async () => {
    const tests = [
        testNonHttpPagesNeverTouchTheBridge,
        testSpecificAndOtherSelectorsApplyImmediately,
        testGenericTokensAreHarvestedDedupedAndApplied,
        testInvalidSelectorDoesNotVoidTheChunk,
        testFlushCapStopsObserving,
    ];
    for (const test of tests) {
        await test();
        console.log('ok', test.name);
    }
    console.log('cosmetic runtime tests passed');
})().catch((error) => { console.error(error); process.exit(1); });
