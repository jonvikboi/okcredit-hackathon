<script>
  import { onMount } from 'svelte';
  import productsData from '$lib/products.json';

  // State variables (Svelte 5 runes)
  let products = $state(productsData);
  let gold24k = $state(15485); // live rate (₹/g)
  let silver = $state(266.16); // live rate (₹/g)
  let connectionStatus = $state('connecting'); // 'live' | 'polling' | 'connecting'
  let timestamp = $state('');
  
  // Owner Override controls
  let isOverride = $state(false);
  let customGold24k = $state(15500); // custom override gold rate
  let customSilver = $state(267.00); // custom override silver rate

  // Search & Filter state
  let searchQuery = $state('');
  let activeCategory = $state('All');

  // Walk-in Calculator state
  let calcWeight = $state(10);
  let calcPurity = $state('22K');
  let calcMakingCharge = $state(12); // %
  let calcFixedValue = $state(0);

  // Recent CSV Audit logs
  let auditLogs = $state([]);

  // Detailed Modal state
  let selectedProduct = $state(null);
  let showModal = $state(false);

  // Active rates derived based on override toggle (Svelte 5 runes)
  let activeGold24k = $derived(isOverride ? customGold24k : gold24k);
  let activeSilver = $derived(isOverride ? customSilver : silver);
  let activeGold22k = $derived(Math.round(activeGold24k * (22 / 24)));
  let activeGold18k = $derived(Math.round(activeGold24k * (18 / 24)));

  // Derived inventory valuations (Svelte 5 runes)
  let computedProducts = $derived(
    products.map(p => {
      let rate = activeGold24k;
      if (p.purity === '22K') rate = activeGold22k;
      else if (p.purity === '18K') rate = activeGold18k;

      const metalValue = p.weight * rate;
      const makingCharges = metalValue * p.makingCharge;
      const fixedValue = p.fixedValue || 0;
      const subtotal = metalValue + makingCharges + fixedValue;
      const gst = subtotal * 0.03;
      const total = subtotal + gst;

      return {
        ...p,
        ratePerGram: rate,
        metalValue: Math.round(metalValue),
        makingCharges: Math.round(makingCharges),
        fixedValue,
        subtotal: Math.round(subtotal),
        gst: Math.round(gst),
        totalPrice: Math.round(total)
      };
    })
  );

  // Active categories in products
  const categories = ['All', 'Necklace', 'Ring', 'Bracelet', 'Earrings', 'Watch'];

  // Filtered products list based on search and category
  let filteredProducts = $derived(
    computedProducts.filter(p => {
      const matchesCategory = activeCategory === 'All' || p.category === activeCategory;
      const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
                            p.id.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    })
  );

  // Total Stock Weight (Gold weight in inventory)
  let totalGoldWeight = $derived(
    products.filter(p => p.category !== 'Watch').reduce((acc, p) => acc + p.weight, 0)
  );

  // Total current stock valuation (based on active rates)
  let totalStockValuation = $derived(
    computedProducts.reduce((acc, p) => acc + p.totalPrice, 0)
  );

  // derived quick quote calculator calculation
  let calcResult = $derived.by(() => {
    let rate = activeGold24k;
    if (calcPurity === '22K') rate = activeGold22k;
    else if (calcPurity === '18K') rate = activeGold18k;

    const metalValue = calcWeight * rate;
    const makingCharges = metalValue * (calcMakingCharge / 100);
    const subtotal = metalValue + makingCharges + calcFixedValue;
    const gst = subtotal * 0.03;
    return Math.round(subtotal + gst);
  });

  // Format currency helper
  function formatCurrency(val) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(val);
  }

  // Decompress standard Gzip bytes using browser native DecompressionStream
  async function decompressGzip(bytes) {
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(bytes);
        controller.close();
      }
    }).pipeThrough(new DecompressionStream('gzip'));

    const reader = stream.getReader();
    const chunks = [];
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
    }
    
    let totalLength = 0;
    for (const chunk of chunks) {
      totalLength += chunk.length;
    }
    const result = new Uint8Array(totalLength);
    let offset = 0;
    for (const chunk of chunks) {
      result.set(chunk, offset);
      offset += chunk.length;
    }
    
    return new TextDecoder().decode(result);
  }

  // Decode Base64 and decompress payload
  async function decodeAndDecompress(base64Str) {
    try {
      const binaryString = atob(base64Str);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const decompressedStr = await decompressGzip(bytes);
      return JSON.parse(decompressedStr);
    } catch (err) {
      console.error("Decompression failed:", err);
      return null;
    }
  }

  // Process bullion rates array
  function processProducts(refProducts) {
    for (let item of refProducts) {
      if (typeof item === 'string') {
        try {
          item = JSON.parse(item);
        } catch (e) {
          continue;
        }
      }
      if (item && typeof item === 'object') {
        const name = item.Name || item.symbol || '';
        const ask = parseFloat(item.Ask);
        if (isNaN(ask)) continue;
        
        // Update gold 24K and silver rates
        if (['GOLD26JUNFUT', '117574919', 'GOLD26AUGFUT', '119445255'].includes(name)) {
          gold24k = Math.round(ask / 10);
          timestamp = new Date().toLocaleTimeString();
          connectionStatus = 'live';
        } else if (['SILVER26JULFUT', '118822407', 'SILVER26SEPFUT', '120761607'].includes(name)) {
          silver = Math.round((ask / 1000) * 100) / 100;
          connectionStatus = 'live';
        }
      }
    }
  }

  // Fetch rates and recent logs from SvelteKit API endpoint (falls back to local CSV)
  async function fetchRatesFromAPI() {
    try {
      const res = await fetch('/api/rates');
      const data = await res.json();
      if (data.success) {
        if (connectionStatus !== 'live') {
          gold24k = data.gold24k;
          silver = data.silver;
          timestamp = new Date(data.timestamp).toLocaleTimeString();
          connectionStatus = 'polling';
        }
        if (data.logs) {
          auditLogs = data.logs;
        }
      }
    } catch (err) {
      console.error("API Fetch Error:", err);
    }
  }

  let ws;
  let wsReconnectTimer;
  let apiPollInterval;

  function connectWS() {
    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(`${protocol}//${window.location.host}/ws-bullion?user=ambicaa&auth=1&type=web`);
      
      ws.onopen = () => {
        console.log("Live WebSocket Feed Connected");
        ws.send('{"protocol":"json","version":1}\x1e');
      };
      
      ws.onmessage = async (event) => {
        const parts = event.data.split('\x1e');
        for (const part of parts) {
          if (!part.trim()) continue;
          try {
            const data = JSON.parse(part);
            if (Object.keys(data).length === 0 || (!data.target && data.type === undefined)) {
              ws.send(JSON.stringify({
                arguments: ["ambicaa"],
                invocationId: "0",
                target: "client",
                type: 1
              }) + '\x1e');
              continue;
            }
            
            if (data.target && ['workerPublish', 'workerPublishCoin', 'referanceDetails', 'symbolDetails'].includes(data.target)) {
              const encoded = data.arguments[0];
              const decoded = await decodeAndDecompress(encoded);
              if (decoded && decoded.refProduct) {
                processProducts(decoded.refProduct);
              }
            }
          } catch (err) {
            // Handshake message
          }
        }
      };
      
      ws.onerror = (err) => {
        console.error("WebSocket encountered an error:", err);
        connectionStatus = 'polling';
      };
      
      ws.onclose = () => {
        console.log("WebSocket closed. Attempting reconnect in 5s...");
        connectionStatus = 'polling';
        clearTimeout(wsReconnectTimer);
        wsReconnectTimer = setTimeout(connectWS, 5000);
      };
      
    } catch (e) {
      console.error("WS error:", e);
      connectionStatus = 'polling';
      clearTimeout(wsReconnectTimer);
      wsReconnectTimer = setTimeout(connectWS, 5000);
    }
  }

  onMount(() => {
    fetchRatesFromAPI();
    apiPollInterval = setInterval(fetchRatesFromAPI, 5000);
    connectWS();

    // Set initial custom values matching live feed on mount
    customGold24k = gold24k;
    customSilver = silver;

    return () => {
      if (ws) ws.close();
      clearTimeout(wsReconnectTimer);
      clearInterval(apiPollInterval);
    };
  });

  // Modal open helper
  function openBreakdown(product) {
    selectedProduct = product;
    showModal = true;
  }

  function closeModal() {
    showModal = false;
    selectedProduct = null;
  }

  function addToBag(product) {
    console.log("Added to bag:", product);
    alert(`${product.name} has been added to your valuation cart.`);
  }
</script>



<div class="dashboard-container">
  <div class="dashboard-header">
    <div>
      <h1 class="dashboard-logo">Store Valuations & Bullion Dashboard</h1>
      <p class="dashboard-subtitle">Monitor live market gold rates, override calculations, and manage inventory valuations.</p>
    </div>
    
    <div style="display: flex; gap: 12px;">
      <button class="btn btn-secondary btn-small" onclick={fetchRatesFromAPI}>
        Force Log Pull <span class="material-symbols-outlined" style="font-size: 12px; vertical-align: middle; margin-left: 4px;">refresh</span>
      </button>
    </div>
  </div>

  <!-- Bullion Metrics Grid -->
  <div class="metrics-grid">
    <div class="metric-card">
      <span class="metric-badge badge-gold">24 Carat</span>
      <span class="metric-label">Gold Price (1g)</span>
      <span class="metric-value">{formatCurrency(activeGold24k)}</span>
    </div>
    
    <div class="metric-card">
      <span class="metric-badge badge-gold">22 Carat</span>
      <span class="metric-label">Gold Price (1g)</span>
      <span class="metric-value">{formatCurrency(activeGold22k)}</span>
    </div>
    
    <div class="metric-card">
      <span class="metric-badge badge-gold">18 Carat</span>
      <span class="metric-label">Gold Price (1g)</span>
      <span class="metric-value">{formatCurrency(activeGold18k)}</span>
    </div>
    
    <div class="metric-card">
      <span class="metric-badge badge-gold">99.9% Purity</span>
      <span class="metric-label">Silver Price (1g)</span>
      <span class="metric-value">₹{activeSilver}</span>
    </div>
  </div>

  <!-- Owner Dashboard Rate Override Controls -->
  <div class="controls-panel">
    <div class="control-group">
      <span class="control-label">Manual Rate Override</span>
      <label class="switch-container" aria-label="Toggle Manual Rate Override">
        <input class="switch-input" type="checkbox" bind:checked={isOverride} />
        <span class="switch-slider"></span>
      </label>
    </div>

    {#if isOverride}
      <div class="override-inputs">
        <div class="override-field">
          <label for="gold-override-input">Override Gold 24K (₹/1g)</label>
          <input id="gold-override-input" class="input-number" type="number" bind:value={customGold24k} min="5000" max="30000" />
        </div>
        <div class="override-field">
          <label for="silver-override-input">Override Silver (₹/1g)</label>
          <input id="silver-override-input" class="input-number" type="number" bind:value={customSilver} step="0.1" min="100" max="500" />
        </div>
        <div style="align-self: flex-end;">
          <button class="btn btn-primary btn-small" onclick={() => { customGold24k = gold24k; customSilver = silver; }} style="padding: 9px 16px;">
            Sync to Live
          </button>
        </div>
      </div>
    {:else}
      <div style="font-size: 12px; color: var(--color-on-surface-variant); display: flex; align-items: center; gap: 8px;">
        <span class="material-symbols-outlined" style="font-size: 16px; color: var(--color-primary);">info</span>
        Inventory valuations are dynamically updating from live spot market rates. Toggle switch to enter custom bullion rates.
      </div>
    {/if}
  </div>

  <!-- Main Grid: Stock Valuation Table + Right Tools Sidebar -->
  <div class="dashboard-grid">
    
    <!-- Left Column: Inventory Valuation Table -->
    <div class="panel">
      <div class="panel-header">
        <div style="display: flex; align-items: center; gap: 16px; width: 100%; justify-content: space-between;">
          <h2 class="panel-title">Stock Valuation Catalog</h2>
          
          <!-- Category Tabs -->
          <div style="display: flex; gap: 6px; overflow-x: auto; padding: 4px 0;">
            {#each categories as cat}
              <button 
                class="btn btn-secondary btn-small {activeCategory === cat ? 'active' : ''}" 
                style="padding: 6px 12px; font-size: 10px; border-radius: var(--radius-full); border-color: {activeCategory === cat ? 'var(--color-primary)' : 'rgba(153, 144, 124, 0.15)'}; color: {activeCategory === cat ? 'var(--color-primary)' : ''};"
                onclick={() => activeCategory = cat}
              >
                {cat === 'All' ? 'All Items' : cat}
              </button>
            {/each}
          </div>
        </div>
      </div>

      <!-- Search & Valuation Totals -->
      <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px;">
        <div style="position: relative; flex-grow: 1; max-width: 400px;">
          <input 
            class="calc-input" 
            style="padding-left: 36px;" 
            type="text" 
            bind:value={searchQuery} 
            placeholder="Search by ID or product name..." 
            aria-label="Search stock"
          />
          <span class="material-symbols-outlined" style="position: absolute; left: 10px; top: 10px; font-size: 18px; color: var(--color-on-surface-variant); opacity: 0.6;">search</span>
        </div>
        
        <div style="display: flex; gap: 32px; background-color: var(--color-surface-lowest); padding: 12px 24px; border-radius: var(--radius-sm); border: 1px solid var(--color-outline-variant);">
          <div style="display: flex; flex-direction: column;">
            <span style="font-size: 10px; text-transform: uppercase; color: var(--color-on-surface-variant); letter-spacing: 0.05em;">Total Gold Weight</span>
            <span style="font-size: 18px; font-weight: 700; color: var(--color-on-background);">{totalGoldWeight} g</span>
          </div>
          <div style="display: flex; flex-direction: column; border-left: 1px solid rgba(153, 144, 124, 0.15); padding-left: 32px;">
            <span style="font-size: 10px; text-transform: uppercase; color: var(--color-on-surface-variant); letter-spacing: 0.05em;">Current Stock Value</span>
            <span style="font-size: 18px; font-weight: 700; color: var(--color-primary);">{formatCurrency(totalStockValuation)}</span>
          </div>
        </div>
      </div>

      <!-- Inventory Table -->
      <div class="table-responsive">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Thumbnail</th>
              <th>Product Details</th>
              <th>Purity</th>
              <th>Weight</th>
              <th>Metal Value</th>
              <th>Making Charge</th>
              <th>GST (3%)</th>
              <th>Owner Valuation</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {#each filteredProducts as product (product.id)}
              <tr>
                <td><span class="id-badge">{product.id}</span></td>
                <td>
                  <img class="thumbnail" src={product.image} alt={product.name} />
                </td>
                <td>
                  <div style="font-weight: 600; color: var(--color-on-background);">{product.name}</div>
                  <div style="font-size: 10px; color: var(--color-on-surface-variant);">{product.category}</div>
                </td>
                <td>{product.purity}</td>
                <td>{product.weight} g</td>
                <td>{formatCurrency(product.metalValue)}</td>
                <td>
                  {formatCurrency(product.makingCharges)}
                  <div style="font-size: 9px; color: var(--color-on-surface-variant); opacity: 0.8;">({Math.round(product.makingCharge * 100)}%)</div>
                </td>
                <td>{formatCurrency(product.gst)}</td>
                <td><span class="price-val">{formatCurrency(product.totalPrice)}</span></td>
                <td>
                  <button class="btn btn-secondary btn-small" style="padding: 6px 12px; font-size: 10px;" onclick={() => openBreakdown(product)}>
                    Full Audit
                  </button>
                </td>
              </tr>
            {:else}
              <tr>
                <td colspan="10" style="text-align: center; padding: 32px; color: var(--color-on-surface-variant);">
                  No items found in stock inventory matching your filters.
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </div>
    
    <!-- Right Column: Sidebar Tools -->
    <div style="display: flex; flex-direction: column; gap: 24px;">
      
      <!-- Calculator Tool -->
      <div class="panel">
        <h2 class="panel-title" style="margin-bottom: 16px; border-bottom: 1px solid rgba(153, 144, 124, 0.15); padding-bottom: 8px;">Walk-in Calculator</h2>
        
        <div class="calc-form">
          <div class="calc-row">
            <label for="calc-weight-input">Metal Weight (grams)</label>
            <input id="calc-weight-input" class="calc-input" type="number" bind:value={calcWeight} min="0.1" step="0.01" />
          </div>
          
          <div class="calc-row">
            <label for="calc-purity-select">Purity (Carats)</label>
            <select id="calc-purity-select" class="select-input" bind:value={calcPurity}>
              <option value="24K">24K Gold (99.9%)</option>
              <option value="22K">22K Gold (91.6%)</option>
              <option value="18K">18K Gold (75.0%)</option>
            </select>
          </div>
          
          <div class="calc-row">
            <label for="calc-making-input">Making Charges (%)</label>
            <input id="calc-making-input" class="calc-input" type="number" bind:value={calcMakingCharge} min="0" max="50" />
          </div>

          <div class="calc-row">
            <label for="calc-fixed-input">Gem/Accents Value (₹)</label>
            <input id="calc-fixed-input" class="calc-input" type="number" bind:value={calcFixedValue} min="0" />
          </div>
          
          <div class="calc-result-box">
            <span class="calc-result-label">Dynamic Calculated Quote (with 3% GST)</span>
            <div class="calc-result-value">{formatCurrency(calcResult)}</div>
          </div>
        </div>
      </div>
      
      <!-- CSV Bullion Logs Console Auditor -->
      <div class="panel">
        <h2 class="panel-title" style="margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between;">
          Recent CSV Ticks
          <span style="font-size: 9px; font-family: monospace; color: var(--color-on-surface-variant); font-weight: normal;">ambicaa_rates.csv</span>
        </h2>
        
        <div class="console-container">
          {#each auditLogs as log}
            <div class="console-row">
              <span class="console-time">{log.timestamp.split(' ')[1] || log.timestamp}</span>
              <span class="console-symbol">{log.symbol}</span>
              <span class="console-rate">Ask: ₹{log.ask.toLocaleString()}</span>
            </div>
          {:else}
            <div style="text-align: center; color: #666; padding: 20px;">
              No log entries loaded. Start the Python feed collector to begin recording.
            </div>
          {/each}
        </div>
      </div>
    </div>
  </div>
</div>

<!-- Detailed Price Audit Modal -->
{#if showModal && selectedProduct}
  <div class="modal-backdrop">
    <button class="modal-backdrop-close" onclick={closeModal} aria-label="Close modal overlay"></button>
    <div class="modal-content glass-card" role="dialog" aria-modal="true" aria-labelledby="modal-title" tabindex="-1">
      <button class="modal-close-btn" onclick={closeModal} aria-label="Close Modal">
        <span class="material-symbols-outlined">close</span>
      </button>
      
      <div class="modal-body">
        <div class="modal-product-img-container">
          <img class="modal-product-img" src={selectedProduct.image} alt={selectedProduct.name} />
        </div>
        
        <div class="modal-details-container">
          <span class="modal-category-tag">{selectedProduct.category} • {selectedProduct.id}</span>
          <h3 id="modal-title" class="modal-product-title">{selectedProduct.name}</h3>
          <p class="modal-desc">{selectedProduct.description}</p>
          
          <div class="modal-purity-row">
            <span class="modal-spec-badge">Weight: {selectedProduct.weight} g</span>
            <span class="modal-spec-badge">Purity: {selectedProduct.purity} Gold</span>
          </div>

          <h4 class="modal-section-title">Valuation Audit Logs</h4>
          
          <table class="breakdown-table">
            <tbody>
              <tr>
                <td>Gold Metal Value <small>({selectedProduct.weight}g @ {formatCurrency(selectedProduct.ratePerGram)}/g)</small></td>
                <td class="table-val">{formatCurrency(selectedProduct.metalValue)}</td>
              </tr>
              <tr>
                <td>Making Charges <small>({Math.round(selectedProduct.makingCharge * 100)}% of Metal)</small></td>
                <td class="table-val">{formatCurrency(selectedProduct.makingCharges)}</td>
              </tr>
              {#if selectedProduct.fixedValue > 0}
                <tr>
                  <td>Fixed Accents / Gemstones Value</td>
                  <td class="table-val">{formatCurrency(selectedProduct.fixedValue)}</td>
                </tr>
              {/if}
              <tr class="table-subtotal-row">
                <td>Subtotal Value</td>
                <td class="table-val">{formatCurrency(selectedProduct.subtotal)}</td>
              </tr>
              <tr>
                <td>Taxes & GST <small>(3%)</small></td>
                <td class="table-val">{formatCurrency(selectedProduct.gst)}</td>
              </tr>
              <tr class="table-total-row">
                <td>Total Valuation Cost</td>
                <td class="table-val text-primary-color">{formatCurrency(selectedProduct.totalPrice)}</td>
              </tr>
            </tbody>
          </table>

          <div style="margin-top: 16px; display: flex; gap: 12px;">
            <button class="btn btn-primary" style="flex-grow: 1;" onclick={() => addToBag(selectedProduct)}>
              Add Item to Cart
            </button>
            <button class="btn btn-secondary" onclick={closeModal}>
              Close Audit
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
{/if}

<footer style="margin-top: 60px; background-color: var(--color-surface-lowest); border-top: 1px solid rgba(242, 202, 80, 0.15); padding: 24px 0; font-size: 12px; color: var(--color-on-surface-variant); text-align: center;">
  <div class="container">
    Ambicaa Jewellers Admin Portal © 2026. Connected to Bullion Websocket Feed & CSV Log Sync.
  </div>
</footer>
