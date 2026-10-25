import com.midea.pureclimate.ir.Codebook;
import com.midea.pureclimate.ir.SignalMatcher;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.*;

public class CodebookTest {
    public static void main(String[] a) throws Exception {
        Codebook book = Codebook.load(new FileInputStream(a[0]));
        // 1) Exactness against the reference generator output.
        Map<String, Map<String, int[]>> ref = new HashMap<>();
        String cur = null;
        try (BufferedReader r = new BufferedReader(new FileReader(a[1]))) {
            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.split("\\|", -1);
                if (p[0].equals("P")) {
                    cur = p[1] + (p[3].isEmpty() ? "" : ":" + p[3]);
                    ref.putIfAbsent(cur, new HashMap<>());
                } else if (!p[2].isEmpty()) {
                    ref.get(cur).put(p[1], Arrays.stream(p[2].split(",")).mapToInt(Integer::parseInt).toArray());
                }
            }
        }
        int checked = 0, bad = 0;
        for (Codebook.Variant v : book.variants()) {
            Map<String, int[]> m = ref.get(v.id);
            if (m == null) { System.out.println("missing ref " + v.id); bad++; continue; }
            if (!Arrays.equals(v.powerOff(), m.get("off"))) { bad++; System.out.println("OFF mismatch " + v.id); }
            for (int mo = 0; mo < 5; mo++) for (int t = 17; t <= 30; t++) for (int f = 0; f < 4; f++) for (int s = 0; s < 2; s++) {
                int[] want = m.get(mo + "," + t + "," + f + "," + s);
                int[] got = v.state(mo, t, f, s == 1);
                if (v.swingIsToggle && s == 1) {
                    int[] tog = v.swingToggle();
                    int[] base = m.get(mo + "," + t + "," + f + ",0");
                    // reference = base + gap + toggle
                    if (!Arrays.equals(got, base) || want.length != base.length + 1 + tog.length
                            || !Arrays.equals(Arrays.copyOfRange(want, base.length + 1, want.length), tog)) {
                        bad++; System.out.println("toggle mismatch " + v.id);
                    }
                } else if (!Arrays.equals(got, want)) { bad++; System.out.println("state mismatch " + v.id + " " + mo + t + f + s); }
                checked++;
            }
        }
        System.out.println("exactness: checked " + checked + " states, bad=" + bad + ", variants=" + book.variants().size());

        // 2) Matcher on receiver-like noisy captures.
        Random rnd = new Random(7);
        int trials = 0, varOk = 0, stateOk = 0, confident = 0, wrongConfident = 0;
        long t0 = System.nanoTime();
        for (int it = 0; it < 300; it++) {
            Codebook.Variant v = book.variants().get(rnd.nextInt(book.variants().size()));
            int seq = v.sequenceForState(rnd.nextInt(Codebook.STATE_COUNT));
            int[] clean = v.sequence(seq);
            int[] cap = clean.clone();
            for (int i = 0; i < cap.length; i++) {
                double j = 1 + (rnd.nextGaussian() * 0.04);
                cap[i] = (int) (cap[i] * j + (i % 2 == 0 ? 90 : -90));
            }
            // sometimes the receiver only caught the first frame
            if (rnd.nextBoolean()) {
                for (int i = 1; i < cap.length; i += 2) if (clean[i] > 15000) { cap = Arrays.copyOf(cap, i); break; }
            }
            List<SignalMatcher.Match> res = SignalMatcher.identify(book, cap, 3);
            trials++;
            if (res.isEmpty()) continue;
            SignalMatcher.Match top = res.get(0);
            boolean sameCodes = Arrays.equals(top.variant.sequence(top.sequence), clean);
            if (top.variant == v || sameCodes) varOk++;
            if (sameCodes) stateOk++;
            if (top.isConfident()) { confident++; if (!sameCodes) { wrongConfident++; System.out.printf("WRONG src=%s len=%d cap=%d -> %s %.3f | 2nd %s %.3f%n", v.id, clean.length, cap.length, top.variant.id, top.score, res.size()>1?res.get(1).variant.id:"-", res.size()>1?res.get(1).score:0); } }
        }
        System.out.printf("matcher: trials=%d protocolOK=%d exactFrameOK=%d confident=%d wrongButConfident=%d avg=%.0fms%n",
                trials, varOk, stateOk, confident, wrongConfident, (System.nanoTime() - t0) / 1e6 / trials);
    }
}
