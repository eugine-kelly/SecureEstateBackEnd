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

    // Check if user has paid access fee for this property
    public boolean hasAccess(Long propertyId, String buyerEmail) {
        if (buyerEmail == null || buyerEmail.isBlank()) {
            log.warn("hasAccess called with empty email for property {}", propertyId);
            return false;
        }

        List<EscrowTransaction> transactions = escrowRepo.findByBuyerEmail(buyerEmail);

        log.info("Checking access for email: {} property: {} — found {} transactions",
                buyerEmail, propertyId, transactions.size());

        return transactions.stream().anyMatch(t ->
                t.getPropertyId() != null &&
                        t.getPropertyId().equals(propertyId) &&
                        (t.getStatus() == EscrowTransaction.EscrowStatus.IN_ESCROW ||
                                t.getStatus() == EscrowTransaction.EscrowStatus.RELEASED)
        );
    }

    @Transactional(readOnly = true)
    public PropertyAccessResponse getPropertyAccess(
            Long propertyId, String buyerEmail, boolean isRental) {

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found: " + propertyId));

        BigDecimal accessFee = AccessFeeUtil.calculateFee(
                property.getType(), property.getPrice(), isRental);

        boolean unlocked = hasAccess(propertyId, buyerEmail);

        log.info("Property {} access for {}: {}", propertyId, buyerEmail, unlocked);

        PropertyAccessResponse.PropertyAccessResponseBuilder builder =
                PropertyAccessResponse.builder()
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
            String agentEmail = property.getOwner().getEmail();
            String agentName = property.getOwner().getFullName() != null
                    && !property.getOwner().getFullName().isBlank()
                    ? property.getOwner().getFullName()
                    : "James Kamau — SecureEstate Agent";

            String agentPhone = property.getOwner().getPhone() != null
                    && !property.getOwner().getPhone().isBlank()
                    ? property.getOwner().getPhone()
                    : "+254 712 345 678";

            // Clean phone for WhatsApp
            String waPhone = agentPhone.replaceAll("[^0-9]", "");
            if (waPhone.startsWith("0")) waPhone = "254" + waPhone.substring(1);
            if (!waPhone.startsWith("254")) waPhone = "254" + waPhone;

            // Get M-Pesa receipt — find most recent paid transaction for this property
            String receipt = escrowRepo.findByBuyerEmail(buyerEmail).stream()
                    .filter(t -> t.getPropertyId() != null
                            && t.getPropertyId().equals(propertyId)
                            && (t.getStatus() == EscrowTransaction.EscrowStatus.IN_ESCROW
                            || t.getStatus() == EscrowTransaction.EscrowStatus.RELEASED))
                    .findFirst()
                    .map(EscrowTransaction::getMpesaReceiptNumber)
                    .orElse("SANDBOX-TEST");

            String description = property.getDescription() != null
                    && !property.getDescription().isBlank()
                    ? property.getDescription()
                    : "This is a premium " + property.getType() + " located in " +
                    property.getLocation() + ". Contact the agent for viewing arrangements and more details.";

            log.info("Returning agent details — name: {}, phone: {}, email: {}, receipt: {}",
                    agentName, agentPhone, agentEmail, receipt);

            builder
                    .description(description)
                    .agentFullName(agentName)
                    .agentEmail(agentEmail)
                    .agentPhone(agentPhone)
                    .agentWhatsApp("https://wa.me/" + waPhone)
                    .ownerEmail(agentEmail)
                    .mpesaReceiptNumber(receipt);
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public BigDecimal getAccessFee(Long propertyId, boolean isRental) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found: " + propertyId));
        return AccessFeeUtil.calculateFee(
                property.getType(), property.getPrice(), isRental);
    }
}