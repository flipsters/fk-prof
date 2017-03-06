package fk.prof;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @understands calling test-jni functions
 */
public class TestJni {
    private static final AtomicBoolean loaded = new AtomicBoolean(false);

    public native boolean generateCpusampleSimpleProfile(String filePath);
        
    public static void loadJniLib() {
        if (loaded.compareAndSet(false, true)) {
            String linkTargetPath = new File("build/libtestjni" + Platforms.getDynamicLibraryExtension()).getAbsolutePath();
            System.load(linkTargetPath);
            setupLogger();
        }
    }
    
    private native static void setupLogger(); 
    
    public native void setupPerfCtx();
    public native void teardownPerfCtx();
    public native void setupThdTracker();
    public native void teardownThdTracker();
    public native int getCurrentCtx(long[] fill);
    public native String getCtxName(long ctxid);
    public native int getCtxCov(long ctxid);
    public native int getCtxMergeSemantic(long ctxid);
    public native boolean isGenerated(long ctxid);
}
