package com.midea.pureclimate.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Identifies which protocol and state a captured IR signal is, by comparing it
 * pulse-by-pulse with every frame in the {@link Codebook}.
 *
 * AC remotes send their whole state on every key press, so a single clean
 * capture is usually enough to pin down both the protocol and the settings.
 */
public final class SignalMatcher {
    /** A capture shorter than this many pulses is noise, not an AC frame. */
    public static final int MIN_PULSES = 40;
    /** Score at or above this is treated as a confident identification. */
    public static final double CONFIDENT = 0.92;

    private SignalMatcher() {}

    public static final class Match implements Comparable<Match> {
        public final Codebook.Variant variant;
        public final int sequence;
        public final double score;
        /** Score lead over the best match from a different protocol (1.0 if none). */
        double margin = 1.0;

        Match(Codebook.Variant variant, int sequence, double score) {
            this.variant = variant;
            this.sequence = sequence;
            this.score = score;
        }

        public boolean isConfident() {
            return score >= CONFIDENT && margin >= 0.03;
        }

        /** State indices (see {@link Codebook#stateIndex}) this frame represents. */
        public List<Integer> states() {
            return variant.statesForSequence(sequence);
        }

        public boolean isSwingToggle() {
            return variant.isToggleSequence(sequence);
        }

        @Override
        public int compareTo(Match o) {
            return Double.compare(o.score, score);
        }
    }

    /** Does a measured duration agree with the reference duration? */
    static boolean close(int measured, int reference) {
        int tol = Math.max(200, (int) (reference * 0.30));
        return Math.abs(measured - reference) <= tol;
    }

    /**
     * Similarity in [0,1]: share of matching pulses over the overlapping part,
     * weighted by how much of the longer signal the overlap covers. A capture
     * that caught only the first of two repeated frames still scores well.
     */
    public static double score(int[] captured, int[] reference) {
        int n = Math.min(captured.length, reference.length);
        if (n == 0) return 0;
        int allowedMisses = Math.max(2, n / 6);
        int matches = 0;
        int misses = 0;
        for (int i = 0; i < n; i++) {
            if (close(captured[i], reference[i])) {
                matches++;
            } else if (++misses > allowedMisses) {
                return 0;
            }
        }
        double acc = matches / (double) n;
        double coverage = n / (double) Math.max(captured.length, reference.length);
        return acc * (0.75 + 0.25 * coverage);
    }

    /** Best matches across the whole codebook, highest score first. */
    public static List<Match> identify(Codebook book, int[] captured, int limit) {
        List<Match> best = new ArrayList<>();
        if (captured == null || captured.length < MIN_PULSES) return best;
        for (Codebook.Variant v : book.variants()) {
            Match top = null;
            for (int s = 0; s < v.sequenceCount(); s++) {
                double sc = score(captured, v.sequence(s));
                if (sc > 0 && (top == null || sc > top.score)) top = new Match(v, s, sc);
            }
            if (top != null) best.add(top);
        }
        Collections.sort(best);
        // Margin = lead over the best competitor that would transmit something
        // different. Variants sending identical frames are equivalent for the AC.
        for (Match m : best) {
            int[] mine = m.variant.sequence(m.sequence);
            for (Match o : best) {
                if (o == m) continue;
                if (java.util.Arrays.equals(o.variant.sequence(o.sequence), mine)) continue;
                m.margin = m.score - o.score;
                break; // list is sorted, so the first different one is the strongest
            }
        }
        return best.size() > limit ? new ArrayList<>(best.subList(0, limit)) : best;
    }

    /**
     * Parses timings as printed by the PureClimate ESP32 receiver firmware:
     * a line like "IR:4420,4380,560,1640,...". Leading spaces and a trailing
     * gap are trimmed so the signal starts and ends on a mark.
     */
    public static int[] parseLine(String line) {
        if (line == null) return null;
        String s = line.trim();
        int colon = s.indexOf(':');
        if (!s.startsWith("IR:") || colon < 0) return null;
        String[] parts = s.substring(colon + 1).split(",");
        List<Integer> vals = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            try {
                int v = Integer.parseInt(t);
                if (v > 0) vals.add(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (vals.size() % 2 == 0 && !vals.isEmpty()) vals.remove(vals.size() - 1);
        int[] out = new int[vals.size()];
        for (int i = 0; i < out.length; i++) out[i] = vals.get(i);
        return out;
    }
}
