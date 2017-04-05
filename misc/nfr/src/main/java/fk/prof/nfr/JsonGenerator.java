package fk.prof.nfr;

import fk.prof.ClosablePerfCtx;
import fk.prof.MergeSemantics;
import fk.prof.PerfCtx;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Created by gaurav.ashok on 03/04/17.
 */
public class JsonGenerator {

    private static Random rnd;
    private static PerfCtx childCtx = new PerfCtx("hashmap-create-ctx", 20, MergeSemantics.PARENT_SCOPED);

    public JsonGenerator(long seed) {
        this.rnd = new Random(seed);
    }

    public Map<String, Object> genJsonMap(int size, float objProb, float arrayProb) {
        try(ClosablePerfCtx ctx = childCtx.open()) {
            Map<String, Object> map = new HashMap<>();
            if (size == 0) {
                return map;
            }
            genFields(map, size, 0, objProb, arrayProb);
            return map;
        }
    }

    private String randomString(int bound) {
        int len = rnd.nextInt(bound);
        return RandomStringUtils.randomAlphanumeric(len);
    }

    private void genFields(Map<String, Object> map, int size, int idx, float objProb, float arrayProb) {

        if(idx < size) {
            map.put(randomString(32), genRndmObject(size / 2, objProb, arrayProb));
            genFields(map, size, idx + 1, objProb, arrayProb);
        }
        return;
    }

    private Object genRndmObject(int size, float objProb, float arrayProb) {
        float rndFloat = rnd.nextFloat();
        if(rndFloat < objProb) {
            return genJsonMap(size, objProb, arrayProb);
        }
        else if(rndFloat < arrayProb + objProb) {
            List<Object> list = new ArrayList<>();
            for(int i = 0; i < size; ++i) {
                list.add(genRndmObject(size / 2, objProb, arrayProb));
            }
            return list;
        }
        else {
            int rndmInt = rnd.nextInt(2);
            if(rndmInt == 0) {
                return randomString(32);
            }
            else {
                return ZonedDateTime.now(Clock.systemUTC());
            }
        }
    }
}
