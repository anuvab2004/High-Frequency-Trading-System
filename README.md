High Frequency Trading SystemA comprehensive high-frequency trading system built with Java, featuring real-time order matching, risk management, FIX protocol integration, and WebSocket-based market data distribution.🎯 OverviewThis system is a production-grade trading engine designed for high-performance order processing. It implements a complete matching engine with risk controls, multiple protocol support (FIX 4.4, custom TCP), and real-time market data distribution via WebSocket.🏗️ Architecture+-------------------------------------------------------------------------+
|                              CLIENT LAYER                               |
|   [FIX Client]          [TCP/Algo Client]          [Web Dashboard]      |
+--------+------------------------+--------------------------+------------+
         |                        |                          |
         v                        v                          v
+-------------------------------------------------------------------------+
|                             GATEWAY LAYER                               |
|  [FIX Gateway :9876]    [Trading Server :8080]    [WS Server :8081]     |
|   (QuickFIX/J)             (Binary Proto)           (Jetty/JSON)        |
+--------+------------------------+--------------------------+------------+
         |                        |                          |
         +------------------------v--------------------------+
                                  |
                                  v
+-------------------------------------------------------------------------+
|                          CORE TRADING ENGINE                            |
|                                                                         |
|   +---------------------+    Risk Check     +-----------------------+   |
|   |   Risk Engine       |<------------------|  EnhancedOrderBook    |   |
|   | (Pre-Trade Limits)  |    Approve/Rej    | (Matching Logic)      |   |
|   +---------------------+                   +-----------+-----------+   |
|                                                         |               |
|                                                         v               |
|                                              +-----------------------+  |
|                                              |    Order Journal      |  |
|                                              |   (Recovery/Audit)    |  |
|                                              +-----------------------+  |
+-------------------------------------------------------------------------+
✨ Key FeaturesCore Trading EngineHigh-Performance Order Matching: Price-time priority matching algorithm using TreeMap for efficient O(log n) operations.Order Management: Support for NEW, CANCEL, and MODIFY operations.Order Journaling: Persistent order log for recovery and audit trails.Zero-GC Optimization: Object pooling and primitive collections to minimize Garbage Collection pauses.Risk ManagementPre-Trade Validation: All checks performed in nanoseconds before matching.Position Limits: Per-trader position monitoring.Fat-Finger Protection: Price deviation (5%) and max quantity checks.Daily Volume Caps: Configurable daily trading volume limits.Protocol SupportFIX 4.4 Gateway: Industry-standard FIX protocol (QuickFIX/J) for institutional order entry.Custom TCP Server: High-performance binary protocol for internal low-latency clients.WebSocket Market Data: Real-time Level 2 market data distribution to web clients.📊 Performance BenchmarksBenchmarked using JMH (Java Microbenchmark Harness) on standard hardware.MetricLatency (99th %ile)DescriptionTick-to-Trade~1.12 µsFull cycle: Order In -> Risk -> Match -> Execution ReportOrder Injection~0.42 µsTime to accept a resting limit orderThroughput1M+ ops/secSustained message processing rate📦 Technology StackJava 21: Core programming language (Virtual Threads, ZGC).QuickFIX/J 2.3.1: FIX protocol implementation.Jetty 11: WebSocket and HTTP server.Jackson: High-performance JSON processing.JMH: Microbenchmarking framework.Maven: Build and dependency management.🚀 Getting StartedPrerequisitesJava 21 or higherMaven 3.6+(Optional) Finnhub API key for live market dataInstallationClone the repositorygit clone [https://github.com/yourusername/hft-system.git](https://github.com/yourusername/hft-system.git)
cd hft-system
Build the projectmvn clean package
Run the trading enginejava -cp target/High_Frequency_Trading_System-1.0-SNAPSHOT.jar org.example.Main
⚙️ ConfigurationFIX Gateway (fix-config.cfg)Defines the session parameters for the QuickFIX/J engine.[DEFAULT]
ConnectionType=acceptor
SocketAcceptPort=9876
SenderCompID=EXCHANGE
TargetCompID=CLIENT
HeartBtInt=30
FileStorePath=store
FileLogPath=log
Market DataTo use real-time market data, set your API key as an environment variable:export FINNHUB_API_KEY=your_api_key_here
📖 UsageCLI InterfaceThe engine provides an interactive command-line interface for manual control.[CMD] > send
Side (BUY/SELL): BUY
User ID: 1001
Price: 150.25
Quantity: 100
Available Commands:view: Show top 5 levels of the order book (Bids/Asks).send: Place a manual Limit Order.cancel: Cancel an order by ID.metrics: Display real-time latency histograms.stress: Trigger internal stress tests.Web DashboardStart the WebSocket server:java -cp target/High_Frequency_Trading_System-1.0-SNAPSHOT.jar org.example.websocket.SimpleWebSocketStarter
Open src/main/resources/static/index.html in your browser to visualize the Order Book in real-time.🧪 TestingThe project includes a comprehensive suite of unit and integration tests.Run Unit Tests:mvn test
Run Latency Benchmarks (JMH):java -cp target/High_Frequency_Trading_System-1.0-SNAPSHOT.jar org.example.test.OrderBookBenchmark
📁 Project StructureHigh Frequency Trading System/
├── src/main/java/org/example/
│   ├── EnhancedOrderBook.java       # Core matching engine
│   ├── fix/                         # FIX Gateway implementation
│   ├── risk/                        # Risk management logic
│   ├── marketdata/                  # Feed handlers (Finnhub, Sim)
│   ├── websocket/                   # Real-time web server
│   └── test/                        # JMH Benchmarks & Integration tests
├── src/main/resources/static/       # Web Dashboard (JS/HTML)
├── fix-config.cfg                   # FIX Configuration
└── pom.xml                          # Maven dependencies
🐛 TroubleshootingPort Conflicts:If ports 8080 (TCP), 8081 (WS), or 9876 (FIX) are in use:Modify fix-config.cfg for FIX ports.Check TradingServer.java for TCP ports.FIX Session Errors:If you encounter sequence number mismatches during development:rm -rf store/* client_store/* log/*
