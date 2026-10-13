package com.lankapay.city;

import com.lankapay.audit.domain.AuditRecord;
import com.lankapay.audit.domain.AuditStore;
import com.lankapay.audit.service.AuditService;
import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.MerchantStatus;
import com.lankapay.city.service.CityService;
import com.lankapay.city.store.InMemoryCityFavouriteStore;
import com.lankapay.city.store.InMemoryMerchantStore;
import com.lankapay.city.web.dto.*;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.qr.service.QrService;
import com.lankapay.rewards.domain.PointsLedgerEntry;
import com.lankapay.rewards.event.TransactionConfirmedEvent;
import com.lankapay.rewards.service.RewardsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CityModuleTest {

    private InMemoryMerchantStore merchantStore;
    private InMemoryCityFavouriteStore favouriteStore;
    private CityService cityService;

    @Mock
    private AuditStore auditStore;

    private AuditService auditService;

    @Mock
    private QrService qrService;

    @Mock
    private RewardsService rewardsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private final List<AuditRecord> capturedAuditRecords = new ArrayList<>();

    @BeforeEach
    void setUp() {
        merchantStore = new InMemoryMerchantStore();
        favouriteStore = new InMemoryCityFavouriteStore();
        capturedAuditRecords.clear();

        lenient().doAnswer(invocation -> {
            AuditRecord record = invocation.getArgument(0);
            capturedAuditRecords.add(record);
            return record;
        }).when(auditStore).save(any(AuditRecord.class));

        auditService = new AuditService(auditStore);

        cityService = new CityService(
                merchantStore,
                favouriteStore,
                auditService,
                qrService,
                null,
                rewardsService,
                passwordEncoder
        );

        testUser = new User("+94771234567", "Saman Perera", "199012345678", "$2a$12$hashedPin");
        ReflectionTestUtils.setField(testUser, "id", 101L);
        ReflectionTestUtils.setField(testUser, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(testUser, "role", UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("Verify 20 simulated merchants seeded with no real brands")
    void testSeededMerchantsCount() {
        List<CityMerchant> all = merchantStore.findAllMerchants();
        assertThat(all).hasSize(20);
        // verify all are active and no real brands
        assertThat(all).allMatch(CityMerchant::isActive);
        assertThat(all).noneMatch(m -> m.name().contains("McDonald") || m.name().contains("Starbucks") || m.name().contains("KFC"));
    }

    @Test
    @DisplayName("Category listing returns all categories with merchant counts")
    void testGetCategories() {
        List<CityCategoryDto> categories = cityService.getCategories();
        assertThat(categories).isNotEmpty();
        assertThat(categories).anyMatch(c -> c.name().equals("FOOD") && c.merchantCount() > 0);
        assertThat(categories).anyMatch(c -> c.name().equals("FUEL") && c.merchantCount() > 0);
        assertThat(categories).anyMatch(c -> c.name().equals("GROCERIES") && c.merchantCount() > 0);
    }

    @Test
    @DisplayName("Filter merchants by category, search term, and area")
    void testMerchantFilters() {
        // 1. Filter by Category FOOD
        MerchantPageResponse foodPage = cityService.getMerchants(101L, CityCategory.FOOD, null, null, null, 10);
        assertThat(foodPage.merchants()).isNotEmpty();
        assertThat(foodPage.merchants()).allMatch(m -> m.category() == CityCategory.FOOD);

        // 2. Search query matches name or area (e.g. "Bistro" or "Colombo")
        MerchantPageResponse searchPage = cityService.getMerchants(101L, null, "Bistro", null, null, 10);
        assertThat(searchPage.merchants()).isNotEmpty();
        assertThat(searchPage.merchants().get(0).name()).contains("Bistro");

        // 3. Filter by area "Kandy City"
        MerchantPageResponse areaPage = cityService.getMerchants(101L, null, null, "Kandy City", null, 10);
        assertThat(areaPage.merchants()).isNotEmpty();
        assertThat(areaPage.merchants()).allMatch(m -> m.area().equalsIgnoreCase("Kandy City"));
    }

    @Test
    @DisplayName("Cursor pagination traverses pages properly")
    void testCursorPagination() {
        // Page 1: limit 5
        MerchantPageResponse page1 = cityService.getMerchants(101L, null, null, null, null, 5);
        assertThat(page1.merchants()).hasSize(5);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        // Page 2: with cursor from page 1
        MerchantPageResponse page2 = cityService.getMerchants(101L, null, null, null, page1.nextCursor(), 5);
        assertThat(page2.merchants()).hasSize(5);
        assertThat(page2.merchants().get(0).id()).isNotEqualTo(page1.merchants().get(0).id());

        // Traverse remaining pages to end
        String cursor = page2.nextCursor();
        int pagesCount = 2;
        while (cursor != null && pagesCount < 10) {
            MerchantPageResponse next = cityService.getMerchants(101L, null, null, null, cursor, 5);
            cursor = next.nextCursor();
            pagesCount++;
        }
        assertThat(pagesCount).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("Favourites authorization and management per user")
    void testFavouritesFlow() {
        // User must be authenticated
        assertThatThrownBy(() -> cityService.getFavourites(null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> cityService.addFavourite(null, "merch_1"))
                .isInstanceOf(ValidationException.class);

        // Initially no favourites
        List<CityMerchantDto> favs = cityService.getFavourites(101L);
        assertThat(favs).isEmpty();

        // Add favourite merch_1
        cityService.addFavourite(101L, "merch_1");
        favs = cityService.getFavourites(101L);
        assertThat(favs).hasSize(1);
        assertThat(favs.get(0).id()).isEqualTo("merch_1");
        assertThat(favs.get(0).isFavourite()).isTrue();

        // User 202 has separate favourites
        assertThat(cityService.getFavourites(202L)).isEmpty();

        // Remove favourite
        cityService.removeFavourite(101L, "merch_1");
        assertThat(cityService.getFavourites(101L)).isEmpty();
    }

    @Test
    @DisplayName("Admin CRUD operations for merchants and offers are audited")
    void testAdminAuditing() {
        // Admin creates merchant
        CreateMerchantRequest createMerch = new CreateMerchantRequest(
                "merch_test_99",
                "Green Valley Tea Lounge",
                CityCategory.FOOD,
                "Ella Town",
                "Panoramic mountain view tea house with fresh bakes",
                "08:00 - 20:00",
                "QR-CITY-MERCH-99",
                MerchantStatus.ACTIVE
        );
        CityMerchant createdMerch = cityService.adminCreateMerchant(5001L, createMerch, "192.168.1.10");
        assertThat(createdMerch.name()).isEqualTo("Green Valley Tea Lounge");

        // Verify audit log for merchant creation
        assertThat(capturedAuditRecords).anyMatch(r ->
                r.eventType().equals("ADMIN_CITY_MERCHANT_CREATED")
                        && r.actorId().equals(5001L)
                        && r.detailJson().contains("Green Valley Tea Lounge")
        );

        // Admin updates merchant
        UpdateMerchantRequest updateMerch = new UpdateMerchantRequest(
                "Green Valley Grand Lounge",
                null,
                null,
                null,
                null,
                null,
                null
        );
        cityService.adminUpdateMerchant(5001L, "merch_test_99", updateMerch, "192.168.1.10");
        assertThat(capturedAuditRecords).anyMatch(r ->
                r.eventType().equals("ADMIN_CITY_MERCHANT_UPDATED")
                        && r.detailJson().contains("Green Valley Grand Lounge")
        );

        // Admin creates offer
        CreateOfferRequest createOffer = new CreateOfferRequest(
                "offer_test_99",
                "merch_test_99",
                "Sunset Special 2x",
                "Double points during mountain sunset",
                2.0,
                "15% OFF Tea",
                true,
                "Valid between 16:30 and 18:30"
        );
        cityService.adminCreateOffer(5001L, createOffer, "192.168.1.10");
        assertThat(capturedAuditRecords).anyMatch(r ->
                r.eventType().equals("ADMIN_CITY_OFFER_CREATED")
                        && r.detailJson().contains("Sunset Special 2x")
        );

        // Admin deletes offer
        cityService.adminDeleteOffer(5001L, "offer_test_99", "192.168.1.10");
        assertThat(capturedAuditRecords).anyMatch(r ->
                r.eventType().equals("ADMIN_CITY_OFFER_DELETED")
        );
    }

    @Test
    @DisplayName("Pay-at-merchant ends in confirmed transaction with merchant category and triggers rewards")
    void testPayAtMerchantFlow() {
        when(passwordEncoder.matches("123456", "$2a$12$hashedPin")).thenReturn(true);
        when(rewardsService.handleTransactionConfirmed(any(TransactionConfirmedEvent.class)))
                .thenReturn(Optional.of(mock(PointsLedgerEntry.class)));

        PayAtMerchantRequest payReq = new PayAtMerchantRequest(
                new BigDecimal("2500.00"),
                "123456",
                "Dinner at Cinnamon Leaf",
                1L
        );

        PayAtMerchantResponse response = cityService.payAtMerchant(testUser, "merch_1", payReq, "10.0.0.5");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.transactionId()).startsWith("TXN-CITY-");
        assertThat(response.merchantId()).isEqualTo("merch_1");
        assertThat(response.merchantName()).isEqualTo("Cinnamon Leaf Bistro");
        assertThat(response.category()).isEqualTo("FOOD");
        assertThat(response.amount()).isEqualByComparingTo("2500.00");

        // Verify rewards service received confirmed transaction event with merchant category
        ArgumentCaptor<TransactionConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(TransactionConfirmedEvent.class);
        verify(rewardsService).handleTransactionConfirmed(eventCaptor.capture());

        TransactionConfirmedEvent event = eventCaptor.getValue();
        assertThat(event.transactionId()).isEqualTo(response.transactionId());
        assertThat(event.transactionType()).isEqualTo("QR_PAYMENT");
        assertThat(event.senderUserId()).isEqualTo(101L);
        assertThat(event.amount()).isEqualByComparingTo("2500.00");
        assertThat(event.merchantOrCategory()).isEqualTo("FOOD");

        // Verify audit log recorded payment
        assertThat(capturedAuditRecords).anyMatch(r ->
                r.eventType().equals("PAY_AT_MERCHANT_COMPLETED")
                        && r.actorId().equals(101L)
                        && r.detailJson().contains("Cinnamon Leaf Bistro")
                        && r.detailJson().contains("FOOD")
        );
    }
}
