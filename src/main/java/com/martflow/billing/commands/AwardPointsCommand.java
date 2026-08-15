package com.martflow.billing.commands;

import com.martflow.loyalty.LoyaltyService;

/**
 * Credits loyalty points for a completed sale (1 pt / 100 BDT). Undo reverses the earn — floored
 * at zero so a void can never drive a balance negative.
 */
public final class AwardPointsCommand implements BillingCommand {

    private final LoyaltyService loyalty;
    private final String customerId;
    private final int points;

    public AwardPointsCommand(LoyaltyService loyalty, String customerId, int points) {
        this.loyalty = loyalty;
        this.customerId = customerId;
        this.points = points;
    }

    @Override
    public void execute() {
        if (points > 0) {
            loyalty.earn(customerId, points);
        }
    }

    @Override
    public void undo() {
        if (points > 0) {
            loyalty.reverseEarn(customerId, points);
        }
    }
}
