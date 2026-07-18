package com.chanebplus.stockcare.modules.prediction.domain;

public enum PredictionRunStatus {
    PENDING,     // queued, not started
    PROCESSING,  // model running
    COMPLETED,   // fresh results available
    FAILED       // last run failed; retry available
}
