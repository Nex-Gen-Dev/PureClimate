package com.midea.pureclimate.ir;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Exact IR timings for every supported AC protocol and every common state.
 *
 * The data is generated offline from the IRremoteESP8266 reference library
 * (see tools/codegen), so each frame is bit-for-bit what that library sends.
 *
 * State index: 0 = power off, then 1 + (((mode * 14 + (temp - 17)) * 4 + fan) * 2 + swing)
 * with mode 0..4 = COOL, DRY, AUTO, HEAT, FAN and fan 0..3 = AUTO, LOW, MED, HIGH.
 */
public final class Codebook {
    public static final int MIN_TEMP = 17;
    public static final int MAX_TEMP = 30;
    public static final int STATE_COUNT = 561;

    private final List<Variant> variants;

    private Codebook(List<Variant> variants) {
        this.variants = Collections.unmodifiableList(variants);
    }

    public List<Variant> variants() {
        return variants;
    }

    public Variant find(String id) {
        for (Variant v : variants) if (v.id.equals(id)) return v;
        return null;
    }

    /** Variants whose name, id or brand list contains the query (case-insensitive). */
    public List<Variant> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return variants;
        List<Variant> out = new ArrayList<>();
        for (Variant v : variants) {
            if (v.name.toLowerCase(Locale.ROOT).contains(q)
                    || v.id.toLowerCase(Locale.ROOT).contains(q)
                    || v.brands.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(v);
            }
        }
        return out;
    }

    public static int stateIndex(int mode, int temp, int fan, boolean swing) {
        int t = Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp)) - MIN_TEMP;
        return 1 + (((mode * 14 + t) * 4 + fan) * 2 + (swing ? 1 : 0));
    }

    /** Reads the codebook, gzipped or plain (build tools may unpack .gz assets). */
    public static Codebook load(InputStream raw) throws IOException {
        BufferedInputStream buf = new BufferedInputStream(raw, 1 << 16);
        buf.mark(2);
        int b0 = buf.read();
        int b1 = buf.read();
        buf.reset();
        boolean gzip = b0 == 0x1f && b1 == 0x8b;
        DataInputStream in = new DataInputStream(
                gzip ? new BufferedInputStream(new GZIPInputStream(buf), 1 << 16) : buf);
        try {
            if (in.readInt() != 0x50434231) throw new IOException("Bad codebook header");
            int count = in.readUnsignedShort();
            List<Variant> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) list.add(Variant.read(in));
            return new Codebook(list);
        } finally {
            in.close();
        }
    }

    public static final class Variant {
        public final String id;
        public final String name;
        public final String brands;
        public final int carrierHz;
        public final boolean swingIsToggle;
        private final int[] dict;
        private final byte[][] seqs;
        private final int[] stateSeq;
        private final int toggleSeq;

        private Variant(String id, String name, String brands, int carrierHz,
                        boolean swingIsToggle, int[] dict, byte[][] seqs,
                        int[] stateSeq, int toggleSeq) {
            this.id = id;
            this.name = name;
            this.brands = brands;
            this.carrierHz = carrierHz;
            this.swingIsToggle = swingIsToggle;
            this.dict = dict;
            this.seqs = seqs;
            this.stateSeq = stateSeq;
            this.toggleSeq = toggleSeq;
        }

        static Variant read(DataInputStream in) throws IOException {
            String id = in.readUTF();
            String name = in.readUTF();
            String brands = in.readUTF();
            int freq = in.readInt();
            boolean toggle = (in.readByte() & 1) != 0;
            int[] dict = new int[in.readUnsignedShort()];
            for (int i = 0; i < dict.length; i++) dict[i] = in.readInt();
            byte[][] seqs = new byte[in.readUnsignedShort()][];
            for (int i = 0; i < seqs.length; i++) {
                seqs[i] = new byte[in.readUnsignedShort()];
                in.readFully(seqs[i]);
            }
            int[] stateSeq = new int[STATE_COUNT];
            for (int i = 0; i < STATE_COUNT; i++) stateSeq[i] = in.readUnsignedShort();
            int toggleSeq = toggle ? in.readUnsignedShort() : -1;
            return new Variant(id, name, brands, freq, toggle, dict, seqs, stateSeq, toggleSeq);
        }

        public int sequenceCount() {
            return seqs.length;
        }

        /** Timings (alternating mark/space in microseconds) of a stored sequence. */
        public int[] sequence(int seq) {
            byte[] s = seqs[seq];
            int[] out = new int[s.length];
            for (int i = 0; i < s.length; i++) out[i] = dict[s[i] & 0xFF];
            return out;
        }

        public int sequenceForState(int stateIndex) {
            return stateSeq[stateIndex];
        }

        public int[] powerOff() {
            return sequence(stateSeq[0]);
        }

        /**
         * Full-state frame. For protocols where swing is a separate toggle command
         * the returned frame leaves the louvers as they are; send {@link #swingToggle()}
         * to flip them.
         */
        public int[] state(int mode, int temp, int fan, boolean swing) {
            return sequence(stateSeq[stateIndex(mode, temp, fan, swing)]);
        }

        public int[] swingToggle() {
            return toggleSeq < 0 ? null : sequence(toggleSeq);
        }

        public boolean isToggleSequence(int seq) {
            return seq == toggleSeq;
        }

        /** All state indices that transmit the given stored sequence. */
        public List<Integer> statesForSequence(int seq) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < STATE_COUNT; i++) if (stateSeq[i] == seq) out.add(i);
            return out;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
