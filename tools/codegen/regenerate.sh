#!/usr/bin/env bash
# Rebuilds app/src/main/assets/codebook.bin.gz from IRremoteESP8266.
#
# Compiles the library natively (UNIT_TEST mode), drives IRac with every
# power/mode/temp/fan/swing combination for every supported AC protocol,
# records the exact timings sent, and packs them for the app.
# Needs: git, g++, python3, javac/java (for the verification test).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$(mktemp -d)}"
REF="${IRREMOTE_REF:-1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f}"

git clone -q https://github.com/crankyoldgit/IRremoteESP8266 "$WORK/irlib"
git -C "$WORK/irlib" checkout -q "$REF"
cp -r "$WORK/irlib/src" "$WORK/gen"
cp "$WORK/irlib/test/IRsend_test.h" "$WORK/gen/"
cp "$HERE/gen.cpp" "$WORK/gen/"
cd "$WORK/gen"

# Keep each sender's timings after it is destroyed.
python3 - <<'EOF'
s = open('IRsend_test.h').read()
s = s.replace('''class IRsendTest : public IRsend {
 public:''', '''#include <vector>
extern std::vector<uint32_t> g_lastTimings;
extern std::vector<uint32_t> g_lastFreq;
class IRsendTest : public IRsend {
 public:
  ~IRsendTest() {
    if (last == 0 && output[0] == 0) return;
    g_lastTimings.assign(output, output + last + 1);
    g_lastFreq.assign(freq, freq + last + 1);
  }''')
open('IRsend_test.h', 'w').write(s)
EOF

mkdir -p obj
ls *.cpp | grep -v '^gen.cpp$' | xargs -P "$(nproc)" -I{} g++ -std=gnu++11 -O1 -DUNIT_TEST -I. -c {} -o obj/{}.o
g++ -std=gnu++11 -O1 -DUNIT_TEST -I. gen.cpp obj/*.o -o gen
./gen > codebook.txt
python3 "$HERE/pack_codebook.py" codebook.txt "$REPO/app/src/main/assets/codebook.bin.gz" .

# Verify every packed frame against the reference output.
mkdir -p "$WORK/test"
javac -d "$WORK/test" "$REPO"/app/src/main/java/com/midea/pureclimate/ir/*.java "$HERE/CodebookTest.java"
java -cp "$WORK/test" CodebookTest "$REPO/app/src/main/assets/codebook.bin.gz" codebook.txt
