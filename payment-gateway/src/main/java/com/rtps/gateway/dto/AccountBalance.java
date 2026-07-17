package com.rtps.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalance {
    private String accountNumber;
    private String accountHolderName;
    private BigDecimal balance;
    private String currency;
    private String status;
}
