// Trading Dashboard JavaScript
class TradingDashboard {
    constructor() {
        // WebSocket Configuration
        this.ws = null;
        this.wsUrl = 'ws://localhost:8080/market-data';
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 3000;

        // Connection State
        this.connectionId = null;
        this.connectionStartTime = null;
        this.isConnected = false;
        this.heartbeatInterval = null;
        this.metricsInterval = null;

        // Market Data
        this.selectedSymbol = 'TEST';
        this.marketData = {
            'TEST': { bid: 0, ask: 0, last: 0, volume: 0, timestamp: 0, history: [] },
            'AAPL': { bid: 0, ask: 0, last: 0, volume: 0, timestamp: 0, history: [] },
            'GOOGL': { bid: 0, ask: 0, last: 0, volume: 0, timestamp: 0, history: [] },
            'MSFT': { bid: 0, ask: 0, last: 0, volume: 0, timestamp: 0, history: [] },
            'AMZN': { bid: 0, ask: 0, last: 0, volume: 0, timestamp: 0, history: [] },
            'TSLA': { bid: 0, ask: 0, last: 0, volume: 0, timestamp: 0, history: [] }
        };
        // Add this after initializing marketData
        this.simulatePriceChanges();

        // Performance Metrics
        this.lastMetricsUpdate = null;
        this.metrics = {
            connections: 0,
            totalOrders: 0,
            totalTrades: 0,
            avgLatency: 0
        };

        // Statistics
        this.messageCount = 0;
        this.totalMessages = 0;
        this.messageTimestamps = [];
        this.tradeHistory = [];
        this.systemMessages = [];

        // Chart
        this.priceChart = null;
        this.chartData = [];
        this.chartTimeframe = '1s';

        // DOM Elements
        this.initializeElements();

        // Initialize Dashboard
        this.initializeDashboard();
    }

    initializeElements() {
        // Connection Elements
        this.statusDot = document.getElementById('statusDot');
        this.connectionStatus = document.getElementById('connectionStatus');
        this.connectionIdEl = document.getElementById('connectionId');
        this.connectedSince = document.getElementById('connectedSince');
        this.totalClients = document.getElementById('totalClients');
        this.messageRate = document.getElementById('messageRate');
        this.latency = document.getElementById('latency');

        // Performance Metrics Elements
        this.metricConnections = document.getElementById('metricConnections');
        this.metricTotalOrders = document.getElementById('metricTotalOrders');
        this.metricTotalTrades = document.getElementById('metricTotalTrades');
        this.metricAvgLatency = document.getElementById('metricAvgLatency');
        this.lastMetricsUpdateEl = document.getElementById('lastMetricsUpdate');

        // Control Buttons
        this.connectBtn = document.getElementById('connectBtn');
        this.disconnectBtn = document.getElementById('disconnectBtn');
        this.reconnectBtn = document.getElementById('reconnectBtn');
        this.getMetricsBtn = document.getElementById('getMetricsBtn');

        // Symbol Elements
        this.selectedSymbolEl = document.getElementById('selectedSymbol');
        this.currentPrice = document.getElementById('currentPrice');
        this.currentBid = document.getElementById('currentBid');
        this.currentAsk = document.getElementById('currentAsk');
        this.detailedSpread = document.getElementById('detailedSpread');
        this.currentVolume = document.getElementById('currentVolume');
        this.priceChange = document.getElementById('priceChange');
        this.lastUpdateTime = document.getElementById('lastUpdateTime');

        // Statistics
        this.dayHigh = document.getElementById('dayHigh');
        this.dayLow = document.getElementById('dayLow');
        this.volatility = document.getElementById('volatility');

        // Order Book
        this.bidsList = document.getElementById('bidsList');
        this.asksList = document.getElementById('asksList');
        this.currentSpread = document.getElementById('currentSpread');
        this.midPrice = document.getElementById('midPrice');

        // Messages
        this.messageCountEl = document.getElementById('messageCount');
        this.messagesLog = document.getElementById('messagesLog');
        this.messageInput = document.getElementById('messageInput');
        this.sendMessageBtn = document.getElementById('sendMessageBtn');

        // Trade Log
        this.tradeEntries = document.getElementById('tradeEntries');

        // Performance
        this.uptime = document.getElementById('uptime');
        this.totalMessagesEl = document.getElementById('totalMessages');
        this.dataRate = document.getElementById('dataRate');
        this.currentTime = document.getElementById('currentTime');
        this.systemStatus = document.getElementById('systemStatus');

        // Symbol Grid
        this.symbolOptions = document.querySelectorAll('.symbol-option');

        // Timeframe Selector
        this.timeframeBtns = document.querySelectorAll('.timeframe-btn');
    }

    initializeDashboard() {
        // Set current time
        this.updateCurrentTime();
        setInterval(() => this.updateCurrentTime(), 1000);

        // Initialize chart
        this.initializeChart();

        // Initialize mock order book
        this.initializeOrderBook();

        // Initialize trade log
        this.initializeTradeLog();

        // Bind event listeners
        this.bindEvents();

        //Symbol Hover
        this.setupSymbolHover();

        // Try to auto-connect
        setTimeout(() => this.connect(), 1000);
    }

    setupSymbolHover() {
        document.addEventListener('mouseover', (e) => {
            const symbolOption = e.target.closest('.symbol-option');
            if (symbolOption) {
                const removeBtn = symbolOption.querySelector('.symbol-remove');
                if (removeBtn) removeBtn.style.display = 'block';
            }
        });

        document.addEventListener('mouseout', (e) => {
            const symbolOption = e.target.closest('.symbol-option');
            if (symbolOption) {
                const removeBtn = symbolOption.querySelector('.symbol-remove');
                if (removeBtn) removeBtn.style.display = 'none';
            }
        });
    }

    bindEvents() {
        // Connection Controls
        this.connectBtn.addEventListener('click', () => this.connect());
        this.disconnectBtn.addEventListener('click', () => this.disconnect());
        this.reconnectBtn.addEventListener('click', () => this.reconnect());
        this.getMetricsBtn.addEventListener('click', () => this.requestMetrics());

        // Symbol Selection
        document.addEventListener('click', (e) => {
            const symbolOption = e.target.closest('.symbol-option');
            if (symbolOption && !e.target.closest('.symbol-remove')) {
                this.selectSymbol(symbolOption);
            }
        });

        // Timeframe Selection
        this.timeframeBtns.forEach(btn => {
            btn.addEventListener('click', (e) => this.selectTimeframe(e.target));
        });

        // Message Input
        this.sendMessageBtn.addEventListener('click', () => this.sendMessage());
        this.messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.sendMessage();
        });

        // Order Book Controls
        document.getElementById('refreshOrderBook').addEventListener('click', () => this.refreshOrderBook());
        document.getElementById('clearTrades').addEventListener('click', () => this.clearTradeLog());

        // Add Symbol
        document.getElementById('addSymbolBtn').addEventListener('click', () => this.addSymbol());

        // Add remove symbol functionality
        document.addEventListener('click', (e) => {
            if (e.target.closest('.symbol-remove')) {
               const symbol = e.target.closest('.symbol-remove').dataset.symbol;
               this.removeSymbol(symbol);
            }
        });


        // System Commands
        document.getElementById('symbolSearch').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.addSymbol();
        });
    }
    //Simulate price changes
    simulatePriceChanges() {
        // Simulate price changes for all symbols every 5 seconds
        setInterval(() => {
            if (!this.isConnected) return;

            Object.keys(this.marketData).forEach(symbol => {
                if (symbol !== 'TEST') {
                    const data = this.marketData[symbol];
                    if (data.last === 0) data.last = 100; // Initialize if zero

                    // Random price movement
                    const change = (Math.random() - 0.5) * 2; // -1 to +1
                    const newPrice = data.last + change;
                    const newBid = newPrice - 0.05;
                    const newAsk = newPrice + 0.05;

                    // Update market data
                    data.bid = newBid;
                    data.ask = newAsk;
                    data.last = newPrice;
                    data.volume = Math.floor(Math.random() * 10000) + 1000;
                    data.timestamp = Date.now();

                    // Add to history
                    data.history.push({
                        time: Date.now(),
                        price: newPrice,
                        bid: newBid,
                        ask: newAsk
                    });

                    // Keep history limited
                    if (data.history.length > 1000) {
                        data.history.shift();
                    }

                    // Update UI if this symbol is selected
                    if (symbol === this.selectedSymbol) {
                        this.updateSymbolDisplay(symbol, data.last - change);
                        this.updateChart();
                    }

                    // Update symbol price in grid
                    this.updateSymbolPrice(symbol, newPrice);
                }
            });
        }, 2000); // Update every 2 seconds
    }

    // WebSocket Methods
    connect() {
        if (this.isConnected) return;

        this.updateConnectionStatus('Connecting...', 'connecting');
        this.logMessage('Connecting to WebSocket server...', 'info');

        try {
            this.ws = new WebSocket(this.wsUrl);

            this.ws.onopen = () => this.onWebSocketOpen();
            this.ws.onmessage = (event) => this.onWebSocketMessage(event);
            this.ws.onclose = () => this.onWebSocketClose();
            this.ws.onerror = (error) => this.onWebSocketError(error);

        } catch (error) {
            this.logMessage(`Connection error: ${error.message}`, 'error');
            this.scheduleReconnect();
        }
    }

    onWebSocketOpen() {
        this.isConnected = true;
        this.reconnectAttempts = 0;
        this.connectionStartTime = Date.now();
        this.connectionId = this.generateConnectionId();

        this.updateConnectionStatus('Connected', 'connected');
        this.logMessage('WebSocket connection established', 'success');

        // Update UI
        this.connectionIdEl.textContent = this.connectionId;
        this.connectedSince.textContent = this.formatTime(new Date(this.connectionStartTime));
        this.connectBtn.disabled = true;
        this.disconnectBtn.disabled = false;

        // Start heartbeat
        this.startHeartbeat();

        // Start metrics polling
        this.startMetricsPolling();

        // Request initial data
        this.sendWebSocketMessage({ type: 'subscribe', symbol: this.selectedSymbol });

        // Request initial metrics
        setTimeout(() => this.requestMetrics(), 1000);
    }

    onWebSocketMessage(event) {
        this.messageCount++;
        this.totalMessages++;
        this.messageTimestamps.push(Date.now());

        // Keep only last 60 timestamps for rate calculation
        if (this.messageTimestamps.length > 60) {
            this.messageTimestamps.shift();
        }

        try {
            const data = JSON.parse(event.data);

            // Handle different message types
            if (data.type === 'metrics') {
                this.handleMetricsData(data);
            } else if (data.type === 'market_data') {
                this.processMarketData(data);
            } else if (data.type === 'connection') {
                this.logMessage(data.message, 'success');
            } else {
                this.logMessage(`Unknown message type: ${data.type}`, 'warning');
            }
        } catch (error) {
            this.logMessage(`Error parsing message: ${error.message}`, 'error');
        }

        // Update statistics
        this.updateStatistics();
    }

    handleMetricsData(data) {
        // Update metrics
        this.metrics.connections = data.connections || 0;
        this.metrics.totalOrders = data.totalOrders || 0;
        this.metrics.totalTrades = data.totalTrades || 0;
        this.metrics.avgLatency = data.avgLatency || 0;
        this.lastMetricsUpdate = Date.now();

        // Update UI
        this.updateMetricsDisplay();

        // Update total clients display
        this.totalClients.textContent = this.metrics.connections;

        // Log for debugging
        this.logMessage(`Metrics updated: ${this.metrics.connections} connections`, 'info');
    }

    updateMetricsDisplay() {
        // Animate metric updates
        const animateUpdate = (elementId, newValue) => {
            const element = document.getElementById(elementId);
            const oldValue = element.textContent;

            if (element && oldValue !== String(newValue)) {
                element.textContent = newValue;
                element.parentElement.classList.add('updated');
                setTimeout(() => {
                    element.parentElement.classList.remove('updated');
                }, 500);
            }
        };

        animateUpdate('metricConnections', this.metrics.connections);
        animateUpdate('metricTotalOrders', this.metrics.totalOrders.toLocaleString());
        animateUpdate('metricTotalTrades', this.metrics.totalTrades.toLocaleString());
        animateUpdate('metricAvgLatency', `${this.metrics.avgLatency.toFixed(2)} ms`);

        // Update last update time
        if (this.lastMetricsUpdate) {
            const timeStr = this.formatTime(new Date(this.lastMetricsUpdate));
            this.lastMetricsUpdateEl.textContent = `Last: ${timeStr}`;
        }
    }

    onWebSocketClose() {
        this.isConnected = false;
        this.updateConnectionStatus('Disconnected', 'disconnected');
        this.logMessage('WebSocket connection closed', 'warning');

        // Update UI
        this.connectBtn.disabled = false;
        this.disconnectBtn.disabled = true;

        // Stop intervals
        this.stopHeartbeat();
        this.stopMetricsPolling();

        // Schedule reconnect if not manually disconnected
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.scheduleReconnect();
        }
    }

    onWebSocketError(error) {
        this.logMessage(`WebSocket error: ${error.message || 'Unknown error'}`, 'error');
    }

    disconnect() {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
        this.isConnected = false;
        this.reconnectAttempts = this.maxReconnectAttempts; // Prevent auto-reconnect
        this.updateConnectionStatus('Disconnected', 'disconnected');
        this.logMessage('Manually disconnected from server', 'info');
    }

    reconnect() {
        this.reconnectAttempts = 0;
        this.connect();
    }

    scheduleReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            this.logMessage('Max reconnection attempts reached', 'error');
            return;
        }

        this.reconnectAttempts++;
        const delay = this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts - 1);

        this.logMessage(`Reconnecting in ${delay/1000} seconds (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`, 'info');

        setTimeout(() => {
            if (!this.isConnected) {
                this.connect();
            }
        }, delay);
    }

    // Metrics Methods
    startMetricsPolling() {
        this.metricsInterval = setInterval(() => {
            if (this.isConnected) {
                this.requestMetrics();
            }
        }, 5000); // Every 5 seconds
    }

    stopMetricsPolling() {
        if (this.metricsInterval) {
            clearInterval(this.metricsInterval);
            this.metricsInterval = null;
        }
    }

    requestMetrics() {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            const metricsRequest = {
                type: 'get_metrics',
                timestamp: Date.now()
            };
            this.ws.send(JSON.stringify(metricsRequest));
            this.logMessage('Requesting performance metrics...', 'info');
        } else {
            this.logMessage('Cannot request metrics - not connected', 'warning');
        }
    }

    // Data Processing Methods
    processMarketData(data) {
        if (!data || !data.symbol) return;

        const symbol = data.symbol;
        const now = Date.now();

        // Update market data
        if (!this.marketData[symbol]) {
            this.marketData[symbol] = {
                bid: 0,
                ask: 0,
                last: 0,
                volume: 0,
                timestamp: 0,
                history: [],
                dayHigh: 0,
                dayLow: Infinity
            };
        }

        const previousPrice = this.marketData[symbol].last;
        const newPrice = data.last || 0;

        // Update price history
        this.marketData[symbol].history.push({
            time: now,
            price: newPrice,
            bid: data.bid,
            ask: data.ask
        });

        // Keep only last 1000 data points
        if (this.marketData[symbol].history.length > 1000) {
            this.marketData[symbol].history.shift();
        }

        // Update market data
        this.marketData[symbol].bid = data.bid || this.marketData[symbol].bid;
        this.marketData[symbol].ask = data.ask || this.marketData[symbol].ask;
        this.marketData[symbol].last = newPrice;
        this.marketData[symbol].volume = data.volume || this.marketData[symbol].volume;
        this.marketData[symbol].timestamp = data.timestamp || now;

        // Update day high/low
        if (newPrice > this.marketData[symbol].dayHigh) {
            this.marketData[symbol].dayHigh = newPrice;
        }
        if (newPrice < this.marketData[symbol].dayLow) {
            this.marketData[symbol].dayLow = newPrice;
        }

        // Update UI if this is the selected symbol
        if (symbol === this.selectedSymbol) {
            this.updateSymbolDisplay(symbol, previousPrice);
            this.updateChart();
            this.updateOrderBook();

            // Add to trade log if price changed significantly
            if (Math.abs(newPrice - previousPrice) > 0.01) {
                this.addTradeLog(symbol, newPrice, data.volume || 100);
            }
        }

        // Update symbol price in grid
        this.updateSymbolPrice(symbol, newPrice);
    }

    updateSymbolDisplay(symbol, previousPrice) {
        const data = this.marketData[symbol];
        if (!data) {
            console.error(`No data for symbol: ${symbol}`);
            return;
        }

        const currentPrice = data.last || 0;
        const bid = data.bid || 0;
        const ask = data.ask || 0;
        const spread = ask - bid;
        const spreadPercent = currentPrice > 0 ? (spread / currentPrice * 100).toFixed(2) : "0.00";

        // Safely update main display
        this.currentPrice.textContent = `$${currentPrice.toFixed(2)}`;
        this.currentBid.textContent = `$${bid.toFixed(2)}`;
        this.currentAsk.textContent = `$${ask.toFixed(2)}`;
        this.detailedSpread.textContent = `$${spread.toFixed(2)} (${spreadPercent}%)`;
        this.currentVolume.textContent = this.formatNumber(data.volume || 0);

        // Safely format timestamp
        if (data.timestamp) {
            this.lastUpdateTime.textContent = this.formatTime(new Date(data.timestamp));
        } else {
            this.lastUpdateTime.textContent = '--:--:--';
        }

        // Update price change safely
        if (previousPrice > 0 && currentPrice > 0) {
            const change = currentPrice - previousPrice;
            const changePercent = (change / previousPrice * 100).toFixed(2);

            this.priceChange.textContent = `${change >= 0 ? '+' : ''}${changePercent}%`;
            this.priceChange.classList.toggle('negative', change < 0);

            // Add price animation
            this.currentPrice.classList.remove('price-up', 'price-down');
            void this.currentPrice.offsetWidth; // Trigger reflow
            this.currentPrice.classList.add(change >= 0 ? 'price-up' : 'price-down');
        }

        // Update statistics safely
        this.dayHigh.textContent = `$${(data.dayHigh || 0).toFixed(2)}`;
        this.dayLow.textContent = `$${(data.dayLow || 0).toFixed(2)}`;

        // Calculate volatility safely
        if (data.history && data.history.length >= 20) {
            const recentPrices = data.history.slice(-20).map(h => h.price || 0);
            const avg = recentPrices.reduce((a, b) => a + b, 0) / recentPrices.length;
            const variance = recentPrices.reduce((a, b) => a + Math.pow(b - avg, 2), 0) / recentPrices.length;
            const volatility = avg > 0 ? (Math.sqrt(variance) / avg * 100) : 0;
            this.volatility.textContent = `${volatility.toFixed(2)}%`;
        } else {
            this.volatility.textContent = '0.00%';
        }
    }

    updateSymbolPrice(symbol, price) {
        const priceElement = document.getElementById(`price-${symbol}`);
        if (priceElement) {
            priceElement.textContent = `$${price.toFixed(2)}`;

            // Add animation for price changes
            priceElement.classList.remove('price-up', 'price-down');
            void priceElement.offsetWidth;

            const previousPrice = parseFloat(priceElement.dataset.lastPrice) || 0;
            if (previousPrice > 0 && price !== previousPrice) {
                priceElement.classList.add(price > previousPrice ? 'price-up' : 'price-down');
            }

            priceElement.dataset.lastPrice = price;
        }
    }

    // Chart Methods
    initializeChart() {
        const ctx = document.getElementById('priceChart').getContext('2d');

        this.priceChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'Price',
                    data: this.chartData,
                    borderColor: '#3b82f6',
                    backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false,
                        callbacks: {
                            label: (context) => `$${context.parsed.y.toFixed(2)}`
                        }
                    }
                },
                scales: {
                    x: {
                        display: true,
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        },
                        ticks: {
                            color: '#94a3b8'
                        }
                    },
                    y: {
                        display: true,
                        grid: {
                            color: 'rgba(255, 255, 255, 0.1)'
                        },
                        ticks: {
                            color: '#94a3b8',
                            callback: (value) => `$${value.toFixed(2)}`
                        }
                    }
                },
                interaction: {
                    intersect: false,
                    mode: 'nearest'
                }
            }
        });
    }

    updateChart() {
        const data = this.marketData[this.selectedSymbol];
        if (!data || data.history.length === 0) return;

        // Filter data based on timeframe
        const now = Date.now();
        let filteredData = [];

        switch (this.chartTimeframe) {
            case '1s':
                filteredData = data.history.filter(h => now - h.time <= 60000); // Last minute
                break;
            case '5s':
                filteredData = data.history.filter((h, i) => i % 5 === 0 && now - h.time <= 300000); // Last 5 minutes
                break;
            case '30s':
                filteredData = data.history.filter((h, i) => i % 30 === 0 && now - h.time <= 1800000); // Last 30 minutes
                break;
            case '1m':
                filteredData = data.history.filter((h, i) => i % 60 === 0 && now - h.time <= 3600000); // Last hour
                break;
        }

        // Prepare chart data
        this.chartData = filteredData.map(h => ({
            x: new Date(h.time),
            y: h.price
        }));

        // Update chart
        this.priceChart.data.labels = filteredData.map(h =>
            new Date(h.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        );
        this.priceChart.data.datasets[0].data = this.chartData;
        this.priceChart.update('none');
    }

    // Order Book Methods
    initializeOrderBook() {
        // Generate mock order book data
        this.generateOrderBookData();
    }

    generateOrderBookData() {
        const data = this.marketData[this.selectedSymbol];
        if (!data) return;

        const midPrice = data.last;
        const spread = data.ask - data.bid;

        // Generate bids (prices below current price)
        const bids = [];
        for (let i = 10; i >= 1; i--) {
            const price = midPrice - (spread * i * 0.1);
            const size = Math.random() * 100 + 50;
            bids.push({ price, size, total: price * size });
        }

        // Generate asks (prices above current price)
        const asks = [];
        for (let i = 1; i <= 10; i++) {
            const price = midPrice + (spread * i * 0.1);
            const size = Math.random() * 100 + 50;
            asks.push({ price, size, total: price * size });
        }

        // Update UI
        this.updateOrderBookUI(bids, asks, spread, midPrice);
    }

    updateOrderBook() {
        this.generateOrderBookData();
    }

    updateOrderBookUI(bids, asks, spread, midPrice) {
        // Clear current lists
        this.bidsList.innerHTML = '';
        this.asksList.innerHTML = '';

        // Add bids
        bids.forEach(order => {
            const row = document.createElement('div');
            row.className = 'orderbook-row bid-row';
            row.innerHTML = `
                <div class="orderbook-side">BID</div>
                <div class="orderbook-price">$${order.price.toFixed(2)}</div>
                <div class="orderbook-size">${this.formatNumber(order.size)}</div>
                <div class="orderbook-total">${this.formatNumber(order.total)}</div>
            `;
            this.bidsList.appendChild(row);
        });

        // Add asks
        asks.forEach(order => {
            const row = document.createElement('div');
            row.className = 'orderbook-row ask-row';
            row.innerHTML = `
                <div class="orderbook-side">ASK</div>
                <div class="orderbook-price">$${order.price.toFixed(2)}</div>
                <div class="orderbook-size">${this.formatNumber(order.size)}</div>
                <div class="orderbook-total">${this.formatNumber(order.total)}</div>
            `;
            this.asksList.appendChild(row);
        });

        // Update spread and mid price
        this.currentSpread.textContent = `Spread: $${spread.toFixed(2)}`;
        this.midPrice.textContent = `Mid: $${midPrice.toFixed(2)}`;
    }

    refreshOrderBook() {
        this.generateOrderBookData();
        this.logMessage('Order book refreshed', 'info');
    }

    // Trade Log Methods
    initializeTradeLog() {
        // Add some sample trades
        for (let i = 0; i < 5; i++) {
            const time = new Date(Date.now() - (i * 60000));
            const side = Math.random() > 0.5 ? 'BUY' : 'SELL';
            const price = 100 + (Math.random() * 10 - 5);
            const size = Math.floor(Math.random() * 1000) + 100;

            this.addTradeLogEntry(time, side, price, size);
        }
    }

    addTradeLog(symbol, price, volume) {
        const time = new Date();
        const side = price > this.marketData[symbol].last ? 'BUY' : 'SELL';
        this.addTradeLogEntry(time, side, price, volume);
    }

    addTradeLogEntry(time, side, price, size) {
        const value = price * size;

        const entry = document.createElement('div');
        entry.className = `trade-log-entry ${side.toLowerCase()}`;
        entry.innerHTML = `
            <div class="trade-time">${time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
            <div class="trade-side">${side}</div>
            <div class="trade-price">$${price.toFixed(2)}</div>
            <div class="trade-size">${this.formatNumber(size)}</div>
            <div class="trade-value">${this.formatNumber(value)}</div>
        `;

        // Add to top
        this.tradeEntries.insertBefore(entry, this.tradeEntries.firstChild);

        // Keep only last 20 trades
        while (this.tradeEntries.children.length > 20) {
            this.tradeEntries.removeChild(this.tradeEntries.lastChild);
        }

        // Store in history
        this.tradeHistory.unshift({ time, side, price, size, value });
        if (this.tradeHistory.length > 100) {
            this.tradeHistory.pop();
        }
    }

    clearTradeLog() {
        this.tradeEntries.innerHTML = '';
        this.tradeHistory = [];
        this.logMessage('Trade log cleared', 'info');
    }

    // UI Update Methods
    updateConnectionStatus(status, state) {
        this.connectionStatus.textContent = status;
        this.statusDot.className = 'status-dot';
        this.statusDot.classList.add(state);
    }

    updateStatistics() {
        // Calculate message rate (messages per second)
        const now = Date.now();
        const recentMessages = this.messageTimestamps.filter(ts => now - ts <= 1000);
        const messagesPerSecond = recentMessages.length;

        // Update rate display
        this.messageRate.textContent = `${messagesPerSecond}/sec`;

        // Update total messages
        this.totalMessagesEl.textContent = this.totalMessages.toLocaleString();

        // Update data rate (estimated)
        const dataRateKB = (this.totalMessages * 100) / 1024; // Assume 100 bytes per message
        this.dataRate.textContent = `${dataRateKB.toFixed(1)} KB/s`;

        // Update uptime
        if (this.connectionStartTime) {
            const uptimeSeconds = Math.floor((Date.now() - this.connectionStartTime) / 1000);
            this.uptime.textContent = this.formatDuration(uptimeSeconds);
        }

        // Update latency (mock - would come from server metrics)
        if (this.metrics.avgLatency > 0) {
            this.latency.textContent = `${this.metrics.avgLatency.toFixed(1)} ms`;
        }
    }

    updateCurrentTime() {
        const now = new Date();
        this.currentTime.textContent = now.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    // Event Handlers
    selectSymbol(element) {
        const symbol = element.dataset.symbol;
        this.selectedSymbol = symbol;

        // Update UI - Use the updated NodeList
        this.symbolOptions = document.querySelectorAll('.symbol-option');
        this.symbolOptions.forEach(opt => opt.classList.remove('active'));
        element.classList.add('active');

        this.selectedSymbolEl.textContent = symbol;

        // Update display with new symbol data
        if (this.marketData[symbol]) {
            const previousPrice = this.marketData[symbol].last || 0;
            this.updateSymbolDisplay(symbol, previousPrice);
            this.updateChart();
            this.updateOrderBook();
        }

        // Request subscription if connected
        if (this.isConnected) {
            this.sendWebSocketMessage({ type: 'subscribe', symbol });
        }

        this.logMessage(`Selected symbol: ${symbol}`, 'info');
    }

    selectTimeframe(element) {
        this.timeframeBtns.forEach(btn => btn.classList.remove('active'));
        element.classList.add('active');

        this.chartTimeframe = element.dataset.timeframe;
        this.updateChart();

        this.logMessage(`Timeframe changed to ${this.chartTimeframe}`, 'info');
    }

    addSymbol() {
        const input = document.getElementById('symbolSearch');
        const symbol = input.value.trim().toUpperCase();

        if (!symbol) {
            this.logMessage('Please enter a symbol', 'warning');
            return;
        }

        if (this.marketData[symbol]) {
            this.logMessage(`Symbol ${symbol} already exists`, 'warning');
            return;
        }

        // Add to market data
        this.marketData[symbol] = {
            bid: 0,
            ask: 0,
            last: 100,
            volume: 0,
            timestamp: 0,
            history: [],
            dayHigh: 100,
            dayLow: 100
        };

        // Add to symbol grid
        this.addSymbolToGrid(symbol);

        // Clear input
        input.value = '';

        this.logMessage(`Added symbol: ${symbol}`, 'success');
    }

    addSymbolToGrid(symbol) {
        const symbolGrid = document.querySelector('.symbol-grid');
        const symbolOption = document.createElement('div');
        symbolOption.className = 'symbol-option';
        symbolOption.dataset.symbol = symbol;
        symbolOption.innerHTML = `
            <span class="symbol-name">${symbol}</span>
            <span class="symbol-price" id="price-${symbol}">$100.00</span>
            <button class="symbol-remove" data-symbol="${symbol}" title="Remove symbol">
                <i class="fas fa-times"></i>
            </button>
        `;

        // Update symbolOptions NodeList
        this.symbolOptions = document.querySelectorAll('.symbol-option');

        symbolGrid.appendChild(symbolOption);
    }
    //remove symbol
    removeSymbol(symbol) {
        // Don't remove TEST symbol
        if (symbol === 'TEST') {
            this.logMessage('Cannot remove TEST symbol', 'warning');
            return;
        }

        // Don't remove if it's the selected symbol
        if (symbol === this.selectedSymbol) {
            this.logMessage(`Cannot remove ${symbol} - it's currently selected`, 'warning');
            return;
        }

        // Remove from market data
        delete this.marketData[symbol];

        // Remove from UI
        const symbolOption = document.querySelector(`.symbol-option[data-symbol="${symbol}"]`);
        if (symbolOption) {
            symbolOption.remove();
        }

        // Remove price element
        const priceElement = document.getElementById(`price-${symbol}`);
        if (priceElement) priceElement.remove();

        // Update symbolOptions NodeList after removal
        this.symbolOptions = document.querySelectorAll('.symbol-option');

        this.logMessage(`Removed symbol: ${symbol}`, 'success');
    }

    sendMessage() {
        const message = this.messageInput.value.trim();
        if (!message) return;

        // Handle commands
        if (message.startsWith('/')) {
            this.handleCommand(message);
        } else {
            // Send via WebSocket if connected
            if (this.isConnected) {
                this.sendWebSocketMessage({ type: 'chat', message });
                this.logMessage(`You: ${message}`, 'info');
            } else {
                this.logMessage('Cannot send message - not connected', 'error');
            }
        }

        this.messageInput.value = '';
    }

    handleCommand(command) {
        const cmd = command.toLowerCase();

        switch (cmd) {
            case '/help':
                this.logMessage('Available commands:', 'info');
                this.logMessage('/help - Show this help', 'info');
                this.logMessage('/clear - Clear messages', 'info');
                this.logMessage('/stats - Show statistics', 'info');
                this.logMessage('/ping - Test latency', 'info');
                this.logMessage('/symbols - List available symbols', 'info');
                this.logMessage('/metrics - Get performance metrics', 'info');
                break;

            case '/clear':
                this.systemMessages = [];
                this.messagesLog.innerHTML = '';
                this.logMessage('Messages cleared', 'success');
                break;

            case '/stats':
                this.logMessage(`Total Messages: ${this.totalMessages}`, 'info');
                this.logMessage(`Connected: ${this.isConnected ? 'Yes' : 'No'}`, 'info');
                this.logMessage(`Selected Symbol: ${this.selectedSymbol}`, 'info');
                this.logMessage(`Uptime: ${this.uptime.textContent}`, 'info');
                this.logMessage(`Active Connections: ${this.metrics.connections}`, 'info');
                break;

            case '/ping':
                if (this.isConnected) {
                    const start = Date.now();
                    this.sendWebSocketMessage({ type: 'ping' });
                    // Note: Would need server to respond with pong
                    this.logMessage('Ping sent (pong response not implemented)', 'info');
                } else {
                    this.logMessage('Not connected', 'error');
                }
                break;

            case '/symbols':
                const symbols = Object.keys(this.marketData).join(', ');
                this.logMessage(`Available symbols: ${symbols}`, 'info');
                break;

            case '/metrics':
                this.requestMetrics();
                this.logMessage('Requesting performance metrics...', 'info');
                break;

            default:
                this.logMessage(`Unknown command: ${command}`, 'error');
                this.logMessage('Type /help for available commands', 'info');
        }
    }

    // WebSocket Communication
    sendWebSocketMessage(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        }
    }

    startHeartbeat() {
        this.heartbeatInterval = setInterval(() => {
            if (this.isConnected) {
                this.sendWebSocketMessage({ type: 'heartbeat', timestamp: Date.now() });
            }
        }, 30000); // Every 30 seconds
    }

    stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }

    // Utility Methods
    logMessage(text, type = 'info') {
        const time = new Date();
        const timeStr = time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

        const messageEntry = document.createElement('div');
        messageEntry.className = 'message-entry';
        messageEntry.innerHTML = `
            <span class="message-time">[${timeStr}]</span>
            <span class="message-text ${type}">${text}</span>
        `;

        // Add to messages log
        this.messagesLog.appendChild(messageEntry);

        // Scroll to bottom
        this.messagesLog.scrollTop = this.messagesLog.scrollHeight;

        // Store message
        this.systemMessages.push({ time, text, type });
        if (this.systemMessages.length > 100) {
            this.systemMessages.shift();
        }

        // Update message count
        this.messageCountEl.textContent = this.systemMessages.length;
    }

    generateConnectionId() {
        return 'CONN-' + Math.random().toString(36).substr(2, 9).toUpperCase();
    }

    formatTime(date) {
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }

    formatDuration(seconds) {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const secs = seconds % 60;

        return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }

    formatNumber(num) {
        if (!num && num !== 0) return '0';

        const n = Number(num);
        if (isNaN(n)) return '0';

        if (n >= 1000000) {
            return (n / 1000000).toFixed(1) + 'M';
        }
        if (n >= 1000) {
            return (n / 1000).toFixed(1) + 'K';
        }
        return n.toFixed(0);
    }
}

// Initialize dashboard when page loads
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new TradingDashboard();
});