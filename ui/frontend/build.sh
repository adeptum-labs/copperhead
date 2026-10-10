#!/bin/sh
# Builds static/js/editor.bundle.js and its provenance from the packages pinned in package-lock.json. The output is
# committed, so the studio never fetches code at runtime; run this after changing a source here or a version.
set -eu

cd "$(dirname "$0")"

bundle=../src/main/resources/static/js/editor.bundle.js
meta=node_modules/.bundle-meta.json

npm ci --no-audit --no-fund
npm test
npx esbuild editor.entry.js --bundle --format=iife --minify --global-name=CopperheadEditor \
	--legal-comments=none --metafile="$meta" --outfile="$bundle"
node provenance.mjs "$bundle" "$meta" > "$bundle.provenance"
