package com.limelight.test;

import android.os.Looper;
import com.limelight.binding.input.touch.AbsoluteTouchContext;
import com.limelight.binding.input.touch.RelativeTouchContext;
import com.limelight.binding.input.touch.TrackpadContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.test.scheduler.VirtualScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production Touch Contexts — Constructors and Ported Defaults")
class ProductionContextConstructorsTest {

    private NvConnection conn;
    private VirtualScheduler scheduler;

    @BeforeEach
    void setUp() {
        Looper.resetMainLooper();
        conn = new NvConnection();
        scheduler = Looper.getMainLooper().getScheduler();
    }

    @Test
    @DisplayName("TrackpadContext default constructor sets actionIndex and default sensitivities (1.0x)")
    void trackpadDefaultConstructor() {
        TrackpadContext ctx = new TrackpadContext(conn, 0);
        assertThat(ctx.getActionIndex()).isEqualTo(0);
        assertThat(ctx.isCancelled()).isFalse();

        // Single tap should trigger left click down, then delayed release
        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        ctx.touchUpEvent(100, 100, 1050);

        assertThat(conn.events()).contains(new NvConnection.MouseButtonDownEvent(MouseButtonPacket.BUTTON_LEFT));
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();

        scheduler.advanceTime(235);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
    }

    @Test
    @DisplayName("TrackpadContext configurable constructor sets swapAxis and custom sensitivity")
    void trackpadConfigurableConstructor() {
        // swapAxis = false, sensitivity = 100 (1.0x)
        TrackpadContext ctx = new TrackpadContext(conn, 0, false, 100, 100);
        assertThat(ctx.getActionIndex()).isEqualTo(0);
        assertThat(ctx.isCancelled()).isFalse();

        ctx.setPointerCount(1);
        ctx.touchDownEvent(100, 100, 1000, true);
        // Move > TAP_MOVEMENT_THRESHOLD (30px)
        ctx.touchMoveEvent(150, 100, 1020);

        // Movement occurred
        assertThat(conn.events()).anyMatch(e -> e instanceof NvConnection.MouseMoveEvent);
    }

    @Test
    @DisplayName("AbsoluteTouchContext default constructor uses primary=BUTTON_LEFT and secondary=BUTTON_RIGHT")
    void absoluteDefaultConstructor() {
        android.view.View view = new android.view.View(1920, 1080);
        AbsoluteTouchContext ctx = new AbsoluteTouchContext(conn, 0, view);
        assertThat(ctx.getActionIndex()).isEqualTo(0);
        assertThat(ctx.isCancelled()).isFalse();

        // Tap down
        ctx.setPointerCount(1);
        ctx.touchDownEvent(500, 500, 1000, true);
        // TOUCH_DOWN_DEAD_ZONE_TIME_THRESHOLD is 100ms
        scheduler.advanceTime(110);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isTrue();

        // Touch up sends up
        ctx.touchUpEvent(500, 500, 1150);
        scheduler.advanceTime(110);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_LEFT)).isFalse();
    }

    @Test
    @DisplayName("AbsoluteTouchContext configurable constructor allows custom primary/secondary buttons")
    void absoluteConfigurableConstructor() {
        android.view.View view = new android.view.View(1920, 1080);
        AbsoluteTouchContext ctx = new AbsoluteTouchContext(
                conn, 0, view, MouseButtonPacket.BUTTON_RIGHT, MouseButtonPacket.BUTTON_LEFT);
        assertThat(ctx.getActionIndex()).isEqualTo(0);

        ctx.setPointerCount(1);
        ctx.touchDownEvent(500, 500, 1000, true);
        scheduler.advanceTime(110);

        // Custom primary is BUTTON_RIGHT
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_RIGHT)).isTrue();

        ctx.touchUpEvent(500, 500, 1150);
        scheduler.advanceTime(110);
        assertThat(conn.isButtonHeld(MouseButtonPacket.BUTTON_RIGHT)).isFalse();
    }

    @Test
    @DisplayName("RelativeTouchContext default and configurable constructors compile and operate")
    void relativeConstructors() {
        android.view.View view = new android.view.View(1920, 1080);
        PreferenceConfiguration pref = new PreferenceConfiguration();

        RelativeTouchContext defaultCtx = new RelativeTouchContext(conn, 0, 1920, 1080, view, pref);
        assertThat(defaultCtx.getActionIndex()).isEqualTo(0);

        RelativeTouchContext customCtx = new RelativeTouchContext(conn, 0, 1920, 1080, view, pref, 150f, 150f);
        assertThat(customCtx.getActionIndex()).isEqualTo(0);
    }
}
