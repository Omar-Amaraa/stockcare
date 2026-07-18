package com.chanebplus.stockcare.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanebplus.stockcare.common.geo.GeoUtils;
import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void distanceTunisToSfaxIsAboutTwoHundredSeventyKm() {
        double km = GeoUtils.distanceKm(36.8065, 10.1815, 34.7406, 10.7603);
        assertThat(km).isBetween(220.0, 280.0);
    }

    @Test
    void interpolateMidpoint() {
        double[] mid = GeoUtils.interpolate(0, 0, 10, 20, 0.5);
        assertThat(mid[0]).isEqualTo(5.0);
        assertThat(mid[1]).isEqualTo(10.0);
    }
}
