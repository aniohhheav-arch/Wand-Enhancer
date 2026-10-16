#!/bin/sh
# Downloads only the VSCO 2 Community Edition folders Soundcraft uses (CC0 1.0), then run:
#   python3 tools/build-samples.py <dir>/vsco
set -e
DIR=${1:-/tmp/vsco-src}
mkdir -p "$DIR" && cd "$DIR"
[ -d vsco ] || git clone -q --filter=blob:none --no-checkout https://github.com/sgossner/VSCO-2-CE vsco
cd vsco
git checkout HEAD -- LICENSE "Brass/Trumpet/sus" "Brass/Trumpet/stac" "Brass/Trumpet/straightM-sus" "Brass/F Horn/sus" "Brass/F Horn/stac" \
  "Brass/Tenor Trombone/sus" "Brass/Tenor Trombone/stac" "Brass/Tuba/sus" "Brass/Tuba/stac" \
  "Strings/Solo Violin/Arco Vib" "Strings/Solo Violin/spic" "Strings/Solo Violin/Pizz" "Strings/Violin Section/susVib" "Strings/Violin Section/Spic" "Strings/Violin Section/Pizz" \
  "Strings/Viola Section/susvib" "Strings/Viola Section/spic" "Strings/Viola Section/pizz" "Strings/Cello Section/susvib" "Strings/Cello Section/spic" "Strings/Cello Section/pizzT" \
  "Strings/Solo Contrabass/SusVib" "Strings/Solo Contrabass/Pizz" "Strings/Harp" \
  "Woodwinds/Flute/susvib" "Woodwinds/Flute/stac" "Woodwinds/Oboe/Vib" "Woodwinds/Oboe/Stacc" "Woodwinds/Clarinet/susLong" "Woodwinds/Clarinet/stac" "Woodwinds/Bassoon/vib" "Woodwinds/Bassoon/stac" \
  "Percussion/Glock" "Percussion/Xylo" "Percussion/Marimba"
echo "Ready: $DIR/vsco"
