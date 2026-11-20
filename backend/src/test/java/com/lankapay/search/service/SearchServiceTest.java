package com.lankapay.search.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.search.domain.SearchResultType;
import com.lankapay.search.web.dto.SearchResponse;
import com.lankapay.search.web.dto.SearchResultItem;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchService Scope, Security, Anti-Enumeration, and Audit Tests")
class SearchServiceTest {

    @Mock
    private TransactionStore transactionStore;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private AuditService auditService;

    private SearchService searchService;

    private static final Long USER_A = 101L;
    private static final Long USER_B = 202L;
    private static final String CLIENT_IP = "192.168.1.50";

    @BeforeEach
    void setUp() {
        searchService = new SearchService(transactionStore, featureFlagService, auditService);
    }

    @Test
    @DisplayName("Validation: Query shorter than 2 characters throws ValidationException")
    void queryTooShortThrowsException() {
        assertThatThrownBy(() -> searchService.search(USER_A, "a", null, CLIENT_IP))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Search query must be at least 2 characters");

        assertThatThrownBy(() -> searchService.search(USER_A, "   ", null, CLIENT_IP))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> searchService.search(USER_A, null, null, CLIENT_IP))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Scope: Public catalogs (features, billers, merchants) return expected results")
    void scopePublicCatalogs() {
        // Feature flags: bills enabled, city enabled
        when(featureFlagService.isEnabled("bills")).thenReturn(true);
        when(featureFlagService.isEnabled("city")).thenReturn(true);

        when(transactionStore.findTransfersByUserId(eq(USER_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(transactionStore.findTransfersBySenderId(eq(USER_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // 1. Search for public biller "ceb"
        SearchResponse cebResponse = searchService.search(USER_A, "ceb", Set.of(SearchResultType.BILLER), CLIENT_IP);
        assertThat(cebResponse.results()).hasSize(1);
        assertThat(cebResponse.results().get(0).title()).contains("CEB");
        assertThat(cebResponse.results().get(0).type()).isEqualTo(SearchResultType.BILLER);

        // 2. Search for public merchant "keells"
        SearchResponse merchantResponse = searchService.search(USER_A, "keells", Set.of(SearchResultType.MERCHANT), CLIENT_IP);
        assertThat(merchantResponse.results()).hasSize(1);
        assertThat(merchantResponse.results().get(0).title()).contains("Keells Super");
        assertThat(merchantResponse.results().get(0).type()).isEqualTo(SearchResultType.MERCHANT);

        // 3. Search for feature "bill"
        SearchResponse featureResponse = searchService.search(USER_A, "bill", Set.of(SearchResultType.FEATURE), CLIENT_IP);
        assertThat(featureResponse.results()).anyMatch(item -> item.title().contains("Bills"));
    }

    @Test
    @DisplayName("Scope: Feature flag disabled excludes corresponding feature from search")
    void scopeFeatureFlagDisabledHidesFeature() {
        when(featureFlagService.isEnabled("bills")).thenReturn(false);

        SearchResponse response = searchService.search(USER_A, "bills", Set.of(SearchResultType.FEATURE), CLIENT_IP);
        assertThat(response.results()).noneMatch(item -> item.id().equals("feat-bills"));
    }

    @Test
    @DisplayName("Scope & Object-Level Access Control: User only searches their OWN transactions and recipients")
    void objectLevelAccessControlOwnTransactionsAndRecipients() {
        TransactionRecord userATxn = new TransactionRecord(
                1L, "TXN-USERA-001", "IDEM-1", USER_A, "0771111111", "User A",
                999L, "0779999999", "Dilshan Silva", "BOC", "COM",
                new BigDecimal("5000.00"), "LKR", TransactionStatus.COMPLETED,
                null, "BTX-1", null, "Dinner share", false, null, null, 1L,
                Instant.now(), Instant.now()
        );

        when(transactionStore.findTransfersByUserId(eq(USER_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(userATxn)));
        when(transactionStore.findTransfersBySenderId(eq(USER_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(userATxn)));

        // Search by reference
        SearchResponse refSearch = searchService.search(USER_A, "USERA", Set.of(SearchResultType.TRANSACTION), CLIENT_IP);
        assertThat(refSearch.results()).hasSize(1);
        assertThat(refSearch.results().get(0).title()).isEqualTo("Dinner share");
        assertThat(refSearch.results().get(0).subtitle()).contains("TXN-USERA-001");

        // Search by recipient name
        SearchResponse recipSearch = searchService.search(USER_A, "Dilshan", Set.of(SearchResultType.RECIPIENT), CLIENT_IP);
        assertThat(recipSearch.results()).hasSize(1);
        assertThat(recipSearch.results().get(0).title()).isEqualTo("Dilshan Silva");
        assertThat(recipSearch.results().get(0).type()).isEqualTo(SearchResultType.RECIPIENT);

        // Verification: transactionStore was only queried for USER_A, never USER_B
        verify(transactionStore).findTransfersByUserId(eq(USER_A), any(Pageable.class));
        verify(transactionStore, never()).findTransfersByUserId(eq(USER_B), any(Pageable.class));
    }

    @Test
    @DisplayName("Object-Level Access: Transactions not belonging to user are filtered out even if returned by store")
    void objectLevelAccessFiltersUnrelatedTransactions() {
        TransactionRecord foreignTxn = new TransactionRecord(
                2L, "TXN-FOREIGN-999", "IDEM-2", USER_B, "0772222222", "User B",
                888L, "0778888888", "Foreign Recipient", "BOC", "COM",
                new BigDecimal("10000.00"), "LKR", TransactionStatus.COMPLETED,
                null, "BTX-2", null, "Secret Payment", false, null, null, 1L,
                Instant.now(), Instant.now()
        );

        // Store erroneously returns foreign transaction
        when(transactionStore.findTransfersByUserId(eq(USER_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(foreignTxn)));

        SearchResponse response = searchService.search(USER_A, "Secret", Set.of(SearchResultType.TRANSACTION), CLIENT_IP);
        assertThat(response.results()).isEmpty(); // Strict enforcement drops foreign records
    }

    @Test
    @DisplayName("Anti-Enumeration: Phone number query returns ONLY a single Action and NEVER account holders")
    void antiEnumerationPhoneNumberQueryReturnsActionOnly() {
        String phoneQuery = "0771234567";

        SearchResponse response = searchService.search(USER_A, phoneQuery, null, CLIENT_IP);

        assertThat(response.results()).hasSize(1);
        SearchResultItem item = response.results().get(0);
        assertThat(item.type()).isEqualTo(SearchResultType.ACTION);
        assertThat(item.title()).isEqualTo("Send to this number");
        assertThat(item.subtitle()).contains("0771234567");
        assertThat(item.deepLink()).contains("app://send?recipient=0771234567");

        // Verify transaction store was NOT queried for phone lookup
        verify(transactionStore, never()).findTransfersByUserId(any(), any());
    }

    @Test
    @DisplayName("Anti-Enumeration: International phone format also returns Action without leaking users")
    void antiEnumerationInternationalFormat() {
        String phoneQuery = "+94712345678";

        SearchResponse response = searchService.search(USER_A, phoneQuery, null, CLIENT_IP);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).type()).isEqualTo(SearchResultType.ACTION);
        assertThat(response.results().get(0).subtitle()).isEqualTo("+94712345678");
    }

    @Test
    @DisplayName("Audit: Suspicious query patterns (e.g. SQL injection) trigger SEARCH_ABUSE_SUSPECTED audit")
    void auditAbusivePatterns() {
        String maliciousQuery = "' OR 1=1 --";

        searchService.search(USER_A, maliciousQuery, null, CLIENT_IP);

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(
                eq(USER_A),
                eventCaptor.capture(),
                eq("SEARCH"),
                isNull(),
                contains("SUSPICIOUS_PAYLOAD"),
                eq(CLIENT_IP)
        );
        assertThat(eventCaptor.getValue()).isEqualTo("SEARCH_ABUSE_SUSPECTED");
    }

    @Test
    @DisplayName("Audit: Normal query logs SEARCH_QUERY audit record")
    void auditNormalQuery() {
        searchService.search(USER_A, "water", null, CLIENT_IP);

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(
                eq(USER_A),
                eventCaptor.capture(),
                eq("SEARCH"),
                isNull(),
                anyString(),
                eq(CLIENT_IP)
        );
        assertThat(eventCaptor.getValue()).isEqualTo("SEARCH_QUERY");
    }
}
