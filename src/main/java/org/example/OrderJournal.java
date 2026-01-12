package org.example;
import java.io.*;
import java.util.concurrent.*;

public class OrderJournal {
    private final String fileName = "order_journal.csv";
    // This queue holds orders waiting to be written to disk
    private final BlockingQueue<Order> queue = new LinkedBlockingQueue<>();

    public OrderJournal() {
        // Start a background thread to "consume" the queue
        Thread writerThread = new Thread(() -> {
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)))) {
                while (true) {
                    Order order = queue.take(); // Waits until an order is available
                    out.printf("%d,%s,%s,%d,%d,%d\n",
                            order.getId(), order.getUserId(), order.getSide(),
                            order.getPrice(), order.getQuantity(), System.currentTimeMillis());
                    out.flush(); // Ensure data is written
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        writerThread.setDaemon(true); // Close thread when app stops
        writerThread.start();
    }

    public void log(Order order) {
        queue.offer(order); // Just drops it in the queue; takes almost 0ms
    }
}