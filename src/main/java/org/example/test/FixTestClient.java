package org.example.test;

import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import java.time.LocalDateTime;

public class FixTestClient {
    private static int orderCounter = 1;

    public static void main(String[] args) throws Exception {
        // Configuration for client
        SessionSettings settings = new SessionSettings();

        SessionID sessionId = new SessionID(
                "FIX.4.4",
                "CLIENT",  // SenderCompID
                "EXCHANGE"  // TargetCompID
        );

        // Add all required settings
        settings.setString(sessionId, "ConnectionType", "initiator");
        settings.setString(sessionId, "SocketConnectHost", "localhost");
        settings.setString(sessionId, "SocketConnectPort", "9876");
        settings.setString(sessionId, "StartTime", "00:00:00");
        settings.setString(sessionId, "EndTime", "23:59:59");
        settings.setString(sessionId, "HeartBtInt", "30");
        settings.setString(sessionId, "ReconnectInterval", "5");
        settings.setString(sessionId, "FileStorePath", "client_store"); // Add this
        settings.setString(sessionId, "FileLogPath", "client_log");     // Add this
        settings.setString(sessionId, "UseDataDictionary", "N");        // Simplify for testing
        settings.setString(sessionId, "ResetOnLogon", "Y");
        settings.setString(sessionId, "ResetOnLogout", "Y");
        settings.setString(sessionId, "ResetOnDisconnect", "Y");

        // Create application
        Application application = new ApplicationAdapter() {
            @Override
            public void fromApp(Message message, SessionID sessionId) {
                try {
                    String msgType = message.getHeader().getString(MsgType.FIELD);
                    System.out.println("[CLIENT] Received: " + msgType);

                    if (MsgType.EXECUTION_REPORT.equals(msgType)) {
                        System.out.println("[CLIENT] Execution Report: " + message);
                    }
                } catch (FieldNotFound e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onLogon(SessionID sessionId) {
                System.out.println("[CLIENT] Logged on");
            }

            @Override
            public void onLogout(SessionID sessionId) {
                System.out.println("[CLIENT] Logged out");
            }
        };

        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new ScreenLogFactory(true, true, true, true);
        MessageFactory messageFactory = new DefaultMessageFactory();

        SocketInitiator initiator = new SocketInitiator(
                application, storeFactory, settings, logFactory, messageFactory);

        // Start connection
        System.out.println("Connecting to FIX gateway...");
        initiator.start();

        // Wait for connection
        Thread.sleep(2000);

        // Test scenario 1: Send new order
        sendNewOrder(sessionId, "TEST", Side.BUY, 100.50, 100);
        Thread.sleep(1000);

        // Test scenario 2: Send another order
        sendNewOrder(sessionId, "TEST", Side.SELL, 100.45, 50);
        Thread.sleep(1000);

        // Test scenario 3: Cancel an order
        sendCancelOrder(sessionId, "ORD001", "ORD002");
        Thread.sleep(1000);

        // Keep running for a while
        Thread.sleep(5000);

        initiator.stop();
    }

    private static void sendNewOrder(SessionID sessionId, String symbol,
                                     char side, double price, double quantity) throws Exception {
        NewOrderSingle order = new NewOrderSingle();

        String clOrdId = "ORD" + orderCounter++;

        order.set(new ClOrdID(clOrdId));
        order.set(new HandlInst('1')); // Automated execution
        order.set(new Symbol(symbol));
        order.set(new Side(side));
        order.set(new TransactTime(LocalDateTime.now()));
        order.set(new OrderQty(quantity));
        order.set(new Price(price));
        order.set(new OrdType(OrdType.LIMIT));

        System.out.printf("[CLIENT] Sending NewOrderSingle: %s %s %.2f @ %.2f%n",
                clOrdId, side == Side.BUY ? "BUY" : "SELL", quantity, price);

        Session.sendToTarget(order, sessionId);
    }

    private static void sendCancelOrder(SessionID sessionId, String clOrdId,
                                        String origClOrdId) throws Exception {
        OrderCancelRequest cancel = new OrderCancelRequest();

        cancel.set(new ClOrdID(clOrdId));
        cancel.set(new OrigClOrdID(origClOrdId));
        cancel.set(new Symbol("TEST"));
        cancel.set(new Side(Side.BUY));
        cancel.set(new TransactTime(LocalDateTime.now()));

        System.out.printf("[CLIENT] Sending Cancel: %s for %s%n", clOrdId, origClOrdId);

        Session.sendToTarget(cancel, sessionId);
    }
}