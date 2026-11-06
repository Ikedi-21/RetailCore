package com.ikedi.pos.command;

import com.ikedi.pos.exception.PosException;
import com.ikedi.pos.model.Role;
import java.time.LocalDate;
import java.util.List;

/**
 * Management report commands (Manager only).
 */
public final class ReportCommands {

    private ReportCommands() {}

    public static class ReportDailyCommand implements Command {
        @Override public String name() { return "report-daily"; }
        @Override public String description() { return "Daily sales summary report (Manager only)"; }
        @Override public String usage() { return "report-daily [YYYY-MM-DD]"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            LocalDate date = args.isEmpty() ? null : LocalDate.parse(args.get(0));
            ctx.getOut().println(ctx.getReportService().formatDailySalesSummary(date));
        }
    }

    public static class ReportTopQtyCommand implements Command {
        @Override public String name() { return "report-top-qty"; }
        @Override public String description() { return "Top products by quantity sold (Manager only)"; }
        @Override public String usage() { return "report-top-qty [limit=5]"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            int limit = args.isEmpty() ? 5 : Integer.parseInt(args.get(0));
            ctx.getOut().println(ctx.getReportService().formatTopProductsByQuantity(limit));
        }
    }

    public static class ReportTopRevCommand implements Command {
        @Override public String name() { return "report-top-rev"; }
        @Override public String description() { return "Top products by revenue generated (Manager only)"; }
        @Override public String usage() { return "report-top-rev [limit=5]"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            int limit = args.isEmpty() ? 5 : Integer.parseInt(args.get(0));
            ctx.getOut().println(ctx.getReportService().formatTopProductsByRevenue(limit));
        }
    }

    public static class ReportLowStockCommand implements Command {
        @Override public String name() { return "report-low-stock"; }
        @Override public String description() { return "List products at or below reorder level (Manager only)"; }
        @Override public String usage() { return "report-low-stock"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            ctx.getOut().println(ctx.getReportService().formatLowStockReport());
        }
    }

    public static class ReportExpiringCommand implements Command {
        @Override public String name() { return "report-expiring"; }
        @Override public String description() { return "List perishable products expiring soon (Manager only)"; }
        @Override public String usage() { return "report-expiring [daysAhead=7]"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            int days = args.isEmpty() ? 7 : Integer.parseInt(args.get(0));
            ctx.getOut().println(ctx.getReportService().formatExpiringReport(days));
        }
    }

    public static class ReportProfitCommand implements Command {
        @Override public String name() { return "report-profit"; }
        @Override public String description() { return "Profit analysis report per decision D11 (Manager only)"; }
        @Override public String usage() { return "report-profit [fromDate: YYYY-MM-DD] [toDate: YYYY-MM-DD]"; }
        @Override public Role requiredRole() { return Role.MANAGER; }

        @Override
        public void execute(CommandContext ctx, List<String> args) throws PosException {
            LocalDate from = args.size() > 0 ? LocalDate.parse(args.get(0)) : null;
            LocalDate to = args.size() > 1 ? LocalDate.parse(args.get(1)) : null;
            ctx.getOut().println(ctx.getReportService().formatProfitReport(from, to));
        }
    }
}
