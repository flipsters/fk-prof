package fk.prof.nfr;

import fk.prof.ClosablePerfCtx;
import fk.prof.PerfCtx;

import java.lang.reflect.Array;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Created by gaurav.ashok on 11/04/17.
 */
public class Inception {

    int[] iterationCounts;
    Runnable[] work;
    RndGen rndGen = new RndGen();

    Consumer<Param>[] functions;
    PerfCtx[][] perfctxs;
    int perfCtxCount;

    public Inception(int[] iterations, Runnable[] work, PerfCtx[][] perfctxs) {
        this.iterationCounts = iterations;
        this.work = work;

        this.perfctxs = perfctxs;
        this.perfCtxCount = perfctxs[0].length;

        functions = (Consumer<Param>[]) Array.newInstance(Consumer.class, 20);

        functions[0] = this::level1;
        functions[1] = this::level2;
        functions[2] = this::level3;
        functions[3] = this::level4;
        functions[4] = this::level5;
        functions[5] = this::level6;
        functions[6] = this::level7;
        functions[7] = this::level8;
        functions[8] = this::level9;
        functions[9] = this::level10;
        functions[10] = this::level11;
        functions[11] = this::level12;
        functions[12] = this::level13;
        functions[13] = this::level14;
        functions[14] = this::level15;
        functions[15] = this::level16;
        functions[16] = this::level17;
        functions[17] = this::level18;
        functions[18] = this::level19;
        functions[19] = this::level20;
    }

    public void doWorkOnSomeLevel() {
        common(new Param(functions, 0));
    }

    private void level1(Param params) {
        common(params);
    }
    private void level2(Param params) {
        common(params);
    }
    private void level3(Param params) {
        common(params);
    }
    private void level4(Param params) {
        common(params);
    }
    private void level5(Param params) {
        common(params);
    }
    private void level6(Param params) {
        common(params);
    }
    private void level7(Param params) {
        common(params);
    }
    private void level8(Param params) {
        common(params);
    }
    private void level9(Param params) {
        common(params);
    }
    private void level10(Param params) {
        common(params);
    }
    private void level11(Param params) {
        common(params);
    }
    private void level12(Param params) {
        common(params);
    }
    private void level13(Param params) {
        common(params);
    }
    private void level14(Param params) {
        common(params);
    }
    private void level15(Param params) {
        common(params);
    }
    private void level16(Param params) {
        common(params);
    }
    private void level17(Param params) {
        common(params);
    }
    private void level18(Param params) {
        common(params);
    }
    private void level19(Param params) {
        common(params);
    }
    private void level20(Param params) {
        common(params);
    }

    private void common(Param params) {
        if(params.idx < 12) {
            if(rndGen.check((0.05f + (0.12f * params.idx/10.0f)))) {
                params.transcend(params.skip());
            }
            else {
                params.transcend(params.next());
            }
        }
        else {
            if(params.idx >= params.functions.length) {
                return;
            }
            else {
                if(rndGen.check(0.2f)) {
                    doSecondWorthOfWork();
                }
                else {
                    params.transcend(params.next());
                }
            }
        }
    }

    private void doSecondWorthOfWork() {
        for(int i = 0; i < iterationCounts.length; ++i) {
            try (ClosablePerfCtx ctx = perfctxs[i][rndGen.getInt(perfCtxCount)].open()) {
                for (int j = 0; j < iterationCounts[i]; ++j) {
                    work[i].run();
                }
            }
        }
    }

    private static class Param {
        public Consumer<Param>[] functions;
        public int idx;

        public Param(Consumer<Param>[] functions, int idx) {
            this.functions = functions;
            this.idx = idx;
        }

        public Param skip() {
            return new Param(functions, idx + 2);
        }

        public Param next() {
            return new Param(functions, idx + 1);
        }

        public void transcend(Param p) {
            functions[idx].accept(p);
        }
    }

    public class RndGen {
        byte[] bytes = new byte[10240];
        int idx = bytes.length;
        Random rnd = new Random();

        public boolean check(float prob) {
            ensureRndBytes();

            int limit = (int)(prob * 256.0f);
            int r = toInt(bytes[idx]);

            idx++;

            return r < limit;
        }

        public int getInt(int bound) {
            ensureRndBytes();

            int r = toInt(bytes[idx]);
            return bound * r / 256;
        }

        private void ensureRndBytes() {
            if(idx == bytes.length) {
                rnd.nextBytes(bytes);
                idx = 0;
            }
        }
    }

    private int toInt(byte b) {
        return ((int)b) & 0xff;
    }
}
