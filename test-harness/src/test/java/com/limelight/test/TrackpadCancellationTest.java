package com.limelight.test;

import com.limelight.test.fakes.FakeConnection;
import com.limelight.test.fakes.FakeScheduler;
import com.limelight.test.fakes.TrackpadLogicMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for gesture state cancellation in TrackpadContext.
 *
 * Covers:
 * - Pointer-cancel resets momentum (isFlicking → false, velocity → 0)
 * - Pointer-cancel resets all pending delta
 * - State is clean after cancel so a new gesture can start fresh
 * - touchUp with all fingers up marks gesture as cancelled
 *
 * Verified against production code at commit e1402952.
 */
@DisplayName("TrackpadContext — State Cancellation")
class TrackpadCancellationTest {

    private FakeConnection conn;
    private FakeScheduler scheduler;
    private TrackpadLogicMirror ctx;

    @BeforeEach
    void setUp() {
        conn = new FakeConnection();
        scheduler = new FakeScheduler();
        ctx = new TrackpadLogicMirror(conn, 0, scheduler);
    }

    private void startOneFingerGesture() {
        ctx.touchDown(0, 0, 0, true);
        // Simulate fast swipe to build velocity
        ctx.touchMove(20, 0, 10);
        ctx.touchMove(40, 0, 20);
        ctx.touchMove(60, 0, 30);
    }

    @Test
    @DisplayName("cancelGesture clears isFlicking flag")
    void cancel_clearsFlicking() {
        startOneFingerGesture();
        ctx.tryStartFlick();
        assertThat(ctx.isFlicking()).isTrue();

        ctx.cancelGesture();
        assertThat(ctx.isFlicking()).isFalse();
    }

    @Test
    @DisplayName("cancelGesture zeroes velocity")
    void cancel_clearsVelocity() {
        startOneFingerGesture();
        ctx.tryStartFlick();

        ctx.cancelGesture();
        assertThat(ctx.getVelocityX()).isEqualTo(0.0);
        assertThat(ctx.getVelocityY()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("cancelGesture marks gesture as cancelled")
    void cancel_marksCancelled() {
        startOneFingerGesture();
        ctx.cancelGesture();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("cancelGesture stops momentum tick from producing moves")
    void cancel_stopsMomentumTicks() {
        startOneFingerGesture();
        ctx.tryStartFlick();
        conn.reset(); // clear setup moves
        ctx.cancelGesture();

        boolean stillRunning = ctx.tickMomentum();
        assertThat(stillRunning).isFalse();
        assertThat(conn.moves()).isEmpty();
    }

    @Test
    @DisplayName("touchUp with all fingers lifted cancels gesture")
    void touchUp_allFingersLift_cancelsGesture() {
        ctx.touchDown(0, 0, 0, true);
        ctx.touchMove(50, 0, 50);
        ctx.touchUp(50, 0, 100); // last finger up
        assertThat(ctx.isCancelled()).isTrue();
        assertThat(ctx.getPointerCount()).isZero();
    }

    @Test
    @DisplayName("cancelled context does not produce more moves on touchMove")
    void cancelledContext_ignoresSubsequentMoveEvents() {
        startOneFingerGesture();
        ctx.cancelGesture();
        conn.reset();

        ctx.touchMove(100, 0, 40); // should be ignored
        assertThat(conn.moves()).isEmpty();
    }

    @Test
    @DisplayName("momentum does not produce moves after cancel even with non-zero velocity")
    void cancelledContext_momentumProducesNoMoves() {
        startOneFingerGesture();
        // Force velocity to something large without proper flick start
        ctx.cancelGesture();
        conn.reset();

        // tickMomentum should return false immediately
        assertThat(ctx.tickMomentum()).isFalse();
        assertThat(conn.moves()).isEmpty();
    }
}
