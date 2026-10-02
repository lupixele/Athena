package com.limelight.test.fakes;

/**
 * Pure-JVM test-mirror of TrackpadContext production logic.
 *
 * <p>This class reimplements the stateless/pure logic extracted from
 * {@code com.limelight.binding.input.touch.TrackpadContext} without any
 * Android framework dependencies. It is used ONLY in the test-harness.
 *
 * <p>Handlers and Looper are replaced by a synchronous {@link FakeScheduler}
 * that lets tests drive timer callbacks deterministically.
 *
 * <p>If production TrackpadContext logic changes, update this mirror and
 * the corresponding tests. The source of truth is always the production file.
 */
public class TrackpadLogicMirror {

    // ---- Constants mirrored from TrackpadContext ----
    public static final int TAP_MOVEMENT_THRESHOLD         = 30;
    public static final int TAP_TIME_THRESHOLD             = 230;
    public static final int CLICK_RELEASE_DELAY            = TAP_TIME_THRESHOLD;
    public static final int SCROLL_SPEED_FACTOR_X          = 2;
    public static final int SCROLL_SPEED_FACTOR_Y          = 3;
    public static final double ACCELERATION_THRESHOLD      = 8.0;
    public static final double FLICK_FRICTION              = 0.93;
    public static final double FLICK_THRESHOLD             = 0.8;
    public static final int MOMENTUM_FRAME_INTERVAL_MS     = 10;
    public static final int FLICK_VELOCITY_DECAY_TIMEOUT_MS = 50;
    public static final int SCROLL_TRANSITION_TIMEOUT_MS   = 200;

    // ---- Mutable state (mirrors TrackpadContext fields) ----
    private double pendingDeltaX = 0;
    private double pendingDeltaY = 0;
    private int lastTouchX = 0;
    private int lastTouchY = 0;
    private int originalTouchX = 0;
    private int originalTouchY = 0;
    private long originalTouchTime = 0;
    private boolean cancelled = false;
    private boolean confirmedMove = false;
    private boolean confirmedDrag = false;
    private boolean confirmedScroll = false;
    private double distanceMoved = 0;
    private int pointerCount = 0;
    private boolean clickedMiddle = false;
    private int maxPointerCountInGesture = 0;
    private boolean isClickPending = false;
    private boolean isDblClickPending = false;
    private boolean isFlicking = false;
    private double velocityX = 0.0;
    private double velocityY = 0.0;
    private long lastMoveTime = 0;
    private boolean isScrollTransitioning = false;

    // ---- Construction-time config ----
    private final FakeConnection conn;
    private final int actionIndex;
    private final FakeScheduler scheduler;
    private final boolean swapAxis;
    private final float sensitivityX;
    private final float sensitivityY;

    /**
     * Default constructor — mirrors {@code TrackpadContext(conn, actionIndex)}.
     * sensitivityX/Y default to 1.0 (i.e. 100 / 100), swapAxis = false.
     */
    public TrackpadLogicMirror(FakeConnection conn, int actionIndex, FakeScheduler scheduler) {
        this(conn, actionIndex, scheduler, false, 100, 100);
    }

    /**
     * Full constructor — mirrors
     * {@code TrackpadContext(conn, actionIndex, swapAxis, sensitivityX, sensitivityY)}.
     *
     * @param sensitivityX raw integer as stored in preferences (100 = 1.0×)
     * @param sensitivityY raw integer as stored in preferences (100 = 1.0×)
     */
    public TrackpadLogicMirror(FakeConnection conn, int actionIndex, FakeScheduler scheduler,
                               boolean swapAxis, int sensitivityX, int sensitivityY) {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.scheduler = scheduler;
        this.swapAxis = swapAxis;
        this.sensitivityX = (float) sensitivityX / 100;
        this.sensitivityY = (float) sensitivityY / 100;
    }

    // ---- Accessors for test inspection ----

    public float getSensitivityX() { return sensitivityX; }
    public float getSensitivityY() { return sensitivityY; }
    public boolean isSwapAxis()    { return swapAxis; }
    public boolean isCancelled()   { return cancelled; }
    public boolean isFlicking()    { return isFlicking; }
    public double getVelocityX()   { return velocityX; }
    public double getVelocityY()   { return velocityY; }
    public int getPointerCount()   { return pointerCount; }
    public int getMaxPointerCountInGesture() { return maxPointerCountInGesture; }
    public boolean isConfirmedScroll() { return confirmedScroll; }
    public boolean isConfirmedDrag()   { return confirmedDrag; }
    public boolean isConfirmedMove()   { return confirmedMove; }

    // ---- Pure logic helpers ----

    /**
     * Mirrors {@code TrackpadContext.getMouseButtonIndex()}.
     * Returns the button byte for the current pointer count.
     */
    public byte getMouseButtonIndex() {
        switch (maxPointerCountInGesture) {
            case 1:  return FakeConnection.BUTTON_LEFT;
            case 2:  return FakeConnection.BUTTON_RIGHT;
            case 3:  return FakeConnection.BUTTON_MIDDLE;
            default: return FakeConnection.BUTTON_LEFT;
        }
    }

    /**
     * Applies sensitivity scaling to a raw delta — mirrors production logic in
     * {@code TrackpadContext.touchMoveEvent} for mouse movement.
     *
     * @param rawDelta raw pixel delta
     * @param axis     'x' or 'y'
     * @return scaled delta before accumulation
     */
    public double applyScaling(double rawDelta, char axis) {
        if (swapAxis) {
            axis = (axis == 'x') ? 'y' : 'x';
        }
        return axis == 'x' ? rawDelta * sensitivityX : rawDelta * sensitivityY;
    }

    /**
     * Simulates a pointer-down event. Updates pointerCount and maxPointerCountInGesture.
     */
    public void touchDown(int x, int y, long eventTime, boolean isNewFinger) {
        if (!isNewFinger) return;

        pointerCount++;
        if (pointerCount > maxPointerCountInGesture) {
            maxPointerCountInGesture = pointerCount;
        }

        if (pointerCount == 1) {
            originalTouchX = lastTouchX = x;
            originalTouchY = lastTouchY = y;
            originalTouchTime = eventTime;
            cancelled = false;
            confirmedMove = false;
            confirmedDrag = false;
            confirmedScroll = false;
            distanceMoved = 0;
            clickedMiddle = false;
        }

        if (pointerCount == 3 && !clickedMiddle) {
            clickedMiddle = true;
            conn.sendMouseButtonDown(FakeConnection.BUTTON_MIDDLE);
        }
    }

    /**
     * Simulates a pointer-up / cancel event. Mirrors cancellation / state reset.
     */
    public void touchUp(int x, int y, long eventTime) {
        if (pointerCount > 0) {
            pointerCount--;
        }

        if (pointerCount == 0) {
            cancelled = true;
            isFlicking = false;
            velocityX = 0.0;
            velocityY = 0.0;
            pendingDeltaX = 0;
            pendingDeltaY = 0;
        }
    }

    /**
     * Cancels the current gesture (pointer-cancel). Equivalent to touchUp(count→0).
     */
    public void cancelGesture() {
        cancelled = true;
        isFlicking = false;
        velocityX = 0.0;
        velocityY = 0.0;
        pendingDeltaX = 0;
        pendingDeltaY = 0;
        pointerCount = 0;
    }

    /**
     * Simulates a move event and accumulates momentum. Mirrors the velocity EMA update
     * in TrackpadContext.touchMoveEvent.
     *
     * @param x         new X position
     * @param y         new Y position
     * @param eventTime event timestamp (ms)
     */
    public void touchMove(int x, int y, long eventTime) {
        if (cancelled || pointerCount == 0) return;

        int rawDx = x - lastTouchX;
        int rawDy = y - lastTouchY;
        long dt = (lastMoveTime == 0) ? 1 : Math.max(1, eventTime - lastMoveTime);

        // Accumulate scaled delta
        pendingDeltaX += applyScaling(rawDx, 'x');
        pendingDeltaY += applyScaling(rawDy, 'y');

        // Velocity EMA — mirrors production "velocityX = (vx + rawDelta/dt) / 2" pattern
        double vxSample = (double) rawDx / dt;
        double vySample = (double) rawDy / dt;
        velocityX = (velocityX + vxSample) / 2.0;
        velocityY = (velocityY + vySample) / 2.0;

        distanceMoved += Math.sqrt((double) rawDx * rawDx + (double) rawDy * rawDy);
        confirmedMove = distanceMoved > TAP_MOVEMENT_THRESHOLD;

        lastTouchX = x;
        lastTouchY = y;
        lastMoveTime = eventTime;

        // Flush accumulated delta to connection
        short intDx = (short) pendingDeltaX;
        short intDy = (short) pendingDeltaY;
        if (intDx != 0 || intDy != 0) {
            conn.sendMouseMove(intDx, intDy);
            pendingDeltaX -= intDx;
            pendingDeltaY -= intDy;
        }
    }

    /**
     * Triggers flick (momentum) start. Mirrors the flick initiation in touchUpEvent.
     * Returns true if flick was started.
     */
    public boolean tryStartFlick() {
        double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (speed > FLICK_THRESHOLD) {
            isFlicking = true;
            return true;
        }
        return false;
    }

    /**
     * Advances momentum by one frame. Call repeatedly to simulate animation frames.
     * Returns true if momentum is still active.
     */
    public boolean tickMomentum() {
        if (!isFlicking) return false;

        pendingDeltaX += velocityX * MOMENTUM_FRAME_INTERVAL_MS;
        pendingDeltaY += velocityY * MOMENTUM_FRAME_INTERVAL_MS;

        short intDx = (short) pendingDeltaX;
        short intDy = (short) pendingDeltaY;
        if (intDx != 0 || intDy != 0) {
            conn.sendMouseMove(intDx, intDy);
            pendingDeltaX -= intDx;
            pendingDeltaY -= intDy;
        }

        velocityX *= FLICK_FRICTION;
        velocityY *= FLICK_FRICTION;

        double magnitude = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (magnitude * MOMENTUM_FRAME_INTERVAL_MS < 0.5) {
            isFlicking = false;
        }
        return isFlicking;
    }
}
