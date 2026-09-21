const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.join(__dirname, '..');
const resourcesRoot = path.join(root, 'third_party/ublock/src/js/resources');
const entrySource = path.join(resourcesRoot, 'scriptlets.js');
const runtimePath = path.join(root, 'app/src/main/assets/adblock/ubo-scriptlets.js');
const runtimeSource = fs.readFileSync(runtimePath, 'utf8');
const upstreamRevision = '95eba8035945c16879e063ec9405f858991afad9';

function testUpstreamRevision() {
    const readme = fs.readFileSync(path.join(root, 'third_party/ublock/README.md'), 'utf8');
    assert(
        readme.includes(`Revision: \`${upstreamRevision}\``),
        `Vendored uBO sources are not pinned to the compatibility revision ${upstreamRevision}`,
    );
}

function reachableSources(entry) {
    const pending = [entry];
    const visited = new Set();
    while (pending.length !== 0) {
        const file = pending.pop();
        if (visited.has(file)) continue;
        visited.add(file);
        const source = fs.readFileSync(file, 'utf8');
        const imports = /(?:import\s+(?:[^'";]+?\s+from\s+)?|export\s+[^'";]+?\s+from\s+)['"](\.\/[^'"]+)['"]/g;
        for (const match of source.matchAll(imports)) {
            pending.push(path.resolve(path.dirname(file), match[1]));
        }
    }
    return [...visited];
}

function metadataObjects(source) {
    const starts = [];
    const patterns = [/registerScriptlet\s*\([^,]+,\s*\{/g, /builtinScriptlets\.push\s*\(\s*\{/g];
    for (const pattern of patterns) {
        for (const match of source.matchAll(pattern)) starts.push(match.index + match[0].lastIndexOf('{'));
    }
    return starts.map(start => {
        let depth = 0;
        let quote = null;
        let escaped = false;
        for (let index = start; index < source.length; index++) {
            const char = source[index];
            if (quote !== null) {
                if (escaped) escaped = false;
                else if (char === '\\') escaped = true;
                else if (char === quote) quote = null;
                continue;
            }
            if (char === '"' || char === "'" || char === '`') quote = char;
            else if (char === '{') depth++;
            else if (char === '}' && --depth === 0) return source.slice(start, index + 1);
        }
        throw new Error('Unterminated upstream scriptlet metadata object');
    });
}

function upstreamRegistry() {
    const entries = [];
    for (const file of reachableSources(entrySource)) {
        const source = fs.readFileSync(file, 'utf8');
        for (const object of metadataObjects(source)) {
            const name = /\bname\s*:\s*['"]([^'"]+)['"]/.exec(object)?.[1];
            if (!name) continue;
            const aliasesSource = /\baliases\s*:\s*\[([\s\S]*?)\]/.exec(object)?.[1] || '';
            const aliases = [...aliasesSource.matchAll(/['"]([^'"]+)['"]/g)].map(match => match[1]);
            entries.push({
                name,
                aliases,
                requiresTrust: /\brequiresTrust\s*:\s*true\b/.test(object),
                world: /\bworld\s*:\s*['"]([^'"]+)['"]/.exec(object)?.[1] || 'MAIN',
            });
        }
    }
    return entries;
}

function baseSandbox(invocations) {
    class XMLHttpRequest {
        open() {}
        send() {}
    }
    const document = new EventTarget();
    Object.assign(document, {
        readyState: 'complete',
        currentScript: null,
        documentElement: {},
        addEventListener: document.addEventListener.bind(document),
        removeEventListener: document.removeEventListener.bind(document),
    });
    const sandbox = {
        console,
        URL,
        Request,
        Response,
        Headers,
        EventTarget,
        XMLHttpRequest,
        document,
        location: { href: 'https://example.test/', hostname: 'example.test' },
        setTimeout,
        clearTimeout,
        requestAnimationFrame: callback => setTimeout(callback, 0),
        cancelAnimationFrame: clearTimeout,
        fetch: async () => new Response('{}'),
        __aabrowserScriptletBridge: {
            getInvocations: () => JSON.stringify(invocations),
        },
    };
    sandbox.self = sandbox;
    sandbox.window = sandbox;
    sandbox.globalThis = sandbox;
    return vm.createContext(sandbox);
}

function execute(invocations, configure = () => {}) {
    const context = baseSandbox(invocations);
    configure(context);
    vm.runInContext(runtimeSource, context, { filename: runtimePath });
    return context;
}

function invocation(name, args = [], trusted = false) {
    return { name, arguments: args, trusted };
}

function testRegistryParity() {
    const registry = upstreamRegistry();
    assert(registry.length >= 100, `Expected the current uBO registry, found only ${registry.length} entries`);
    const tokens = registry.flatMap(entry => [entry.name, ...entry.aliases]);
    for (const token of tokens) {
        assert(
            runtimeSource.includes(JSON.stringify(token)),
            `Generated runtime is missing upstream token ${token}`,
        );
    }
    assert(registry.some(entry => entry.requiresTrust), 'Upstream registry has no trusted scriptlets');
    assert(registry.some(entry => entry.world === 'ISOLATED'), 'Upstream registry has no isolated-world scriptlets');
    return { entries: registry.length, tokens: new Set(tokens).size };
}

function testAliasesAndTrust() {
    const alias = execute([invocation('set', ['__aliasProbe', 'true'])]);
    assert.strictEqual(alias.__aliasProbe, true, 'The upstream set alias did not execute');

    const denied = execute([invocation('trusted-set-constant', ['__trustedProbe', 'true'])]);
    assert.strictEqual(denied.__trustedProbe, undefined, 'An untrusted list executed a trusted scriptlet');

    const allowed = execute([invocation('trusted-set-constant', ['__trustedProbe', 'true'], true)]);
    assert.strictEqual(allowed.__trustedProbe, true, 'A trusted upstream scriptlet did not execute');
}

function testJsonPrune() {
    const context = execute([invocation('json-prune', ['adPlacements playerAds'])]);
    const parsed = vm.runInContext(
        `JSON.parse('{"adPlacements":[1],"playerAds":[2],"keep":3}')`,
        context,
    );
    assert.deepStrictEqual(JSON.parse(JSON.stringify(parsed)), { keep: 3 });
}

async function testTrustedFetchReplacement() {
    const context = execute([
        invocation(
            'trusted-replace-fetch-response',
            ['"adPlacements"', '"no_ads"', 'player?'],
            true,
        ),
    ], sandbox => {
        sandbox.fetch = async () => new Response(
            '{"adPlacements":[1],"keep":2}',
            { headers: { 'Content-Type': 'application/json' } },
        );
    });
    const response = await context.fetch('https://www.youtube.com/youtubei/v1/player?key=test');
    assert.strictEqual(await response.text(), '{"no_ads":[1],"keep":2}');
}

function testOfficialNoSetTimeoutCases() {
    const cases = [
        [['bad'], [false, false, true, true]],
        [['!good'], [false, false, true, true]],
        [['', '33'], [false, true, false, true]],
        [['', '!66'], [false, true, false, true]],
        [['bad', '33'], [false, true, true, true]],
        [['!good', '!66'], [false, true, true, true]],
        [['bad', '!33'], [true, false, true, true]],
        [['!good', '66'], [true, false, true, true]],
        [['!bad', '33'], [true, true, false, true]],
        [['good', '!66'], [true, true, false, true]],
    ];
    for (const [args, expected] of cases) {
        const context = execute([invocation('nostif', args)], sandbox => {
            sandbox.__executed = [false, false, false, false];
            sandbox.setTimeout = (callback, _delay) => {
                callback();
                return 1;
            };
        });
        vm.runInContext(`
            setTimeout(function(){ 'bad'; __executed[0] = true; }, 33);
            setTimeout(function(){ 'bad'; __executed[1] = true; }, 66);
            setTimeout(function(){ 'good'; __executed[2] = true; }, 33);
            setTimeout(function(){ 'good'; __executed[3] = true; }, 66);
        `, context);
        assert.deepStrictEqual(
            Array.from(context.__executed),
            expected,
            `nostif differs from the upstream case (${args.join(', ')})`,
        );
    }
}

async function main() {
    testUpstreamRevision();
    const coverage = testRegistryParity();
    testAliasesAndTrust();
    testJsonPrune();
    await testTrustedFetchReplacement();
    testOfficialNoSetTimeoutCases();
    console.log(
        `uBO compatibility suite passed at ${upstreamRevision.slice(0, 12)} ` +
        `(${coverage.entries} registry entries, ${coverage.tokens} names/aliases)`,
    );
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
