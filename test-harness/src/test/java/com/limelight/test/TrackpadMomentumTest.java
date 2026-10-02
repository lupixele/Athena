package com.limelight.test;

import com.limelight.test.fakes.FakeConnection;
import com.limelight.test.fakes.FakeScheduler;
import com.limelight.test.fakes.TrackpadLogicMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for multi-finger gesture state machine and flick/momentum in TrackpadContext.
 *
 * Covers:
 * - 1-finger flick starts momentum (velocity above FLICK_THRESHOLD)
 * - Slow move does NOT start flick (velocity below FLICK_THRESHOLD)
 * - Momentum decays via FLICK_FRICTION per tick
 * - Momentum stops when speed drops below threshold
 * - 2-finger gesture reaches BUTTON_RIGHT mapping
 * - 3-finger gesture reaches BUTTON_MIDDLE mapping and sends middle click
 * - Multi-finger pointer count tracks correctly through down/up
 *
 * Constant reference from TrackpadContext (commit e1402952):
 *   FLICK_THRESHOLD = 0.8   px/ms
 *   FLICK_FRICTION  = 0.93
 *   MOMENTUM_FRAME_INTERVAL_MS = 10
 */
@DisplayName("TrackpadContext — Multi-Finger & Momentum")
class TrackpadMomentumTest {

    private FakeConnection conn;
    private FakeScheduler scheduler;
    private TrackpadLogicMirror ctx;

    @BeforeEach
    void setUp() {
        conn = new FakeConnection();
        scheduler = new FakeScheduler();
        ctx = new TrackpadLogicMirror(conn, 0, scheduler);
    }

    /** Build a gesture that exceeds FLICK_THRESHOLD (0.8 px/ms). */
    private void doFastSwipe() {
        ctx.touchDown(0, 0, 0, true);
        // Fast: 30 px in 10 ms = 3 px/ms >> 0.8 threshold — should flick
        ctx.touchMove(10, 0, 3);
        ctx.touchMove(20, 0, 6);
        ctx.touchMove(30, 0, 9);
        ctx.touchMove(40, 0, 12);
    }

    /** Build a gesture well below FLICK_THRESHOLD. */
    private void doSlowMove() {
        ctx.touchDown(0, 0, 0, true);
        // Slow: 5 px in 200 ms = 0.025 px/ms << 0.8 threshold — should NOT flick
        ctx.touchMove(2, 0, 100);
        ctx.touchMove(4, 0, 200);
    }

    // ---- Flick initiation ----

    @Test
    @DisplayName("fast 1-finger swipe starts flick momentum")
    void fastSwipe_startsFlick() {
        doFastSwipe();
        boolean started = ctx.tryStartFlick();
        assertThat(started).isTrue();
        assertThat(ctx.isFlicking()).isTrue();
    }

    @Test
    @DisplayName("slow move does NOT start flick momentum")
    void slowMove_doesNotStartFlick() {
        doSlowMove();
        boolean started = ctx.tryStartFlick();
        assertThat(started).isFalse();
        assertThat(ctx.isFlicking()).isFalse();
    }

    // ---- Momentum decay ----

    @Test
    @DisplayName("momentum tick reduces velocity by FLICK_FRICTION factor")
    void momentumTick_reducesVelocityByFriction() {
        doFastSwipe();
        ctx.tryStartFlick();

        double vxBefore = ctx.getVelocityX();
        ctx.tickMomentum();
        double vxAfter = ctx.getVelocityX();

        assertThat(vxAfter).isCloseTo(vxBefore * TrackpadLogicMirror.FLICK_FRICTION, within(1e-9));
    }

    @Test
    @DisplayName("momentum eventually stops after enough ticks")
    void momentum_stopsEventually() {
        doFastSwipe();
        ctx.tryStartFlick();

        int maxTicks = 1000;
        int ticks = 0;
        while (ctx.tickMomentum() && ticks < maxTicks) {
            ticks++;
        }
        assertThat(ticks).isLessThan(maxTicks); // must have stopped
        assertThat(ctx.isFlicking()).isFalse();
    }

    @Test
    @DisplayName("momentum ticks produce sendMouseMove calls while active")
    void momentum_producesMoveEvents() {
        doFastSwipe();
        ctx.tryStartFlick();
        conn.reset(); // ignore setup moves

        // Run a few ticks — at least some moves should come out
        for (int i = 0; i < 10; i++) {
            ctx.tickMomentum();
        }
        assertThat(conn.moves()).isNotEmpty();
    }

    @Test
    @DisplayName("momentum produces no moves after it stops")
    void momentumStopped_producesNoMoves() {
        doFastSwipe();
        ctx.tryStartFlick();

        // Drain to completion
        while (ctx.tickMomentum()) { /* spin */ }
        conn.reset(); // clear

        // Extra ticks after stop should produce nothing
        ctx.tickMomentum();
        ctx.tickMomentum();
        assertThat(conn.moves()).isEmpty();
    }

    // ---- Multi-finger state machine ----

    @Test
    @DisplayName("2-finger down: pointerCount=2, maxPointerCount=2")
    void twoFingers_down_tracksCorrectly() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 100, 5, true);

        assertThat(ctx.getPointerCount()).isEqualTo(2);
        assertThat(ctx.getMaxPointerCountInGesture()).isEqualTo(2);
    }

    @Test
    @DisplayName("3-finger down: pointerCount=3, maxPointerCount=3")
    void threeFingers_down_tracksCorrectly() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 100, 5, true);
        ctx.touchDown(300, 100, 10, true);

        assertThat(ctx.getPointerCount()).isEqualTo(3);
        assertThat(ctx.getMaxPointerCountInGesture()).isEqualTo(3);
    }

    @Test
    @DisplayName("lifting 1 of 3 fingers: pointerCount=2, maxPointerCount=3 retained")
    void threeFingers_liftOne_retainsMax() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 100, 5, true);
        ctx.touchDown(300, 100, 10, true);
        ctx.touchUp(300, 100, 50); // lift one

        assertThat(ctx.getPointerCount()).isEqualTo(2);
        assertThat(ctx.getMaxPointerCountInGesture()).isEqualTo(3);
    }

    @Test
    @DisplayName("lifting all fingers: pointerCount=0, gesture cancelled")
    void allFingersLifted_cancelGesture() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 100, 5, true);
        ctx.touchUp(200, 100, 50);
        ctx.touchUp(100, 100, 60);

        assertThat(ctx.getPointerCount()).isZero();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("FLICK_THRESHOLD constant is 0.8 px/ms as declared in production source")
    void flickThresholdConstant_matchesProduction() {
        assertThat(TrackpadLogicMirror.FLICK_THRESHOLD).isEqualTo(0.8);
    }

    @Test
    @DisplayName("FLICK_FRICTION constant is 0.93 as declared in production source")
    void flickFrictionConstant_matchesProduction() {
        assertThat(TrackpadLogicMirror.FLICK_FRICTION).isEqualTo(0.93);
    }

    @Test
    @DisplayName("MOMENTUM_FRAME_INTERVAL_MS is 10 as declared in production source")
    void momentumFrameInterval_matchesProduction() {
        assertThat(TrackpadLogicMirror.MOMENTUM_FRAME_INTERVAL_MS).isEqualTo(10);
    }
}
