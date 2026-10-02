package com.limelight.test;

import android.os.Handler;
import android.os.Looper;
import com.limelight.binding.input.touch.TrackpadContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.test.scheduler.VirtualScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production TrackpadContext — Cancellation & Bug Fix Tests")
class TrackpadCancellationFixTest {

    private NvConnection conn;
    private VirtualScheduler scheduler;
    private TrackpadContext ctx;

    @BeforeEach
    void setUp() {
        Looper.resetMainLooper();
        conn = new NvConnection();
        scheduler = Looper.getMainLooper().getScheduler();
        ctx = new TrackpadContext(conn, 0);
    }

    @Test
    @DisplayName("Bug Fix: Tap followed by second down before release timeout, then CANCEL releases button")
    void tap_secondDown_cancel_releasesButton() {
        // Step 1: User does a quick tap (down + up)
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        ctx.touchUpEvent(100, 100, 1050);

        // Tap confirmed: BUTTON_LEFT down is sent, delayed release scheduled (CLICK_RELEASE_DELAY = 230ms)
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();

        // Step 2: Before 230ms expires (e.g. at 1100ms, +50ms after up), user puts finger down again
        // In unpatched code, this turns isClickPending into isDblClickPending and cancels the release timer
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1100, true);

        // Button is still held
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();

        // Step 3: MotionEvent.ACTION_CANCEL arrives
        ctx.cancelTouch();

        // Under the bug, cancelTouch did NOT send button up unless confirmedDrag was true.
        // With our fix, cancelTouch idempotently releases all held buttons!
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
        assertThat(ctx.isCancelled()).isTrue();

        // Step 4: Advance scheduler time to ensure no stale delayed release fires
        int eventCount = conn.events().size();
        scheduler.advanceTime(1000);
        assertThat(conn.events()).hasSize(eventCount); // No extra events fired!
    }

    @Test
    @DisplayName("Bug Fix: 3-finger middle-down cancel releases middle button")
    void threeFingerMiddleDown_cancel_releasesButton() {
        // TrackpadContext 3-finger tap:
        // Set actionIndex to 2 so (actionIndex + 1 == maxPointerCountInGesture) is satisfied for 3-finger gesture
        TrackpadContext ctx3 = new TrackpadContext(conn, 2);
        ctx3.setPointerCount(3);
        ctx3.touchDownEvent(100, 100, 1000, true);

        // Tap up sends BUTTON_MIDDLE down with delayed release
        ctx3.touchUpEvent(100, 100, 1050);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_MIDDLE)).isTrue();

        // While middle click is down/pending, cancel arrives
        ctx3.cancelTouch();
        // Middle button is released idempotently!
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_MIDDLE)).isFalse();
    }

    @Test
    @DisplayName("Bug Fix: Drag cancelled then new gesture starts cleanly")
    void drag_cancel_newGesture() {
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        // Move > 30px to confirm move/drag
        ctx.touchMoveEvent(150, 150, 1020);

        // Now cancel
        ctx.cancelTouch();
        assertThat(ctx.isCancelled()).isTrue();
        assertThat(conn.heldButtons()).isEmpty();

        // Start new gesture with isNewFinger = true
        ctx.setPointerCount(1);
        ctx.touchDownEvent(200, 200, 2000, true);
        assertThat(ctx.isCancelled()).isFalse();

        // Tap on new gesture works
        ctx.touchUpEvent(200, 200, 2050);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();

        // Scheduler advances -> releases
        scheduler.advanceTime(235);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
    }

    @Test
    @DisplayName("Bug Fix: Repeated cancelTouch calls are idempotent")
    void repeatedCancel_isIdempotent() {
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        ctx.touchUpEvent(100, 100, 1050);

        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();

        // First cancel
        ctx.cancelTouch();
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
        int eventCount = conn.events().size();

        // Second cancel
        ctx.cancelTouch();
        assertThat(conn.events()).hasSize(eventCount); // No duplicate ButtonUp events sent!
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
    }

    @Test
    @DisplayName("Bug Fix: Owner Handler isolation on shared Looper — cancelling one Handler preserves the other")
    void twoHandler_cancelOne_preservesOther() {
        Handler handlerA = new Handler();
        Handler handlerB = new Handler();

        AtomicBoolean executedA = new AtomicBoolean(false);
        AtomicBoolean executedB = new AtomicBoolean(false);

        handlerA.postDelayed(() -> executedA.set(true), 100);
        handlerB.postDelayed(() -> executedB.set(true), 100);

        assertThat(scheduler.pendingCount(handlerA)).isEqualTo(1);
        assertThat(scheduler.pendingCount(handlerB)).isEqualTo(1);
        assertThat(scheduler.pendingCount()).isEqualTo(2);

        // Cancel callbacks only on Handler A
        handlerA.removeCallbacksAndMessages(null);

        // Handler A's callbacks must be gone, Handler B's must be preserved
        assertThat(scheduler.pendingCount(handlerA)).isEqualTo(0);
        assertThat(scheduler.pendingCount(handlerB)).isEqualTo(1);
        assertThat(scheduler.pendingCount()).isEqualTo(1);

        // Advance time to run Handler B
        scheduler.advanceTime(150);

        assertThat(executedA.get()).isFalse();
        assertThat(executedB.get()).isTrue();
    }

    @Test
    @DisplayName("Bug Fix: Two Handlers sharing same Runnable instance — removeCallbacks on Handler A preserves Handler B")
    void twoHandler_sameRunnable_cancelOne_preservesOther() {
        Handler handlerA = new Handler();
        Handler handlerB = new Handler();
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        Runnable sharedRunnable = callCount::incrementAndGet;

        handlerA.postDelayed(sharedRunnable, 100);
        handlerB.postDelayed(sharedRunnable, 100);

        // Cancel shared runnable on handlerA only
        handlerA.removeCallbacks(sharedRunnable);

        assertThat(scheduler.pendingCount(handlerA)).isEqualTo(0);
        assertThat(scheduler.pendingCount(handlerB)).isEqualTo(1);

        scheduler.advanceTime(150);

        assertThat(callCount.get()).isEqualTo(1); // Only handlerB's ran!
    }

    @Test
    @DisplayName("Bug Fix: Delayed click release timer is cancelled on cancelTouch, preventing late button release")
    void clickReleaseTimer_cancelledOnCancelTouch() {
        // Tap down + up schedules delayed release timer (230ms)
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        ctx.touchUpEvent(100, 100, 1050);

        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();
        assertThat(scheduler.hasPending()).isTrue();

        // cancelTouch immediately releases buttons and removes all callbacks
        ctx.cancelTouch();
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
        assertThat(scheduler.hasPending()).isFalse();

        int eventsBeforeAdvance = conn.events().size();
        scheduler.advanceTime(500);

        // No trailing release or double release packet emitted
        assertThat(conn.events()).hasSize(eventsBeforeAdvance);
    }

    @Test
    @DisplayName("Bug Fix: Scroll transition timer is cancelled on cancelTouch")
    void scrollTransitionTimer_cancelledOnCancelTouch() {
        // Perform 2-finger scroll
        ctx.setPointerCount(2);
        ctx.touchDownEvent(100, 100, 1000, true);
        ctx.touchMoveEvent(100, 200, 1020);

        // Lift one finger -> triggers scrollTransitionRunnable scheduled at SCROLL_TRANSITION_TIMEOUT_MS
        ctx.setPointerCount(1);
        assertThat(scheduler.hasPending()).isTrue();

        // Cancel gesture
        ctx.cancelTouch();
        assertThat(scheduler.hasPending()).isFalse();

        // Advance scheduler -> no leftover tasks run
        scheduler.advanceTime(500);
        assertThat(scheduler.hasPending()).isFalse();
    }

    @Test
    @DisplayName("Bug Fix: Pointer flick momentum initiates emits, cancel with queued frame stops all further events")
    void pointerFlickMomentum_cancel_stopsQueuedFramesAndEmits() {
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        // High velocity move (dx=100 in 10ms -> speed=10.0 > FLICK_THRESHOLD 0.8)
        ctx.touchMoveEvent(200, 100, 1010);
        // Finger up triggers flick momentum runnable post
        ctx.touchUpEvent(200, 100, 1015);

        // Momentum frame runnable should be queued
        assertThat(scheduler.hasPending()).isTrue();

        // Advance 1 frame (10ms) to let momentum start emitting mouse moves
        int eventsBeforeFirstFrame = conn.events().size();
        scheduler.advanceTime(10);
        int eventsAfterFirstFrame = conn.events().size();

        // MouseMoveEvent emitted by momentum
        assertThat(eventsAfterFirstFrame).isGreaterThan(eventsBeforeFirstFrame);
        // There should still be a next momentum frame queued
        assertThat(scheduler.hasPending()).isTrue();

        // Now CANCEL mid-momentum!
        ctx.cancelTouch();

        // Cancellation must clear all queued momentum frames
        assertThat(scheduler.hasPending()).isFalse();

        // Advance time extensively — verify no further events are emitted
        int eventsAfterCancel = conn.events().size();
        scheduler.advanceTime(1000);
        assertThat(conn.events()).hasSize(eventsAfterCancel);
        assertThat(scheduler.hasPending()).isFalse();
    }

    @Test
    @DisplayName("Bug Fix: Scroll flick momentum initiates emits, cancel with queued frame stops all further events")
    void scrollFlickMomentum_cancel_stopsQueuedFramesAndEmits() {
        TrackpadContext scrollCtx = new TrackpadContext(conn, 1);
        scrollCtx.setPointerCount(2);
        scrollCtx.touchDownEvent(100, 100, 1000, true);
        // High velocity scroll move (dy=100 in 10ms -> speed=10.0 > FLICK_THRESHOLD 0.8)
        scrollCtx.touchMoveEvent(100, 200, 1010);
        // Fingers up triggers scroll flick momentum runnable post
        scrollCtx.touchUpEvent(100, 200, 1015);

        // Scroll momentum frame runnable should be queued
        assertThat(scheduler.hasPending()).isTrue();

        // Advance 1 frame (10ms) to let scroll momentum start emitting scroll events
        int eventsBeforeFirstFrame = conn.events().size();
        scheduler.advanceTime(10);
        int eventsAfterFirstFrame = conn.events().size();

        // Scroll event emitted by momentum
        assertThat(eventsAfterFirstFrame).isGreaterThan(eventsBeforeFirstFrame);
        // There should still be a next scroll momentum frame queued
        assertThat(scheduler.hasPending()).isTrue();

        // Now CANCEL mid-scroll-momentum!
        scrollCtx.cancelTouch();

        // Cancellation must clear all queued momentum frames
        assertThat(scheduler.hasPending()).isFalse();

        // Advance time extensively — verify no further scroll events are emitted
        int eventsAfterCancel = conn.events().size();
        scheduler.advanceTime(1000);
        assertThat(conn.events()).hasSize(eventsAfterCancel);
        assertThat(scheduler.hasPending()).isFalse();
    }
}
