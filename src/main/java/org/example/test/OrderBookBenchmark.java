package org.example.test;

import org.example.EnhancedOrderBook;
import org.example.Order;
import org.example.Trade;
import org.example.TradeListener;
import org.example.risk.RiskEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class OrderBookBenchmark {

    private EnhancedOrderBook orderBook;
    private Order buyOrder;
    private Order sellOrder;

    @Setup(Level.Trial)
    public void setup() {
        // 1. Create a Dummy Risk Engine that always approves
        RiskEngine dummyRisk = new RiskEngine() {
            @Override
            public RiskCheckResult checkOrder(Order order, double marketPrice) {
                return RiskCheckResult.approve();
            }

            @Override
            public void updatePosition(String traderId, Order.Side side, long quantity) {
                // Do nothing for benchmark
            }
        };

        // 2. Create a Dummy Listener
        TradeListener dummyListener = new TradeListener() {
            @Override
            public void onTrade(Trade trade) {
                // Do nothing
            }
        };

        // 3. Initialize OrderBook
        orderBook = new EnhancedOrderBook(dummyListener, dummyRisk);

        // 4. Create Orders with NUMERIC IDs (as Strings)
        // FIX: Changed "TRADER1" to "1001" to prevent NumberFormatException
        buyOrder = new Order(1001L, "1001", Order.Side.BUY, 15000L, 100L);
        sellOrder = new Order(1002L, "1002", Order.Side.SELL, 15000L, 100L);
    }

    @Benchmark
    public void measureAddOrder(Blackhole blackhole) {
        // Reset quantity because it might have been decremented in previous runs
        buyOrder.setQuantity(100L);

        // Process order
        Object result = orderBook.processOrder(buyOrder);

        // Prevent dead code elimination
        blackhole.consume(result);
    }

    @Benchmark
    public void measureMatching(Blackhole blackhole) {
        // 1. Reset orders
        buyOrder.setQuantity(100L);
        sellOrder.setQuantity(100L);

        // 2. Place Sell Order (It sits in the book)
        orderBook.processOrder(sellOrder);

        // 3. Place Buy Order (It matches immediately)
        // This triggers: Risk Check -> Tree Lookup -> Match -> Callback
        Object result = orderBook.processOrder(buyOrder);

        blackhole.consume(result);
    }

    // ================= MAIN METHOD =================
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrderBookBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}