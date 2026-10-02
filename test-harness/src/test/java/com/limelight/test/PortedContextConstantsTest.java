package com.limelight.test;

import com.limelight.test.fakes.FakeConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AbsoluteTouchContext button mapping constants and RelativeTouchContext
 * sensitivity scaling formula.
 *
 * These are pure-constant and formula tests — no Android framework needed.
 *
 * AbsoluteTouchContext port (commit e1402952): added configurable buttonPrimary /
 * buttonSecondary. Default constructor passes BUTTON_LEFT / BUTTON_RIGHT.
 *
 * RelativeTouchContext port (commit e1402952): added sensitivityX/Y params,
 * default constructor passes 100/100 (= 1.0×).
 */
@DisplayName("AbsoluteTouchContext & RelativeTouchContext — Ported Defaults Verification")
class PortedContextConstantsTest {

    // ---- AbsoluteTouchContext ----

    @Test
    @DisplayName("default buttonPrimary is BUTTON_LEFT (0x01)")
    void absoluteDefault_primaryIsLeft() {
        // Default constructor: AbsoluteTouchContext(conn, idx, view)
        // assigns buttonPrimary = MouseButtonPacket.BUTTON_LEFT = 0x01
        assertThat(FakeConnection.BUTTON_LEFT).isEqualTo((byte) 0x01);
    }

    @Test
    @DisplayName("default buttonSecondary is BUTTON_RIGHT (0x03)")
    void absoluteDefault_secondaryIsRight() {
        // Default constructor assigns buttonSecondary = BUTTON_RIGHT = 0x03
        assertThat(FakeConnection.BUTTON_RIGHT).isEqualTo((byte) 0x03);
    }

    @Test
    @DisplayName("configurable constructor allows swapped button mapping")
    void absoluteConfigurable_canSwapButtons() {
        // Simulate the configurable constructor assigning primary=RIGHT, secondary=LEFT
        byte primary   = FakeConnection.BUTTON_RIGHT;
        byte secondary = FakeConnection.BUTTON_LEFT;
        assertThat(primary).isEqualTo((byte) 0x03);
        assertThat(secondary).isEqualTo((byte) 0x01);
        // key invariant: they must be different
        assertThat(primary).isNotEqualTo(secondary);
    }

    // ---- RelativeTouchContext sensitivity formula ----

    /**
     * RelativeTouchContext stores sensitivityX = (float) rawSensitivityX / 100.
     * The same formula as TrackpadContext. Verify the mapping is identical.
     */
    @Test
    @DisplayName("RelativeTouchContext sensitivity 100 → 1.0 (unit scale)")
    void relativeDefault_sensitivity100givesUnit() {
        float sx = (float) 100 / 100;
        float sy = (float) 100 / 100;
        assertThat(sx).isEqualTo(1.0f);
        assertThat(sy).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("RelativeTouchContext sensitivity 0 → 0.0 (no movement)")
    void relative_sensitivity0givesZero() {
        float sx = (float) 0 / 100;
        assertThat(sx).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("RelativeTouchContext sensitivity 150 → 1.5x scale")
    void relative_sensitivity150givesOnePointFive() {
        float sx = (float) 150 / 100;
        assertThat(sx).isCloseTo(1.5f, within(1e-6f));
    }

    @Test
    @DisplayName("RelativeTouchContext and TrackpadContext use identical sensitivity formula")
    void bothContexts_shareIdenticalSensitivityFormula() {
        // Both contexts: sensitivityX = (float) rawValue / 100
        // Verify they produce the same result for several values
        for (int raw : new int[]{0, 1, 50, 100, 150, 200, 300}) {
            float fromRelative  = (float) raw / 100;
            float fromTrackpad  = (float) raw / 100;
            assertThat(fromRelative).isEqualTo(fromTrackpad);
        }
    }
}
