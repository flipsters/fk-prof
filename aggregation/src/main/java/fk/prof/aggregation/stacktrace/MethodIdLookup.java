package fk.prof.aggregation.stacktrace;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodIdLookup {

    private AtomicInteger counter = new AtomicInteger(0);
    private ConcurrentHashMap<String, Integer> lookup = new ConcurrentHashMap<>();

    public Integer getOrAdd(String methodSignature) {
        return lookup.computeIfAbsent(methodSignature, (key -> counter.incrementAndGet()));
    }

    public int size() {
        return lookup.size();
    }

    public Set<Map.Entry<String, Integer>> entrySet() {
        return lookup.entrySet();
    }
}
