package com.martflow.reports;

import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.common.TimeSource;
import com.martflow.persistence.InMemoryAuditLogRepository;
import com.martflow.persistence.InMemoryDayCloseRepository;
import com.martflow.persistence.InMemoryReturnRepository;
import com.martflow.persistence.InMemorySaleRepository;
import com.martflow.payment.TenderType;
import com.martflow.returns.SaleReturn;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleStatus;
import com.martflow.sales.Tender;
import com.martflow.security.Caller;
import com.martflow.security.Role;
import com.martflow.security.RoleContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Z-report's drawer math, hand-checked scenario by scenario: change subtraction, cash
 * refunds, same-window voids netting to zero, and the cross-midnight void that costs today's
 * drawer — plus the close/variance/history lifecycle.
 */
class DayCloseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private static InMemorySaleRepository sales;
    private static InMemoryReturnRepository returns;
    private static InMemoryDayCloseRepository closes;
    private static InMemoryAuditLogRepository auditLog;
    private static AuditService audit;
    private static DayCloseService service;

    @BeforeAll
    static void init() {
        RoleContext.set(new Caller("u-manager", "manager", Role.MANAGER));
        TimeSource.useFixedClock(Clock.fixed(Instant.parse("2026-08-16T06:00:00Z"),
                ZoneId.of("Asia/Dhaka"))); // 2026-08-16 12:00 Dhaka
        sales = new InMemorySaleRepository();
        returns = new InMemoryReturnRepository();
        closes = new InMemoryDayCloseRepository();
        auditLog = new InMemoryAuditLogRepository();
        audit = new AuditService(auditLog);
        service = new DayCloseService(sales, returns, closes, audit);
    }

    @AfterAll
    static void tearDown() {
        TimeSource.resetToSystemClock();
        RoleContext.clear();
    }

    @BeforeEach
    void freshBooks() {
        sales.findAll().forEach(s -> sales.delete(s.getReceiptNo()));
        returns.findAll().forEach(r -> returns.delete(r.getId()));
        closes.findAll().forEach(c -> closes.delete(c.id()));
        auditLog.findAll().forEach(e -> auditLog.delete(e.getId()));
    }

    private static Sale sale(String receiptNo, LocalDateTime at, TenderType type,
                             BigDecimal tenderAmount, BigDecimal change, BigDecimal net) {
        Sale s = new Sale(receiptNo, at, "cashier", null, List.of(),
                new Sale.Totals(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, net, BigDecimal.ZERO, tenderAmount, change),
                List.of(new Tender(type, tenderAmount, null, null)));
        sales.save(s);
        return s;
    }

    @Test
    void multiTenderDayExpectsTenderedCashMinusChange() {
        sale("MF-1", TODAY.atTime(10, 0), TenderType.CASH, new BigDecimal("500"),
                new BigDecimal("50"), new BigDecimal("450"));
        sale("MF-2", TODAY.atTime(11, 0), TenderType.BKASH, new BigDecimal("300"),
                BigDecimal.ZERO, new BigDecimal("300"));

        DayClose z = service.preview(TODAY, TODAY);
        assertEquals(2, z.bills());
        assertEquals(0, z.net().compareTo(new BigDecimal("750")));
        assertEquals(0, z.cashIn().compareTo(new BigDecimal("500")));
        assertEquals(0, z.tenders().get("BKASH").compareTo(new BigDecimal("300")));
        assertEquals(0, z.expectedDrawerCash().compareTo(new BigDecimal("450")),
                "500 cash in − 50 change out; bKash money never touches the drawer");
        assertNull(z.countedCash(), "a preview has no counted cash yet");
    }

    @Test
    void cashReturnReducesTheDrawer() {
        sale("MF-1", TODAY.atTime(10, 0), TenderType.CASH, new BigDecimal("500"),
                new BigDecimal("50"), new BigDecimal("450"));
        returns.save(new SaleReturn("RET-1", "MF-1", TODAY.atTime(12, 0), "cashier",
                List.of(), new BigDecimal("90"), "CASH", null));

        DayClose z = service.preview(TODAY, TODAY);
        assertEquals(0, z.expectedDrawerCash().compareTo(new BigDecimal("360")),
                "500 − 50 change − 90 cash refund");
        assertEquals(0, z.cashRefunds().compareTo(new BigDecimal("90")));
        assertEquals(1, z.returnsCount());
    }

    @Test
    void sameWindowVoidNetsToZeroOnPaper() {
        sale("MF-keep", TODAY.atTime(10, 0), TenderType.CASH, new BigDecimal("450"),
                BigDecimal.ZERO, new BigDecimal("450"));
        Sale voided = sale("MF-void", TODAY.atTime(11, 0), TenderType.CASH,
                new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"));
        voided.setStatus(SaleStatus.VOIDED);
        voided.setVoidReason("mis-scan");
        voided.setVoidedAt(TODAY.atTime(12, 0));
        sales.save(voided);

        DayClose z = service.preview(TODAY, TODAY);
        assertEquals(1, z.bills(), "the voided sale left the revenue figures");
        assertEquals(1, z.voidsCount());
        assertEquals(0, z.expectedDrawerCash().compareTo(new BigDecimal("450")),
                "cash in 650 − void refund 200 = same 450 as if the voided sale never happened");
    }

    @Test
    void yesterdaySaleVoidedTodayCostsTodaysDrawerOnly() {
        Sale old = sale("MF-old", YESTERDAY.atTime(15, 0), TenderType.CASH,
                new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200"));
        old.setStatus(SaleStatus.VOIDED);
        old.setVoidReason("chargeback");
        old.setVoidedAt(TODAY.atTime(9, 0));
        sales.save(old);

        DayClose today = service.preview(TODAY, TODAY);
        assertEquals(0, today.bills());
        assertEquals(0, today.expectedDrawerCash().compareTo(new BigDecimal("-200")),
                "the refund cash left today's drawer although nothing was sold today");

        DayClose yesterday = service.preview(YESTERDAY, YESTERDAY);
        assertEquals(0, yesterday.expectedDrawerCash().compareTo(new BigDecimal("200")),
                "yesterday's Z stays closed — its cash was really in the drawer then");
    }

    @Test
    void closeRecordsVarianceAndLandsInTheHistoryAndAudit() {
        sale("MF-1", TODAY.atTime(10, 0), TenderType.CASH, new BigDecimal("500"),
                new BigDecimal("50"), new BigDecimal("450"));

        DayClose closed = service.close(TODAY, TODAY, new BigDecimal("430"), "till 1", "manager");
        assertEquals(0, closed.variance().compareTo(new BigDecimal("-20")), "430 counted − 450 expected");
        assertEquals(1, service.history().size());
        assertEquals(AuditLog.Action.DAY_CLOSED, audit.query(null, null, null, null, 10).get(0).getAction());
    }

    @Test
    void emptyWindowIsAllZeros() {
        DayClose z = service.preview(TODAY, TODAY);
        assertEquals(0, z.bills());
        assertEquals(0, z.expectedDrawerCash().compareTo(BigDecimal.ZERO));
        assertEquals(0, z.net().compareTo(BigDecimal.ZERO));
    }
}
