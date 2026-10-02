package android.os;

import com.limelight.test.scheduler.VirtualScheduler;

public class Handler {
    private final Looper looper;

    public Handler() {
        this(Looper.getMainLooper());
    }

    public Handler(Looper looper) {
        this.looper = looper != null ? looper : Looper.getMainLooper();
    }

    public Looper getLooper() {
        return looper;
    }

    private VirtualScheduler scheduler() {
        return looper.getScheduler();
    }

    public boolean post(Runnable r) {
        scheduler().post(this, r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        scheduler().postDelayed(this, r, delayMillis);
        return true;
    }

    public void removeCallbacks(Runnable r) {
        scheduler().removeCallbacks(this, r);
    }

    public void removeCallbacksAndMessages(Object token) {
        scheduler().removeCallbacksAndMessages(this, token);
    }
}
