package fk.prof;

/**
 * @understands patching instrumented-site to recorder
 *
 * As a user of fk-prof, one should never interact with this class directly.
 */
@SuppressWarnings("unused")
public class InstrumentationStub {
    private static boolean engaged = false;

    private InstrumentationStub() {
    }

    private static native void evtReturn();

    @SuppressWarnings("unused")
    public static void returnTracepoint(int var0, int var1) {
        if (engaged) {
            evtReturn();
        }
    }
}

