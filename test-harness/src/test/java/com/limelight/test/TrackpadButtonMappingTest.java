package com.limelight.test;

import com.limelight.test.fakes.FakeConnection;
import com.limelight.test.fakes.FakeScheduler;
import com.limelight.test.fakes.TrackpadLogicMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for button mapping in TrackpadContext.
 *
 * Production logic (TrackpadContext.getMouseButtonIndex()):
 *   1 finger → BUTTON_LEFT  (0x01)
 *   2 fingers → BUTTON_RIGHT (0x03)
 *   3 fingers → BUTTON_MIDDLE (0x02)
 *   other → BUTTON_LEFT (default)
 *
 * Also tests the 3-finger → middle click auto-send behavior.
 * Verified against production code at commit e1402952.
 */
@DisplayName("TrackpadContext — Button Mapping")
class TrackpadButtonMappingTest {

    private FakeConnection conn;
    private FakeScheduler scheduler;
    private TrackpadLogicMirror ctx;

    @BeforeEach
    void setUp() {
        conn = new FakeConnection();
        scheduler = new FakeScheduler();
        ctx = new TrackpadLogicMirror(conn, 0, scheduler);
    }

    @Test
    @DisplayName("1-finger gesture maps to BUTTON_LEFT")
    void oneFingerGesture_mapsToLeftButton() {
        ctx.touchDown(100, 100, 0, true);
        assertThat(ctx.getMouseButtonIndex()).isEqualTo(FakeConnection.BUTTON_LEFT);
    }

    @Test
    @DisplayName("2-finger gesture maps to BUTTON_RIGHT")
    void twoFingerGesture_mapsToRightButton() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 200, 5, true);
        assertThat(ctx.getMouseButtonIndex()).isEqualTo(FakeConnection.BUTTON_RIGHT);
    }

    @Test
    @DisplayName("3-finger gesture maps to BUTTON_MIDDLE")
    void threeFingerGesture_mapsToMiddleButton() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 200, 5, true);
        ctx.touchDown(300, 300, 10, true);
        assertThat(ctx.getMouseButtonIndex()).isEqualTo(FakeConnection.BUTTON_MIDDLE);
    }

    @Test
    @DisplayName("maxPointerCountInGesture persists after fingers lift")
    void maxPointerCount_persistsAfterLift() {
        // Touch 2 fingers down, lift one — maxPointerCount still 2
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 200, 5, true);
        ctx.touchUp(200, 200, 50);
        // maxPointerCountInGesture should still be 2
        assertThat(ctx.getMaxPointerCountInGesture()).isEqualTo(2);
        // button mapping still RIGHT (based on max, not current)
        assertThat(ctx.getMouseButtonIndex()).isEqualTo(FakeConnection.BUTTON_RIGHT);
    }

    @Test
    @DisplayName("3-finger touch-down triggers immediate BUTTON_MIDDLE press event")
    void threeFingerTouchDown_sendsMiddleButtonDown() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 200, 5, true);
        assertThat(conn.pressCount(FakeConnection.BUTTON_MIDDLE)).isZero();

        ctx.touchDown(300, 300, 10, true);
        assertThat(conn.pressCount(FakeConnection.BUTTON_MIDDLE)).isEqualTo(1);
    }

    @Test
    @DisplayName("3-finger middle click not sent twice on same gesture")
    void threeFingerMiddleClick_sentOnlyOnce() {
        ctx.touchDown(100, 100, 0, true);
        ctx.touchDown(200, 200, 5, true);
        ctx.touchDown(300, 300, 10, true);
        // Simulate extra touch-down calls (shouldn't double-fire)
        ctx.touchDown(300, 300, 15, true);
        assertThat(conn.pressCount(FakeConnection.BUTTON_MIDDLE)).isEqualTo(1);
    }

    @Test
    @DisplayName("BUTTON_LEFT, BUTTON_RIGHT, BUTTON_MIDDLE constants have expected byte values")
    void buttonConstants_haveExpectedValues() {
        assertThat(FakeConnection.BUTTON_LEFT).isEqualTo((byte) 0x01);
        assertThat(FakeConnection.BUTTON_MIDDLE).isEqualTo((byte) 0x02);
        assertThat(FakeConnection.BUTTON_RIGHT).isEqualTo((byte) 0x03);
    }
}
