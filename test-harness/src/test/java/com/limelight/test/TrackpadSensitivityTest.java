package com.limelight.test;

import com.limelight.test.fakes.FakeConnection;
import com.limelight.test.fakes.FakeScheduler;
import com.limelight.test.fakes.TrackpadLogicMirror;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for sensitivity scaling in TrackpadContext.
 *
 * Production formula (from TrackpadContext constructor):
 *   this.sensitivityX = (float) sensitivityX / 100;
 *   this.sensitivityY = (float) sensitivityY / 100;
 *
 * Applied in touchMoveEvent as a multiplier on raw pixel deltas.
 * Verified against production code at commit e1402952.
 */
@DisplayName("TrackpadContext — Sensitivity Scaling")
class TrackpadSensitivityTest {

    private TrackpadLogicMirror mirror(int sx, int sy) {
        return new TrackpadLogicMirror(
                new FakeConnection(), 0, new FakeScheduler(), false, sx, sy);
    }

    @Test
    @DisplayName("sensitivity 0 produces 0.0 scale factor")
    void sensitivity_zero_givesZeroScale() {
        TrackpadLogicMirror ctx = mirror(0, 0);
        assertThat(ctx.getSensitivityX()).isEqualTo(0.0f);
        assertThat(ctx.getSensitivityY()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("sensitivity 100 produces 1.0 scale factor")
    void sensitivity_100_givesUnitScale() {
        TrackpadLogicMirror ctx = mirror(100, 100);
        assertThat(ctx.getSensitivityX()).isEqualTo(1.0f);
        assertThat(ctx.getSensitivityY()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("sensitivity 200 produces 2.0 scale factor")
    void sensitivity_200_givesDoubleScale() {
        TrackpadLogicMirror ctx = mirror(200, 200);
        assertThat(ctx.getSensitivityX()).isEqualTo(2.0f);
        assertThat(ctx.getSensitivityY()).isEqualTo(2.0f);
    }

    @Test
    @DisplayName("sensitivity 50 produces 0.5 scale factor")
    void sensitivity_50_givesHalfScale() {
        TrackpadLogicMirror ctx = mirror(50, 50);
        assertThat(ctx.getSensitivityX()).isCloseTo(0.5f, within(1e-6f));
        assertThat(ctx.getSensitivityY()).isCloseTo(0.5f, within(1e-6f));
    }

    @ParameterizedTest(name = "sensitivity {0} → scale {1}")
    @CsvSource({
        "1,   0.01",
        "10,  0.10",
        "75,  0.75",
        "150, 1.50",
        "300, 3.00"
    })
    @DisplayName("sensitivity integer maps linearly to float scale (÷100)")
    void sensitivity_linearMapping(int rawSensitivity, float expectedScale) {
        TrackpadLogicMirror ctx = mirror(rawSensitivity, rawSensitivity);
        assertThat(ctx.getSensitivityX()).isCloseTo(expectedScale, within(1e-5f));
        assertThat(ctx.getSensitivityY()).isCloseTo(expectedScale, within(1e-5f));
    }

    @Test
    @DisplayName("applyScaling multiplies raw delta by sensitivityX on x-axis")
    void applyScaling_x_appliesSensitivityX() {
        TrackpadLogicMirror ctx = mirror(200, 100); // sX=2.0, sY=1.0
        double result = ctx.applyScaling(10.0, 'x');
        assertThat(result).isCloseTo(20.0, within(1e-9));
    }

    @Test
    @DisplayName("applyScaling multiplies raw delta by sensitivityY on y-axis")
    void applyScaling_y_appliesSensitivityY() {
        TrackpadLogicMirror ctx = mirror(100, 50); // sX=1.0, sY=0.5
        double result = ctx.applyScaling(10.0, 'y');
        assertThat(result).isCloseTo(5.0, within(1e-9));
    }

    @Test
    @DisplayName("applyScaling with swapAxis=true swaps X→Y and Y→X scaling")
    void applyScaling_withSwapAxis_swapsFactors() {
        // swapAxis=true, sX=2.0, sY=0.5
        TrackpadLogicMirror ctx = new TrackpadLogicMirror(
                new FakeConnection(), 0, new FakeScheduler(), true, 200, 50);

        // x-axis with swap → actually applied as Y scale (0.5)
        double scaledX = ctx.applyScaling(10.0, 'x');
        assertThat(scaledX).isCloseTo(5.0, within(1e-9));

        // y-axis with swap → actually applied as X scale (2.0)
        double scaledY = ctx.applyScaling(10.0, 'y');
        assertThat(scaledY).isCloseTo(20.0, within(1e-9));
    }

    @Test
    @DisplayName("zero raw delta produces zero scaled delta regardless of sensitivity")
    void zeroRawDelta_alwaysZero() {
        TrackpadLogicMirror ctx = mirror(999, 999);
        assertThat(ctx.applyScaling(0.0, 'x')).isEqualTo(0.0);
        assertThat(ctx.applyScaling(0.0, 'y')).isEqualTo(0.0);
    }
}
