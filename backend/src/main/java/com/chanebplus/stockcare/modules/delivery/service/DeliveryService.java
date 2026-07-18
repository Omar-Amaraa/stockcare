package com.chanebplus.stockcare.modules.delivery.service;

import com.chanebplus.stockcare.common.error.BusinessRuleException;
import com.chanebplus.stockcare.common.error.ForbiddenAccessException;
import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.integration.model.ModelExecutionRecorder;
import com.chanebplus.stockcare.integration.model.ModelExecutionStatus;
import com.chanebplus.stockcare.integration.model.ModelType;
import com.chanebplus.stockcare.modules.delivery.domain.*;
import com.chanebplus.stockcare.modules.delivery.dto.*;
import com.chanebplus.stockcare.modules.delivery.repo.*;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import com.chanebplus.stockcare.modules.priority.repo.PriorityResultRepository;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequestItem;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.repo.PharmacyRequestRepository;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationInput;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationResult;
import com.chanebplus.stockcare.modules.route.spi.RouteOptimizationService;
import com.chanebplus.stockcare.modules.user.domain.Role;
import com.chanebplus.stockcare.security.SecurityUtils;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final DeliveryEventRepository eventRepository;
    private final TrackingPositionRepository positionRepository;
    private final PharmacyRequestRepository requestRepository;
    private final PriorityResultRepository priorityRepository;
    private final RouteOptimizationService optimizer;
    private final ModelsProperties models;
    private final ModelExecutionRecorder recorder;
    private final NotificationService notifications;
    private final ApplicationClock clock;
    private final DeliverySimulator simulator;

    public DeliveryService(DeliveryRepository deliveryRepository, VehicleRepository vehicleRepository,
                           DriverRepository driverRepository, DeliveryEventRepository eventRepository,
                           TrackingPositionRepository positionRepository,
                           PharmacyRequestRepository requestRepository,
                           PriorityResultRepository priorityRepository,
                           RouteOptimizationService optimizer, ModelsProperties models,
                           ModelExecutionRecorder recorder, NotificationService notifications,
                           ApplicationClock clock, DeliverySimulator simulator) {
        this.deliveryRepository = deliveryRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.eventRepository = eventRepository;
        this.positionRepository = positionRepository;
        this.requestRepository = requestRepository;
        this.priorityRepository = priorityRepository;
        this.optimizer = optimizer;
        this.models = models;
        this.recorder = recorder;
        this.notifications = notifications;
        this.clock = clock;
        this.simulator = simulator;
    }

    // -------- Vehicles & drivers --------

    @Transactional(readOnly = true)
    public List<VehicleDto> vehicles() {
        return vehicleRepository.findByDepotId(currentDepotId()).stream().map(DeliveryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DriverDto> drivers() {
        return driverRepository.findByDepotId(currentDepotId()).stream().map(DeliveryMapper::toDto).toList();
    }

    // -------- Plan creation --------

    @Transactional
    public DeliveryDto createPlan(DeliveryPlanRequest req) {
        UUID depotId = currentDepotId();
        Vehicle vehicle = vehicleRepository.findById(req.vehicleId())
                .orElseThrow(() -> NotFoundException.of("Vehicle", req.vehicleId()));
        if (!vehicle.getDepot().getId().equals(depotId)) {
            throw new ForbiddenAccessException("Vehicle belongs to another depot");
        }
        Driver driver = req.driverId() == null ? null : driverRepository.findById(req.driverId())
                .orElseThrow(() -> NotFoundException.of("Driver", req.driverId()));

        List<PharmacyRequest> requests = new ArrayList<>();
        for (UUID id : req.requestIds()) {
            PharmacyRequest r = requestRepository.findWithItemsById(id)
                    .orElseThrow(() -> NotFoundException.of("PharmacyRequest", id));
            if (!r.getDepot().getId().equals(depotId)) {
                throw new ForbiddenAccessException("Request " + id + " belongs to another depot");
            }
            if (r.getStatus() != RequestStatus.PLANNED) {
                throw new BusinessRuleException("Request " + id + " must be approved (PLANNED) before delivery planning");
            }
            requests.add(r);
        }

        Depot depot = requests.get(0).getDepot();

        // Group by pharmacy → one stop each
        Map<UUID, List<PharmacyRequest>> byPharmacy = new LinkedHashMap<>();
        for (PharmacyRequest r : requests) {
            byPharmacy.computeIfAbsent(r.getPharmacy().getId(), k -> new ArrayList<>()).add(r);
        }
        List<RouteOptimizationInput.Stop> stops = new ArrayList<>();
        Map<UUID, com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy> pharmacyById = new HashMap<>();
        for (var entry : byPharmacy.entrySet()) {
            var pharmacy = entry.getValue().get(0).getPharmacy();
            pharmacyById.put(pharmacy.getId(), pharmacy);
            int units = 0; boolean cold = false; double priority = 0;
            for (PharmacyRequest r : entry.getValue()) {
                for (PharmacyRequestItem it : r.getItems()) {
                    units += it.getRequestedQuantity();
                    cold = cold || it.getMedication().isColdChain();
                }
                var pr = priorityRepository.findByRequestId(r.getId()).orElse(null);
                if (pr != null) priority = Math.max(priority, pr.getCoefficient());
            }
            stops.add(new RouteOptimizationInput.Stop(entry.getValue().get(0).getId(), pharmacy.getId(),
                    coord(pharmacy.getLatitude()), coord(pharmacy.getLongitude()), priority, units, cold, null, null));
        }

        var input = new RouteOptimizationInput(depot.getId(), coord(depot.getLatitude()), coord(depot.getLongitude()),
                stops, List.of(new RouteOptimizationInput.Vehicle(vehicle.getId(), vehicle.getCapacityUnits(),
                vehicle.isRefrigerated())), null);

        long start = System.currentTimeMillis();
        RouteOptimizationResult result = optimizer.optimize(input);
        recorder.record(ModelType.ROUTE_OPTIMIZATION, optimizer.mode(), models.getRouteOptimization().getVersion(),
                ModelExecutionStatus.SUCCESS, System.currentTimeMillis() - start,
                "stops=" + stops.size() + ", vehicle=" + vehicle.getCode(),
                "status=" + result.status() + ", routes=" + result.routes().size(), null);

        if (result.routes().isEmpty()) {
            throw new BusinessRuleException("No route could be built (vehicle capacity or cold-chain constraints)");
        }
        RouteOptimizationResult.VehicleRoute route = result.routes().get(0);

        Delivery delivery = new Delivery();
        delivery.setDepot(depot);
        delivery.setVehicle(vehicle);
        delivery.setDriver(driver);
        delivery.setStatus(DeliveryStatus.PLANNED);
        delivery.setReference("DLV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        delivery.setSimulated(true);
        delivery.setOptimizerVersion(models.getRouteOptimization().getVersion());
        delivery.setTotalDistanceKm(route.totalDistanceKm());
        delivery.setTotalDurationMinutes(route.totalDurationMinutes());
        delivery.setPlannedAt(clock.now());
        delivery.setCurrentLatitude(coord(depot.getLatitude()));
        delivery.setCurrentLongitude(coord(depot.getLongitude()));
        delivery.setCurrentStopIndex(0);
        delivery.setProgress(0);
        delivery.setEtaMinutes(route.totalDurationMinutes());

        for (RouteOptimizationResult.OrderedStop os : route.stops()) {
            var pharmacy = pharmacyById.get(os.pharmacyId());
            RouteStop stop = new RouteStop();
            stop.setSequence(os.sequence());
            stop.setPharmacy(pharmacy);
            stop.setRequestId(os.requestId());
            stop.setLatitude(coord(pharmacy.getLatitude()));
            stop.setLongitude(coord(pharmacy.getLongitude()));
            stop.setStatus(RouteStopStatus.PENDING);
            stop.setEstimatedArrivalMinute(os.estimatedArrivalMinute());
            delivery.addStop(stop);
        }
        for (PharmacyRequest r : requests) {
            for (PharmacyRequestItem it : r.getItems()) {
                DeliveryItem di = new DeliveryItem();
                di.setPharmacy(r.getPharmacy());
                di.setRequestId(r.getId());
                di.setMedication(it.getMedication());
                di.setQuantity(it.getRequestedQuantity());
                delivery.addItem(di);
            }
        }
        Delivery saved = deliveryRepository.save(delivery);
        event(saved, DeliveryEventType.PLANNED, "Delivery planned (mock, non-optimized route)");

        notifications.notifyDepot(depotId, NotificationType.ROUTE_PLANNED, "Route planned",
                "Delivery " + saved.getReference() + " planned with " + route.stops().size() + " stop(s).",
                saved.getId(), "DELIVERY");
        byPharmacy.keySet().forEach(pid -> notifications.notifyPharmacy(pid, NotificationType.ROUTE_PLANNED,
                "Delivery planned", "A delivery to your pharmacy has been planned (" + saved.getReference() + ").",
                saved.getId(), "DELIVERY"));

        return DeliveryMapper.toDto(saved);
    }

    // -------- Lifecycle controls --------

    @Transactional
    public DeliveryDto start(UUID id) {
        Delivery d = loadForDepot(id);
        if (d.getStatus() != DeliveryStatus.PLANNED && d.getStatus() != DeliveryStatus.PREPARING
                && d.getStatus() != DeliveryStatus.READY) {
            throw new BusinessRuleException("Delivery cannot be started from status " + d.getStatus());
        }
        d.setStatus(DeliveryStatus.STARTED);
        d.setStartedAt(clock.now());
        updateRequestStatuses(d, RequestStatus.IN_DELIVERY);
        deliveryRepository.save(d);
        event(d, DeliveryEventType.STARTED, "Vehicle dispatched from depot");
        Set<UUID> pharmacies = pharmacyIds(d);
        pharmacies.forEach(pid -> notifications.notifyPharmacy(pid, NotificationType.DELIVERY_STARTED,
                "Delivery started", "Your delivery " + d.getReference() + " is on the way.", d.getId(), "DELIVERY"));
        simulator.resume(id);
        return DeliveryMapper.toDto(d);
    }

    @Transactional
    public DeliveryDto pause(UUID id) {
        Delivery d = loadForDepot(id);
        simulator.pause(id);
        event(d, DeliveryEventType.PAUSED, "Delivery paused");
        return DeliveryMapper.toDto(d);
    }

    @Transactional
    public DeliveryDto resume(UUID id) {
        Delivery d = loadForDepot(id);
        simulator.resume(id);
        event(d, DeliveryEventType.RESUMED, "Delivery resumed");
        return DeliveryMapper.toDto(d);
    }

    @Transactional
    public DeliveryDto complete(UUID id) {
        Delivery d = loadForDepot(id);
        simulator.forceComplete(d);
        return DeliveryMapper.toDto(deliveryRepository.findWithStopsById(id).orElse(d));
    }

    @Transactional
    public DeliveryDto cancel(UUID id) {
        Delivery d = loadForDepot(id);
        d.setStatus(DeliveryStatus.CANCELLED);
        deliveryRepository.save(d);
        event(d, DeliveryEventType.CANCELLED, "Delivery cancelled");
        return DeliveryMapper.toDto(d);
    }

    // -------- Queries --------

    @Transactional(readOnly = true)
    public List<DeliveryDto> listForDepot() {
        return deliveryRepository.findByDepotIdOrderByCreatedAtDesc(currentDepotId())
                .stream().map(DeliveryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryDto> listForPharmacy(UUID pharmacyId) {
        assertPharmacyView(pharmacyId);
        return deliveryRepository.findByPharmacyStop(pharmacyId).stream().map(DeliveryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DeliveryDto get(UUID id) {
        return DeliveryMapper.toDto(loadVisible(id));
    }

    @Transactional(readOnly = true)
    public List<DeliveryEventDto> events(UUID id) {
        loadVisible(id);
        return eventRepository.findByDeliveryIdOrderByOccurredAtAsc(id).stream().map(DeliveryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TrackingPositionDto> positions(UUID id) {
        loadVisible(id);
        return positionRepository.findByDeliveryIdOrderByRecordedAtAsc(id).stream().map(DeliveryMapper::toDto).toList();
    }

    // -------- Helpers --------

    private void updateRequestStatuses(Delivery d, RequestStatus status) {
        Set<UUID> requestIds = new HashSet<>();
        d.getItems().forEach(i -> { if (i.getRequestId() != null) requestIds.add(i.getRequestId()); });
        for (UUID rid : requestIds) {
            requestRepository.findById(rid).ifPresent(r -> { r.setStatus(status); requestRepository.save(r); });
        }
    }

    private Set<UUID> pharmacyIds(Delivery d) {
        Set<UUID> ids = new LinkedHashSet<>();
        d.getStops().forEach(s -> ids.add(s.getPharmacy().getId()));
        return ids;
    }

    private void event(Delivery d, DeliveryEventType type, String message) {
        DeliveryEvent e = new DeliveryEvent();
        e.setDelivery(d);
        e.setType(type);
        e.setMessage(message);
        e.setOccurredAt(clock.now());
        e.setLatitude(d.getCurrentLatitude());
        e.setLongitude(d.getCurrentLongitude());
        eventRepository.save(e);
    }

    private double coord(Double v) {
        return v == null ? 0.0 : v;
    }

    private UUID currentDepotId() {
        if (SecurityUtils.currentRole() == Role.DEPOT) {
            return SecurityUtils.currentDepotId();
        }
        throw new ForbiddenAccessException("Depot role required");
    }

    private Delivery loadForDepot(UUID id) {
        Delivery d = deliveryRepository.findWithStopsById(id)
                .orElseThrow(() -> NotFoundException.of("Delivery", id));
        if (!SecurityUtils.isAdmin() && (SecurityUtils.currentRole() != Role.DEPOT
                || !d.getDepot().getId().equals(SecurityUtils.currentDepotId()))) {
            throw new ForbiddenAccessException("Delivery belongs to another depot");
        }
        return d;
    }

    private Delivery loadVisible(UUID id) {
        Delivery d = deliveryRepository.findWithStopsById(id)
                .orElseThrow(() -> NotFoundException.of("Delivery", id));
        Role role = SecurityUtils.currentRole();
        switch (role) {
            case ADMIN -> { }
            case DEPOT -> {
                if (!d.getDepot().getId().equals(SecurityUtils.currentDepotId())) {
                    throw new ForbiddenAccessException("Delivery belongs to another depot");
                }
            }
            case PHARMACY -> {
                if (!deliveryRepository.existsStopForPharmacy(id, SecurityUtils.currentPharmacyId())) {
                    throw new ForbiddenAccessException("Delivery does not concern your pharmacy");
                }
            }
        }
        return d;
    }

    private void assertPharmacyView(UUID pharmacyId) {
        Role role = SecurityUtils.currentRole();
        if (role == Role.PHARMACY && !SecurityUtils.currentPharmacyId().equals(pharmacyId)) {
            throw new ForbiddenAccessException("Not your pharmacy");
        }
    }
}
