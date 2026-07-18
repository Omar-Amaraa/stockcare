package com.chanebplus.stockcare.time.dto;

/** Advance the simulated clock by a number of days and/or hours. Enables simulation if needed. */
public record AdvanceTimeRequest(long days, long hours) {}
