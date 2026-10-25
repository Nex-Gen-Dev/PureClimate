#!/usr/bin/env python3
"""Pack the IRremoteESP8266-generated codebook into the app's binary asset.

Format (big-endian, read with java.io.DataInputStream, file is gzipped):
  int    magic 0x50434231 ('PCB1')
  short  variantCount
  per variant:
    UTF   id               e.g. "COOLIX" or "GREE:YBOFB"
    UTF   displayName
    UTF   brands           comma separated
    int   carrierHz
    byte  flags            bit0 = swing is a separate toggle command
    short dictSize, int[dictSize]      distinct durations (us)
    short seqCount
      per seq: short len, byte[len] (indices into dict)
    short[561] stateSeq    0 = off, then mode(5) x temp(17..30) x fan(4) x swing(2)
    short toggleSeq        (only when flags bit0)
"""
import glob
import gzip
import os
import re
import struct
import sys

SRC = sys.argv[1]
OUT = sys.argv[2]
HDRS = sys.argv[3]

ORDER = ["COOLIX", "MIDEA", "GREE", "ELECTRA_AC", "LG2", "LG", "SAMSUNG_AC", "TCL112AC",
         "HAIER_AC_YRW02", "HAIER_AC176", "HAIER_AC", "DAIKIN", "MITSUBISHI_AC",
         "PANASONIC_AC", "FUJITSU_AC", "TOSHIBA_AC", "HITACHI_AC", "KELVINATOR",
         "WHIRLPOOL_AC", "SHARP_AC", "CARRIER_AC64", "VESTEL_AC", "TECO", "KELON"]
NICE = {
    "COOLIX": "Midea / Coolix (R51, RG57 24-bit)",
    "MIDEA": "Midea 48-bit (RG57, newer)",
    "GREE": "Gree",
    "ELECTRA_AC": "Electra / AUX",
    "LG2": "LG",
    "LG": "LG",
    "SAMSUNG_AC": "Samsung",
    "TCL112AC": "TCL",
    "HAIER_AC_YRW02": "Haier YR-W02",
    "HAIER_AC176": "Haier 176",
    "HAIER_AC160": "Haier 160",
    "HAIER_AC": "Haier",
    "MITSUBISHI_AC": "Mitsubishi Electric",
    "MITSUBISHI_HEAVY_88": "Mitsubishi Heavy 88",
    "MITSUBISHI_HEAVY_152": "Mitsubishi Heavy 152",
    "PANASONIC_AC": "Panasonic",
    "FUJITSU_AC": "Fujitsu",
    "TOSHIBA_AC": "Toshiba",
    "WHIRLPOOL_AC": "Whirlpool",
    "SHARP_AC": "Sharp",
    "CARRIER_AC64": "Carrier",
    "VESTEL_AC": "Vestel",
    "ELECTRA": "Electra",
}

KEYS = ["off"] + [f"{m},{t},{f},{s}" for m in range(5) for t in range(17, 31)
                  for f in range(4) for s in range(2)]


def nice_name(proto, model):
    base = NICE.get(proto)
    if not base:
        base = proto.replace("_AC", "").replace("_", " ").title()
    return f"{base} · {model}" if model else base


def parse_brands():
    """protocol name -> set of brands, from IRremoteESP8266 header comments."""
    by_hint, by_file = {}, {}
    for h in glob.glob(os.path.join(HDRS, "ir_*.h")):
        fam = os.path.basename(h)[3:-2].upper()
        for line in open(h, errors="ignore"):
            m = re.search(r"Brand:\s*([^,]+),", line)
            if not m:
                continue
            brand = re.sub(r"\s*\(.*\)", "", m.group(1)).strip()
            hint = re.findall(r"\(([A-Z0-9_]+)\)\s*$", line.strip())
            if hint:
                by_hint.setdefault(hint[-1], set()).add(brand)
            by_file.setdefault(fam, set()).add(brand)
    return by_hint, by_file


def main():
    by_hint, by_file = parse_brands()
    variants, cur = [], None
    for line in open(SRC):
        line = line.rstrip("\n")
        if line.startswith("P|"):
            _, proto, model, mname, freq = line.split("|")
            cur = {"proto": proto, "model": mname, "freq": int(freq), "states": {}}
            variants.append(cur)
        else:
            _, key, tm = line.split("|")
            cur["states"][key] = tuple(map(int, tm.split(","))) if tm else ()

    kept, sigs = [], set()
    for v in variants:
        st = v["states"]
        uniq = set(st.values())
        if () in uniq or len(uniq) < 20:
            continue  # protocol not usable through the common API
        sig = tuple(st[k] for k in KEYS)
        if sig in sigs:
            continue
        sigs.add(sig)
        # Swing as a separate toggle message appended to the state?
        toggle = None
        suffixes = set()
        for k in KEYS[1:]:
            if k.endswith(",1"):
                on, off = st[k], st[k[:-1] + "0"]
                if on != off and len(on) > len(off) and on[:len(off)] == off:
                    suffixes.add(on[len(off):])
                else:
                    suffixes.add(None)
        if len(suffixes) == 1 and None not in suffixes:
            suf = next(iter(suffixes))
            # suffix begins with the gap space; make it start with a mark
            toggle = suf[1:]
        v["toggle"] = toggle
        kept.append(v)

    def rank(v):
        p = v["proto"]
        return (ORDER.index(p) if p in ORDER else len(ORDER), p, v["model"])
    kept.sort(key=rank)

    buf = bytearray()
    buf += struct.pack(">IH", 0x50434231, len(kept))
    total_states = 0
    for v in kept:
        p = v["proto"]
        fam_brands = set()
        for fam, b in by_file.items():
            if p.startswith(fam) or fam.startswith(p.split("_")[0]):
                fam_brands |= b
        brands = sorted(by_hint.get(p, set()) | fam_brands)
        st = v["states"]
        seqs, seq_index, state_seq = [], {}, []
        toggle = v["toggle"]
        for k in KEYS:
            s = st[k]
            if toggle and k.endswith(",1"):
                s = st[k[:-1] + "0"]
            if s not in seq_index:
                seq_index[s] = len(seqs)
                seqs.append(s)
            state_seq.append(seq_index[s])
        if toggle:
            seq_index.setdefault(toggle, len(seqs))
            if seq_index[toggle] == len(seqs):
                seqs.append(toggle)
        durs = sorted({d for s in seqs for d in s})
        assert len(durs) < 256, (p, len(durs))
        di = {d: i for i, d in enumerate(durs)}
        ident = p + (":" + v["model"] if v["model"] else "")
        for s in (ident, nice_name(p, v["model"]), ",".join(brands)):
            b = s.encode("utf-8")
            buf += struct.pack(">H", len(b)) + b
        buf += struct.pack(">iB", v["freq"], 1 if toggle else 0)
        buf += struct.pack(">H", len(durs)) + b"".join(struct.pack(">i", d) for d in durs)
        buf += struct.pack(">H", len(seqs))
        for s in seqs:
            buf += struct.pack(">H", len(s)) + bytes(di[d] for d in s)
        buf += b"".join(struct.pack(">H", i) for i in state_seq)
        if toggle:
            buf += struct.pack(">H", seq_index[toggle])
        total_states += len(seqs)
        print(f"{ident:34} {nice_name(p, v['model'])[:34]:34} seqs={len(seqs):4} "
              f"toggle={'Y' if toggle else '-'} brands={len(brands)}")
    with gzip.open(OUT, "wb", compresslevel=9) as f:
        f.write(bytes(buf))
    print(f"{len(kept)} variants, raw {len(buf)/1e6:.2f} MB, "
          f"gz {os.path.getsize(OUT)/1e3:.0f} KB")


main()
