package com.limelight.test;

import android.os.Looper;
import com.limelight.binding.input.touch.TrackpadContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.test.scheduler.VirtualScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production TrackpadContext — Gesture Dynamics & Mapping")
class TrackpadProductionGesturesTest {

    private NvConnection conn;
    private VirtualScheduler scheduler;

    @BeforeEach
    void setUp() {
        Looper.resetMainLooper();
        conn = new NvConnection();
        scheduler = Looper.getMainLooper().getScheduler();
    }

    @Test
    @DisplayName("Axis swap actual behavior: raw X maps to deltaY and raw Y maps to deltaX")
    void axisSwap_actualBehavior() {
        // swapAxis = false (default behavior)
        TrackpadContext normalCtx = new TrackpadContext(conn, 0, false, 100, 100);
        normalCtx.setPointerCount(1);
        normalCtx.touchDownEvent(100, 100, 1000, true);
        // Move with dx=60, dy=15
        normalCtx.touchMoveEvent(160, 115, 1020);

        List<NvConnection.Event> normalEvents = conn.events();
        NvConnection.MouseMoveEvent normalMove = normalEvents.stream()
                .filter(e -> e instanceof NvConnection.MouseMoveEvent)
                .map(e -> (NvConnection.MouseMoveEvent) e)
                .findFirst()
                .orElseThrow();

        assertThat(normalMove.deltaX()).isGreaterThan((short) 0);
        assertThat(normalMove.deltaX()).isGreaterThan(normalMove.deltaY());

        // Now test swapAxis = true with identical touch movement
        conn.reset();
        TrackpadContext swappedCtx = new TrackpadContext(conn, 0, true, 100, 100);
        swappedCtx.setPointerCount(1);
        swappedCtx.touchDownEvent(100, 100, 1000, true);
        // Move with dx=60, dy=15
        swappedCtx.touchMoveEvent(160, 115, 1020);

        List<NvConnection.Event> swappedEvents = conn.events();
        NvConnection.MouseMoveEvent swappedMove = swappedEvents.stream()
                .filter(e -> e instanceof NvConnection.MouseMoveEvent)
                .map(e -> (NvConnection.MouseMoveEvent) e)
                .findFirst()
                .orElseThrow();

        // In swapAxis: deltaX receives rawDeltaY (15), deltaY receives rawDeltaX (60)
        assertThat(swappedMove.deltaY()).isGreaterThan(swappedMove.deltaX());
        assertThat(swappedMove.deltaX()).isEqualTo(normalMove.deltaY());
        assertThat(swappedMove.deltaY()).isEqualTo(normalMove.deltaX());
    }

    @Test
    @DisplayName("Sensitivity scaling scales mouse movement deltas proportionally")
    void sensitivityScaling_actualBehavior() {
        TrackpadContext ctx100 = new TrackpadContext(conn, 0, false, 100, 100);
        ctx100.setPointerCount(1);
        ctx100.touchDownEvent(100, 100, 1000, true);
        ctx100.touchMoveEvent(150, 100, 1020);

        NvConnection.MouseMoveEvent move100 = conn.events().stream()
                .filter(e -> e instanceof NvConnection.MouseMoveEvent)
                .map(e -> (NvConnection.MouseMoveEvent) e)
                .findFirst()
                .orElseThrow();

        conn.reset();
        TrackpadContext ctx200 = new TrackpadContext(conn, 0, false, 200, 200);
        ctx200.setPointerCount(1);
        ctx200.touchDownEvent(100, 100, 1000, true);
        ctx200.touchMoveEvent(150, 100, 1020);

        NvConnection.MouseMoveEvent move200 = conn.events().stream()
                .filter(e -> e instanceof NvConnection.MouseMoveEvent)
                .map(e -> (NvConnection.MouseMoveEvent) e)
                .findFirst()
                .orElseThrow();

        // 200% sensitivity produces approximately double the delta
        assertThat(move200.deltaX()).isGreaterThan(move100.deltaX());
        assertThat(move200.deltaX()).isCloseTo((short) (move100.deltaX() * 2), org.assertj.core.data.Offset.offset((short) 1));
    }

    @Test
    @DisplayName("Two finger movement produces high-res scroll events, not mouse moves")
    void twoFingerScroll_actualBehavior() {
        TrackpadContext ctx = new TrackpadContext(conn, 1);
        ctx.setPointerCount(2);
        ctx.touchDownEvent(200, 200, 1000, true);
        // Move > 30px in Y
        ctx.touchMoveEvent(200, 260, 1020);

        List<NvConnection.Event> events = conn.events();
        assertThat(events).noneMatch(e -> e instanceof NvConnection.MouseMoveEvent);
        assertThat(events).anyMatch(e -> e instanceof NvConnection.MouseScrollEvent);
    }

    @Test
    @DisplayName("Double click gesture generates down -> up -> down -> up sequence")
    void doubleClick_actualBehavior() {
        TrackpadContext ctx = new TrackpadContext(conn, 0);
        ctx.setPointerCount(1);

        // Tap 1: down at 1000, up at 1050
        ctx.touchDownEvent(100, 100, 1000, true);
        ctx.touchUpEvent(100, 100, 1050);

        // First click down sent:
        assertThat(conn.events()).contains(new NvConnection.MouseButtonDownEvent(MouseButtonPacket.BUTTON_LEFT));

        // Tap 2 down at 1100 (within 230ms window):
        ctx.touchDownEvent(100, 100, 1100, true);
        // Tap 2 up at 1150:
        ctx.touchUpEvent(100, 100, 1150);

        // Double click completed: sends up, down, up
        List<NvConnection.Event> events = conn.events();
        long downCount = events.stream().filter(e -> e instanceof NvConnection.MouseButtonDownEvent).count();
        long upCount = events.stream().filter(e -> e instanceof NvConnection.MouseButtonUpEvent).count();

        assertThat(downCount).isEqualTo(2);
        assertThat(upCount).isEqualTo(2);
        assertThat(conn.heldButtons()).isEmpty();
    }
}
