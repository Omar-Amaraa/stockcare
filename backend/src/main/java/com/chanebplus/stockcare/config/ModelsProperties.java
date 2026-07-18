package com.chanebplus.stockcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for pluggable model integrations. Only "mock" is implemented in the MVP;
 * "external" values are the prepared integration points for the LightGBM prediction model,
 * the priority-scoring model and the MILP route optimizer.
 */
@Component
@ConfigurationProperties(prefix = "stockcare.models")
public class ModelsProperties {

    private ModelConfig prediction = new ModelConfig();
    private ModelConfig priority = new ModelConfig();
    private ModelConfig routeOptimization = new ModelConfig();

    public ModelConfig getPrediction() { return prediction; }
    public void setPrediction(ModelConfig prediction) { this.prediction = prediction; }
    public ModelConfig getPriority() { return priority; }
    public void setPriority(ModelConfig priority) { this.priority = priority; }
    public ModelConfig getRouteOptimization() { return routeOptimization; }
    public void setRouteOptimization(ModelConfig routeOptimization) { this.routeOptimization = routeOptimization; }

    public static class ModelConfig {
        /** "mock" or "external". */
        private String mode = "mock";
        private String version = "mock-v1";
        private String url;
        private long timeoutMs = 5000;
        private int horizonDays = 14;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getHorizonDays() { return horizonDays; }
        public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    }
}
