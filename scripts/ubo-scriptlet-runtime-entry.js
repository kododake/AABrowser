/* Bundled at development time; see third_party/ublock/README.md. */
import { builtinScriptlets } from '../third_party/ublock/src/js/resources/scriptlets.js';

Object.defineProperty(globalThis, 'scriptletGlobals', {
    value: Object.freeze({
        bcSecret: undefined,
        logLevel: 0,
        warOrigin: undefined,
        warSecret: undefined,
    }),
    configurable: false,
    writable: false,
});

const registry = new Map();
for (const details of builtinScriptlets) {
    registry.set(details.name, details);
    for (const alias of details.aliases || []) registry.set(alias, details);
}

try {
    const serialized = globalThis.__aabrowserScriptletBridge?.getInvocations(location.href);
    const invocations = serialized ? JSON.parse(serialized) : [];
    for (const invocation of invocations) {
        const rawName = invocation.name || '';
        const token = rawName.endsWith('.js') ? rawName : `${rawName}.js`;
        const details = registry.get(token) || registry.get(rawName);
        if (!details || token.endsWith('.fn')) continue;
        if (details.requiresTrust && invocation.trusted !== true) continue;
        try { details.fn(...(invocation.arguments || [])); } catch (_) {}
    }
} catch (_) {}
