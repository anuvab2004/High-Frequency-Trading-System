package org.example;

public record Trade(long buyOrderId, long sellOrderId, long price, long quantity) {
}
