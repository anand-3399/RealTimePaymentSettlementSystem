package com.payment.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPageResponse {
    private long total;
    private long success;
    private long failure;
    private long fetched;
    private List<OrderResponse> content;
}
