package org.example;
import java.io.*;

public class OrderParser {

    public static Order parse(byte[] data, int length) {
        int idx = 0;

        // Parse side (first character: 'B' for Buy, 'S' for Sell)
        char sideChar = (char) data[idx++];
        Order.Side side = sideChar == 'B' ? Order.Side.BUY : Order.Side.SELL;

        // Skip comma
        idx++;

        // Parse user ID
        int userIdStart = idx;
        while (idx < length && data[idx] != ',') {
            idx++;
        }
        String userId = new String(data, userIdStart, idx - userIdStart);
        idx++; // Skip comma

        // Parse price (with possible decimal point)
        long price = 0;
        boolean hasDecimal = false;
        int decimalPlaces = 0;

        while (idx < length && data[idx] != ',') {
            if (data[idx] == '.') {
                hasDecimal = true;
            } else {
                price = price * 10 + (data[idx] - '0');
                if (hasDecimal) {
                    decimalPlaces++;
                }
            }
            idx++;
        }

        // Convert to integer representation (multiply by 100 for 2 decimal places)
        // If already has 2 decimal places, no need to multiply
        if (decimalPlaces == 0) {
            price *= 100;  // Assume whole number, convert to cents
        } else if (decimalPlaces == 1) {
            price *= 10;   // One decimal place, need one more
        }
        // If decimalPlaces == 2, price is already correct

        idx++; // Skip comma

        // Parse quantity
        long quantity = 0;
        while (idx < length && (data[idx] != '\n' && data[idx] != '\r' && data[idx] != ',')) {
            quantity = quantity * 10 + (data[idx] - '0');
            idx++;
        }

        // Create order with unique ID
        long orderId = System.nanoTime();
        return new Order(orderId, userId, side, price, quantity);
    }

    // For even better performance, use a reusable buffer per thread
    private static final ThreadLocal<OrderParser> PARSERS =
            ThreadLocal.withInitial(OrderParser::new);

    private final byte[] reusableBuffer = new byte[256];
    private int position = 0;

    // Use this method in TradingServer instead of the static parse()
    public Order parseWithReuse(InputStream in) throws IOException {
        position = 0;

        // Read until newline
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (position < reusableBuffer.length) {
                reusableBuffer[position++] = (byte) b;
            }
        }

        if (position == 0) return null;

        // Reuse the static parse method with our buffer
        return parse(reusableBuffer, position);
    }
    //GETTER
    public static OrderParser getParser() {
        return PARSERS.get();
    }

}