package com.limelight.nvstream;

import com.limelight.nvstream.input.MouseButtonPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-JVM test fake for NvConnection used in unit testing touch contexts.
 * Records ordered events and tracks currently-held buttons.
 */
public class NvConnection {

    public interface Event {}

    public static final class MouseButtonDownEvent implements Event {
        private final byte button;
        public MouseButtonDownEvent(byte button) { this.button = button; }
        public byte button() { return button; }
        @Override
        public String toString() { return "MouseButtonDown(" + button + ")"; }
        @Override
        public boolean equals(Object o) {
            return o instanceof MouseButtonDownEvent && ((MouseButtonDownEvent) o).button == button;
        }
        @Override
        public int hashCode() { return Byte.hashCode(button); }
    }

    public static final class MouseButtonUpEvent implements Event {
        private final byte button;
        public MouseButtonUpEvent(byte button) { this.button = button; }
        public byte button() { return button; }
        @Override
        public String toString() { return "MouseButtonUp(" + button + ")"; }
        @Override
        public boolean equals(Object o) {
            return o instanceof MouseButtonUpEvent && ((MouseButtonUpEvent) o).button == button;
        }
        @Override
        public int hashCode() { return Byte.hashCode(button); }
    }

    public static final class MouseMoveEvent implements Event {
        private final short deltaX;
        private final short deltaY;
        public MouseMoveEvent(short deltaX, short deltaY) {
            this.deltaX = deltaX;
            this.deltaY = deltaY;
        }
        public short deltaX() { return deltaX; }
        public short deltaY() { return deltaY; }
        @Override
        public String toString() { return "MouseMove(" + deltaX + ", " + deltaY + ")"; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MouseMoveEvent)) return false;
            MouseMoveEvent m = (MouseMoveEvent) o;
            return m.deltaX == deltaX && m.deltaY == deltaY;
        }
        @Override
        public int hashCode() { return 31 * deltaX + deltaY; }
    }

    public static final class MousePositionEvent implements Event {
        private final short x;
        private final short y;
        private final short width;
        private final short height;
        public MousePositionEvent(short x, short y, short width, short height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        public short x() { return x; }
        public short y() { return y; }
        public short width() { return width; }
        public short height() { return height; }
        @Override
        public String toString() { return "MousePosition(" + x + ", " + y + ", " + width + ", " + height + ")"; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MousePositionEvent)) return false;
            MousePositionEvent m = (MousePositionEvent) o;
            return m.x == x && m.y == y && m.width == width && m.height == height;
        }
        @Override
        public int hashCode() { return 31 * (31 * (31 * x + y) + width) + height; }
    }

    public static final class MouseScrollEvent implements Event {
        private final short amount;
        private final boolean horizontal;
        public MouseScrollEvent(short amount, boolean horizontal) {
            this.amount = amount;
            this.horizontal = horizontal;
        }
        public short amount() { return amount; }
        public boolean horizontal() { return horizontal; }
        @Override
        public String toString() { return (horizontal ? "HScroll(" : "VScroll(") + amount + ")"; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MouseScrollEvent)) return false;
            MouseScrollEvent s = (MouseScrollEvent) o;
            return s.amount == amount && s.horizontal == horizontal;
        }
        @Override
        public int hashCode() { return 31 * amount + (horizontal ? 1 : 0); }
    }

    private final List<Event> events = new ArrayList<>();
    private final Set<Byte> heldButtons = new LinkedHashSet<>();

    public synchronized void sendMouseButtonDown(byte button) {
        events.add(new MouseButtonDownEvent(button));
        heldButtons.add(button);
    }

    public synchronized void sendMouseButtonUp(byte button) {
        events.add(new MouseButtonUpEvent(button));
        heldButtons.remove(button);
    }

    public synchronized void sendMouseMove(short deltaX, short deltaY) {
        events.add(new MouseMoveEvent(deltaX, deltaY));
    }

    public synchronized void sendMousePosition(short x, short y, short width, short height) {
        events.add(new MousePositionEvent(x, y, width, height));
    }

    public synchronized void sendMouseMoveAsMousePosition(short x, short y, short width, short height) {
        sendMousePosition(x, y, width, height);
    }

    public synchronized void sendMouseHighResScroll(short amount) {
        events.add(new MouseScrollEvent(amount, false));
    }

    public synchronized void sendMouseHighResHScroll(short amount) {
        events.add(new MouseScrollEvent(amount, true));
    }

    public synchronized List<Event> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized Set<Byte> heldButtons() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(heldButtons));
    }

    public synchronized boolean isButtonHeld(byte button) {
        return heldButtons.contains(button);
    }

    public synchronized void clearEvents() {
        events.clear();
    }

    public synchronized void reset() {
        events.clear();
        heldButtons.clear();
    }
}
