package com.chanebplus.stockcare.modules.delivery.dto;

import java.util.UUID;

public record DriverDto(UUID id, String fullName, String phone, boolean active) {}
