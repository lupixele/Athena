package android.os;

import com.limelight.test.scheduler.VirtualScheduler;

public class Looper {
    private static final Looper MAIN_LOOPER = new Looper();
    private final VirtualScheduler scheduler = new VirtualScheduler();

    private Looper() {}

    public static Looper getMainLooper() {
        return MAIN_LOOPER;
    }

    public VirtualScheduler getScheduler() {
        return scheduler;
    }

    public static void resetMainLooper() {
        MAIN_LOOPER.scheduler.reset();
    }
}
