package com.chanebplus.stockcare.common.geo;

/** Great-circle distance helpers for the mock router and tracking simulator. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    /** Haversine distance in kilometres. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Linear interpolation between two coordinates (fraction 0..1). Adequate for a simulated path. */
    public static double[] interpolate(double lat1, double lon1, double lat2, double lon2, double f) {
        double t = Math.max(0, Math.min(1, f));
        return new double[]{lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t};
    }
}
