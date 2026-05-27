package com.prod.secureestatebackend.service;

import com.prod.secureestatebackend.Entities.EscrowTransaction;
import com.prod.secureestatebackend.Entities.Property;
import com.prod.secureestatebackend.dto.PropertyAccessResponse;
import com.prod.secureestatebackend.exception.ResourceNotFoundException;
import com.prod.secureestatebackend.repository.EscrowTransactionRepository;
import com.prod.secureestatebackend.repository.PropertyRepository;
import com.prod.secureestatebackend.util.AccessFeeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyAccessService {

    private final PropertyRepository propertyRepository;
    private final EscrowTransactionRepository escrowRepo;

    // Check if user has already paid access fee for this property
    public boolean hasAccess(Long propertyId, String buyerEmail) {
        List<EscrowTransaction> transactions = escrowRepo.findByBuyerEmail(buyerEmail);
        return transactions.stream()
                .anyMatch(t ->
                        t.getPropertyId() != null &&
                                t.getPropertyId().equals(propertyId) &&
                                (t.getStatus() == EscrowTransaction.EscrowStatus.IN_ESCROW ||
                                        t.getStatus() == EscrowTransaction.EscrowStatus.RELEASED)
                );
    }

    // Get property info — limited if not paid, full if paid
    @Transactional(readOnly = true)
    public PropertyAccessResponse getPropertyAccess(Long propertyId, String buyerEmail, boolean isRental) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));

        BigDecimal accessFee = AccessFeeUtil.calculateFee(
                property.getType(), property.getPrice(), isRental);

        boolean unlocked = hasAccess(propertyId, buyerEmail);

        PropertyAccessResponse.PropertyAccessResponseBuilder builder = PropertyAccessResponse.builder()
                .propertyId(property.getId())
                .title(property.getTitle())
                .location(property.getLocation())
                .price(property.getPrice())
                .type(property.getType())
                .ardhisasaVerified(property.isArdhisasaVerified())
                .imageUrl(property.getImageUrl())
                .accessFee(accessFee)
                .accessUnlocked(unlocked);

        if (unlocked) {
            // Return full details including agent contacts
            String agentPhone = "+254 700 000 000"; // In production, get from User entity
            String agentEmail = property.getOwner().getEmail();
            String agentName = property.getOwner().getFullName();

            // Get M-Pesa receipt as proof of payment
            String receipt = escrowRepo.findByBuyerEmail(buyerEmail).stream()
                    .filter(t -> t.getPropertyId() != null && t.getPropertyId().equals(propertyId))
                    .findFirst()
                    .map(EscrowTransaction::getMpesaReceiptNumber)
                    .orElse(null);

            builder
                    .description(property.getDescription())
                    .agentFullName(agentName)
                    .agentEmail(agentEmail)
                    .agentPhone(agentPhone)
                    .agentWhatsApp("https://wa.me/" + agentPhone.replaceAll("[^0-9]", ""))
                    .ownerEmail(agentEmail)
                    .mpesaReceiptNumber(receipt);
        }

        return builder.build();
    }

    // Calculate access fee for a property
    @Transactional(readOnly = true)
    public BigDecimal getAccessFee(Long propertyId, boolean isRental) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        return AccessFeeUtil.calculateFee(property.getType(), property.getPrice(), isRental);
    }
}