package com.rtps.ajbank.exception;

import com.rtps.ajbank.dto.TransferResponse;
import com.rtps.ajbank.entity.LockTimeout;
import com.rtps.ajbank.repository.LockTimeoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private LockTimeoutRepository lockTimeoutRepository;

    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<TransferResponse> handleLockException(Exception e) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = "UNKNOWN";
        }

        logger.error("Lock timeout occurred | correlationId: {} | error: {}", correlationId, e.getMessage());

        // Save monitoring alert
        LockTimeout alert = LockTimeout.builder()
                .correlationId(correlationId)
                .message("Pessimistic lock timeout occurred due to high account contention")
                .createdAt(LocalDateTime.now())
                .build();
        
        lockTimeoutRepository.save(alert);

        // Return HTTP 409 Conflict with PENDING_RETRY payload
        TransferResponse response = TransferResponse.builder()
                .status("PENDING_RETRY")
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<TransferResponse> handleBusinessValidationException(BusinessValidationException e) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) correlationId = "UNKNOWN";

        logger.warn("Business validation error | correlationId: {} | error: {}", correlationId, e.getMessage());

        TransferResponse response = TransferResponse.builder()
                .status("FAILED")
                .message(e.getMessage())
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TransferResponse> handleGenericException(Exception e) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) correlationId = "UNKNOWN";

        logger.error("Unexpected error in AJBank | correlationId: {} | error: {}", correlationId, e.getMessage(), e);
        
        TransferResponse response = TransferResponse.builder()
                .status("FAILED")
                .message("System cannot process")
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
