#!/usr/bin/env bash
# Populate app/src/main/res/font with placeholder font files so CI can build.
#
# The real Satoshi typeface (Fontshare) is licensed and not redistributable, so
# it is gitignored (see README "Building from source"). For CI we only need a
# VALID font file under each expected name — appearance is irrelevant to
# compilation, packaging, unit tests, and instrumented tests. We reuse a libre
# TTF already present on the runner (DejaVu) to avoid any network dependency;
# a TTF is a valid sfnt font and loads fine under an .otf filename.
set -euo pipefail

dest="app/src/main/res/font"
mkdir -p "$dest"

src="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
if [[ ! -f "$src" ]]; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq fonts-dejavu-core
fi

for weight in light regular medium bold black; do
  cp "$src" "$dest/satoshi_$weight.otf"
done

echo "Placeholder fonts written to $dest:"
ls -1 "$dest"
