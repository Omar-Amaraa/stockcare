package com.chanebplus.stockcare.modules.delivery.service;

import com.chanebplus.stockcare.modules.delivery.domain.Delivery;
import com.chanebplus.stockcare.modules.delivery.domain.Driver;
import com.chanebplus.stockcare.modules.delivery.domain.Vehicle;
import com.chanebplus.stockcare.modules.delivery.dto.DeliveryDto;
import com.chanebplus.stockcare.modules.delivery.dto.DeliveryEventDto;
import com.chanebplus.stockcare.modules.delivery.dto.DeliveryItemDto;
import com.chanebplus.stockcare.modules.delivery.dto.DriverDto;
import com.chanebplus.stockcare.modules.delivery.dto.RouteStopDto;
import com.chanebplus.stockcare.modules.delivery.dto.TrackingPositionDto;
import com.chanebplus.stockcare.modules.delivery.dto.VehicleDto;
import com.chanebplus.stockcare.modules.delivery.domain.DeliveryEvent;
import com.chanebplus.stockcare.modules.delivery.domain.TrackingPosition;

public final class DeliveryMapper {

    private DeliveryMapper() {}

    public static VehicleDto toDto(Vehicle v) {
        return v == null ? null : new VehicleDto(v.getId(), v.getCode(), v.getPlateNumber(),
                v.getCapacityUnits(), v.isRefrigerated(), v.isActive());
    }

    public static DriverDto toDto(Driver d) {
        return d == null ? null : new DriverDto(d.getId(), d.getFullName(), d.getPhone(), d.isActive());
    }

    public static DeliveryDto toDto(Delivery d) {
        var stops = d.getStops().stream().map(s -> new RouteStopDto(
                s.getId(), s.getSequence(), s.getPharmacy().getId(), s.getPharmacy().getName(),
                s.getRequestId(), s.getLatitude(), s.getLongitude(), s.getStatus(),
                s.getEstimatedArrivalMinute(), s.getDistanceFromPrevKm(), s.getArrivedAt())).toList();
        var items = d.getItems().stream().map(i -> new DeliveryItemDto(
                i.getId(), i.getPharmacy().getId(), i.getRequestId(), i.getMedication().getId(),
                i.getMedication().getName(), i.getQuantity())).toList();
        return new DeliveryDto(
                d.getId(), d.getReference(), d.getStatus(), d.getDepot().getId(),
                d.getDepot().getLatitude(), d.getDepot().getLongitude(),
                toDto(d.getVehicle()), toDto(d.getDriver()), d.isSimulated(), false,
                d.getOptimizerVersion(), d.getTotalDistanceKm(), d.getTotalDurationMinutes(),
                d.getCurrentLatitude(), d.getCurrentLongitude(), d.getCurrentStopIndex(),
                d.getProgress(), d.getEtaMinutes(), d.getPlannedAt(), d.getStartedAt(),
                d.getCompletedAt(), stops, items);
    }

    public static DeliveryEventDto toDto(DeliveryEvent e) {
        return new DeliveryEventDto(e.getId(), e.getType(), e.getMessage(), e.getOccurredAt(),
                e.getLatitude(), e.getLongitude());
    }

    public static TrackingPositionDto toDto(TrackingPosition p) {
        return new TrackingPositionDto(p.getLatitude(), p.getLongitude(), p.getStopIndex(),
                p.getEtaMinutes(), p.getRecordedAt());
    }
}
