package com.lankapay.city.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.CityOffer;
import com.lankapay.city.domain.MerchantStatus;
import com.lankapay.city.store.CityFavouriteStore;
import com.lankapay.city.store.MerchantStore;
import com.lankapay.city.web.dto.*;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.qr.domain.QrCodeRecord;
import com.lankapay.qr.domain.QrStatus;
import com.lankapay.qr.domain.QrStore;
import com.lankapay.qr.domain.QrType;
import com.lankapay.qr.service.QrService;
import com.lankapay.rewards.event.TransactionConfirmedEvent;
import com.lankapay.rewards.service.RewardsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);

    private final MerchantStore merchantStore;
    private final CityFavouriteStore favouriteStore;
    private final AuditService auditService;
    private final QrService qrService;
    private final QrStore qrStore;
    private final RewardsService rewardsService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CityService(
            MerchantStore merchantStore,
            CityFavouriteStore favouriteStore,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) QrService qrService,
            @Autowired(required = false) QrStore qrStore,
            @Autowired(required = false) RewardsService rewardsService,
            @Autowired(required = false) PasswordEncoder passwordEncoder
    ) {
        this.merchantStore = merchantStore;
        this.favouriteStore = favouriteStore;
        this.auditService = auditService;
        this.qrService = qrService;
        this.qrStore = qrStore;
        this.rewardsService = rewardsService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void initQrRecords() {
        if (qrStore == null) return;
        try {
            List<CityMerchant> merchants = merchantStore.findAllMerchants();
            for (CityMerchant m : merchants) {
                if (m.qrReference() != null && qrStore.findQrByReferenceOrQrId(m.qrReference()).isEmpty()) {
                    QrCodeRecord record = new QrCodeRecord(
                            null,
                            9999L,
                            m.id(),
                            m.qrReference(),
                            QrType.STATIC,
                            null,
                            "LKR",
                            m.name() + " - Simulated QR",
                            QrStatus.ACTIVE,
                            false,
                            "LANKAQR-SIMULATED-" + m.id(),
                            null,
                            Instant.now()
                    );
                    qrStore.saveQr(record);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not pre-initialize merchant QR records in CityService: {}", ex.getMessage());
        }
    }

    // ── Categories ──────────────────────────────────────────────────────────

    public List<CityCategoryDto> getCategories() {
        List<CityMerchant> all = merchantStore.findAllMerchants();
        List<CityCategoryDto> results = new ArrayList<>();
        for (CityCategory cat : CityCategory.values()) {
            int count = (int) all.stream()
                    .filter(m -> m.isActive() && m.category() == cat)
                    .count();
            results.add(new CityCategoryDto(
                    cat.name(),
                    cat.getDisplayName(),
                    cat.getIconName(),
                    count
            ));
        }
        return results;
    }

    // ── Merchants with Filters & Cursor Pagination ─────────────────────────

    public MerchantPageResponse getMerchants(
            Long userId,
            CityCategory category,
            String search,
            String area,
            String cursor,
            int limit
    ) {
        if (limit <= 0) limit = 10;
        if (limit > 50) limit = 50;

        List<CityMerchant> filtered = merchantStore.findAllMerchants().stream()
                .filter(CityMerchant::isActive)
                .filter(m -> category == null || m.category() == category)
                .filter(m -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.trim().toLowerCase();
                    return m.name().toLowerCase().contains(s)
                            || (m.description() != null && m.description().toLowerCase().contains(s))
                            || (m.area() != null && m.area().toLowerCase().contains(s));
                })
                .filter(m -> {
                    if (area == null || area.isBlank()) return true;
                    return m.area() != null && m.area().equalsIgnoreCase(area.trim());
                })
                .sorted(Comparator.comparing(CityMerchant::id))
                .toList();

        int totalCount = filtered.size();
        int offset = decodeCursor(cursor);
        if (offset < 0) offset = 0;
        if (offset > totalCount) offset = totalCount;

        int toIndex = Math.min(offset + limit, totalCount);
        List<CityMerchant> pageItems = filtered.subList(offset, toIndex);

        boolean hasMore = toIndex < totalCount;
        String nextCursor = hasMore ? encodeCursor(toIndex) : null;

        Set<String> userFavs = userId != null ? favouriteStore.getFavouriteMerchantIds(userId) : Set.of();

        List<CityMerchantDto> dtos = pageItems.stream()
                .map(m -> {
                    boolean isFav = userFavs.contains(m.id());
                    List<CityOfferDto> offers = merchantStore.findOffersByMerchantId(m.id()).stream()
                            .filter(CityOffer::active)
                            .map(CityOfferDto::from)
                            .toList();
                    return CityMerchantDto.from(m, isFav, offers);
                })
                .toList();

        return new MerchantPageResponse(dtos, nextCursor, hasMore, totalCount);
    }

    public CityMerchantDto getMerchantById(Long userId, String id) {
        CityMerchant merchant = merchantStore.findMerchantById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MERCHANT_NOT_FOUND", "Merchant not found: " + id));

        boolean isFav = userId != null && favouriteStore.isFavourite(userId, merchant.id());
        List<CityOfferDto> offers = merchantStore.findOffersByMerchantId(merchant.id()).stream()
                .filter(CityOffer::active)
                .map(CityOfferDto::from)
                .toList();

        return CityMerchantDto.from(merchant, isFav, offers);
    }

    public List<CityOfferDto> getActiveOffers() {
        return merchantStore.findAllOffers().stream()
                .filter(CityOffer::active)
                .map(CityOfferDto::from)
                .toList();
    }

    // ── Favourites ──────────────────────────────────────────────────────────

    public List<CityMerchantDto> getFavourites(Long userId) {
        if (userId == null) {
            throw new ValidationException("UNAUTHORIZED", "User must be authenticated to view favourites");
        }
        Set<String> favIds = favouriteStore.getFavouriteMerchantIds(userId);
        return favIds.stream()
                .map(merchantStore::findMerchantById)
                .flatMap(Optional::stream)
                .filter(CityMerchant::isActive)
                .map(m -> {
                    List<CityOfferDto> offers = merchantStore.findOffersByMerchantId(m.id()).stream()
                            .filter(CityOffer::active)
                            .map(CityOfferDto::from)
                            .toList();
                    return CityMerchantDto.from(m, true, offers);
                })
                .sorted(Comparator.comparing(CityMerchantDto::name))
                .toList();
    }

    public void addFavourite(Long userId, String merchantId) {
        if (userId == null) {
            throw new ValidationException("UNAUTHORIZED", "User must be authenticated to add favourite");
        }
        if (merchantId == null || merchantId.isBlank()) {
            throw new ValidationException("INVALID_MERCHANT", "Merchant ID cannot be blank");
        }
        merchantStore.findMerchantById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("MERCHANT_NOT_FOUND", "Merchant not found: " + merchantId));
        favouriteStore.addFavourite(userId, merchantId);
    }

    public void removeFavourite(Long userId, String merchantId) {
        if (userId == null) {
            throw new ValidationException("UNAUTHORIZED", "User must be authenticated to remove favourite");
        }
        favouriteStore.removeFavourite(userId, merchantId);
    }

    // ── Pay At Merchant (Reusing QR Flow + Rewards Event) ───────────────────

    public PayAtMerchantResponse payAtMerchant(
            User user,
            String merchantId,
            PayAtMerchantRequest request,
            String ipAddress
    ) {
        if (user == null || user.getId() == null) {
            throw new ValidationException("UNAUTHORIZED", "Authenticated user required to pay at merchant");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("INVALID_AMOUNT", "Payment amount must be greater than zero");
        }

        CityMerchant merchant = merchantStore.findMerchantById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("MERCHANT_NOT_FOUND", "Merchant not found: " + merchantId));

        if (!merchant.isActive()) {
            throw new ValidationException("MERCHANT_INACTIVE", "Cannot pay at inactive merchant");
        }

        // 1. Resolve QR Reference if QR Service is available
        String resolvedQr = merchant.qrReference();
        if (qrService != null && resolvedQr != null && !resolvedQr.isBlank()) {
            try {
                qrService.resolveQrReference(resolvedQr);
            } catch (Exception e) {
                log.info("QR verification note for {}: {}", resolvedQr, e.getMessage());
            }
        }

        // 2. Authorize via PIN if provided & user has passwordHash
        if (request.pin() != null && !request.pin().isBlank() && passwordEncoder != null && user.getPasswordHash() != null) {
            if (!passwordEncoder.matches(request.pin(), user.getPasswordHash())) {
                throw new ValidationException("INVALID_PIN", "Invalid authorization PIN");
            }
        }

        // 3. Complete transaction
        String transactionId = "TXN-CITY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        // 4. Trigger Rewards confirmed transaction event with merchant category
        Integer pointsAwarded = null;
        if (rewardsService != null) {
            try {
                TransactionConfirmedEvent confirmedEvent = new TransactionConfirmedEvent(
                        transactionId,
                        "QR_PAYMENT",
                        user.getId(),
                        9999L,
                        "7010",
                        "7010",
                        "ACC-" + user.getId(),
                        "ACC-MERCH-" + merchant.id(),
                        request.amount(),
                        merchant.category().name(),
                        false,
                        Instant.now(),
                        Map.of(
                                "merchantId", merchant.id(),
                                "merchantName", merchant.name(),
                                "category", merchant.category().name(),
                                "area", merchant.area() != null ? merchant.area() : "Colombo"
                        )
                );
                var entryOpt = rewardsService.handleTransactionConfirmed(confirmedEvent);
                if (entryOpt.isPresent()) {
                    pointsAwarded = entryOpt.get().points();
                }
            } catch (Exception e) {
                log.warn("Failed rewards trigger for merchant payment: {}", e.getMessage());
            }
        }

        // 5. Audit the payment
        if (auditService != null) {
            auditService.log(
                    user.getId(),
                    "PAY_AT_MERCHANT_COMPLETED",
                    "CITY_MERCHANT",
                    (long) Math.abs(merchant.id().hashCode()),
                    String.format("{\"merchantId\":\"%s\",\"merchantName\":\"%s\",\"amount\":\"%s\",\"category\":\"%s\",\"transactionId\":\"%s\"}",
                            merchant.id(), merchant.name(), request.amount(), merchant.category().name(), transactionId),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return new PayAtMerchantResponse(
                transactionId,
                merchant.id(),
                merchant.name(),
                merchant.category().name(),
                request.amount(),
                "LKR",
                merchant.qrReference(),
                "SUCCESS",
                Instant.now(),
                pointsAwarded,
                request.note() != null ? request.note() : "Payment at " + merchant.name()
        );
    }

    // ── Admin Management (Audited) ──────────────────────────────────────────

    public CityMerchant adminCreateMerchant(Long adminId, CreateMerchantRequest request, String ipAddress) {
        String id = request.id() != null && !request.id().isBlank() ? request.id() : "merch_" + UUID.randomUUID().toString().substring(0, 8);
        String qrRef = request.qrReference() != null && !request.qrReference().isBlank()
                ? request.qrReference()
                : "QR-CITY-" + id.toUpperCase();

        CityMerchant merchant = new CityMerchant(
                id,
                request.name(),
                request.category(),
                request.area(),
                request.description(),
                request.openingHours() != null ? request.openingHours() : "08:00 - 22:00",
                qrRef,
                request.status() != null ? request.status() : MerchantStatus.ACTIVE
        );

        CityMerchant saved = merchantStore.saveMerchant(merchant);

        if (auditService != null) {
            auditService.log(
                    adminId,
                    "ADMIN_CITY_MERCHANT_CREATED",
                    "CITY_MERCHANT",
                    (long) Math.abs(id.hashCode()),
                    String.format("{\"id\":\"%s\",\"name\":\"%s\",\"category\":\"%s\"}", id, merchant.name(), merchant.category().name()),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return saved;
    }

    public CityMerchant adminUpdateMerchant(Long adminId, String id, UpdateMerchantRequest req, String ipAddress) {
        CityMerchant existing = merchantStore.findMerchantById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MERCHANT_NOT_FOUND", "Merchant not found: " + id));

        CityMerchant updated = new CityMerchant(
                existing.id(),
                req.name() != null ? req.name() : existing.name(),
                req.category() != null ? req.category() : existing.category(),
                req.area() != null ? req.area() : existing.area(),
                req.description() != null ? req.description() : existing.description(),
                req.openingHours() != null ? req.openingHours() : existing.openingHours(),
                req.qrReference() != null ? req.qrReference() : existing.qrReference(),
                req.status() != null ? req.status() : existing.status()
        );

        CityMerchant saved = merchantStore.saveMerchant(updated);

        if (auditService != null) {
            auditService.log(
                    adminId,
                    "ADMIN_CITY_MERCHANT_UPDATED",
                    "CITY_MERCHANT",
                    (long) Math.abs(id.hashCode()),
                    String.format("{\"id\":\"%s\",\"name\":\"%s\"}", id, updated.name()),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return saved;
    }

    public boolean adminDeleteMerchant(Long adminId, String id, String ipAddress) {
        merchantStore.findMerchantById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MERCHANT_NOT_FOUND", "Merchant not found: " + id));

        boolean removed = merchantStore.deleteMerchant(id);

        if (auditService != null) {
            auditService.log(
                    adminId,
                    "ADMIN_CITY_MERCHANT_DELETED",
                    "CITY_MERCHANT",
                    (long) Math.abs(id.hashCode()),
                    String.format("{\"id\":\"%s\"}", id),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return removed;
    }

    public CityOffer adminCreateOffer(Long adminId, CreateOfferRequest request, String ipAddress) {
        String id = request.id() != null && !request.id().isBlank() ? request.id() : "offer_" + UUID.randomUUID().toString().substring(0, 8);
        CityOffer offer = new CityOffer(
                id,
                request.merchantId(),
                request.title(),
                request.description(),
                request.pointsMultiplier(),
                request.discountLabel(),
                request.active() != null ? request.active() : true,
                request.terms() != null ? request.terms() : "Simulated promo terms"
        );

        CityOffer saved = merchantStore.saveOffer(offer);

        if (auditService != null) {
            auditService.log(
                    adminId,
                    "ADMIN_CITY_OFFER_CREATED",
                    "CITY_OFFER",
                    (long) Math.abs(id.hashCode()),
                    String.format("{\"id\":\"%s\",\"merchantId\":\"%s\",\"title\":\"%s\"}", id, offer.merchantId(), offer.title()),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return saved;
    }

    public CityOffer adminUpdateOffer(Long adminId, String id, UpdateOfferRequest req, String ipAddress) {
        CityOffer existing = merchantStore.findOfferById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OFFER_NOT_FOUND", "Offer not found: " + id));

        CityOffer updated = new CityOffer(
                existing.id(),
                existing.merchantId(),
                req.title() != null ? req.title() : existing.title(),
                req.description() != null ? req.description() : existing.description(),
                req.pointsMultiplier() != null ? req.pointsMultiplier() : existing.pointsMultiplier(),
                req.discountLabel() != null ? req.discountLabel() : existing.discountLabel(),
                req.active() != null ? req.active() : existing.active(),
                req.terms() != null ? req.terms() : existing.terms()
        );

        CityOffer saved = merchantStore.saveOffer(updated);

        if (auditService != null) {
            auditService.log(
                    adminId,
                    "ADMIN_CITY_OFFER_UPDATED",
                    "CITY_OFFER",
                    (long) Math.abs(id.hashCode()),
                    String.format("{\"id\":\"%s\",\"title\":\"%s\"}", id, updated.title()),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return saved;
    }

    public boolean adminDeleteOffer(Long adminId, String id, String ipAddress) {
        merchantStore.findOfferById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OFFER_NOT_FOUND", "Offer not found: " + id));

        boolean removed = merchantStore.deleteOffer(id);

        if (auditService != null) {
            auditService.log(
                    adminId,
                    "ADMIN_CITY_OFFER_DELETED",
                    "CITY_OFFER",
                    (long) Math.abs(id.hashCode()),
                    String.format("{\"id\":\"%s\"}", id),
                    ipAddress != null ? ipAddress : "127.0.0.1"
            );
        }

        return removed;
    }

    // ── Helper: Cursor Base64 Encoding/Decoding ────────────────────────────

    private String encodeCursor(int offset) {
        String raw = "offset:" + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(bytes, StandardCharsets.UTF_8);
            if (raw.startsWith("offset:")) {
                return Integer.parseInt(raw.substring("offset:".length()));
            }
        } catch (Exception e) {
            log.warn("Invalid cursor string '{}': {}", cursor, e.getMessage());
        }
        return 0;
    }
}
