/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.kododake.aabrowser.web

object SpeechPolyfill {
    const val BRIDGE_OBJECT_NAME = "_SpeechBridgeChannel"

    val JS: String = """
        (function(){
            if(window.__sr_polyfill) return;
            window.__sr_polyfill = true;
            var bridge = window.${BRIDGE_OBJECT_NAME};
            var active = null;
            window.__sr_event = function(type, data) {
                if(!active) return;
                var r = active;
                if(type === 'result') {
                    try {
                        var d = JSON.parse(data);
                        var alts = d.a;
                        var result = {isFinal: d.f, length: alts.length};
                        for(var i = 0; i < alts.length; i++) result[i] = alts[i];
                        var results = {length: 1, 0: result};
                        var evt = {resultIndex: 0, results: results};
                        if(r.onresult) r.onresult(evt);
                    } catch(e) {}
                } else if(type === 'error') {
                    if(r.onerror) r.onerror({error: data});
                } else {
                    var handler = r['on' + type];
                    if(handler) {
                        try { handler(new Event(type)); } catch(e) { handler({}); }
                    }
                }
                if(type === 'end') active = null;
            };
            function postToNative(payload) {
                if(!bridge || typeof bridge.postMessage !== 'function') {
                    window.__sr_event('error', 'service-not-allowed');
                    window.__sr_event('end');
                    return false;
                }
                try {
                    bridge.postMessage(JSON.stringify(payload));
                    return true;
                } catch(e) {
                    window.__sr_event('error', 'service-not-allowed');
                    window.__sr_event('end');
                    return false;
                }
            }
            function SR() {
                this.lang = '';
                this.continuous = false;
                this.interimResults = false;
                this.maxAlternatives = 1;
                this.onresult = null;
                this.onerror = null;
                this.onstart = null;
                this.onend = null;
                this.onspeechstart = null;
                this.onspeechend = null;
                this.onaudiostart = null;
                this.onaudioend = null;
                this.onnomatch = null;
            }
            SR.prototype.start = function() {
                active = this;
                postToNative({type: 'start', lang: this.lang || ''});
            };
            SR.prototype.stop = function() {
                postToNative({type: 'stop'});
            };
            SR.prototype.abort = function() {
                postToNative({type: 'abort'});
            };
            SR.prototype.addEventListener = function(type, fn) {
                this['on' + type] = fn;
            };
            SR.prototype.removeEventListener = function(type, fn) {
                if(this['on' + type] === fn) this['on' + type] = null;
            };
            window.SpeechRecognition = SR;
            window.webkitSpeechRecognition = SR;
        })();
    """.trimIndent()
}
