package com.martflow.api;

import com.martflow.api.dto.ApiMappers;
import com.martflow.api.dto.ProductDtos.AlertResponse;
import com.martflow.app.MartFlowFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The store's alert center: low stock, restocks, price changes, expiry and wastage. */
@RestController
@RequestMapping("/api/alerts")
public class AlertsController {

    private final MartFlowFacade shop;

    public AlertsController(MartFlowFacade shop) {
        this.shop = shop;
    }

    @GetMapping
    public List<AlertResponse> alerts(@RequestParam(required = false) Boolean unreadOnly) {
        return shop.alerts(Boolean.TRUE.equals(unreadOnly))
                .stream().map(ApiMappers::toResponse).toList();
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable String id) {
        shop.markAlertRead(id);
    }
}
