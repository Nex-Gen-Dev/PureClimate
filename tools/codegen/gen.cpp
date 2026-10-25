// Codebook generator: drives IRremoteESP8266's IRac with every common state
// and prints the exact timings each protocol would transmit.
#include <cstdio>
#include <set>
#include <string>
#include <vector>
#include "IRac.h"
#include "IRutils.h"
#include "IRsend_test.h"

std::vector<uint32_t> g_lastTimings;
std::vector<uint32_t> g_lastFreq;

static std::string capture(IRac &ac, stdAc::state_t st, uint32_t *freq) {
  g_lastTimings.clear();
  g_lastFreq.clear();
  ac.sendAc(st, nullptr);
  if (g_lastTimings.empty()) return "";
  *freq = g_lastFreq.empty() ? 38000 : g_lastFreq[0];
  // Drop trailing spaces (odd indices at the end) - Android ends on a mark.
  std::vector<uint32_t> t = g_lastTimings;
  while (!t.empty() && (t.size() % 2 == 0)) t.pop_back();
  std::string s;
  for (size_t i = 0; i < t.size(); i++) {
    if (i) s += ",";
    s += std::to_string(t[i]);
  }
  return s;
}

int main() {
  const stdAc::opmode_t modes[] = {stdAc::opmode_t::kCool, stdAc::opmode_t::kDry,
                                   stdAc::opmode_t::kAuto, stdAc::opmode_t::kHeat,
                                   stdAc::opmode_t::kFan};
  const stdAc::fanspeed_t fans[] = {stdAc::fanspeed_t::kAuto, stdAc::fanspeed_t::kLow,
                                    stdAc::fanspeed_t::kMedium, stdAc::fanspeed_t::kHigh};
  IRac ac(0);
  for (int p = 0; p <= (int)kLastDecodeType; p++) {
    decode_type_t proto = (decode_type_t)p;
    if (!IRac::isProtocolSupported(proto)) continue;
    std::vector<int16_t> models = {-1};
    for (int m = 0; m <= 8; m++) {
      String ms = irutils::modelToStr(proto, m);
      if (ms != "UNKNOWN" && ms != "") models.push_back(m);
    }
    std::set<std::string> seen;
    for (int16_t model : models) {
      stdAc::state_t st;
      IRac::initState(&st);
      st.protocol = proto;
      st.model = model;
      st.celsius = true;
      st.swingh = stdAc::swingh_t::kOff;
      st.beep = true;
      std::vector<std::string> lines;
      std::string sig;
      uint32_t freq = 38000;
      st.power = false;
      st.mode = stdAc::opmode_t::kCool;
      st.degrees = 24;
      st.fanspeed = stdAc::fanspeed_t::kAuto;
      st.swingv = stdAc::swingv_t::kOff;
      std::string off = capture(ac, st, &freq);
      lines.push_back("off|" + off);
      sig += off;
      st.power = true;
      for (int mi = 0; mi < 5; mi++)
        for (int t = 17; t <= 30; t++)
          for (int fi = 0; fi < 4; fi++)
            for (int sw = 0; sw < 2; sw++) {
              st.mode = modes[mi];
              st.degrees = t;
              st.fanspeed = fans[fi];
              st.swingv = sw ? stdAc::swingv_t::kAuto : stdAc::swingv_t::kOff;
              std::string tm = capture(ac, st, &freq);
              char key[32];
              snprintf(key, sizeof(key), "%d,%d,%d,%d", mi, t, fi, sw);
              lines.push_back(std::string(key) + "|" + tm);
              sig += tm;
            }
      if (seen.count(sig)) continue;
      seen.insert(sig);
      std::string mname = model < 0 ? "" : irutils::modelToStr(proto, model).c_str();
      printf("P|%s|%d|%s|%u\n", typeToString(proto).c_str(), model, mname.c_str(), freq);
      for (auto &l : lines) printf("S|%s\n", l.c_str());
    }
  }
  return 0;
}
