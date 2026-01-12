package org.example.fix;

import org.example.EnhancedOrderBook;
import org.example.Order;
import org.example.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.FileStoreFactory;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import quickfix.UnsupportedMessageType;

import quickfix.field.*;
import quickfix.fix44.*;

public class FixGateway implements Application {
    private final EnhancedOrderBook orderBook;
    private SessionID sessionId;
    private final Map<String, String> accountToUserIdMap = new ConcurrentHashMap<>();

    public FixGateway(EnhancedOrderBook orderBook) {
        this.orderBook = orderBook;
        // Initialize some default account mappings
        accountToUserIdMap.put("DEFAULT", "1");
        accountToUserIdMap.put("TRADER1", "1001");
        accountToUserIdMap.put("TRADER2", "1002");
    }

    public void start() throws ConfigError, FieldConvertError {
        SessionSettings settings = new SessionSettings("fix-config.cfg");
        FileStoreFactory storeFactory = new FileStoreFactory(settings);
        ScreenLogFactory logFactory = new ScreenLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        SocketAcceptor acceptor = new SocketAcceptor(
                this, storeFactory, settings, logFactory, messageFactory);

        System.out.println("Starting FIX Gateway on port 9876...");
        acceptor.start();
    }

    @Override
    public void onCreate(SessionID sessionId) {
        this.sessionId = sessionId;
        System.out.println("[FIX] Session created: " + sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("[FIX] Logon successful: " + sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("[FIX] Logout: " + sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (MsgType.LOGON.equals(msgType)) {
                message.setField(new HeartBtInt(30));
            }
        } catch (FieldNotFound e) {
            System.err.println("[FIX] Error in toAdmin: " + e.getMessage());
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound {
        String msgType = message.getHeader().getString(MsgType.FIELD);
        switch (msgType) {
            case MsgType.LOGON:
                System.out.println("[FIX] Received Logon from " + sessionId);
                break;
            case MsgType.LOGOUT:
                System.out.println("[FIX] Received Logout from " + sessionId);
                break;
            case MsgType.HEARTBEAT:
                break;
            case MsgType.TEST_REQUEST:
                handleTestRequest(message, sessionId);
                break;
        }
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend { }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue, IncorrectDataFormat {
        crack(message, sessionID);
    }

    private void crack(Message message, SessionID sessionId)
            throws FieldNotFound, UnsupportedMessageType {
        String msgType = message.getHeader().getString(MsgType.FIELD);

        switch (msgType) {
            case MsgType.ORDER_SINGLE:
                processNewOrderSingle((NewOrderSingle) message, sessionId);
                break;
            case MsgType.ORDER_CANCEL_REQUEST:
                processOrderCancelRequest((OrderCancelRequest) message, sessionId);
                break;
            case MsgType.ORDER_CANCEL_REPLACE_REQUEST:
                processOrderCancelReplaceRequest((OrderCancelReplaceRequest) message, sessionId);
                break;
            default:
                System.out.println("[FIX] Unsupported message type: " + msgType);
                sendBusinessMessageReject(message, sessionId, "Unsupported message type");
        }
    }

    private void processNewOrderSingle(NewOrderSingle order, SessionID sessionId)
            throws FieldNotFound {
        try {
            String clOrdId = order.getClOrdID().getValue();
            String symbol = order.isSetSymbol() ? order.getSymbol().getValue() : "UNKNOWN";
            char side = order.getSide().getValue();

            double orderQty = order.getOrderQty().getValue();
            double price = order.getPrice().getValue();
            BigDecimal quantityBD = BigDecimal.valueOf(orderQty);
            BigDecimal priceBD = BigDecimal.valueOf(price);

            String account = order.isSetAccount() ? order.getAccount().getValue() : "DEFAULT";
            String userId = convertAccountToUserId(account);

            System.out.printf("[FIX] NewOrderSingle: %s %s %s @ %s Qty=%s Account=%s UserId=%s%n",
                    clOrdId, side == Side.BUY ? "BUY" : "SELL",
                    symbol, priceBD, quantityBD, account, userId);

            Order internalOrder = new Order(
                    System.nanoTime(),
                    userId,  // Using String userId
                    side == Side.BUY ? Order.Side.BUY : Order.Side.SELL,
                    priceBD.multiply(BigDecimal.valueOf(100)).longValue(),
                    quantityBD.longValue()
            );

            OrderIdMapping.storeMapping(clOrdId, internalOrder.getId());

            EnhancedOrderBook.OrderResponse response = orderBook.processOrder(internalOrder);
            sendExecutionReport(clOrdId, internalOrder, response, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error processing NewOrderSingle: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processOrderCancelRequest(OrderCancelRequest cancelRequest, SessionID sessionId)
            throws FieldNotFound {
        try {
            String clOrdId = cancelRequest.getClOrdID().getValue();
            String origClOrdId = cancelRequest.getOrigClOrdID().getValue();

            Long internalOrderId = OrderIdMapping.getInternalId(origClOrdId);
            if (internalOrderId == null) {
                sendOrderCancelReject(clOrdId, origClOrdId,
                        CxlRejReason.UNKNOWN_ORDER, "Order not found", sessionId);
                return;
            }

            boolean cancelled = orderBook.cancelOrder(internalOrderId);

            if (cancelled) {
                sendCancelExecutionReport(clOrdId, origClOrdId, sessionId);
            } else {
                sendOrderCancelReject(clOrdId, origClOrdId,
                        CxlRejReason.TOO_LATE_TO_CANCEL, "Unable to cancel order", sessionId);
            }

        } catch (Exception e) {
            System.err.println("[FIX] Error processing OrderCancelRequest: " + e.getMessage());
        }
    }

    private void processOrderCancelReplaceRequest(OrderCancelReplaceRequest replaceRequest,
                                                  SessionID sessionId) throws FieldNotFound {
        try {
            String clOrdId = replaceRequest.getClOrdID().getValue();
            String origClOrdId = replaceRequest.getOrigClOrdID().getValue();

            double price = replaceRequest.getPrice().getValue();
            double orderQty = replaceRequest.getOrderQty().getValue();
            BigDecimal priceBD = BigDecimal.valueOf(price);
            BigDecimal quantityBD = BigDecimal.valueOf(orderQty);

            Long internalOrderId = OrderIdMapping.getInternalId(origClOrdId);
            if (internalOrderId == null) {
                sendOrderCancelReject(clOrdId, origClOrdId,
                        CxlRejReason.UNKNOWN_ORDER, "Order not found", sessionId);
                return;
            }

            EnhancedOrderBook.OrderResponse response = orderBook.modifyOrder(
                    internalOrderId,
                    priceBD.multiply(BigDecimal.valueOf(100)).longValue(),
                    quantityBD.longValue()
            );

            if (response.isAccepted()) {
                sendReplaceExecutionReport(clOrdId, origClOrdId, sessionId);
                OrderIdMapping.storeMapping(clOrdId, internalOrderId);
            } else {
                sendOrderCancelReject(clOrdId, origClOrdId,
                        CxlRejReason.OTHER,
                        "Modify failed: " + response.getRejectReason(), sessionId);
            }

        } catch (Exception e) {
            System.err.println("[FIX] Error processing Cancel/Replace: " + e.getMessage());
        }
    }

    private void sendExecutionReport(String clOrdId, Order order,
                                     EnhancedOrderBook.OrderResponse response, SessionID sessionId) {
        try {
            double leavesQty = order.getQuantity() - getCumQty(order, response);
            double cumQty = (double) getCumQty(order, response);

            ExecutionReport report = new ExecutionReport(
                    new OrderID(Long.toString(order.getId())),
                    new ExecID(Long.toString(System.nanoTime())),
                    new ExecType(determineExecType(response)),
                    new OrdStatus(determineOrdStatus(response)),
                    new Side(order.getSide() == Order.Side.BUY ? Side.BUY : Side.SELL),
                    new LeavesQty(leavesQty),
                    new CumQty(cumQty),
                    new AvgPx(0.0)
            );

            report.set(new ClOrdID(clOrdId));
            report.set(new Symbol("TEST"));
            report.set(new OrderQty((double) order.getQuantity()));
            report.set(new Price(order.getPrice() / 100.0));
            report.set(new TransactTime(LocalDateTime.now()));

            // Get account from userId
            String account = getAccountFromUserId(order.getUserId());
            report.set(new Account(account));

            if (!response.getTrades().isEmpty()) {
                Trade lastTrade = response.getTrades().get(response.getTrades().size() - 1);
                report.set(new LastQty((double) lastTrade.quantity()));
                report.set(new LastPx(lastTrade.price() / 100.0));
            }

            Session.sendToTarget(report, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error sending ExecutionReport: " + e.getMessage());
        }
    }

    private void sendCancelExecutionReport(String clOrdId, String origClOrdId, SessionID sessionId) {
        try {
            ExecutionReport report = new ExecutionReport(
                    new OrderID(origClOrdId),
                    new ExecID(Long.toString(System.nanoTime())),
                    new ExecType(ExecType.CANCELED),
                    new OrdStatus(OrdStatus.CANCELED),
                    new Side(Side.BUY),
                    new LeavesQty(0.0),
                    new CumQty(0.0),
                    new AvgPx(0.0)
            );

            report.set(new ClOrdID(clOrdId));
            report.set(new OrigClOrdID(origClOrdId));
            report.set(new Symbol("TEST"));
            report.set(new TransactTime(LocalDateTime.now()));

            Session.sendToTarget(report, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error sending Cancel ExecutionReport: " + e.getMessage());
        }
    }

    private void sendReplaceExecutionReport(String clOrdId, String origClOrdId, SessionID sessionId) {
        try {
            ExecutionReport report = new ExecutionReport(
                    new OrderID(clOrdId),
                    new ExecID(Long.toString(System.nanoTime())),
                    new ExecType(ExecType.REPLACED),
                    new OrdStatus(OrdStatus.REPLACED),
                    new Side(Side.BUY),
                    new LeavesQty(0.0),
                    new CumQty(0.0),
                    new AvgPx(0.0)
            );

            report.set(new ClOrdID(clOrdId));
            report.set(new OrigClOrdID(origClOrdId));
            report.set(new Symbol("TEST"));
            report.set(new TransactTime(LocalDateTime.now()));

            Session.sendToTarget(report, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error sending Replace ExecutionReport: " + e.getMessage());
        }
    }

    private void sendOrderCancelReject(String clOrdId, String origClOrdId,
                                       int rejectReason, String text, SessionID sessionId) {
        try {
            OrderCancelReject reject = new OrderCancelReject(
                    new OrderID(origClOrdId),
                    new ClOrdID(clOrdId),
                    new OrigClOrdID(origClOrdId),
                    new OrdStatus(OrdStatus.REJECTED),
                    new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST)
            );

            reject.set(new CxlRejReason(rejectReason));
            reject.set(new Text(text));

            Session.sendToTarget(reject, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error sending OrderCancelReject: " + e.getMessage());
        }
    }

    private void sendBusinessMessageReject(Message message, SessionID sessionId, String reason) {
        try {
            BusinessMessageReject reject = new BusinessMessageReject();
            reject.set(new RefMsgType(message.getHeader().getString(MsgType.FIELD)));
            reject.set(new BusinessRejectReason(BusinessRejectReason.UNSUPPORTED_MESSAGE_TYPE));
            reject.set(new Text(reason));

            Session.sendToTarget(reject, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error sending BusinessMessageReject: " + e.getMessage());
        }
    }

    private char determineExecType(EnhancedOrderBook.OrderResponse response) {
        switch (response.getStatus()) {
            case "FILLED": return ExecType.FILL;
            case "PARTIAL": return ExecType.PARTIAL_FILL;
            case "NEW": return ExecType.NEW;
            case "REJECTED": return ExecType.REJECTED;
            default: return ExecType.NEW;
        }
    }

    private char determineOrdStatus(EnhancedOrderBook.OrderResponse response) {
        switch (response.getStatus()) {
            case "FILLED": return OrdStatus.FILLED;
            case "PARTIAL": return OrdStatus.PARTIALLY_FILLED;
            case "NEW": return OrdStatus.NEW;
            case "REJECTED": return OrdStatus.REJECTED;
            default: return OrdStatus.NEW;
        }
    }

    private long getCumQty(Order order, EnhancedOrderBook.OrderResponse response) {
        return response.getTrades().stream()
                .mapToLong(Trade::quantity)
                .sum();
    }

    private void handleTestRequest(Message message, SessionID sessionId) throws FieldNotFound {
        try {
            TestRequest testRequest = new TestRequest();
            String testReqId = message.getString(TestReqID.FIELD);
            testRequest.set(new TestReqID(testReqId));

            Session.sendToTarget(testRequest, sessionId);

        } catch (Exception e) {
            System.err.println("[FIX] Error handling TestRequest: " + e.getMessage());
        }
    }

    private String convertAccountToUserId(String account) {
        return accountToUserIdMap.computeIfAbsent(account,
                acc -> String.valueOf(Math.abs(acc.hashCode()) & 0x7FFFFFFF));
    }

    private String getAccountFromUserId(String userId) {
        // Reverse lookup
        return accountToUserIdMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(userId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("DEFAULT");
    }

    private static class OrderIdMapping {
        private static final Map<String, Long> clOrdIdToInternalId = new ConcurrentHashMap<>();

        static void storeMapping(String clOrdId, Long internalId) {
            clOrdIdToInternalId.put(clOrdId, internalId);
        }

        static Long getInternalId(String clOrdId) {
            return clOrdIdToInternalId.get(clOrdId);
        }

        static void removeMapping(String clOrdId) {
            clOrdIdToInternalId.remove(clOrdId);
        }
    }
}