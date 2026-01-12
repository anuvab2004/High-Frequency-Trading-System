# **High Frequency Trading System**

A comprehensive high-frequency trading system built with Java, featuring real-time order matching, risk management, FIX protocol integration, and WebSocket-based market data distribution.

## **🎯 Overview**

This system is a production-grade trading engine designed for high-performance order processing. It implements a complete matching engine with risk controls, multiple protocol support (FIX 4.4, custom TCP), and real-time market data distribution via WebSocket.

## **🏗️ Architecture**

          +------------------+
          |   CLIENT LAYER   |
          +--------+---------+
                   |
                   v
          +------------------+
          |   GATEWAY LAYER  |
          |                  |
          | [FIX Gateway]    |
          | [Trading Server] |
          | [WS Server]      |
          +--------+---------+
                   |
                   v
    +-----------------------------+
    |     CORE TRADING ENGINE     |
    |                             |
    |      [ Risk Engine ]        |
    |            ^                |
    |            | (Check)        |
    |            v                |
    |   [ EnhancedOrderBook ]     |
    |            |                |
    |            | (Log)          |
    |            v                |
    |     [ Order Journal ]       |
    |                             |
    +-----------------------------+



## **✨ Key Features**

### **Core Trading Engine**

* **High-Performance Order Matching**: Price-time priority matching algorithm using TreeMap for efficient O(log n) operations.  
* **Order Management**: Support for NEW, CANCEL, and MODIFY operations.  
* **Order Journaling**: Persistent order log for recovery and audit trails.  
* **Zero-GC Optimization**: Object pooling and primitive collections to minimize Garbage Collection pauses.

### **Risk Management**

* **Pre-Trade Validation**: All checks performed in nanoseconds before matching.  
* **Position Limits**: Per-trader position monitoring.  
* **Fat-Finger Protection**: Price deviation (5%) and max quantity checks.  
* **Daily Volume Caps**: Configurable daily trading volume limits.

### **Protocol Support**

* **FIX 4.4 Gateway**: Industry-standard FIX protocol (QuickFIX/J) for institutional order entry.  
* **Custom TCP Server**: High-performance binary protocol for internal low-latency clients.  
* **WebSocket Market Data**: Real-time Level 2 market data distribution to web clients.

## **📊 Performance Benchmarks**

Benchmarked using **JMH (Java Microbenchmark Harness)** on standard hardware.

| Metric | Latency (99th %ile) | Description |
| :---- | :---- | :---- |
| **Tick-to-Trade** | **\~1.12 µs** | Full cycle: Order In \-\> Risk \-\> Match \-\> Execution Report |
| **Order Injection** | **\~0.42 µs** | Time to accept a resting limit order |
| **Throughput** | **1M+ ops/sec** | Sustained message processing rate |

## **📦 Technology Stack**

* **Java 21**: Core programming language (Virtual Threads, ZGC).  
* **QuickFIX/J 2.3.1**: FIX protocol implementation.  
* **Jetty 11**: WebSocket and HTTP server.  
* **Jackson**: High-performance JSON processing.  
* **JMH**: Microbenchmarking framework.  
* **Maven**: Build and dependency management.

## **🚀 Getting Started**

### **Prerequisites**

* Java 21 or higher  
* Maven 3.6+  
* (Optional) Finnhub API key for live market data

### **Installation**

1. **Clone the repository**
   ```bash  
   git clone https://github.com/anuvab2004/High-Frequency-Trading-System.git

2. **Build the project**  
   ```bash
   mvn clean package

3. **Run the trading engine**  
   ```bash
   java -cp target/High_Frequency_Trading_System-1.0-SNAPSHOT.jar org.example.Main
   
## **⚙️ Configuration**

### FIX Gateway (fix-config.cfg)
Defines the session parameters for the QuickFIX/J engine.

Create a file named `fix-config.cfg` in your project root with the following content:

```ini
[DEFAULT]
ConnectionType=acceptor
SocketAcceptPort=9876
SenderCompID=EXCHANGE
TargetCompID=CLIENT
HeartBtInt=30
FileStorePath=store
FileLogPath=log
```

### **Market Data**

To use real-time market data, set your API key as an environment variable:

export FINNHUB\_API\_KEY=your\_api\_key\_here

## **📖 Usage**

### **CLI Interface**

The engine provides an interactive command-line interface for manual control.

```bash
[CMD] > send
Side (BUY/SELL): BUY
User ID: 1001
Price: 150.25
Quantity: 100
```

**Available Commands:**

* view: Show top 5 levels of the order book (Bids/Asks).  
* send: Place a manual Limit Order.  
* cancel: Cancel an order by ID.  
* metrics: Display real-time latency histograms.  
* stress: Trigger internal stress tests.

### **Web Dashboard**

1. Start the WebSocket server:  
   java \-cp target/High\_Frequency\_Trading\_System-1.0-SNAPSHOT.jar org.example.websocket.SimpleWebSocketStarter

2. Open src/main/resources/static/index.html in your browser to visualize the Order Book in real-time.

## **🧪 Testing**

The project includes a comprehensive suite of unit and integration tests.

**Run Unit Tests:**

mvn test

**Run Latency Benchmarks (JMH):**

java \-cp target/High\_Frequency\_Trading\_System-1.0-SNAPSHOT.jar org.example.test.OrderBookBenchmark

## **📁 Project Structure**

High Frequency Trading System/  
├── src/main/java/org/example/  
│   ├── EnhancedOrderBook.java       \# Core matching engine  
│   ├── fix/                         \# FIX Gateway implementation  
│   ├── risk/                        \# Risk management logic  
│   ├── marketdata/                  \# Feed handlers (Finnhub, Sim)  
│   ├── websocket/                   \# Real-time web server  
│   └── test/                        \# JMH Benchmarks & Integration tests  
├── src/main/resources/static/       \# Web Dashboard (JS/HTML)  
├── fix-config.cfg                   \# FIX Configuration  
└── pom.xml                          \# Maven dependencies

## **🐛 Troubleshooting**

**Port Conflicts:** If ports 8080 (TCP), 8081 (WS), or 9876 (FIX) are in use:

* Modify fix-config.cfg for FIX ports.  
* Check TradingServer.java for TCP ports.

**FIX Session Errors:** If you encounter sequence number mismatches during development:
```bash
rm -rf store/* client_store/* log/*
