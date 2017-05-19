package fk.prof.recorder.main;

import scala.reflect.internal.Trees;

/**
 * @understands
 */
public class JniBurn {
    private static final int MULTIPLIER = 10000;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        System.load("/tmp/burner.so");
        try {
            System.err.println("Calling jni-fn");
            infLoop();
            System.err.println("Called jni-fn");
        } catch (Throwable e) {
            e.printStackTrace();
            System.err.println("Calling jni-fn failed");
        }
    }

    private static void infLoop() {
        jniBurn();
    }

    private native static void jniBurn();
}
