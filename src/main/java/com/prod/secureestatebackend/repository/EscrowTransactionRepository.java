package com.prod.secureestatebackend.repository;

import com.prod.secureestatebackend.Entities.EscrowTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, Long> {
    Optional<EscrowTransaction> findByCheckoutRequestId(String checkoutRequestId);
    Optional<EscrowTransaction> findByMpesaReceiptNumber(String receiptNumber);
    List<EscrowTransaction> findByBuyerEmail(String email);
    List<EscrowTransaction> findByPropertyId(Long propertyId);
    List<EscrowTransaction> findByStatus(EscrowTransaction.EscrowStatus status);
}