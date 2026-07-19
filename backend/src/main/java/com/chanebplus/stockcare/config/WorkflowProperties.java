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
    /** If true, approving requests for planning automatically triggers a fleet-wide MILP solve. */
    private boolean autoRoute = true;
    /** Minimum number of approved, unplanned requests before automatic planning fires. */
    private int autoRouteMinRequests = 1;
    /**
     * If true, a new approval arriving while route proposals are still awaiting the depot's
     * decision folds those proposals into a fresh fleet-wide solve (they are cancelled only once
     * the new solve succeeds), so the proposed routes always reflect the complete current demand.
     * Dispatched deliveries (STARTED and beyond) are never touched.
     */
    private boolean autoRouteReplan = true;

    public String getShortageAction() { return shortageAction; }
    public void setShortageAction(String shortageAction) { this.shortageAction = shortageAction; }
    public boolean isAutoSubmit() { return autoSubmit; }
    public void setAutoSubmit(boolean autoSubmit) { this.autoSubmit = autoSubmit; }
    public boolean isAutoPriority() { return autoPriority; }
    public void setAutoPriority(boolean autoPriority) { this.autoPriority = autoPriority; }
    public boolean isAutoRoute() { return autoRoute; }
    public void setAutoRoute(boolean autoRoute) { this.autoRoute = autoRoute; }
    public int getAutoRouteMinRequests() { return autoRouteMinRequests; }
    public void setAutoRouteMinRequests(int autoRouteMinRequests) { this.autoRouteMinRequests = autoRouteMinRequests; }
    public boolean isAutoRouteReplan() { return autoRouteReplan; }
    public void setAutoRouteReplan(boolean autoRouteReplan) { this.autoRouteReplan = autoRouteReplan; }
}
