#!/bin/sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
npx --yes esbuild "$ROOT_DIR/scripts/ubo-scriptlet-runtime-entry.js" \
  --bundle --format=iife --target=chrome120 --minify --legal-comments=inline \
  --outfile="$ROOT_DIR/app/src/main/assets/adblock/ubo-scriptlets.js"
