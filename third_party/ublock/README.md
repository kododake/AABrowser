# uBlock Origin scriptlet sources

This directory contains the upstream JavaScript sources needed to generate AA Browser's document-start scriptlet runtime.

- Upstream: https://github.com/gorhill/uBlock
- Revision: `95eba8035945c16879e063ec9405f858991afad9`
- License: GPL-3.0; see `LICENSE.txt`

Run `scripts/generate_ubo_scriptlets.sh` after updating these sources. The generated Android asset is committed so building AA Browser does not require Node.js.
