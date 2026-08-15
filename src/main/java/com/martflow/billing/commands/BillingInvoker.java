package com.martflow.billing.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs an ordered list of {@link BillingCommand}s as one transaction. If any command fails,
 * every command that already executed is {@code undo()}ne in reverse order, so the system is
 * left as if the tender never started.
 */
public class BillingInvoker {

    private final List<BillingCommand> commands = new ArrayList<>();

    public BillingInvoker addCommand(BillingCommand command) {
        commands.add(command);
        return this;
    }

    public void run() {
        List<BillingCommand> executed = new ArrayList<>();
        try {
            for (BillingCommand command : commands) {
                command.execute();
                executed.add(command);
            }
        } catch (RuntimeException failure) {
            rollback(executed);
            throw failure;
        }
    }

    private void rollback(List<BillingCommand> executed) {
        for (int i = executed.size() - 1; i >= 0; i--) {
            try {
                executed.get(i).undo();
            } catch (RuntimeException ignored) {
                // Best-effort rollback: keep undoing the rest even if one step errors.
            }
        }
    }
}
