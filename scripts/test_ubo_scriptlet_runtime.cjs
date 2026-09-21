const fs = require('fs');
const path = require('path');

global.self = global.window = globalThis;
global.location = { href: 'https://example.test/', hostname: 'example.test' };
global.document = { readyState: 'complete', currentScript: null, addEventListener() {} };
global.XMLHttpRequest = function XMLHttpRequest() {};
global.XMLHttpRequest.prototype = { open() {}, send() {} };
global.__aabrowserScriptletBridge = {
    getInvocations() {
        return JSON.stringify([{
            name: 'set-constant',
            arguments: [ '__aabrowserRuntimeProbe', 'true' ],
            trusted: false,
        }]);
    },
};

const runtime = path.join(__dirname, '../app/src/main/assets/adblock/ubo-scriptlets.js');
eval(fs.readFileSync(runtime, 'utf8'));
if (global.__aabrowserRuntimeProbe !== true) {
    throw new Error('uBO scriptlet runtime probe failed');
}
console.log('uBO scriptlet runtime probe passed');
