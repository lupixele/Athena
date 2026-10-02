package com.limelight.test.fakes;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM fake for NvConnection. Records every mouse call made by touch context logic.
 * No Android, no JNI, no network. Used ONLY in test-harness.
 */
public class FakeConnection {

    public static final byte BUTTON_LEFT   = 0x01;
    public static final byte BUTTON_MIDDLE = 0x02;
    public static final byte BUTTON_RIGHT  = 0x03;

    public record MouseMove(short deltaX, short deltaY) {}
    public record MouseButton(byte event, byte button) {}
    public record MouseScroll(short amount) {}

    public static final byte PRESS_EVENT   = 0x07;
    public static final byte RELEASE_EVENT = 0x08;

    private final List<MouseMove>   moves   = new ArrayList<>();
    private final List<MouseButton> buttons = new ArrayList<>();
    private final List<MouseScroll> scrolls = new ArrayList<>();

    public void sendMouseMove(short dx, short dy) {
        moves.add(new MouseMove(dx, dy));
    }

    public void sendMouseButtonDown(byte button) {
        buttons.add(new MouseButton(PRESS_EVENT, button));
    }

    public void sendMouseButtonUp(byte button) {
        buttons.add(new MouseButton(RELEASE_EVENT, button));
    }

    public void sendMouseHighResScroll(short amount) {
        scrolls.add(new MouseScroll(amount));
    }

    // ---- Assertions helpers ----

    public List<MouseMove>   moves()   { return List.copyOf(moves); }
    public List<MouseButton> buttons() { return List.copyOf(buttons); }
    public List<MouseScroll> scrolls() { return List.copyOf(scrolls); }

    public void reset() {
        moves.clear();
        buttons.clear();
        scrolls.clear();
    }

    public long pressCount(byte button) {
        return buttons.stream()
                .filter(b -> b.event() == PRESS_EVENT && b.button() == button)
                .count();
    }

    public long releaseCount(byte button) {
        return buttons.stream()
                .filter(b -> b.event() == RELEASE_EVENT && b.button() == button)
                .count();
    }

    public short totalDeltaX() {
        return (short) moves.stream().mapToInt(MouseMove::deltaX).sum();
    }

    public short totalDeltaY() {
        return (short) moves.stream().mapToInt(MouseMove::deltaY).sum();
    }
}
