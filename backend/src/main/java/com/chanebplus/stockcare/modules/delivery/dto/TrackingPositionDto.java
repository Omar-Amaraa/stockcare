package com.chanebplus.stockcare.modules.delivery.dto;

import java.time.Instant;

public record TrackingPositionDto(double latitude, double longitude, Integer stopIndex,
                                  Double etaMinutes, Instant recordedAt) {}
