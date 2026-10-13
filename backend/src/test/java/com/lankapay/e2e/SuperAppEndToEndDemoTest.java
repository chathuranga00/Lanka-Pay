package com.lankapay.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.analytics.domain.TransactionCategory;
import com.lankapay.analytics.service.*;
import com.lankapay.analytics.store.InMemoryBudgetStore;
import com.lankapay.analytics.store.InMemoryStatementStore;
import com.lankapay.analytics.store.InMemoryUserTransactionCategoryStore;
import com.lankapay.analytics.web.dto.*;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.bill.adapter.BillerAdapterRegistry;
import com.lankapay.bill.adapter.SimulatedBillerAdapter;
import com.lankapay.bill.crypto.BillerReferenceEncryptor;
import com.lankapay.bill.service.BillService;
import com.lankapay.bill.service.SavedBillerService;
import com.lankapay.bill.store.InMemoryBillPaymentStore;
import com.lankapay.bill.store.InMemorySavedBillerStore;
import com.lankapay.bill.web.dto.BillPaymentReceiptResponse;
import com.lankapay.bill.web.dto.PayBillRequest;
import com.lankapay.card.domain.*;
import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.service.CityService;
import com.lankapay.city.store.InMemoryCityFavouriteStore;
import com.lankapay.city.store.InMemoryMerchantStore;
import com.lankapay.city.web.dto.MerchantPageResponse;
import com.lankapay.city.web.dto.PayAtMerchantRequest;
import com.lankapay.city.web.dto.PayAtMerchantResponse;
import com.lankapay.feature.config.FeatureProperties;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.home.domain.HomeSectionStatus;
import com.lankapay.home.service.HomeAggregatorService;
import com.lankapay.home.web.dto.HomeAggregateResponse;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.qr.domain.QrStore;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.service.QrService;
import com.lankapay.rewards.domain.PointsEntryType;
import com.lankapay.rewards.domain.PointsLedgerEntry;
import com.lankapay.rewards.event.TransactionConfirmedEvent;
import com.lankapay.rewards.service.RewardsService;
import com.lankapay.rewards.store.InMemoryPointsLedgerStore;
import com.lankapay.rewards.store.InMemoryRewardOfferStore;
import com.lankapay.rewards.web.dto.RedeemPointsRequest;
import com.lankapay.rewards.web.dto.RedeemPointsResponse;
import com.lankapay.rewards.web.dto.RewardsSummaryResponse;
import com.lankapay.scheduled.domain.ScheduleFrequency;
import com.lankapay.scheduled.domain.ScheduleType;
import com.lankapay.scheduled.domain.ScheduledPayee;
import com.lankapay.scheduled.domain.ScheduledPayment;
import com.lankapay.scheduled.service.ScheduledPaymentService;
import com.lankapay.scheduled.store.InMemoryScheduledPaymentStore;
import com.lankapay.scheduled.store.InMemoryScheduledRunStore;
import com.lankapay.scheduled.web.dto.CreateScheduleRequest;
import com.lankapay.split.service.SplitService;
import com.lankapay.split.store.InMemorySplitStore;
import com.lankapay.split.web.dto.CreateSplitRequest;
import com.lankapay.split.web.dto.PaySplitRequest;
import com.lankapay.split.web.dto.SplitParticipantRequest;
import com.lankapay.split.web.dto.SplitResponse;
import com.lankapay.story.domain.*;
import com.lankapay.story.service.StoryService;
import com.lankapay.story.service.StoryValidator;
import com.lankapay.story.store.InMemoryStoryStore;
import com.lankapay.story.web.dto.StoryResponse;
import com.lankapay.support.domain.MessageSender;
import com.lankapay.support.domain.SupportCategory;
import com.lankapay.support.service.FaqBotEngine;
import com.lankapay.support.service.SupportRateLimiter;
import com.lankapay.support.service.SupportService;
import com.lankapay.support.store.InMemorySupportStore;
import com.lankapay.support.web.dto.CreateConversationRequest;
import com.lankapay.support.web.dto.SupportConversationDto;
import com.lankapay.support.web.dto.SupportMessageDto;
import com.lankapay.transfer.domain.FundingSource;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.service.CardDailyLimitService;
import com.lankapay.transfer.service.DailyLimitService;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Super App End-to-End Demo Test covering the full user journey:
 * 1. Register & verify user profile
 * 2. Link bank account and debit/credit card
 * 3. See Home screen with nudge banner & real name
 * 4. Open and view a promotional story
 * 5. Pay a utility bill from a card
 * 6. Schedule recurring bill payment monthly
 * 7. Split a dinner bill with 2 friends
 * 8. Review analytics & monthly budget
 * 9. Earn and redeem rewards points
 * 10. Pay at a merchant via LankaQR / City flow
 * 11. Chat with customer support FAQ bot
 * 12. Run an activity statement export
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SuperAppEndToEndDemoTest {

    // Common dependencies
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private FeatureFlagService featureFlagService;

    // Mocks for external/support services
    @Mock private UserStore userStore;
    @Mock private LinkedAccountStore linkedAccountStore;
    @Mock private CardStore cardStore;
    @Mock private TransactionStore transactionStore;
    @Mock private NotificationStore notificationStore;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private CardDailyLimitService cardDailyLimitService;
    @Mock private TransferService transferService;
    @Mock private PaymentRequestService paymentRequestService;
    @Mock private QrStore qrStore;
    @Mock private QrService qrService;

    // Concrete In-Memory Stores
    private InMemoryStoryStore storyStore;
    private InMemoryBillPaymentStore billPaymentStore;
    private InMemorySavedBillerStore savedBillerStore;
    private InMemoryScheduledPaymentStore scheduledPaymentStore;
    private InMemoryScheduledRunStore scheduledRunStore;
    private InMemorySplitStore splitStore;
    private InMemoryPointsLedgerStore pointsLedgerStore;
    private InMemoryRewardOfferStore rewardOfferStore;
    private InMemoryMerchantStore merchantStore;
    private InMemoryCityFavouriteStore cityFavouriteStore;
    private InMemorySupportStore supportStore;
    private InMemoryBudgetStore budgetStore;
    private InMemoryUserTransactionCategoryStore categoryStore;
    private InMemoryStatementStore statementStore;

    // Domain Services
    private DailyLimitService dailyLimitService;
    private HomeAggregatorService homeAggregatorService;
    private StoryService storyService;
    private BillService billService;
    private ScheduledPaymentService scheduledPaymentService;
    private SplitService splitService;
    private RewardsService rewardsService;
    private CityService cityService;
    private SupportService supportService;
    private AnalyticsService analyticsService;

    // Test Persona: Kasun Perera
    private static final Long KASUN_ID = 1001L;
    private static final String KASUN_PHONE = "+94771234567";
    private static final String PIN_PLAIN = "123456";
    private UserAccount kasunAccount;
    private User kasunUser;
    private LinkedAccount kasunLinkedAccount;
    private Card kasunCard;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Enable all super app feature flags for the demo
        FeatureProperties featureProperties = new FeatureProperties();
        featureFlagService = new FeatureFlagService(featureProperties, auditService);
        for (String flag : List.of("stories", "bills", "scheduled", "split", "analytics", "rewards", "city", "support", "insights", "goals", "family")) {
            featureFlagService.setFeature(flag, true, 1L, "127.0.0.1");
        }

        // Initialize concrete stores
        storyStore = new InMemoryStoryStore();
        billPaymentStore = new InMemoryBillPaymentStore();
        savedBillerStore = new InMemorySavedBillerStore();
        scheduledPaymentStore = new InMemoryScheduledPaymentStore();
        scheduledRunStore = new InMemoryScheduledRunStore();
        splitStore = new InMemorySplitStore();
        pointsLedgerStore = new InMemoryPointsLedgerStore();
        rewardOfferStore = new InMemoryRewardOfferStore();
        merchantStore = new InMemoryMerchantStore();
        cityFavouriteStore = new InMemoryCityFavouriteStore();
        supportStore = new InMemorySupportStore();
        budgetStore = new InMemoryBudgetStore();
        categoryStore = new InMemoryUserTransactionCategoryStore();
        statementStore = new InMemoryStatementStore();

        dailyLimitService = new DailyLimitService(transactionStore);

        // 1. Kasun User Setup
        String hashedPin = passwordEncoder.encode(PIN_PLAIN);
        kasunAccount = new UserAccount(
                KASUN_ID, KASUN_PHONE, "Kasun Perera", "199012345678", hashedPin,
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        );
        kasunUser = kasunAccount.toUser();

        when(userStore.findUserById(KASUN_ID)).thenReturn(Optional.of(kasunAccount));
        when(userStore.findUserByPhone(KASUN_PHONE)).thenReturn(Optional.of(kasunAccount));

        // 2. Linked Account Setup
        kasunLinkedAccount = new LinkedAccount(
                201L, KASUN_ID, "BANK_B", "h_bank_b", "•••• 5678", "Kasun Perera",
                "Primary Checking", true, true, "enc_ref_b", BankAccountStatus.ACTIVE,
                true, Instant.now(), Instant.now()
        );
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(KASUN_ID))
                .thenReturn(List.of(kasunLinkedAccount));
        when(linkedAccountStore.findAccountById(201L)).thenReturn(Optional.of(kasunLinkedAccount));
        when(linkedAccountStore.findAccountByIdAndUserId(eq(201L), any())).thenReturn(Optional.of(kasunLinkedAccount));

        // 3. Card Setup
        kasunCard = new Card(
                301L, KASUN_ID, 201L, "vlt_card_token_01", "0010", 12, 2028,
                "KASUN PERERA", "Platinum Debit", CardKind.VIRTUAL, CardStatus.ACTIVE,
                true, 50000000L, 200000000L, Instant.now(), Instant.now(), Instant.now(),
                null, 1L, null, true
        );
        when(cardStore.findAllByUserId(KASUN_ID)).thenReturn(List.of(kasunCard));
        when(cardStore.findById(301L)).thenReturn(Optional.of(kasunCard));
        when(cardStore.findByIdAndUserId(eq(301L), any())).thenReturn(Optional.of(kasunCard));

        // 4. Stories Service
        StoryValidator storyValidator = new StoryValidator();
        storyService = new StoryService(storyStore, storyValidator, auditService, userStore, linkedAccountStore, cardStore);

        // Pre-seed an active story
        Story demoStory = new Story(
                "story-01", "Welcome to LankaPay Super App", "Discover all services in one place",
                "gradient:blue",
                List.of(new StoryPage("Pay Bills & Scan QR effortlessly", "asset:qr.png", "Explore LankaQR", "lankapay:///qr")),
                StoryAudience.ALL, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(86400),
                1, StoryStatus.PUBLISHED, 0L, Instant.now(), Instant.now()
        );
        storyStore.save(demoStory);

        // 5. Bill Service
        SimulatedBillerAdapter simulatedBillerAdapter = new SimulatedBillerAdapter();
        BillerAdapterRegistry billerRegistry = new BillerAdapterRegistry(List.of(simulatedBillerAdapter));
        BillerReferenceEncryptor encryptor = new BillerReferenceEncryptor("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        SavedBillerService savedBillerService = new SavedBillerService(savedBillerStore, billerRegistry, encryptor, auditService);
        billService = new BillService(
                billerRegistry, billPaymentStore, savedBillerService, encryptor,
                transactionStore, dailyLimitService, cardDailyLimitService,
                linkedAccountStore, cardStore, userStore, passwordEncoder,
                notificationService, auditService, featureProperties, objectMapper
        );

        // 6. Scheduled Payments Service
        scheduledPaymentService = new ScheduledPaymentService(
                scheduledPaymentStore, scheduledRunStore, userStore, linkedAccountStore,
                cardStore, dailyLimitService, cardDailyLimitService, null,
                transferService, billService, billerRegistry, passwordEncoder,
                notificationService, auditService
        );

        // 7. Split Bills Service
        splitService = new SplitService(
                splitStore, userStore, paymentRequestService, transferService,
                notificationService, qrStore
        );

        // 8. Rewards Service
        rewardsService = new RewardsService(
                pointsLedgerStore, rewardOfferStore, userStore, passwordEncoder,
                auditService, notificationService, null
        );

        // 9. City Service
        cityService = new CityService(
                merchantStore, cityFavouriteStore, auditService, qrService,
                qrStore, rewardsService, passwordEncoder
        );

        // 10. Support Service
        FaqBotEngine faqBotEngine = new FaqBotEngine();
        SupportRateLimiter supportRateLimiter = new SupportRateLimiter(10);
        supportService = new SupportService(
                supportStore, faqBotEngine, supportRateLimiter, auditService,
                notificationStore, transactionStore
        );

        // 11. Analytics Service
        CategoryClassificationService classificationService = new CategoryClassificationService(categoryStore);
        StatementPdfGenerator pdfGenerator = new StatementPdfGenerator();
        StatementRateLimiter statementRateLimiter = new StatementRateLimiter(5);
        analyticsService = new AnalyticsService(
                transactionStore, billPaymentStore, classificationService,
                categoryStore, budgetStore, statementStore, pdfGenerator,
                statementRateLimiter, auditService, notificationService, userStore
        );

        // 12. Home Aggregator Service
        homeAggregatorService = new HomeAggregatorService(
                userStore, notificationStore, linkedAccountStore, cardStore,
                transactionStore, featureFlagService
        );

        // Mock empty initial transfers
        when(transactionStore.findTransfersByUserId(eq(KASUN_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        when(transferService.initiateTransfer(any(InitiateTransferRequest.class), any(User.class), any(), any(), any()))
                .thenReturn(new TransferResponse(
                        888L, UUID.randomUUID().toString(), TransactionStatus.COMPLETED,
                        new BigDecimal("2000.00"), "LKR", 1002L, "+94779876543",
                        KASUN_ID, "Kasun Perera", "BANK-TX-001", "PROV-001",
                        "Split dinner", null, true, Instant.now(), Instant.now()
                ));
    }

    @Test
    @DisplayName("Complete Super App Demo Journey Test (12 Steps)")
    void testSuperAppEndToEndDemoScenario() {
        // =========================================================================
        // Step 1: User Profile & Registration
        // =========================================================================
        assertThat(kasunAccount.fullName()).isEqualTo("Kasun Perera");
        assertThat(kasunAccount.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches(PIN_PLAIN, kasunAccount.passwordHash())).isTrue();

        // =========================================================================
        // Step 2: Link Bank Account & Card
        // =========================================================================
        assertThat(kasunLinkedAccount.maskedNumber()).isEqualTo("•••• 5678");
        assertThat(kasunCard.last4()).isEqualTo("0010");
        assertThat(kasunCard.status()).isEqualTo(CardStatus.ACTIVE);

        // =========================================================================
        // Step 3: See Home with Real Name and Nudge Banner
        // =========================================================================
        HomeAggregateResponse home = homeAggregatorService.aggregateHome(KASUN_ID);
        assertThat(home.greeting().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(home.greeting().data().fullName()).isEqualTo("Kasun Perera");
        assertThat(home.greeting().data().initial()).isEqualTo("K");
        assertThat(home.wallet().status()).isEqualTo(HomeSectionStatus.OK);
        assertThat(home.wallet().data().activeCard().maskedNumber()).contains("0010");
        assertThat(home.nudge().status()).isEqualTo(HomeSectionStatus.OK);

        // =========================================================================
        // Step 4: Open and View a Promotional Story
        // =========================================================================
        List<StoryResponse> activeStories = storyService.getActiveStoriesForUser(KASUN_ID);
        assertThat(activeStories).isNotEmpty();
        StoryResponse firstStory = activeStories.get(0);
        assertThat(firstStory.seen()).isFalse();

        storyService.markStorySeen(KASUN_ID, firstStory.id());
        List<StoryResponse> refreshedStories = storyService.getActiveStoriesForUser(KASUN_ID);
        assertThat(refreshedStories.get(0).seen()).isTrue();

        // =========================================================================
        // Step 5: Pay a Utility Bill from Card
        // =========================================================================
        PayBillRequest billReq = new PayBillRequest(
                "SIM_ELEC_01", "1002345678", new BigDecimal("3450.00"),
                "LKR", FundingSource.card(301L), PIN_PLAIN, "Electricity Monthly Bill", false, null
        );
        BillPaymentReceiptResponse billReceipt = billService.payBill(
                kasunUser, billReq, "DEMO-IDEM-BILL-001", PIN_PLAIN, "127.0.0.1"
        );
        assertThat(billReceipt).isNotNull();
        assertThat(billReceipt.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(billReceipt.billerName()).contains("Electricity");
        assertThat(billReceipt.amount()).isEqualByComparingTo(new BigDecimal("3450.00"));

        // =========================================================================
        // Step 6: Schedule It Monthly
        // =========================================================================
        CreateScheduleRequest schedReq = new CreateScheduleRequest(
                ScheduleType.BILL,
                ScheduledPayee.forBill("SIM_ELEC_01", "1002345678", "Simulated Electricity Board", "Monthly Bill"),
                null,
                "LKR",
                true,
                FundingSource.card(301L),
                ScheduleFrequency.MONTHLY,
                LocalDate.now(ZoneOffset.UTC).plusDays(1),
                LocalDate.now(ZoneOffset.UTC).plusMonths(6),
                true,
                PIN_PLAIN
        );
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(kasunUser, schedReq, PIN_PLAIN);
        assertThat(schedule).isNotNull();
        assertThat(schedule.frequency()).isEqualTo(ScheduleFrequency.MONTHLY);
        assertThat(schedule.status().name()).isEqualTo("ACTIVE");

        // =========================================================================
        // Step 7: Split a Dinner with 2 Friends
        // =========================================================================
        UserAccount nimalAccount = new UserAccount(
                1002L, "+94779876543", "Nimal Perera", "199122334455", passwordEncoder.encode("123456"),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        );
        User nimalUser = nimalAccount.toUser();

        when(userStore.findUserByPhone("+94779876543")).thenReturn(Optional.of(nimalAccount));
        when(userStore.findUserById(1002L)).thenReturn(Optional.of(nimalAccount));

        UserAccount sunimalAccount = new UserAccount(
                1003L, "+94775554433", "Sunimal Silva", "199255667788", passwordEncoder.encode("123456"),
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now()
        );
        when(userStore.findUserByPhone("+94775554433")).thenReturn(Optional.of(sunimalAccount));
        when(userStore.findUserById(1003L)).thenReturn(Optional.of(sunimalAccount));

        CreateSplitRequest splitReq = new CreateSplitRequest(
                "Dinner at Colombo Cafe",
                600000L, // 6,000.00 LKR in minor units
                "LKR",
                "EQUAL",
                List.of(
                        new SplitParticipantRequest(1002L, "+94779876543", 200000L),
                        new SplitParticipantRequest(1003L, "+94775554433", 200000L)
                ),
                48
        );
        SplitResponse splitResponse = splitService.createSplit(splitReq, kasunUser);
        assertThat(splitResponse).isNotNull();
        assertThat(splitResponse.participants()).hasSize(2);

        // Nimal pays his share
        PaySplitRequest paySplitReq = new PaySplitRequest("TX-IDEM-SPLIT-01", FundingSource.account(201L), "123456");
        SplitResponse settledSplit = splitService.payShare(splitResponse.id(), 1002L, paySplitReq);
        assertThat(settledSplit).isNotNull();

        // =========================================================================
        // Step 8: Analytics and Budget
        // =========================================================================
        String currentMonth = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7);
        SetBudgetRequest budgetReq = new SetBudgetRequest(
                currentMonth,
                5000000L, // 50,000.00 LKR in minor units
                Map.of("BILLS", 1000000L)
        );
        BudgetResponse budgetRes = analyticsService.setBudget(KASUN_ID, budgetReq);
        assertThat(budgetRes).isNotNull();
        assertThat(budgetRes.monthlyTotalMinor()).isEqualTo(5000000L);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(KASUN_ID, currentMonth);
        assertThat(summary).isNotNull();
        // Completed bill from step 5 is counted in minor units (3,450.00 + 30.00 fee = 3,480.00 LKR = 348000 minor)
        assertThat(summary.spentMinor()).isEqualTo(348000L);

        // =========================================================================
        // Step 9: Earn and Redeem Points (Points != Money)
        // =========================================================================
        TransactionConfirmedEvent earnEvent = new TransactionConfirmedEvent(
                "TX-DEMO-001", "QR_PAYMENT", KASUN_ID, 2000L,
                "BANK_B", "BANK_A", "•••• 5678", "•••• 9999",
                new BigDecimal("5000.00"), "DINING",
                false, Instant.now(), Map.of()
        );
        Optional<PointsLedgerEntry> earnedEntry = rewardsService.handleTransactionConfirmed(earnEvent);
        assertThat(earnedEntry).isPresent();
        assertThat(earnedEntry.get().type()).isEqualTo(PointsEntryType.EARN);
        assertThat(earnedEntry.get().points()).isGreaterThan(0);

        RewardsSummaryResponse rewardsSummary = rewardsService.getSummary(KASUN_ID);
        assertThat(rewardsSummary.pointsBalance()).isGreaterThan(0);

        // Redeem 25 points for voucher
        RedeemPointsRequest redeemReq = new RedeemPointsRequest("VOUCHER", 25, "OFFER-FOOD-3X", "Voucher note", PIN_PLAIN);
        RedeemPointsResponse redeemRes = rewardsService.redeemPoints(
                kasunUser, redeemReq, "IDEM-REDEEM-001", PIN_PLAIN
        );
        assertThat(redeemRes).isNotNull();
        assertThat(redeemRes.voucherCode()).isNotEmpty();
        assertThat(redeemRes.pointsRedeemed()).isEqualTo(25);

        // =========================================================================
        // Step 10: Pay at a Merchant (City Directory & LankaQR)
        // =========================================================================
        MerchantPageResponse merchantPage = cityService.getMerchants(KASUN_ID, null, "Cinnamon", null, null, 10);
        assertThat(merchantPage.merchants()).isNotEmpty();
        var merchant = merchantPage.merchants().get(0);
        assertThat(merchant.name()).contains("Cinnamon Leaf");

        PayAtMerchantRequest merchPayReq = new PayAtMerchantRequest(
                new BigDecimal("1250.00"), PIN_PLAIN, "Lunch payment", 201L
        );
        PayAtMerchantResponse merchPayment = cityService.payAtMerchant(
                kasunUser, merchant.id(), merchPayReq, "127.0.0.1"
        );
        assertThat(merchPayment).isNotNull();
        assertThat(merchPayment.status()).isEqualTo("SUCCESS");
        assertThat(merchPayment.amount()).isEqualByComparingTo(new BigDecimal("1250.00"));

        // =========================================================================
        // Step 11: Chat with FAQ Bot
        // =========================================================================
        CreateConversationRequest chatReq = new CreateConversationRequest(
                SupportCategory.PAYMENTS, "Payment question", "How do I pay with LankaQR?", null
        );
        SupportConversationDto conv = supportService.createConversation(KASUN_ID, chatReq);
        assertThat(conv).isNotNull();
        List<SupportMessageDto> messages = supportService.getMessages(KASUN_ID, conv.id(), null);
        assertThat(messages).isNotEmpty();
        // Verify automated bot reply exists and contains plain text only (no raw HTML)
        boolean hasBotReply = messages.stream().anyMatch(m -> m.sender() == MessageSender.BOT);
        assertThat(hasBotReply).isTrue();
        messages.forEach(m -> {
            assertThat(m.text()).doesNotContain("<script>");
            assertThat(m.text()).doesNotContain("<iframe>");
        });

        // =========================================================================
        // Step 12: Run a Statement Export
        // =========================================================================
        StatementRequest exportReq = new StatementRequest(
                currentMonth,
                LocalDate.now(ZoneOffset.UTC).minusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                Instant.now(),
                "ALL", "ALL", "ALL", null, null, null
        );
        StatementResponse export = analyticsService.exportStatement(
                KASUN_ID, exportReq, "127.0.0.1"
        );
        assertThat(export).isNotNull();
        assertThat(export.downloadUrl()).contains("statement");
        assertThat(export.month()).isEqualTo(currentMonth);
    }
}
