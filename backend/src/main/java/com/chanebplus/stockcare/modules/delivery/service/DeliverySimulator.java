package com.chanebplus.stockcare.modules.delivery.service;

import com.chanebplus.stockcare.common.geo.GeoUtils;
import com.chanebplus.stockcare.modules.delivery.domain.*;
import com.chanebplus.stockcare.modules.delivery.dto.TrackingUpdate;
import com.chanebplus.stockcare.modules.delivery.repo.DeliveryEventRepository;
import com.chanebplus.stockcare.modules.delivery.repo.DeliveryRepository;
import com.chanebplus.stockcare.modules.delivery.repo.TrackingPositionRepository;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.repo.PharmacyRequestRepository;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated GPS tracking. On a fixed cadence it advances every active delivery along its polyline
 * (depot → stops), updates position/ETA, completes stops, emits events + tracking positions and
 * pushes SSE updates. Replaces a real GPS provider for the MVP.
 */
@Service
public class DeliverySimulator {

    private static final Logger log = LoggerFactory.getLogger(DeliverySimulator.class);
    private static final int TICKS_PER_SEGMENT = 4;   // ~4 ticks (8s) between stops
    private static final double APPROACH_FRACTION = 0.6;

    private final DeliveryRepository deliveryRepository;
    private final TrackingPositionRepository positionRepository;
    private final DeliveryEventRepository eventRepository;
    private final PharmacyRequestRepository requestRepository;
    private final NotificationService notifications;
    private final TrackingBroadcaster broadcaster;
    private final ApplicationClock clock;

    private final Set<UUID> paused = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<Integer>> approached = new ConcurrentHashMap<>();

    public DeliverySimulator(DeliveryRepository deliveryRepository, TrackingPositionRepository positionRepository,
                             DeliveryEventRepository eventRepository, PharmacyRequestRepository requestRepository,
                             NotificationService notifications, TrackingBroadcaster broadcaster,
                             ApplicationClock clock) {
        this.deliveryRepository = deliveryRepository;
        this.positionRepository = positionRepository;
        this.eventRepository = eventRepository;
        this.requestRepository = requestRepository;
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    public void pause(UUID deliveryId) { paused.add(deliveryId); }
    public void resume(UUID deliveryId) { paused.remove(deliveryId); }

    @Scheduled(fixedRateString = "${stockcare.tracking.tick-ms:2000}")
    @Transactional
    public void tick() {
        List<Delivery> active = deliveryRepository.findByStatusIn(
                List.of(DeliveryStatus.STARTED, DeliveryStatus.IN_TRANSIT, DeliveryStatus.ARRIVED_AT_STOP));
        for (Delivery d : active) {
            if (paused.contains(d.getId())) {
                continue;
            }
            try {
                advance(d);
            } catch (RuntimeException ex) {
                log.warn("Simulator error on delivery {}: {}", d.getId(), ex.getMessage());
            }
        }
    }

    private void advance(Delivery d) {
        List<RouteStop> stops = d.getStops();
        int n = stops.size();
        if (n == 0) { finalizeDelivered(d); return; }

        double[][] points = new double[n + 1][2];
        points[0] = new double[]{val(d.getDepot().getLatitude()), val(d.getDepot().getLongitude())};
        for (int i = 0; i < n; i++) {
            points[i + 1] = new double[]{stops.get(i).getLatitude(), stops.get(i).getLongitude()};
        }

        double step = 1.0 / (n * TICKS_PER_SEGMENT);
        double newProgress = Math.min(1.0, d.getProgress() + step);
        double x = newProgress * n;
        int seg = (int) Math.floor(x);
        double frac = x - seg;

        double[] pos = seg >= n ? points[n]
                : GeoUtils.interpolate(points[seg][0], points[seg][1], points[seg + 1][0], points[seg + 1][1], frac);

        d.setCurrentLatitude(pos[0]);
        d.setCurrentLongitude(pos[1]);
        d.setProgress(newProgress);
        d.setCurrentStopIndex(seg);
        if (d.getTotalDurationMinutes() != null) {
            d.setEtaMinutes(Math.round((1 - newProgress) * d.getTotalDurationMinutes() * 10.0) / 10.0);
        }
        if (d.getStatus() == DeliveryStatus.STARTED) {
            d.setStatus(DeliveryStatus.IN_TRANSIT);
        }

        // Complete any stops we have passed.
        for (int i = 0; i < Math.min(seg, n); i++) {
            RouteStop s = stops.get(i);
            if (s.getStatus() != RouteStopStatus.COMPLETED) {
                s.setStatus(RouteStopStatus.COMPLETED);
                s.setArrivedAt(clock.now());
                recordEvent(d, DeliveryEventType.DELIVERED, "Delivered to " + s.getPharmacy().getName());
                notifications.notifyPharmacy(s.getPharmacy().getId(), NotificationType.DELIVERY_COMPLETED,
                        "Delivery arrived", "Delivery " + d.getReference() + " reached your pharmacy.",
                        d.getId(), "DELIVERY");
            }
        }

        // Approaching notification for the next stop.
        if (seg < n && frac >= APPROACH_FRACTION) {
            Set<Integer> set = approached.computeIfAbsent(d.getId(), k -> ConcurrentHashMap.newKeySet());
            if (set.add(seg)) {
                RouteStop next = stops.get(seg);
                recordEvent(d, DeliveryEventType.APPROACHING, "Approaching " + next.getPharmacy().getName());
                notifications.notifyPharmacy(next.getPharmacy().getId(), NotificationType.DELIVERY_APPROACHING,
                        "Delivery approaching", "Delivery " + d.getReference() + " is approaching your pharmacy.",
                        d.getId(), "DELIVERY");
            }
        }

        savePosition(d);
        deliveryRepository.save(d);
        broadcast(d);

        if (newProgress >= 1.0) {
            finalizeDelivered(d);
        }
    }

    void forceComplete(Delivery d) {
        finalizeDelivered(d);
    }

    private void finalizeDelivered(Delivery d) {
        d.getStops().forEach(s -> {
            if (s.getStatus() != RouteStopStatus.COMPLETED) {
                s.setStatus(RouteStopStatus.COMPLETED);
                s.setArrivedAt(clock.now());
            }
        });
        d.setProgress(1.0);
        d.setCurrentStopIndex(d.getStops().size());
        d.setEtaMinutes(0.0);
        d.setStatus(DeliveryStatus.DELIVERED);
        d.setCompletedAt(clock.now());

        Set<UUID> requestIds = new HashSet<>();
        d.getItems().forEach(i -> { if (i.getRequestId() != null) requestIds.add(i.getRequestId()); });
        requestIds.forEach(rid -> requestRepository.findById(rid).ifPresent(r -> {
            r.setStatus(RequestStatus.DELIVERED);
            requestRepository.save(r);
        }));

        recordEvent(d, DeliveryEventType.COMPLETED, "Delivery completed");
        deliveryRepository.save(d);
        broadcast(d);
        notifications.notifyDepot(d.getDepot().getId(), NotificationType.DELIVERY_COMPLETED,
                "Delivery completed", "Delivery " + d.getReference() + " completed all stops.",
                d.getId(), "DELIVERY");
        approached.remove(d.getId());
        paused.remove(d.getId());
    }

    private void recordEvent(Delivery d, DeliveryEventType type, String message) {
        DeliveryEvent e = new DeliveryEvent();
        e.setDelivery(d);
        e.setType(type);
        e.setMessage(message);
        e.setOccurredAt(clock.now());
        e.setLatitude(d.getCurrentLatitude());
        e.setLongitude(d.getCurrentLongitude());
        eventRepository.save(e);
    }

    private void savePosition(Delivery d) {
        TrackingPosition p = new TrackingPosition();
        p.setDelivery(d);
        p.setLatitude(val(d.getCurrentLatitude()));
        p.setLongitude(val(d.getCurrentLongitude()));
        p.setStopIndex(d.getCurrentStopIndex());
        p.setEtaMinutes(d.getEtaMinutes());
        p.setRecordedAt(clock.now());
        positionRepository.save(p);
    }

    private void broadcast(Delivery d) {
        broadcaster.broadcast(d.getId(), new TrackingUpdate(d.getId(), d.getStatus(),
                d.getCurrentLatitude(), d.getCurrentLongitude(), d.getCurrentStopIndex(),
                d.getProgress(), d.getEtaMinutes(), Instant.now()));
    }

    private double val(Double v) { return v == null ? 0.0 : v; }
}
