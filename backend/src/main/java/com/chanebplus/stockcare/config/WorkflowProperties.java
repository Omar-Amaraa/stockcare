package com.chanebplus.stockcare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configurable behaviour of the automated workflows. */
@Component
@ConfigurationProperties(prefix = "stockcare.workflow")
public class WorkflowProperties {

    /** What to do when a shortage is predicted: "draft" (create a draft request), "alert" (notify only), or "off". */
    private String shortageAction = "draft";
    /** If true, auto-created draft requests are also submitted automatically. */
    private boolean autoSubmit = false;
    /** If true, priority is (re)calculated automatically through the request lifecycle. */
    private boolean autoPriority = true;
    /** Readiness flag for automatic route planning (kept manual/approved in the MVP). */
    private boolean autoRoute = false;

    public String getShortageAction() { return shortageAction; }
    public void setShortageAction(String shortageAction) { this.shortageAction = shortageAction; }
    public boolean isAutoSubmit() { return autoSubmit; }
    public void setAutoSubmit(boolean autoSubmit) { this.autoSubmit = autoSubmit; }
    public boolean isAutoPriority() { return autoPriority; }
    public void setAutoPriority(boolean autoPriority) { this.autoPriority = autoPriority; }
    public boolean isAutoRoute() { return autoRoute; }
    public void setAutoRoute(boolean autoRoute) { this.autoRoute = autoRoute; }
}
