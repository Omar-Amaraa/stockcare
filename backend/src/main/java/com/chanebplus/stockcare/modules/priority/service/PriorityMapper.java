package com.chanebplus.stockcare.modules.priority.service;

import com.chanebplus.stockcare.modules.priority.domain.PriorityResult;
import com.chanebplus.stockcare.modules.request.dto.PriorityResultDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PriorityMapper {

    private final ObjectMapper objectMapper;

    public PriorityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PriorityResultDto toDto(PriorityResult r) {
        if (r == null) {
            return null;
        }
        Map<String, Double> factors = Map.of();
        if (r.getFactorsJson() != null) {
            try {
                factors = objectMapper.readValue(r.getFactorsJson(), new TypeReference<Map<String, Double>>() {});
            } catch (Exception ignored) {
                // factors are best-effort presentation data
            }
        }
        return new PriorityResultDto(r.getId(), r.getRequest().getId(), r.getCoefficient(), factors,
                r.getExplanation(), r.getCalculationVersion(), r.isSimulated(), r.getCalculatedAt());
    }

    public String toJson(Map<String, Double> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception e) {
            return "{}";
        }
    }
}
