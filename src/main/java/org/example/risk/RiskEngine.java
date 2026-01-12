package org.example.risk;

import org.example.Order;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RiskEngine {
    // Risk limits per trader
    private final Map<String, RiskLimit> traderLimits = new ConcurrentHashMap<>();

    // Current positions per trader (simplified - in reality per symbol)
    private final Map<String, AtomicLong> positions = new ConcurrentHashMap<>();

    // Daily volume per trader
    private final Map<String, AtomicLong> dailyVolume = new ConcurrentHashMap<>();

    // Global risk limits
    private static final long MAX_ORDER_QUANTITY = 1_000_000; // 1M shares
    private static final double MAX_PRICE_DEVIATION = 0.05; // 5%
    private static final long MAX_DAILY_VOLUME = 10_000_000; // 10M shares
    private static final long MAX_POSITION = 100_000; // 100K shares

    public RiskEngine() {
        // Initialize with default limits for all traders
        traderLimits.put("DEFAULT", new RiskLimit(
                MAX_POSITION,        // max position
                MAX_ORDER_QUANTITY,  // max order size
                MAX_DAILY_VOLUME     // daily volume limit
        ));

        // Example trader-specific limits
        traderLimits.put("TRADER1", new RiskLimit(50_000, 500_000, 5_000_000));
        traderLimits.put("TRADER2", new RiskLimit(200_000, 2_000_000, 20_000_000));
        traderLimits.put("ASVAL", new RiskLimit(10_000, 100_000, 1_000_000));
        traderLimits.put("OXFORMED", new RiskLimit(1_000, 10_000, 100_000));
        traderLimits.put("MOD_TEST", new RiskLimit(1_000, 10_000, 100_000));
        traderLimits.put("NEWTRADER", new RiskLimit(5_000, 50_000, 500_000));
    }

    /**
     * Check if an order passes all risk checks
     */
    public RiskCheckResult checkOrder(Order order, double marketPrice) {
        // 1. Fat-finger check - maximum order quantity
        if (order.getQuantity() > MAX_ORDER_QUANTITY) {
            return RiskCheckResult.reject(
                    "EXCEED_MAX_ORDER_SIZE",
                    String.format("Order quantity %d exceeds maximum limit %d",
                            order.getQuantity(), MAX_ORDER_QUANTITY)
            );
        }

        // 2. Price deviation check
        double orderPrice = order.getPrice() / 100.0; // Convert from cents
        double priceDeviation = Math.abs(orderPrice - marketPrice) / marketPrice;

        if (priceDeviation > MAX_PRICE_DEVIATION) {
            return RiskCheckResult.reject(
                    "PRICE_DEVIATION_TOO_HIGH",
                    String.format("Price deviation %.2f%% exceeds maximum %.2f%%",
                            priceDeviation * 100, MAX_PRICE_DEVIATION * 100)
            );
        }

        // 3. Trader-specific limits
        RiskLimit limit = traderLimits.getOrDefault(order.getUserId(),
                traderLimits.get("DEFAULT"));

        // 4. Position limit check
        String positionKey = order.getUserId() + "_" + order.getSide();
        AtomicLong currentPosition = positions.computeIfAbsent(
                positionKey, k -> new AtomicLong(0));

        long newPosition = currentPosition.get() + order.getQuantity();
        if (newPosition > limit.getMaxPosition()) {
            return RiskCheckResult.reject(
                    "EXCEED_POSITION_LIMIT",
                    String.format("New position %d exceeds limit %d",
                            newPosition, limit.getMaxPosition())
            );
        }

        // 5. Daily volume check
        String volumeKey = order.getUserId() + "_DAILY";
        AtomicLong dailyVol = dailyVolume.computeIfAbsent(
                volumeKey, k -> new AtomicLong(0));

        long newDailyVolume = dailyVol.get() + order.getQuantity();
        if (newDailyVolume > limit.getDailyVolumeLimit()) {
            return RiskCheckResult.reject(
                    "EXCEED_DAILY_VOLUME",
                    String.format("Daily volume %d exceeds limit %d",
                            newDailyVolume, limit.getDailyVolumeLimit())
            );
        }

        // 6. Order size check (trader-specific)
        if (order.getQuantity() > limit.getMaxOrderSize()) {
            return RiskCheckResult.reject(
                    "EXCEED_TRADER_ORDER_SIZE",
                    String.format("Order size %d exceeds trader limit %d",
                            order.getQuantity(), limit.getMaxOrderSize())
            );
        }

        // All checks passed
        return RiskCheckResult.approve();
    }

    /**
     * Update position after a trade
     */
    public void updatePosition(String traderId, Order.Side side, long quantity) {
        String key = traderId + "_" + side;
        positions.computeIfAbsent(key, k -> new AtomicLong(0))
                .addAndGet(quantity);

        // Also update daily volume
        String volumeKey = traderId + "_DAILY";
        dailyVolume.computeIfAbsent(volumeKey, k -> new AtomicLong(0))
                .addAndGet(quantity);
    }

    /**
     * Reset daily volumes (call this at start of trading day)
     */
    public void resetDailyVolumes() {
        dailyVolume.clear();
        System.out.println("[RiskEngine] Daily volumes reset");
    }

    /**
     * Get current position for a trader
     */
    public long getPosition(String traderId, Order.Side side) {
        String key = traderId + "_" + side;
        AtomicLong position = positions.get(key);
        return position != null ? position.get() : 0;
    }

    /**
     * Get daily volume for a trader
     */
    public long getDailyVolume(String traderId) {
        String key = traderId + "_DAILY";
        AtomicLong volume = dailyVolume.get(key);
        return volume != null ? volume.get() : 0;
    }

    /**
     * Add or update trader limits
     */
    public void setTraderLimit(String traderId, RiskLimit limit) {
        traderLimits.put(traderId, limit);
        System.out.printf("[RiskEngine] Set limits for %s: %s%n", traderId, limit);
    }



    // ================= INNER CLASSES =================

    /**
     * Risk limits for a trader
     */
    public static class RiskLimit {
        private final long maxPosition;        // Maximum position size
        private final long maxOrderSize;       // Maximum single order size
        private final long dailyVolumeLimit;   // Maximum daily trading volume

        public RiskLimit(long maxPosition, long maxOrderSize, long dailyVolumeLimit) {
            this.maxPosition = maxPosition;
            this.maxOrderSize = maxOrderSize;
            this.dailyVolumeLimit = dailyVolumeLimit;
        }

        // Getters
        public long getMaxPosition() { return maxPosition; }
        public long getMaxOrderSize() { return maxOrderSize; }
        public long getDailyVolumeLimit() { return dailyVolumeLimit; }

        @Override
        public String toString() {
            return String.format("Pos: %d, Order: %d, Daily: %d",
                    maxPosition, maxOrderSize, dailyVolumeLimit);
        }
    }

    /**
     * Result of a risk check
     */
    public static class RiskCheckResult {
        private final boolean approved;
        private final String rejectCode;
        private final String rejectReason;

        public RiskCheckResult(boolean approved, String rejectCode, String rejectReason) {
            this.approved = approved;
            this.rejectCode = rejectCode;
            this.rejectReason = rejectReason;
        }

        public static RiskCheckResult approve() {
            return new RiskCheckResult(true, null, null);
        }

        public static RiskCheckResult reject(String code, String reason) {
            return new RiskCheckResult(false, code, reason);
        }

        // Getters
        public boolean isApproved() { return approved; }
        public String getRejectCode() { return rejectCode; }
        public String getRejectReason() { return rejectReason; }

        @Override
        public String toString() {
            if (approved) {
                return "APPROVED";
            } else {
                return String.format("REJECTED [%s]: %s", rejectCode, rejectReason);
            }
        }
    }
}