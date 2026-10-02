package com.limelight.test;

import com.limelight.test.fakes.FakeConnection;
import com.limelight.test.fakes.FakeScheduler;
import com.limelight.test.fakes.TrackpadLogicMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for TrackpadContext default constructor values.
 *
 * Mirrors: TrackpadContext(NvConnection, int) — the zero-arg sensitivity constructor.
 * Verified against production code at commit e1402952 on branch athena/touch-preservation-slice.
 */
@DisplayName("TrackpadContext — Default Constructor Defaults")
class TrackpadDefaultsTest {

    private FakeConnection conn;
    private FakeScheduler scheduler;
    private TrackpadLogicMirror ctx;

    @BeforeEach
    void setUp() {
        conn = new FakeConnection();
        scheduler = new FakeScheduler();
        ctx = new TrackpadLogicMirror(conn, /* actionIndex= */ 0, scheduler);
    }

    @Test
    @DisplayName("sensitivityX defaults to 1.0 (100/100)")
    void sensitivityX_defaultsToOne() {
        assertThat(ctx.getSensitivityX()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("sensitivityY defaults to 1.0 (100/100)")
    void sensitivityY_defaultsToOne() {
        assertThat(ctx.getSensitivityY()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("swapAxis defaults to false")
    void swapAxis_defaultsFalse() {
        assertThat(ctx.isSwapAxis()).isFalse();
    }

    @Test
    @DisplayName("not cancelled at construction")
    void notCancelledAtConstruction() {
        assertThat(ctx.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("not flicking at construction")
    void notFlickingAtConstruction() {
        assertThat(ctx.isFlicking()).isFalse();
    }

    @Test
    @DisplayName("velocity is zero at construction")
    void velocityZeroAtConstruction() {
        assertThat(ctx.getVelocityX()).isEqualTo(0.0);
        assertThat(ctx.getVelocityY()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("pointerCount is zero at construction")
    void pointerCountZeroAtConstruction() {
        assertThat(ctx.getPointerCount()).isZero();
    }

    @Test
    @DisplayName("maxPointerCountInGesture is zero at construction")
    void maxPointerCountZeroAtConstruction() {
        assertThat(ctx.getMaxPointerCountInGesture()).isZero();
    }
}
