package com.chanebplus.stockcare.modules.priority.web;

import com.chanebplus.stockcare.modules.priority.service.PriorityService;
import com.chanebplus.stockcare.modules.request.dto.PriorityResultDto;
import com.chanebplus.stockcare.modules.request.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Priority scores")
@RestController
@RequestMapping("/api/priorities")
public class PriorityController {

    private final PriorityService priorityService;
    private final RequestService requestService;

    public PriorityController(PriorityService priorityService, RequestService requestService) {
        this.priorityService = priorityService;
        this.requestService = requestService;
    }

    @Operation(summary = "Get the stored priority result for a request")
    @GetMapping("/requests/{requestId}")
    public PriorityResultDto forRequest(@PathVariable UUID requestId) {
        requestService.get(requestId); // enforces access
        return priorityService.getByRequest(requestId);
    }

    @Operation(summary = "(Re)calculate priority for a request (depot/admin)")
    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/requests/{requestId}/calculate")
    public PriorityResultDto calculate(@PathVariable UUID requestId) {
        return requestService.prioritize(requestId);
    }
}
