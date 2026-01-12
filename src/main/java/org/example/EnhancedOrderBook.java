package org.example;

import org.example.risk.RiskEngine;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class EnhancedOrderBook {
    private final TreeMap<Long, Deque<Order>> buyOrders = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, Deque<Order>> sellOrders = new TreeMap<>();

    private final Map<Long, Order> activeOrders = new ConcurrentHashMap<>();

    private final TradeListener listener;
    private final OrderJournal journal;
    private final RiskEngine riskEngine;

    private final AtomicLong totalOrders = new AtomicLong();
    private final AtomicLong totalTrades = new AtomicLong();
    private final AtomicLong rejectedOrders = new AtomicLong();
    private final long[] latencyBuckets = new long[100];

    private double referenceMarketPrice = 100.0;

    public EnhancedOrderBook(TradeListener listener, RiskEngine riskEngine) {
        this.listener = listener;
        this.riskEngine = riskEngine;
        this.journal = new OrderJournal();
    }

    public synchronized OrderResponse processOrder(Order order) {
        long startTime = System.nanoTime();
        totalOrders.incrementAndGet();

        // Convert String userId to long for risk engine
        long userIdLong = Long.parseLong(order.getUserId());

        RiskEngine.RiskCheckResult riskResult = riskEngine.checkOrder(order, referenceMarketPrice);

        if (!riskResult.isApproved()) {
            rejectedOrders.incrementAndGet();
            return OrderResponse.rejected(order.getId(),
                    riskResult.getRejectCode(), riskResult.getRejectReason());
        }

        journal.log(order);
        activeOrders.put(order.getId(), order);

        List<Trade> trades = new ArrayList<>();

        if (order.getSide() == Order.Side.BUY) {
            trades.addAll(match(order, sellOrders, buyOrders));
        } else {
            trades.addAll(match(order, buyOrders, sellOrders));
        }

        // Update positions in risk engine using long userId
        for (Trade trade : trades) {
            riskEngine.updatePosition(order.getUserId(), order.getSide(), trade.quantity());
        }

        long latencyMicros = (System.nanoTime() - startTime) / 1000;
        if (latencyMicros < 100) {
            latencyBuckets[(int)latencyMicros]++;
        }

        String status;
        if (order.getQuantity() == 0) {
            status = "FILLED";
            activeOrders.remove(order.getId());
        } else if (trades.isEmpty()) {
            status = "NEW";
        } else {
            status = "PARTIAL";
        }

        return OrderResponse.accepted(order.getId(), trades, status);
    }

    private List<Trade> match(Order incoming,
                              TreeMap<Long, Deque<Order>> opposite,
                              TreeMap<Long, Deque<Order>> sameSide) {

        List<Trade> trades = new ArrayList<>();

        while (incoming.getQuantity() > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, Deque<Order>> best = opposite.firstEntry();
            long bestPrice = best.getKey();

            if ((incoming.getSide() == Order.Side.BUY && incoming.getPrice() < bestPrice) ||
                    (incoming.getSide() == Order.Side.SELL && incoming.getPrice() > bestPrice)) {
                break;
            }

            Deque<Order> queue = best.getValue();
            Order resting = queue.peek();

            long tradedQty = Math.min(incoming.getQuantity(), resting.getQuantity());

            Trade trade = new Trade(
                    incoming.getSide() == Order.Side.BUY ? incoming.getId() : resting.getId(),
                    incoming.getSide() == Order.Side.SELL ? incoming.getId() : resting.getId(),
                    bestPrice,
                    tradedQty
            );

            trades.add(trade);
            totalTrades.incrementAndGet();

            if (listener != null) {
                listener.onTrade(trade);
            }

            incoming.setQuantity(incoming.getQuantity() - tradedQty);
            resting.setQuantity(resting.getQuantity() - tradedQty);

            if (resting.getQuantity() == 0) {
                queue.poll();
                activeOrders.remove(resting.getId());
                if (queue.isEmpty()) {
                    opposite.remove(bestPrice);
                }
            }
        }

        if (incoming.getQuantity() > 0) {
            sameSide.computeIfAbsent(incoming.getPrice(), p -> new ArrayDeque<>()).add(incoming);
        } else {
            activeOrders.remove(incoming.getId());
        }

        return trades;
    }

    public synchronized boolean cancelOrder(long orderId) {
        Order order = activeOrders.remove(orderId);
        if (order == null) return false;

        TreeMap<Long, Deque<Order>> book =
                order.getSide() == Order.Side.BUY ? buyOrders : sellOrders;

        Deque<Order> queue = book.get(order.getPrice());
        if (queue != null) {
            boolean removed = queue.remove(order);
            if (queue.isEmpty()) {
                book.remove(order.getPrice());
            }
            return removed;
        }
        return false;
    }

    public synchronized OrderResponse modifyOrder(long orderId, long newPrice, long newQty) {
        Order old = activeOrders.get(orderId);
        if (old == null) {
            return OrderResponse.rejected(orderId, "ORDER_NOT_FOUND", "Order does not exist");
        }

        cancelOrder(orderId);

        Order modified = new Order(
                orderId,
                old.getUserId(),  // String userId
                old.getSide(),
                newPrice,
                newQty
        );

        return processOrder(modified);
    }

    public List<OrderBookEntry> getOrderBookSide(Order.Side side, int depth) {
        TreeMap<Long, Deque<Order>> book =
                side == Order.Side.BUY ? buyOrders : sellOrders;

        List<OrderBookEntry> levels = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Long, Deque<Order>> entry : book.entrySet()) {
            if (count >= depth) break;

            long totalQty = entry.getValue().stream()
                    .mapToLong(Order::getQuantity)
                    .sum();
            levels.add(new OrderBookEntry(entry.getKey(), totalQty));
            count++;
        }
        return levels;
    }

    public void updateMarketPrice(double price) {
        this.referenceMarketPrice = price;
    }

    public PerformanceMetrics getPerformanceMetrics() {
        long totalLatencyMicros = 0;
        long totalSamples = 0;

        for (int i = 0; i < latencyBuckets.length; i++) {
            totalLatencyMicros += i * latencyBuckets[i];
            totalSamples += latencyBuckets[i];
        }

        double avgLatency = totalSamples > 0 ?
                (double) totalLatencyMicros / totalSamples : 0.0;

        return new PerformanceMetrics(
                totalOrders.get(),
                totalTrades.get(),
                rejectedOrders.get(),
                activeOrders.size(),
                avgLatency
        );
    }

    public void recover() {
        System.out.println("[EnhancedOrderBook] Recovery not implemented yet");
    }

    public static class OrderResponse {
        private final long orderId;
        private final boolean accepted;
        private final String status;
        private final String rejectCode;
        private final String rejectReason;
        private final List<Trade> trades;

        private OrderResponse(long orderId, boolean accepted, String status,
                              String rejectCode, String rejectReason, List<Trade> trades) {
            this.orderId = orderId;
            this.accepted = accepted;
            this.status = status;
            this.rejectCode = rejectCode;
            this.rejectReason = rejectReason;
            this.trades = trades;
        }

        public static OrderResponse accepted(long orderId, List<Trade> trades, String status) {
            return new OrderResponse(orderId, true, status, null, null, trades);
        }

        public static OrderResponse rejected(long orderId, String code, String reason) {
            return new OrderResponse(orderId, false, "REJECTED", code, reason,
                    Collections.emptyList());
        }

        public long getOrderId() { return orderId; }
        public boolean isAccepted() { return accepted; }
        public String getStatus() { return status; }
        public String getRejectCode() { return rejectCode; }
        public String getRejectReason() { return rejectReason; }
        public List<Trade> getTrades() { return trades; }

        @Override
        public String toString() {
            if (accepted) {
                return String.format("Order %d %s, %d trades", orderId, status, trades.size());
            } else {
                return String.format("Order %d REJECTED: %s - %s", orderId, rejectCode, rejectReason);
            }
        }
    }

    public static class PerformanceMetrics {
        private final long totalOrders;
        private final long totalTrades;
        private final long rejectedOrders;
        private final int activeOrderCount;
        private final double averageLatencyMicros;

        public PerformanceMetrics(long totalOrders, long totalTrades, long rejectedOrders,
                                  int activeOrderCount, double averageLatencyMicros) {
            this.totalOrders = totalOrders;
            this.totalTrades = totalTrades;
            this.rejectedOrders = rejectedOrders;
            this.activeOrderCount = activeOrderCount;
            this.averageLatencyMicros = averageLatencyMicros;
        }

        public long getTotalOrders() { return totalOrders; }
        public long getTotalTrades() { return totalTrades; }
        public long getRejectedOrders() { return rejectedOrders; }
        public int getActiveOrderCount() { return activeOrderCount; }
        public double getAverageLatencyMicros() { return averageLatencyMicros; }

        public double getAcceptanceRate() {
            return totalOrders > 0 ?
                    (double) (totalOrders - rejectedOrders) / totalOrders * 100 : 100.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "Orders: %d, Trades: %d, Rejected: %d (%.1f%% accepted), " +
                            "Active: %d, Avg Latency: %.2f μs",
                    totalOrders, totalTrades, rejectedOrders, getAcceptanceRate(),
                    activeOrderCount, averageLatencyMicros
            );
        }
    }
}