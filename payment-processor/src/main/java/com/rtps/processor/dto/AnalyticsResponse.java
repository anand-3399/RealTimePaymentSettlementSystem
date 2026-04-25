package com.rtps.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsResponse {
    private String date;
    private Metrics metrics;
    private Map<String, Long> statusBreakdown;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Metrics {
        private Long totalTransactions;
        private BigDecimal totalAmount;
        private Long successCount;
        private Long failureCount;
        private Double successRate;
        private BigDecimal averageAmount;
    }
}
