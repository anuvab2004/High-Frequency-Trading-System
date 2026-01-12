package org.example.test;

import org.example.EnhancedOrderBook;
import org.example.fix.FixGateway;
import org.example.risk.RiskEngine;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TestFixGateway {
    private static final Logger logger = Logger.getLogger(TestFixGateway.class.getName());

    public static void main(String[] args) throws Exception {
        System.out.println("=== Starting FIX Gateway ===");

        RiskEngine riskEngine = new RiskEngine(); // Now works

        EnhancedOrderBook orderBook = new EnhancedOrderBook(
                trade -> System.out.printf("[TRADE] %,d @ $%.2f%n",
                        trade.quantity(), trade.price() / 100.0),
                riskEngine
        );

        FixGateway gateway = new FixGateway(orderBook);

        // Extract gateway thread creation to a method
        Thread gatewayThread = createGatewayThread(gateway);

        gatewayThread.setDaemon(true);
        gatewayThread.start();

        Thread.sleep(3000);

        System.out.println("\n✅ FIX Gateway is now running on port 9876");
        System.out.println("==========================================");
        System.out.println("Now you can run the test client in a separate terminal:");
        System.out.println("java -cp \"target/classes;...\" org.example.test.FixTestClient");
        System.out.println("\nOr press Enter to exit the gateway...");

        try {
            int input = System.in.read();
            logger.log(Level.INFO, "User input received: " + input);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reading user input", e);
        }

        System.out.println("Shutting down FIX Gateway...");
    }

    private static Thread createGatewayThread(FixGateway gateway) {
        return new Thread(() -> {
            try {
                gateway.start();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Gateway failed to start", e);
            }
        });
    }
}