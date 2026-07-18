package com.chanebplus.stockcare.modules.request.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalNoteDto(@NotBlank String note) {}
