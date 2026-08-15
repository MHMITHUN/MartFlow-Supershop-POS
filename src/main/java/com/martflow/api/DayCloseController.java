package com.martflow.api;

import com.martflow.common.TimeSource;
import com.martflow.reports.DayClose;
import com.martflow.reports.DayCloseService;
import com.martflow.security.RoleContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Day-close (Z-report): preview the window's drawer math, close it with the counted cash,
 * and browse past closes. Manager and above (enforced in the service).
 */
@RestController
@RequestMapping("/api/reports/day-close")
public class DayCloseController {

    private final DayCloseService dayClose;

    public DayCloseController(DayCloseService dayClose) {
        this.dayClose = dayClose;
    }

    public record CloseRequest(String from, String to, BigDecimal countedCash, String note) {
    }

    @GetMapping("/preview")
    public DayClose preview(@RequestParam(required = false) String from,
                            @RequestParam(required = false) String to) {
        return dayClose.preview(resolve(from), resolve(to));
    }

    @PostMapping
    public DayClose close(@RequestBody CloseRequest req) {
        return dayClose.close(resolve(req.from()), resolve(req.to()), req.countedCash(),
                req.note(), RoleContext.current() == null ? "-" : RoleContext.current().username());
    }

    @GetMapping
    public List<DayClose> history() {
        return dayClose.history();
    }

    private static LocalDate resolve(String raw) {
        return raw == null || raw.isBlank() ? TimeSource.today() : LocalDate.parse(raw);
    }
}
