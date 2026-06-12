<script>
  import { onMount } from "svelte";

  // State variables (Svelte 5 runes) received from +page.server.js load
  let { data } = $props();
  let products = $state(data.products || []);
  let gold24k = $state(15485); // live rate (₹/g)
  let silver = $state(266.16); // live rate (₹/g)
  let connectionStatus = $state("connecting"); // 'live' | 'polling' | 'connecting'
  let timestamp = $state("");

  // Cart & Sales state (Svelte 5 runes)
  let cart = $state([]);
  let showCartDrawer = $state(false);
  let showInvoiceModal = $state(false);
  /** @type {any} */
  let lastInvoice = $state(null);
  let customerName = $state("");
  let customerPhone = $state("");
  let checkoutStatus = $state("idle"); // 'idle' | 'loading' | 'success' | 'error'

  // Owner Override controls
  let isOverride = $state(false);
  let customGold24k = $state(15500); // custom override gold rate
  let customSilver = $state(267.0); // custom override silver rate

  // Search & Filter state
  let searchQuery = $state("");
  let activeCategory = $state("All");

  // Walk-in Calculator state
  let calcWeight = $state(10);
  let calcPurity = $state("22K");
  let calcMakingCharge = $state(12); // %
  let calcFixedValue = $state(0);

  // Live Feed Audit logs
  let auditLogs = $state([]);

  // Detailed Modal state
  /** @type {any} */
  let selectedProduct = $state(null);
  let showModal = $state(false);

  // Active rates derived based on override toggle (Svelte 5 runes)
  let activeGold24k = $derived(isOverride ? customGold24k : gold24k);
  let activeSilver = $derived(isOverride ? customSilver : silver);
  let activeGold22k = $derived(Math.round(activeGold24k * (22 / 24)));
  let activeGold18k = $derived(Math.round(activeGold24k * (18 / 24)));

  // Derived inventory valuations (Svelte 5 runes)
  let computedProducts = $derived(
    products.map((p) => {
      let rate = activeGold24k;
      if (p.purity === "22K") rate = activeGold22k;
      else if (p.purity === "18K") rate = activeGold18k;
      else if (p.purity === "Silver") rate = activeSilver;

      const weight = Number(p.weight ?? p.weightGrams ?? 0);
      let makingCharge = Number(p.makingCharge ?? p.makingChargePercent ?? 0);
      if (makingCharge > 1) makingCharge = makingCharge / 100;

      const metalValue = weight * rate;
      const makingCharges = metalValue * makingCharge;
      const fixedValue = p.fixedValue || 0;
      const subtotal = metalValue + makingCharges + fixedValue;
      const gst = subtotal * 0.03;
      const total = subtotal + gst;

      return {
        ...p,
        id: p.id ?? p.itemCode ?? "",
        weight,
        makingCharge,
        ratePerGram: rate,
        metalValue: Math.round(metalValue),
        makingCharges: Math.round(makingCharges),
        fixedValue,
        subtotal: Math.round(subtotal),
        gst: Math.round(gst),
        totalPrice: Math.round(total),
      };
    }),
  );

  // Active categories in products
  const categories = [
    "All",
    "Necklace",
    "Ring",
    "Bracelet",
    "Earrings",
    "Watch",
  ];

  // Filtered products list based on search and category
  let filteredProducts = $derived(
    computedProducts.filter((p) => {
      const matchesCategory =
        activeCategory === "All" || p.category === activeCategory;
      const query = searchQuery.toLowerCase();
      const matchesSearch =
        (p.name || "").toLowerCase().includes(query) ||
        (p.id || "").toLowerCase().includes(query);
      return matchesCategory && matchesSearch;
    }),
  );

  // Total Stock Weight (Gold weight in inventory)
  let totalGoldWeight = $derived(
    products
      .filter(
        (p) =>
          p.category !== "Watch" &&
          p.purity !== "Silver" &&
          p.category !== "Silver",
      )
      .reduce((acc, p) => acc + p.weight, 0),
  );

  // Total current stock valuation (based on active rates)
  let totalStockValuation = $derived(
    computedProducts.reduce((acc, p) => acc + p.totalPrice, 0),
  );

  // derived quick quote calculator calculation
  let calcResult = $derived.by(() => {
    let rate = activeGold24k;
    if (calcPurity === "22K") rate = activeGold22k;
    else if (calcPurity === "18K") rate = activeGold18k;
    else if (calcPurity === "Silver") rate = activeSilver;

    const metalValue = calcWeight * rate;
    const makingCharges = metalValue * (calcMakingCharge / 100);
    const subtotal = metalValue + makingCharges + calcFixedValue;
    const gst = subtotal * 0.03;
    return Math.round(subtotal + gst);
  });

  // Derived cart calculations (Svelte 5 runes)
  let cartTotalWeight = $derived(
    cart.reduce((acc, item) => acc + item.weight, 0),
  );
  let cartSubtotal = $derived(
    cart.reduce((acc, item) => acc + item.subtotal, 0),
  );
  let cartGst = $derived(cart.reduce((acc, item) => acc + item.gst, 0));
  let cartTotalPrice = $derived(
    cart.reduce((acc, item) => acc + item.totalPrice, 0),
  );

  // Format currency helper
  function formatCurrency(val) {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 0,
    }).format(val);
  }

  // Decompress standard Gzip bytes using browser native DecompressionStream
  async function decompressGzip(bytes) {
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(bytes);
        controller.close();
      },
    }).pipeThrough(new DecompressionStream("gzip"));

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
      if (typeof item === "string") {
        try {
          item = JSON.parse(item);
        } catch (e) {
          continue;
        }
      }
      if (item && typeof item === "object") {
        const name = item.Name || item.symbol || "";
        const ask = parseFloat(item.Ask);
        if (isNaN(ask)) continue;

        // Update gold 24K and silver rates
        if (
          ["GOLD26JUNFUT", "117574919", "GOLD26AUGFUT", "119445255"].includes(
            name,
          )
        ) {
          gold24k = Math.round(ask / 10);
          timestamp = new Date().toLocaleTimeString();
          connectionStatus = "live";
        } else if (
          [
            "SILVER26JULFUT",
            "118822407",
            "SILVER26SEPFUT",
            "120761607",
          ].includes(name)
        ) {
          silver = Math.round((ask / 1000) * 100) / 100;
          connectionStatus = "live";
        }
      }
    }
  }

  // Fetch rates and recent logs from SvelteKit API endpoint (falls back to local database/feed)
  async function fetchRatesFromAPI() {
    try {
      const res = await fetch("/api/rates");
      const data = await res.json();
      if (data.success) {
        if (connectionStatus !== "live") {
          gold24k = data.gold24k;
          silver = data.silver;
          timestamp = new Date(data.timestamp).toLocaleTimeString();
          connectionStatus = "polling";
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
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      ws = new WebSocket(
        `${protocol}//${window.location.host}/ws-bullion?user=ambicaa&auth=1&type=web`,
      );

      ws.onopen = () => {
        console.log("Live WebSocket Feed Connected");
        ws.send('{"protocol":"json","version":1}\x1e');
      };

      ws.onmessage = async (event) => {
        const parts = event.data.split("\x1e");
        for (const part of parts) {
          if (!part.trim()) continue;
          try {
            const data = JSON.parse(part);
            if (
              Object.keys(data).length === 0 ||
              (!data.target && data.type === undefined)
            ) {
              ws.send(
                JSON.stringify({
                  arguments: ["ambicaa"],
                  invocationId: "0",
                  target: "client",
                  type: 1,
                }) + "\x1e",
              );
              continue;
            }

            if (
              data.target &&
              [
                "workerPublish",
                "workerPublishCoin",
                "referanceDetails",
                "symbolDetails",
              ].includes(data.target)
            ) {
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
        connectionStatus = "polling";
      };

      ws.onclose = () => {
        console.log("WebSocket closed. Attempting reconnect in 5s...");
        connectionStatus = "polling";
        clearTimeout(wsReconnectTimer);
        wsReconnectTimer = setTimeout(connectWS, 5000);
      };
    } catch (e) {
      console.error("WS error:", e);
      connectionStatus = "polling";
      clearTimeout(wsReconnectTimer);
      wsReconnectTimer = setTimeout(connectWS, 5000);
    }
  }

  onMount(() => {
    fetchRatesFromAPI();
    fetchSalesLedger();
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

  // Merchant Stock Entry state variables (Svelte 5 runes)
  let entryCategory = $state("Ring");
  let entryPurity = $state("22K");
  let entryWeight = $state("");
  let entryMakingCharge = $state("12"); // %
  let entryFixedValue = $state("0"); // ₹
  let entryName = $state("");
  let entryDescription = $state("");
  let isNameDirty = $state(false);
  let isDescriptionDirty = $state(false);
  let formError = $state("");
  let formSuccess = $state("");
  /** @type {any} */
  let lastGeneratedLabel = $state(null); // { dataUri, id } — shown after successful add

  // Code 39 Encoding Table
  const code39Map = {
    "0": "000110100",
    "1": "100100001",
    "2": "001100001",
    "3": "101100000",
    "4": "000110001",
    "5": "100110000",
    "6": "001110000",
    "7": "000100101",
    "8": "100100100",
    "9": "001100100",
    A: "100001001",
    B: "001001001",
    C: "101001000",
    D: "000011001",
    E: "100011000",
    F: "001011000",
    G: "000001101",
    H: "100001100",
    I: "001001100",
    J: "000011100",
    K: "100000011",
    L: "001000011",
    M: "101000010",
    N: "000010011",
    O: "100010010",
    P: "001010010",
    Q: "000000111",
    R: "100000110",
    S: "001000110",
    T: "000010110",
    U: "110000001",
    V: "011000001",
    W: "111000000",
    X: "010010001",
    Y: "110010000",
    Z: "011010000",
    "-": "010000101",
    ".": "110000100",
    " ": "011000100",
    $: "010101000",
    "/": "010100010",
    "+": "010001010",
    "%": "000101010",
    "*": "010010100",
  };

  // Code 39 SVG Generator
  function generateCode39SVG(text) {
    const narrowWidth = 1.5;
    const wideWidth = 3.75;
    const height = 40;
    const cleanText =
      "*" + text.toUpperCase().replace(/[^0-9A-Z\-.\s$/+%]/g, "") + "*";

    let x = 10;
    let rects = [];

    for (let i = 0; i < cleanText.length; i++) {
      const char = cleanText[i];
      const pattern = code39Map[char];
      if (!pattern) continue;

      for (let j = 0; j < 9; j++) {
        const isWide = pattern[j] === "1";
        const width = isWide ? wideWidth : narrowWidth;
        const isBar = j % 2 === 0;

        if (isBar) {
          rects.push(
            `<rect x="${x}" y="5" width="${width}" height="${height}" fill="black" />`,
          );
        }
        x += width;
      }
      x += narrowWidth; // Inter-character gap
    }

    const totalWidth = x + 10;
    const svgContent = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${totalWidth} 60" width="100%" height="100%"><rect width="${totalWidth}" height="60" fill="white" />${rects.join("")}<text x="${totalWidth / 2}" y="54" font-family="monospace" font-size="8" text-anchor="middle" fill="black">${text}</text></svg>`;

    const dataUri =
      "data:image/svg+xml;base64," +
      btoa(unescape(encodeURIComponent(svgContent.trim())));
    return { svgContent, dataUri, totalWidth };
  }

  // Reactive unique barcode generator
  let generatedBarcodeID = $derived.by(() => {
    const prefixMap = {
      Ring: "RNG",
      Necklace: "NKL",
      Bracelet: "BRC",
      Earrings: "ERR",
      Watch: "WCH",
    };
    const prefix = prefixMap[entryCategory] || "GEN";

    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, "0");
    const dd = String(now.getDate()).padStart(2, "0");
    const dateStr = `${yyyy}${mm}${dd}`;

    let seq = 1;
    let candidate = "";
    let conflict = true;
    while (conflict) {
      const seqStr = String(seq).padStart(3, "0");
      candidate = `${prefix}-${dateStr}-${seqStr}`;
      conflict = products.some((p) => p.id === candidate);
      if (conflict) {
        seq++;
      }
    }
    return candidate;
  });

  let barcodeData = $derived(generateCode39SVG(generatedBarcodeID));
  let autoName = $derived(
    entryPurity === "Silver"
      ? `Silver ${entryCategory}`
      : `${entryPurity} Gold ${entryCategory}`,
  );
  let displayName = $derived(isNameDirty ? entryName : autoName);
  let autoDesc = $derived(
    entryPurity === "Silver"
      ? `Hand-crafted silver ${entryCategory.toLowerCase()} with premium finish.`
      : `Hand-crafted ${entryPurity} gold ${entryCategory.toLowerCase()} with premium finish.`,
  );
  let displayDesc = $derived(isDescriptionDirty ? entryDescription : autoDesc);

  // Reactive Total Silver Weight
  let totalSilverWeight = $derived(
    products
      .filter((p) => p.purity === "Silver" || p.category === "Silver")
      .reduce((acc, p) => acc + p.weight, 0),
  );

  // Client-side composite label image generator (HTML5 Canvas)
  function generateCompositeLabel(itemId, barcodeUri) {
    return new Promise((resolve, reject) => {
      try {
        const canvas = document.createElement("canvas");
        canvas.width = 600;
        canvas.height = 300;
        const ctx = canvas.getContext("2d");

        // Fill background with white
        ctx.fillStyle = "white";
        ctx.fillRect(0, 0, 600, 300);

        let loaded = 0;
        const checkReady = () => {
          loaded++;
          if (loaded === 1) {
            // Draw text details
            ctx.fillStyle = "black";
            ctx.textAlign = "center";
            ctx.font = "bold 28px Arial, sans-serif";
            ctx.fillText("SUNRISE FINE JEWELLS", 300, 55);

            ctx.font = "24px Arial, sans-serif";
            ctx.fillText("ID: " + itemId, 300, 265);
            resolve(canvas.toDataURL("image/png"));
          }
        };

        const barImg = new Image();
        barImg.onload = () => {
          ctx.drawImage(barImg, 50, 85, 500, 140);
          checkReady();
        };
        barImg.onerror = reject;
        barImg.src = barcodeUri;
      } catch (e) {
        reject(e);
      }
    });
  }

  // Form submission handler to persistently save new stock item
  async function addStockItem(e) {
    if (e) e.preventDefault();
    formError = "";
    formSuccess = "";

    const weightNum = parseFloat(entryWeight);
    if (isNaN(weightNum) || weightNum <= 0) {
      formError = "Please enter a valid weight in grams.";
      return;
    }

    const makingChargeNum = parseFloat(entryMakingCharge);
    if (isNaN(makingChargeNum) || makingChargeNum < 0) {
      formError = "Please enter a valid making charge percentage.";
      return;
    }

    const fixedValueNum = parseFloat(entryFixedValue || 0);
    if (isNaN(fixedValueNum) || fixedValueNum < 0) {
      formError = "Please enter a valid gemstone/accent value.";
      return;
    }

    let finalLabelImage = barcodeData.dataUri;
    try {
      finalLabelImage = await generateCompositeLabel(
        generatedBarcodeID,
        barcodeData.dataUri,
      );
    } catch (canvasErr) {
      console.error(
        "Failed to generate composite label image via canvas:",
        canvasErr,
      );
    }

    const newItem = {
      id: generatedBarcodeID,
      name: displayName,
      purity: entryPurity,
      weight: weightNum,
      makingCharge: makingChargeNum / 100, // store as fraction
      fixedValue: fixedValueNum,
      category: entryCategory,
      description: displayDesc,
      image: finalLabelImage,
    };

    try {
      const response = await fetch("/api/products", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newItem),
      });

      const result = await response.json();
      if (result.success) {
        products = [...products, result.product];
        formSuccess = `Product ${result.product.id} successfully added to inventory!`;
        lastGeneratedLabel = {
          dataUri: finalLabelImage,
          id: result.product.id,
        };

        // Reset inputs
        entryWeight = "";
        entryMakingCharge = "12";
        entryFixedValue = "0";
        entryName = "";
        entryDescription = "";
        isNameDirty = false;
        isDescriptionDirty = false;
      } else {
        formError =
          result.error || "Failed to save product to persistent storage.";
      }
    } catch (err) {
      console.error(err);
      formError = "Network error: Failed to save product.";
    }
  }

  // Trigger a PNG file download for a label image
  async function downloadLabel(dataUri, itemId) {
    let finalUri = dataUri;
    try {
      const barcode = generateCode39SVG(itemId);
      finalUri = await generateCompositeLabel(itemId, barcode.dataUri);
    } catch (err) {
      console.warn(
        "Failed to dynamically generate clean label, falling back to original image:",
        err,
      );
    }

    const a = document.createElement("a");
    a.href = finalUri;
    a.download = `${itemId}_label.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  // Modal open helper
  function openBreakdown(product) {
    selectedProduct = product;
    showModal = true;
  }

  function closeModal() {
    showModal = false;
    selectedProduct = null;
  }

  function playBeep(freq = 1200, duration = 0.08) {
    try {
      const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const oscillator = audioCtx.createOscillator();
      const gainNode = audioCtx.createGain();
      oscillator.connect(gainNode);
      gainNode.connect(audioCtx.destination);
      oscillator.type = "sine";
      oscillator.frequency.value = freq;
      gainNode.gain.setValueAtTime(0.08, audioCtx.currentTime);
      oscillator.start();
      oscillator.stop(audioCtx.currentTime + duration);
    } catch (e) {
      console.warn("Failed to play sound:", e);
    }
  }

  function addToBag(product) {
    if (cart.some((item) => item.id === product.id)) {
      alert("This item is already in your cart.");
      return;
    }
    cart = [...cart, product];
    playBeep(800, 0.05);
    showCartDrawer = true;
  }

  function removeFromCart(productId) {
    cart = cart.filter((item) => item.id !== productId);
    playBeep(600, 0.04);
  }

  async function handleCheckout(e) {
    if (e) e.preventDefault();
    if (cart.length === 0) return;

    checkoutStatus = "loading";
    const productIds = cart.map((item) => item.id);

    try {
      const res = await fetch("/api/products", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids: productIds }),
      });

      const result = await res.json();
      if (result.success) {
        // Play checkout success sound
        playBeep(900, 0.1);
        setTimeout(() => playBeep(1200, 0.15), 120);

        // Remove sold products from products array in state
        products = products.filter((p) => !productIds.includes(p.id));

        // Construct invoice details
        const invoiceData = {
          invoiceId: "SRF-" + Math.floor(100000 + Math.random() * 900000),
          date: new Date().toLocaleString(),
          createdAt: new Date().toISOString(),
          customerName: customerName.trim() || "Walk-in Customer",
          customerPhone: customerPhone.trim() || "N/A",
          items: cart.map((item) => ({
            id: item.id,
            name: item.name,
            purity: item.purity,
            weight: item.weight,
            ratePerGram: item.ratePerGram,
            metalValue: item.metalValue,
            makingCharges: item.makingCharges,
            fixedValue: item.fixedValue,
            subtotal: item.subtotal,
            gst: item.gst,
            totalPrice: item.totalPrice,
          })),
          totalWeight: cartTotalWeight,
          subtotal: cartSubtotal,
          gst: cartGst,
          total: cartTotalPrice,
        };

        // Save invoice to MongoDB
        try {
          await fetch("/api/invoices", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(invoiceData),
          });
          fetchSalesLedger(); // Refresh manager ledger
        } catch (err) {
          console.error("Failed to post invoice to MongoDB:", err);
        }

        // Store invoice details
        lastInvoice = invoiceData;

        // Clear cart and close drawer
        cart = [];
        showCartDrawer = false;
        checkoutStatus = "success";
        showInvoiceModal = true;

        // Reset customer details
        customerName = "";
        customerPhone = "";
      } else {
        alert("Checkout failed: " + (result.error || "Unknown error"));
        checkoutStatus = "error";
      }
    } catch (err) {
      console.error("Checkout error:", err);
      alert("Checkout failed: Network error.");
      checkoutStatus = "error";
    }
  }

  // Manager Sales Ledger state variables
  /** @type {any[]} */
  let ledgerInvoices = $state([]);
  let ledgerLoading = $state(false);
  let filterCustomerName = $state("");
  let filterCustomerPhone = $state("");
  let filterStartDate = $state("");
  let filterEndDate = $state("");
  let filterMinAmount = $state("");
  let filterMaxAmount = $state("");

  async function fetchSalesLedger() {
    ledgerLoading = true;
    try {
      const params = new URLSearchParams();
      if (filterCustomerName) params.append("customerName", filterCustomerName);
      if (filterCustomerPhone)
        params.append("customerPhone", filterCustomerPhone);
      if (filterStartDate) params.append("startDate", filterStartDate);
      if (filterEndDate) params.append("endDate", filterEndDate);
      if (filterMinAmount) params.append("minAmount", filterMinAmount);
      if (filterMaxAmount) params.append("maxAmount", filterMaxAmount);

      const res = await fetch(`/api/invoices?${params.toString()}`);
      const data = await res.json();
      if (data.success) {
        ledgerInvoices = data.invoices;
      } else {
        console.error("Failed to fetch ledger invoices:", data.error);
      }
    } catch (err) {
      console.error("Error fetching ledger invoices:", err);
    } finally {
      ledgerLoading = false;
    }
  }

  function reprintInvoice(invoice) {
    lastInvoice = invoice;
    showInvoiceModal = true;
  }

  // Scanner state variables (Svelte 5 runes)
  let showScannerModal = $state(false);
  let scannerError = $state("");
  let camerasList = $state([]);
  let activeCameraId = $state("");
  let scannerInstance = null;
  let Html5Qrcode = null;

  async function startScanner() {
    scannerError = "";
    showScannerModal = true;

    // Allow DOM to render #webcam-reader div
    setTimeout(async () => {
      try {
        if (!Html5Qrcode) {
          const module = await import("html5-qrcode");
          Html5Qrcode = module.Html5Qrcode;
        }

        scannerInstance = new Html5Qrcode("webcam-reader");

        // Query available camera devices
        const devices = await Html5Qrcode.getCameras();
        if (devices && devices.length > 0) {
          camerasList = devices;
          // Look for rear/back camera on mobile devices
          const backCam = devices.find(
            (device) =>
              device.label.toLowerCase().includes("back") ||
              device.label.toLowerCase().includes("rear") ||
              device.label.toLowerCase().includes("environment"),
          );
          activeCameraId = backCam ? backCam.id : devices[0].id;

          await startCamera();
        } else {
          scannerError =
            "No camera devices found. Ensure camera permissions are granted.";
        }
      } catch (err) {
        console.error("Scanner initialization failed:", err);
        scannerError = "Camera access denied or failed: " + err.message;
      }
    }, 150);
  }

  async function startCamera() {
    if (!scannerInstance || !activeCameraId) return;

    try {
      if (scannerInstance.isScanning) {
        await scannerInstance.stop();
      }

      scannerError = "";
      await scannerInstance.start(
        activeCameraId,
        {
          fps: 15,
          qrbox: (width, height) => {
            const minDim = Math.min(width, height);
            // Dynamic scan box area (70% of screen width, 40% of height for barcodes)
            return {
              width: Math.round(minDim * 0.7),
              height: Math.round(minDim * 0.4),
            };
          },
          aspectRatio: 1.333333,
        },
        (decodedText) => {
          handleScanSuccess(decodedText);
        },
        (errorMessage) => {
          // Ignore spammy single frame scan errors
        },
      );
    } catch (err) {
      console.error("Failed to start camera feed:", err);
      scannerError = "Failed to start camera feed: " + err.message;
    }
  }

  async function stopScanner() {
    showScannerModal = false;
    if (scannerInstance) {
      try {
        if (scannerInstance.isScanning) {
          await scannerInstance.stop();
        }
      } catch (err) {
        console.error("Failed to stop camera:", err);
      }
      scannerInstance = null;
    }
    camerasList = [];
    activeCameraId = "";
    scannerError = "";
  }

  function handleScanSuccess(text) {
    console.log("Scanned text successfully:", text);

    // Play a short success beep sound using Web Audio API
    playBeep(1200, 0.08);

    // Try to find the matching item in local inventory
    const matched = products.find(
      (p) => p.id.toLowerCase() === text.trim().toLowerCase(),
    );

    if (matched) {
      searchQuery = text.trim(); // filter catalog
      openBreakdown(matched); // open detail modal
      stopScanner();
    } else {
      scannerError = `Scanned ID "${text}" but no matching product was found in stock.`;
    }
  }
</script>

<div class="dashboard-container">
  <div class="dashboard-header">
    <div>
      <h1 class="dashboard-logo">Store Valuations & Bullion Dashboard</h1>
      <p class="dashboard-subtitle">
        Monitor live market gold rates, override calculations, and manage
        inventory valuations.
      </p>
    </div>

    <div style="display: flex; gap: 12px; align-items: center;">
      <!-- Cart Trigger Button -->
      <button
        class="btn btn-secondary btn-small"
        style="position: relative; display: flex; align-items: center; gap: 6px; border-color: var(--color-primary); color: var(--color-primary);"
        onclick={() => (showCartDrawer = true)}
      >
        <span class="material-symbols-outlined" style="font-size: 16px;"
          >shopping_cart</span
        >
        Cart ({cart.length})
        {#if cart.length > 0}
          <span
            style="position: absolute; top: -6px; right: -6px; background-color: var(--color-error); color: white; border-radius: 50%; font-size: 9px; width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; font-weight: bold; box-shadow: 0 2px 4px rgba(0,0,0,0.2);"
          >
            {cart.length}
          </span>
        {/if}
      </button>

      <button class="btn btn-secondary btn-small" onclick={fetchRatesFromAPI}>
        Force Log Pull <span
          class="material-symbols-outlined"
          style="font-size: 12px; vertical-align: middle; margin-left: 4px;"
          >refresh</span
        >
      </button>
    </div>
  </div>

  <div class="dashboard-grid">
    <!-- Left Column: Stock Entry & Stock Catalog -->
    <div style="display: flex; flex-direction: column; gap: 24px;">
      <!-- Merchant Stock Entry Panel -->
      <div class="panel">
        <h2
          class="panel-title"
          style="margin-bottom: 16px; border-bottom: 1px solid rgba(153, 144, 124, 0.15); padding-bottom: 8px; display: flex; align-items: center; gap: 8px;"
        >
          <span
            class="material-symbols-outlined"
            style="color: var(--color-primary);">inventory_2</span
          >
          Merchant Stock Entry
        </h2>

        {#if formError}
          <div class="alert-box alert-error">
            <span class="material-symbols-outlined" style="font-size: 18px;"
              >error</span
            >
            <span>{formError}</span>
          </div>
        {/if}

        {#if formSuccess}
          <div class="alert-box alert-success">
            <span class="material-symbols-outlined" style="font-size: 18px;"
              >check_circle</span
            >
            <span>{formSuccess}</span>
          </div>
        {/if}

        <form onsubmit={addStockItem} class="calc-form">
          <div class="entry-form-grid">
            <div class="calc-row">
              <label for="entry-category-select">Product Category</label>
              <select
                id="entry-category-select"
                class="select-input"
                bind:value={entryCategory}
              >
                <option value="Ring">Ring</option>
                <option value="Necklace">Necklace</option>
                <option value="Bracelet">Bracelet</option>
                <option value="Earrings">Earrings</option>
                <option value="Watch">Watch</option>
              </select>
            </div>

            <div class="calc-row">
              <label for="entry-purity-select">Quality / Purity</label>
              <select
                id="entry-purity-select"
                class="select-input"
                bind:value={entryPurity}
              >
                <option value="24K">24K Gold (99.9%)</option>
                <option value="22K">22K Gold (91.6%)</option>
                <option value="18K">18K Gold (75.0%)</option>
                <option value="Silver">Silver (99.9%)</option>
              </select>
            </div>

            <div class="calc-row">
              <label for="entry-weight-input">Weight (grams)</label>
              <input
                id="entry-weight-input"
                class="calc-input"
                type="number"
                step="0.001"
                min="0.001"
                bind:value={entryWeight}
                placeholder="0.00"
                required
              />
            </div>

            <div class="calc-row">
              <label for="entry-making-input">Making Charges (%)</label>
              <input
                id="entry-making-input"
                class="calc-input"
                type="number"
                step="0.1"
                min="0"
                bind:value={entryMakingCharge}
                placeholder="12.0"
                required
              />
            </div>

            <div class="calc-row">
              <label for="entry-fixed-input">Gem/Accents Value (₹)</label>
              <input
                id="entry-fixed-input"
                class="calc-input"
                type="number"
                min="0"
                bind:value={entryFixedValue}
                placeholder="0"
              />
            </div>

            <div class="calc-row">
              <span
                style="font-size: 11px; font-weight: 600; text-transform: uppercase; color: var(--color-on-surface-variant); letter-spacing: 0.05em; margin-bottom: 6px; display: block;"
                >Generated Barcode ID</span
              >
              <div
                class="calc-input"
                style="background-color: var(--color-surface-lowest); border-color: var(--color-outline-variant); font-family: monospace; letter-spacing: 1px; color: var(--color-primary); display: flex; align-items: center;"
              >
                {generatedBarcodeID}
              </div>
            </div>

            <div class="calc-row form-full-width">
              <label for="entry-name-input">Product Name</label>
              <input
                id="entry-name-input"
                type="text"
                class="calc-input"
                value={isNameDirty ? entryName : autoName}
                oninput={(e) => {
                  entryName = e.target.value;
                  isNameDirty = e.target.value.trim() !== "";
                }}
                placeholder="e.g. Celestial Gold Ring"
              />
            </div>

            <div class="calc-row form-full-width">
              <label for="entry-desc-textarea">Description</label>
              <textarea
                id="entry-desc-textarea"
                class="calc-input"
                style="resize: vertical; min-height: 60px;"
                value={isDescriptionDirty ? entryDescription : autoDesc}
                oninput={(e) => {
                  entryDescription = e.target.value;
                  isDescriptionDirty = e.target.value.trim() !== "";
                }}
                placeholder="Enter item description..."
              ></textarea>
            </div>
          </div>

          <div
            class="barcode-preview-card"
            style="display: flex; flex-direction: column; align-items: center; gap: 12px; width: 100%;"
          >
            <span
              style="font-size: 10px; text-transform: uppercase; color: var(--color-on-surface-variant); letter-spacing: 0.1em; margin-bottom: 4px;"
              >Real-Time Jewelry Barcode Tag Sticker Preview</span
            >
            <div
              style="display: flex; gap: 20px; width: 100%; justify-content: center;"
            >
              <!-- Unified Barcode Sticker -->
              <div
                style="background-color: white; color: black; padding: 16px; border-radius: var(--radius-sm); border: 1px solid var(--color-outline); width: 280px; display: flex; flex-direction: column; align-items: center; justify-content: center; box-shadow: var(--shadow-sm);"
              >
                <div
                  style="font-size: 9px; font-weight: 700; text-transform: uppercase; margin-bottom: 6px; letter-spacing: 0.8px; color: black;"
                >
                  Sunrise Fine Jewells
                </div>

                <div
                  class="barcode-svg-container"
                  style="background-color: transparent; padding: 0; width: 100%; height: 45px; display: flex; align-items: center; justify-content: center; overflow: hidden; margin-bottom: 6px;"
                >
                  {@html barcodeData.svgContent}
                </div>

                <div
                  style="display: flex; width: 100%; justify-content: space-between; font-family: monospace; font-size: 8px; color: black; border-top: 1px dashed #ccc; padding-top: 6px; margin-top: 2px;"
                >
                  <div>WT: {entryWeight || "0.00"} g</div>
                  <div>
                    {entryPurity === "Silver"
                      ? "Silver"
                      : `KT: ${entryPurity} Gold`}
                  </div>
                  <div>ID: {generatedBarcodeID}</div>
                </div>
              </div>
            </div>
          </div>

          <button
            type="submit"
            class="btn btn-primary"
            style="width: 100%; margin-top: 8px; display: flex; align-items: center; justify-content: center; gap: 8px;"
          >
            <span class="material-symbols-outlined" style="font-size: 16px;"
              >add_box</span
            >
            Add Item to Stock
          </button>

          {#if formError}
            <div class="alert-box alert-error" style="margin-top: 12px;">
              <span class="material-symbols-outlined" style="font-size: 16px;"
                >error</span
              >
              {formError}
            </div>
          {/if}

          {#if formSuccess && lastGeneratedLabel}
            <div
              style="margin-top: 16px; background: rgba(76,175,80,0.08); border: 1px solid rgba(76,175,80,0.3); border-radius: var(--radius-sm); padding: 16px; display: flex; flex-direction: column; align-items: center; gap: 12px;"
            >
              <div
                style="display: flex; align-items: center; gap: 8px; color: var(--color-success); font-size: 13px; font-weight: 600;"
              >
                <span class="material-symbols-outlined" style="font-size: 18px;"
                  >check_circle</span
                >
                {formSuccess}
              </div>
              <img
                src={lastGeneratedLabel.dataUri}
                alt="Label preview"
                style="max-width: 240px; border: 1px solid var(--color-outline-variant); border-radius: var(--radius-sm); background: white;"
              />
              <button
                type="button"
                class="btn btn-primary"
                style="display: flex; align-items: center; gap: 8px; padding: 10px 24px;"
                onclick={() =>
                  downloadLabel(
                    lastGeneratedLabel.dataUri,
                    lastGeneratedLabel.id,
                  )}
              >
                <span class="material-symbols-outlined" style="font-size: 16px;"
                  >download</span
                >
                Download Label PNG
              </button>
              <p
                style="font-size: 10px; color: var(--color-on-surface-variant); margin: 0;"
              >
                Print & stick this label on the jewellery item
              </p>
            </div>
          {/if}
        </form>
      </div>

      <!-- Stock Valuation Catalog Panel -->
      <div class="panel">
        <div class="panel-header">
          <div
            style="display: flex; align-items: center; gap: 16px; width: 100%; justify-content: space-between; flex-wrap: wrap;"
          >
            <h2
              class="panel-title"
              style="display: flex; align-items: center; gap: 8px;"
            >
              <span
                class="material-symbols-outlined"
                style="color: var(--color-primary);">list_alt</span
              >
              Stock Valuation Catalog
            </h2>

            <!-- Category Tabs -->
            <div
              style="display: flex; gap: 6px; overflow-x: auto; padding: 4px 0;"
            >
              {#each categories as cat}
                <button
                  class="btn btn-secondary btn-small {activeCategory === cat
                    ? 'active'
                    : ''}"
                  style="padding: 6px 12px; font-size: 10px; border-radius: var(--radius-full); border-color: {activeCategory ===
                  cat
                    ? 'var(--color-primary)'
                    : 'rgba(153, 144, 124, 0.15)'}; color: {activeCategory ===
                  cat
                    ? 'var(--color-primary)'
                    : ''};"
                  onclick={() => (activeCategory = cat)}
                >
                  {cat === "All" ? "All Items" : cat}
                </button>
              {/each}
            </div>
          </div>
        </div>

        <!-- Search Input & Scanner trigger -->
        <div
          style="display: flex; gap: 10px; margin-bottom: 20px; align-items: center;"
        >
          <div style="position: relative; flex: 1;">
            <input
              class="calc-input"
              style="padding-left: 36px; width: 100%; box-sizing: border-box;"
              type="text"
              bind:value={searchQuery}
              placeholder="Search by ID or product name..."
              aria-label="Search stock"
            />
            <span
              class="material-symbols-outlined"
              style="position: absolute; left: 10px; top: 10px; font-size: 18px; color: var(--color-on-surface-variant); opacity: 0.6;"
              >search</span
            >
          </div>

          <button
            type="button"
            class="btn btn-secondary"
            style="display: flex; align-items: center; gap: 6px; padding: 10px 16px; flex-shrink: 0;"
            onclick={startScanner}
          >
            <span class="material-symbols-outlined" style="font-size: 18px;"
              >photo_camera</span
            >
            Scan Tag
          </button>
        </div>

        <!-- Inventory Table -->
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Thumbnail / Barcode</th>
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
                    <div
                      style="width: 100px; height: 35px; background: white; padding: 2px; border-radius: var(--radius-sm); border: 1px solid var(--color-outline-variant); overflow: hidden; display: flex; align-items: center; justify-content: center;"
                      title="Barcode Label"
                    >
                      {@html generateCode39SVG(product.id).svgContent}
                    </div>
                  </td>
                  <td>
                    <div
                      style="font-weight: 600; color: var(--color-on-background);"
                    >
                      {product.name}
                    </div>
                    <div
                      style="font-size: 10px; color: var(--color-on-surface-variant);"
                    >
                      {product.category}
                    </div>
                  </td>
                  <td>{product.purity}</td>
                  <td>{product.weight} g</td>
                  <td>{formatCurrency(product.metalValue)}</td>
                  <td>
                    {formatCurrency(product.makingCharges)}
                    <div
                      style="font-size: 9px; color: var(--color-on-surface-variant); opacity: 0.8;"
                    >
                      ({Math.round(product.makingCharge * 100)}%)
                    </div>
                  </td>
                  <td>{formatCurrency(product.gst)}</td>
                  <td
                    ><span class="price-val"
                      >{formatCurrency(product.totalPrice)}</span
                    ></td
                  >
                  <td>
                    <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                      <button
                        class="btn btn-primary btn-small"
                        style="padding: 6px 10px; font-size: 10px; display: flex; align-items: center; gap: 4px;"
                        onclick={() => addToBag(product)}
                      >
                        <span
                          class="material-symbols-outlined"
                          style="font-size: 12px;">shopping_cart</span
                        >
                        Sell
                      </button>
                      <button
                        class="btn btn-secondary btn-small"
                        style="padding: 6px 12px; font-size: 10px;"
                        onclick={() => openBreakdown(product)}
                      >
                        Full Audit
                      </button>
                      {#if product.image && product.image.startsWith("data:image")}
                        <button
                          class="btn btn-secondary btn-small"
                          style="padding: 6px 10px; font-size: 10px; display: flex; align-items: center; gap: 4px; border-color: rgba(242,202,80,0.3); color: var(--color-primary);"
                          title="Download label for printing"
                          onclick={() =>
                            downloadLabel(product.image, product.id)}
                        >
                          <span
                            class="material-symbols-outlined"
                            style="font-size: 13px;">download</span
                          >
                          Label
                        </button>
                      {/if}
                    </div>
                  </td>
                </tr>
              {:else}
                <tr>
                  <td
                    colspan="10"
                    style="text-align: center; padding: 32px; color: var(--color-on-surface-variant);"
                  >
                    No items found in stock inventory matching your filters.
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Manager Sales Ledger Panel -->
      <div class="panel">
        <h2
          class="panel-title"
          style="margin-bottom: 16px; border-bottom: 1px solid rgba(153, 144, 124, 0.15); padding-bottom: 8px; display: flex; align-items: center; justify-content: space-between;"
        >
          <span style="display: flex; align-items: center; gap: 8px;">
            <span
              class="material-symbols-outlined"
              style="color: var(--color-primary);">receipt_long</span
            >
            Manager Sales Ledger & Invoices
          </span>
          <button
            type="button"
            class="btn btn-secondary btn-small"
            onclick={fetchSalesLedger}
            disabled={ledgerLoading}
            style="padding: 6px 12px; font-size: 11px;"
          >
            {ledgerLoading ? "Refreshing..." : "Refresh Ledger"}
          </button>
        </h2>

        <!-- Ledger Filters Grid -->
        <div
          class="entry-form-grid"
          style="background: var(--color-surface-lowest); border: 1px solid var(--color-outline-variant); padding: 16px; border-radius: var(--radius-sm); margin-bottom: 20px;"
        >
          <div class="calc-row">
            <label for="filter-cust-name">Customer Name</label>
            <input
              id="filter-cust-name"
              type="text"
              class="calc-input"
              placeholder="All Customers"
              bind:value={filterCustomerName}
              oninput={fetchSalesLedger}
            />
          </div>

          <div class="calc-row">
            <label for="filter-cust-phone">Contact Number</label>
            <input
              id="filter-cust-phone"
              type="text"
              class="calc-input"
              placeholder="All Phones"
              bind:value={filterCustomerPhone}
              oninput={fetchSalesLedger}
            />
          </div>

          <div class="calc-row">
            <label for="filter-start-date">Start Date</label>
            <input
              id="filter-start-date"
              type="date"
              class="calc-input"
              bind:value={filterStartDate}
              onchange={fetchSalesLedger}
            />
          </div>

          <div class="calc-row">
            <label for="filter-end-date">End Date</label>
            <input
              id="filter-end-date"
              type="date"
              class="calc-input"
              bind:value={filterEndDate}
              onchange={fetchSalesLedger}
            />
          </div>

          <div class="calc-row">
            <label for="filter-min-amt">Min Amount (₹)</label>
            <input
              id="filter-min-amt"
              type="number"
              class="calc-input"
              placeholder="Min ₹"
              bind:value={filterMinAmount}
              oninput={fetchSalesLedger}
            />
          </div>

          <div class="calc-row">
            <label for="filter-max-amt">Max Amount (₹)</label>
            <input
              id="filter-max-amt"
              type="number"
              class="calc-input"
              placeholder="Max ₹"
              bind:value={filterMaxAmount}
              oninput={fetchSalesLedger}
            />
          </div>
        </div>

        <!-- Invoices Table -->
        <div class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>Invoice ID</th>
                <th>Date / Time</th>
                <th>Customer</th>
                <th>Phone</th>
                <th>Weight</th>
                <th>Total Value</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {#each ledgerInvoices as invoice (invoice.invoiceId || invoice._id)}
                <tr>
                  <td
                    ><span class="id-badge" style="font-family: monospace;"
                      >{invoice.invoiceId}</span
                    ></td
                  >
                  <td style="font-size: 11px;"
                    >{new Date(
                      invoice.createdAt || invoice.date,
                    ).toLocaleString()}</td
                  >
                  <td><strong>{invoice.customerName}</strong></td>
                  <td>{invoice.customerPhone || "N/A"}</td>
                  <td>{Number(invoice.totalWeight || 0).toFixed(2)} g</td>
                  <td
                    ><span
                      class="price-val"
                      style="color: var(--color-primary);"
                      >{formatCurrency(invoice.total)}</span
                    ></td
                  >
                  <td>
                    <button
                      type="button"
                      class="btn btn-secondary btn-small"
                      style="display: flex; align-items: center; gap: 4px; padding: 6px 10px;"
                      onclick={() => reprintInvoice(invoice)}
                    >
                      <span
                        class="material-symbols-outlined"
                        style="font-size: 14px;">print</span
                      >
                      Reprint
                    </button>
                  </td>
                </tr>
              {:else}
                <tr>
                  <td
                    colspan="7"
                    style="text-align: center; padding: 32px; color: var(--color-on-surface-variant);"
                  >
                    {ledgerLoading
                      ? "Loading invoices..."
                      : "No invoices found matching current filters."}
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Right Column: Sidebar (Rates, Totals, Calculator, Logs) -->
    <div style="display: flex; flex-direction: column; gap: 24px;">
      <!-- Unified Live Bullion Rates Panel -->
      <div class="panel">
        <h2
          class="panel-title"
          style="margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between;"
        >
          <span style="display: flex; align-items: center; gap: 6px;">
            <span
              class="indicator-dot {isOverride ? 'override' : connectionStatus}"
            ></span>
            Live Bullion Rates
          </span>
          <span
            style="font-size: 10px; font-weight: normal; color: var(--color-on-surface-variant); font-family: monospace;"
          >
            {timestamp ? timestamp : "Connecting..."}
          </span>
        </h2>

        <div
          style="display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px;"
        >
          <div class="sidebar-rate-row">
            <div class="rate-row-left">
              <span
                class="rate-purity-badge badge-gold"
                style="background-color: rgba(242, 202, 80, 0.15);"
                >24K Gold</span
              >
              <span
                style="font-size: 12px; color: var(--color-on-surface-variant);"
                >99.9% Purity</span
              >
            </div>
            <span class="rate-value-display"
              >{formatCurrency(activeGold24k)}</span
            >
          </div>

          <div class="sidebar-rate-row">
            <div class="rate-row-left">
              <span
                class="rate-purity-badge badge-gold"
                style="background-color: rgba(242, 202, 80, 0.1);"
                >22K Gold</span
              >
              <span
                style="font-size: 12px; color: var(--color-on-surface-variant);"
                >91.6% Purity</span
              >
            </div>
            <span class="rate-value-display"
              >{formatCurrency(activeGold22k)}</span
            >
          </div>

          <div class="sidebar-rate-row">
            <div class="rate-row-left">
              <span
                class="rate-purity-badge badge-gold"
                style="background-color: rgba(242, 202, 80, 0.05);"
                >18K Gold</span
              >
              <span
                style="font-size: 12px; color: var(--color-on-surface-variant);"
                >75.0% Purity</span
              >
            </div>
            <span class="rate-value-display"
              >{formatCurrency(activeGold18k)}</span
            >
          </div>

          <div class="sidebar-rate-row">
            <div class="rate-row-left">
              <span
                class="rate-purity-badge badge-gold"
                style="background-color: rgba(215, 196, 164, 0.1); color: var(--color-secondary); border-color: rgba(215, 196, 164, 0.2);"
                >Silver</span
              >
              <span
                style="font-size: 12px; color: var(--color-on-surface-variant);"
                >99.9% Pure</span
              >
            </div>
            <span class="rate-value-display">₹{activeSilver}</span>
          </div>
        </div>

        <!-- Rate Override Control Inside Bullion Panel -->
        <div
          style="border-top: 1px solid rgba(153, 144, 124, 0.15); padding-top: 16px;"
        >
          <div
            class="control-group"
            style="justify-content: space-between; width: 100%; margin-bottom: 12px;"
          >
            <span class="control-label" style="font-size: 11px;"
              >Manual Override</span
            >
            <label
              class="switch-container"
              aria-label="Toggle Manual Rate Override"
            >
              <input
                class="switch-input"
                type="checkbox"
                bind:checked={isOverride}
              />
              <span class="switch-slider"></span>
            </label>
          </div>

          {#if isOverride}
            <div
              class="override-inputs"
              style="flex-direction: column; align-items: stretch; gap: 12px; width: 100%;"
            >
              <div class="override-field">
                <label for="gold-override-input">Override Gold 24K (₹/1g)</label
                >
                <input
                  id="gold-override-input"
                  class="input-number"
                  style="width: 100%;"
                  type="number"
                  bind:value={customGold24k}
                  min="5000"
                  max="30000"
                />
              </div>
              <div class="override-field">
                <label for="silver-override-input">Override Silver (₹/1g)</label
                >
                <input
                  id="silver-override-input"
                  class="input-number"
                  style="width: 100%;"
                  type="number"
                  bind:value={customSilver}
                  step="0.1"
                  min="100"
                  max="500"
                />
              </div>
              <button
                class="btn btn-primary btn-small"
                onclick={() => {
                  customGold24k = gold24k;
                  customSilver = silver;
                }}
                style="width: 100%; padding: 8px 16px;"
              >
                Sync to Live
              </button>
            </div>
          {:else}
            <div
              style="font-size: 11px; color: var(--color-on-surface-variant); display: flex; align-items: flex-start; gap: 6px; line-height: 1.4;"
            >
              <span
                class="material-symbols-outlined"
                style="font-size: 14px; color: var(--color-primary); flex-shrink: 0; margin-top: 1px;"
                >info</span
              >
              Valuations dynamically update from live spot rates. Toggle switch to
              override rates.
            </div>
          {/if}
        </div>
      </div>

      <!-- Enhanced Total Inventory Summary Panel -->
      <div class="panel">
        <h2
          class="panel-title"
          style="margin-bottom: 16px; border-bottom: 1px solid rgba(153, 144, 124, 0.15); padding-bottom: 8px; display: flex; align-items: center; gap: 8px;"
        >
          <span
            class="material-symbols-outlined"
            style="color: var(--color-primary);">analytics</span
          >
          Total Inventory Summary
        </h2>

        <div class="inventory-summary-grid">
          <div class="summary-stat-card">
            <span class="summary-stat-label">Total Gold Weight</span>
            <div class="summary-stat-value">
              {totalGoldWeight.toFixed(2)} g
              <span class="summary-stat-subvalue"
                >{(totalGoldWeight / 1000).toFixed(3)} kg</span
              >
            </div>
          </div>

          <div class="summary-stat-card">
            <span class="summary-stat-label">Total Silver Weight</span>
            <div class="summary-stat-value">
              {totalSilverWeight.toFixed(2)} g
              <span class="summary-stat-subvalue"
                >{(totalSilverWeight / 1000).toFixed(3)} kg</span
              >
            </div>
          </div>

          <div
            class="summary-stat-card"
            style="border-color: rgba(242, 202, 80, 0.25); background: linear-gradient(180deg, var(--color-surface-lowest) 0%, rgba(242, 202, 80, 0.02) 100%);"
          >
            <span
              class="summary-stat-label"
              style="color: var(--color-primary);">Current Valuation</span
            >
            <div
              class="summary-stat-value"
              style="color: var(--color-primary); font-size: 22px;"
            >
              {formatCurrency(totalStockValuation)}
              <span
                class="summary-stat-subvalue"
                style="color: var(--color-on-surface-variant);"
                >Full inventory sum</span
              >
            </div>
          </div>
        </div>
      </div>

      <!-- Walk-in Calculator Tool -->
      <div class="panel">
        <h2
          class="panel-title"
          style="margin-bottom: 16px; border-bottom: 1px solid rgba(153, 144, 124, 0.15); padding-bottom: 8px; display: flex; align-items: center; gap: 8px;"
        >
          <span
            class="material-symbols-outlined"
            style="color: var(--color-primary);">calculate</span
          >
          Walk-in Calculator
        </h2>

        <div class="calc-form">
          <div class="calc-row">
            <label for="calc-weight-input">Metal Weight (grams)</label>
            <input
              id="calc-weight-input"
              class="calc-input"
              type="number"
              bind:value={calcWeight}
              min="0.1"
              step="0.01"
            />
          </div>

          <div class="calc-row">
            <label for="calc-purity-select">Purity (Carats)</label>
            <select
              id="calc-purity-select"
              class="select-input"
              bind:value={calcPurity}
            >
              <option value="24K">24K Gold (99.9%)</option>
              <option value="22K">22K Gold (91.6%)</option>
              <option value="18K">18K Gold (75.0%)</option>
              <option value="Silver">Silver (99.9%)</option>
            </select>
          </div>

          <div class="calc-row">
            <label for="calc-making-input">Making Charges (%)</label>
            <input
              id="calc-making-input"
              class="calc-input"
              type="number"
              bind:value={calcMakingCharge}
              min="0"
              max="50"
            />
          </div>

          <div class="calc-row">
            <label for="calc-fixed-input">Gem/Accents Value (₹)</label>
            <input
              id="calc-fixed-input"
              class="calc-input"
              type="number"
              bind:value={calcFixedValue}
              min="0"
            />
          </div>

          <div class="calc-result-box">
            <span class="calc-result-label"
              >Dynamic Calculated Quote (with 3% GST)</span
            >
            <div class="calc-result-value">{formatCurrency(calcResult)}</div>
          </div>
        </div>
      </div>

      <!-- Live Bullion Logs Console Auditor -->
      <div class="panel">
        <h2
          class="panel-title"
          style="margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between;"
        >
          <span style="display: flex; align-items: center; gap: 6px;">
            <span
              class="material-symbols-outlined"
              style="color: var(--color-primary); font-size: 18px;"
              >terminal</span
            >
            Recent Live Feed Ticks
          </span>
          <span
            style="font-size: 9px; font-family: monospace; color: var(--color-on-surface-variant); font-weight: normal;"
            >Live Feed Tracker</span
          >
        </h2>

        <div class="console-container">
          {#each auditLogs as log}
            <div class="console-row">
              <span class="console-time"
                >{log.timestamp.split(" ")[1] || log.timestamp}</span
              >
              <span class="console-symbol">{log.symbol}</span>
              <span class="console-rate">Ask: ₹{log.ask.toLocaleString()}</span>
            </div>
          {:else}
            <div style="text-align: center; color: #666; padding: 20px;">
              No log entries loaded. Start the Python feed collector to begin
              recording.
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
    <button
      class="modal-backdrop-close"
      onclick={closeModal}
      aria-label="Close modal overlay"
    ></button>
    <div
      class="modal-content glass-card"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      tabindex="-1"
    >
      <button
        class="modal-close-btn"
        onclick={closeModal}
        aria-label="Close Modal"
      >
        <span class="material-symbols-outlined">close</span>
      </button>

      <div class="modal-body">
        <div
          class="modal-product-img-container"
          style="background-color: var(--color-surface-lowest); padding: 20px; border-radius: var(--radius-md); display: flex; flex-direction: column; gap: 8px; justify-content: center; align-items: center; width: 100%;"
        >
          <div
            style="background: white; padding: 12px; border-radius: var(--radius-sm); display: flex; flex-direction: column; align-items: center; justify-content: center; width: 220px; border: 1px solid var(--color-outline-variant);"
          >
            <div
              style="font-size: 8px; font-weight: 700; text-transform: uppercase; margin-bottom: 6px; color: black; letter-spacing: 0.5px;"
            >
              Sunrise Fine Jewells
            </div>
            <div
              style="width: 100%; height: 40px; display: flex; align-items: center; justify-content: center; overflow: hidden; margin-bottom: 4px;"
            >
              {@html generateCode39SVG(selectedProduct.id).svgContent}
            </div>
          </div>
        </div>

        <div class="modal-details-container">
          <span class="modal-category-tag"
            >{selectedProduct.category} • {selectedProduct.id}</span
          >
          <h3 id="modal-title" class="modal-product-title">
            {selectedProduct.name}
          </h3>
          <p class="modal-desc">{selectedProduct.description}</p>

          <div class="modal-purity-row">
            <span class="modal-spec-badge"
              >Weight: {selectedProduct.weight} g</span
            >
            <span class="modal-spec-badge"
              >Purity: {selectedProduct.purity}{selectedProduct.purity ===
              "Silver"
                ? ""
                : " Gold"}</span
            >
          </div>

          <h4 class="modal-section-title">Valuation Audit Logs</h4>

          <table class="breakdown-table">
            <tbody>
              <tr>
                <td
                  >{selectedProduct.purity === "Silver" ? "Silver" : "Gold"} Metal
                  Value
                  <small
                    >({selectedProduct.weight}g @ {formatCurrency(
                      selectedProduct.ratePerGram,
                    )}/g)</small
                  ></td
                >
                <td class="table-val"
                  >{formatCurrency(selectedProduct.metalValue)}</td
                >
              </tr>
              <tr>
                <td
                  >Making Charges <small
                    >({Math.round(selectedProduct.makingCharge * 100)}% of
                    Metal)</small
                  ></td
                >
                <td class="table-val"
                  >{formatCurrency(selectedProduct.makingCharges)}</td
                >
              </tr>
              {#if selectedProduct.fixedValue > 0}
                <tr>
                  <td>Fixed Accents / Gemstones Value</td>
                  <td class="table-val"
                    >{formatCurrency(selectedProduct.fixedValue)}</td
                  >
                </tr>
              {/if}
              <tr class="table-subtotal-row">
                <td>Subtotal Value</td>
                <td class="table-val"
                  >{formatCurrency(selectedProduct.subtotal)}</td
                >
              </tr>
              <tr>
                <td>Taxes & GST <small>(3%)</small></td>
                <td class="table-val">{formatCurrency(selectedProduct.gst)}</td>
              </tr>
              <tr class="table-total-row">
                <td>Total Valuation Cost</td>
                <td class="table-val text-primary-color"
                  >{formatCurrency(selectedProduct.totalPrice)}</td
                >
              </tr>
            </tbody>
          </table>

          <div style="margin-top: 16px; display: flex; gap: 12px;">
            <button
              class="btn btn-primary"
              style="flex-grow: 1;"
              onclick={() => addToBag(selectedProduct)}
            >
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

<!-- Live Webcam Scanner Modal -->
{#if showScannerModal}
  <div class="modal-backdrop" style="z-index: 1100;">
    <button
      class="modal-backdrop-close"
      onclick={stopScanner}
      aria-label="Close scanner overlay"
    ></button>
    <div
      class="modal-content glass-card"
      style="max-width: 500px; padding: 24px; position: relative;"
      role="dialog"
      aria-modal="true"
      aria-labelledby="scanner-modal-title"
      tabindex="-1"
    >
      <button
        class="modal-close-btn"
        onclick={stopScanner}
        aria-label="Close Scanner"
      >
        <span class="material-symbols-outlined">close</span>
      </button>

      <div
        style="display: flex; flex-direction: column; align-items: center; gap: 16px; width: 100%;"
      >
        <div
          style="display: flex; align-items: center; gap: 8px; width: 100%; justify-content: flex-start;"
        >
          <span
            class="material-symbols-outlined"
            style="color: var(--color-primary); font-size: 24px;"
            >photo_camera</span
          >
          <h3
            id="scanner-modal-title"
            style="margin: 0; font-family: var(--font-headline); font-size: 18px; font-weight: 600; color: var(--color-on-background);"
          >
            Webcam Barcode Scanner
          </h3>
        </div>

        <p
          style="margin: 0; font-size: 13px; color: var(--color-on-surface-variant); text-align: left; width: 100%;"
        >
          Hold the barcode tag in front of your camera. It will be scanned
          automatically.
        </p>

        <!-- Camera selection dropdown -->
        {#if camerasList.length > 1}
          <div
            style="width: 100%; display: flex; flex-direction: column; gap: 6px; align-items: flex-start;"
          >
            <label
              for="camera-select"
              style="font-size: 11px; font-weight: 600; text-transform: uppercase; color: var(--color-on-surface-variant);"
              >Select Camera Source</label
            >
            <select
              id="camera-select"
              class="select-input"
              style="width: 100%; box-sizing: border-box;"
              bind:value={activeCameraId}
              onchange={startCamera}
            >
              {#each camerasList as cam}
                <option value={cam.id}>{cam.label || `Camera ${cam.id}`}</option
                >
              {/each}
            </select>
          </div>
        {/if}

        <!-- Scanner display container -->
        <div
          style="width: 100%; aspect-ratio: 1.3333; background: #000; border-radius: var(--radius-md); overflow: hidden; position: relative; border: 2px solid var(--color-outline-variant);"
        >
          <div
            id="webcam-reader"
            style="width: 100%; height: 100%; object-fit: cover;"
          ></div>

          <!-- Crosshair / Scanning reticle -->
          {#if !scannerError}
            <div
              style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; pointer-events: none; z-index: 5;"
            >
              <div
                style="width: 70%; height: 40%; border: 2px solid var(--color-primary); border-radius: var(--radius-sm); position: relative; box-shadow: 0 0 0 9999px rgba(0,0,0,0.5);"
              >
                <!-- Laser scan animation -->
                <div
                  style="position: absolute; left: 0; right: 0; height: 2px; background-color: var(--color-primary); top: 0; animation: scanLine 2s linear infinite; box-shadow: 0 0 8px var(--color-primary);"
                ></div>
              </div>
              <span
                style="font-size: 11px; color: white; background: rgba(0,0,0,0.6); padding: 4px 8px; border-radius: var(--radius-sm); margin-top: 16px; font-family: monospace; letter-spacing: 0.5px;"
                >ALIGN TAG HERE</span
              >
            </div>
          {/if}
        </div>

        {#if scannerError}
          <div
            class="alert-box alert-error"
            style="width: 100%; box-sizing: border-box; margin: 0;"
          >
            <span class="material-symbols-outlined" style="font-size: 16px;"
              >error</span
            >
            <span>{scannerError}</span>
          </div>
        {/if}

        <button
          class="btn btn-secondary"
          style="width: 100%;"
          onclick={stopScanner}
        >
          Cancel Scan
        </button>
      </div>
    </div>
  </div>
{/if}

<!-- Cart Drawer Overlay -->
{#if showCartDrawer}
  <div class="cart-drawer-wrapper">
    <!-- Backdrop Button -->
    <button
      class="cart-drawer-backdrop"
      onclick={() => (showCartDrawer = false)}
      aria-label="Close Cart Overlay"
    ></button>
    <div
      class="cart-drawer"
      onclick={(e) => e.stopPropagation()}
      onkeydown={(e) => e.stopPropagation()}
      role="dialog"
      aria-modal="true"
      aria-label="Cart Drawer"
      tabindex="-1"
    >
      <div class="cart-header">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span
            class="material-symbols-outlined"
            style="color: var(--color-primary);">shopping_cart</span
          >
          <h3
            style="margin: 0; font-family: var(--font-headline); font-size: 18px; font-weight: 700;"
          >
            Valuation Cart
          </h3>
        </div>
        <button
          class="modal-close-btn"
          style="position: static; padding: 4px;"
          onclick={() => (showCartDrawer = false)}
          aria-label="Close Cart"
        >
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>

      <div class="cart-body">
        {#if cart.length === 0}
          <div
            style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: var(--color-on-surface-variant); opacity: 0.6; gap: 12px;"
          >
            <span class="material-symbols-outlined" style="font-size: 48px;"
              >shopping_cart_off</span
            >
            <p style="margin: 0; font-size: 14px; font-weight: 500;">
              Your cart is empty
            </p>
            <p style="margin: 0; font-size: 11px; text-align: center;">
              Scan a tag or click 'Sell' in the catalog to add items.
            </p>
          </div>
        {:else}
          {#each cart as item (item.id)}
            <div class="cart-item">
              <div class="cart-item-info">
                <span class="cart-item-name">{item.name}</span>
                <span class="cart-item-specs"
                  >ID: {item.id} • {item.purity} • {item.weight}g</span
                >
              </div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span class="cart-item-price"
                  >{formatCurrency(item.totalPrice)}</span
                >
                <button
                  class="cart-item-remove"
                  onclick={() => removeFromCart(item.id)}
                  aria-label="Remove item"
                >
                  <span
                    class="material-symbols-outlined"
                    style="font-size: 18px;">delete</span
                  >
                </button>
              </div>
            </div>
          {/each}
        {/if}
      </div>

      {#if cart.length > 0}
        <div class="cart-footer">
          <div
            style="display: flex; flex-direction: column; gap: 8px; border-bottom: 1px solid var(--color-outline-variant); padding-bottom: 12px;"
          >
            <div class="cart-summary-row">
              <span>Total Weight:</span>
              <span style="font-weight: 600;"
                >{cartTotalWeight.toFixed(2)} g</span
              >
            </div>
            <div class="cart-summary-row">
              <span>Subtotal:</span>
              <span>{formatCurrency(cartSubtotal)}</span>
            </div>
            <div class="cart-summary-row">
              <span>GST (3%):</span>
              <span>{formatCurrency(cartGst)}</span>
            </div>
            <div class="cart-total-row">
              <span>Grand Total:</span>
              <span style="color: var(--color-primary);"
                >{formatCurrency(cartTotalPrice)}</span
              >
            </div>
          </div>

          <!-- Checkout customer details form -->
          <form
            onsubmit={handleCheckout}
            style="display: flex; flex-direction: column; gap: 12px; margin-top: 4px;"
          >
            <div class="calc-row">
              <label for="cart-customer-name">Customer Name</label>
              <input
                id="cart-customer-name"
                type="text"
                class="calc-input"
                placeholder="Walk-in Customer"
                bind:value={customerName}
              />
            </div>
            <div class="calc-row">
              <label for="cart-customer-phone">Contact Number</label>
              <input
                id="cart-customer-phone"
                type="tel"
                class="calc-input"
                placeholder="Optional"
                bind:value={customerPhone}
              />
            </div>

            <button
              type="submit"
              class="btn btn-primary"
              style="width: 100%; margin-top: 8px; display: flex; align-items: center; justify-content: center; gap: 8px; padding: 12px;"
              disabled={checkoutStatus === "loading"}
            >
              <span class="material-symbols-outlined" style="font-size: 18px;"
                >point_of_sale</span
              >
              {checkoutStatus === "loading"
                ? "Processing Sale..."
                : "Complete Sale & Invoice"}
            </button>
          </form>
        </div>
      {/if}
    </div>
  </div>
{/if}

<!-- Invoice Receipt Modal -->
{#if showInvoiceModal && lastInvoice}
  <div class="modal-backdrop" style="z-index: 1200;">
    <button
      class="modal-backdrop-close"
      onclick={() => (showInvoiceModal = false)}
      aria-label="Close invoice overlay"
    ></button>
    <div
      class="modal-content glass-card invoice-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="invoice-title"
      tabindex="-1"
    >
      <button
        class="modal-close-btn"
        onclick={() => (showInvoiceModal = false)}
        aria-label="Close Invoice"
      >
        <span class="material-symbols-outlined">close</span>
      </button>

      <div
        style="max-height: 85vh; overflow-y: auto; overflow-x: hidden; display: flex; flex-direction: column; width: 100%; box-sizing: border-box;"
      >
        <div class="invoice-container" id="printable-invoice">
          <div class="invoice-header">
            <div class="invoice-logo">SUNRISE FINE JEWELLS</div>
            <div
              style="font-size: 10px; text-transform: uppercase; letter-spacing: 1px; margin-top: 4px; color: black;"
            >
              Premium Bullion & Fine Artistry
            </div>
            <div style="font-size: 9px; color: #666; margin-top: 2px;">
              Vercel Live Cloud Portal
            </div>
          </div>

          <div class="invoice-details" style="color: black;">
            <div><strong>Invoice ID:</strong> {lastInvoice.invoiceId}</div>
            <div><strong>Date:</strong> {lastInvoice.date}</div>
            <div><strong>Customer:</strong> {lastInvoice.customerName}</div>
            {#if lastInvoice.customerPhone && lastInvoice.customerPhone !== "N/A"}
              <div><strong>Phone:</strong> {lastInvoice.customerPhone}</div>
            {/if}
          </div>

          <table class="invoice-table">
            <thead>
              <tr>
                <th style="font-size: 10px; color: black; width: 55%;"
                  >Item Description</th
                >
                <th
                  style="text-align: right; font-size: 10px; color: black; width: 25%;"
                  >Purity/Wt</th
                >
                <th
                  style="text-align: right; font-size: 10px; color: black; width: 20%;"
                  >Price</th
                >
              </tr>
            </thead>
            <tbody>
              {#each lastInvoice.items as item}
                <tr>
                  <td style="color: black;">
                    <div>{item.name}</div>
                    <div
                      style="font-size: 8px; color: #555; font-family: monospace;"
                    >
                      ID: {item.id}
                    </div>
                  </td>
                  <td
                    style="text-align: right; vertical-align: top; color: black;"
                    >{item.purity} / {item.weight}g</td
                  >
                  <td
                    style="text-align: right; vertical-align: top; color: black;"
                    >{formatCurrency(item.totalPrice)}</td
                  >
                </tr>
              {/each}
              <tr class="total-border">
                <td style="color: black;">Subtotal</td>
                <td colspan="2" style="text-align: right; color: black;"
                  >{formatCurrency(lastInvoice.subtotal)}</td
                >
              </tr>
              <tr>
                <td style="color: black;">Taxes & GST (3%)</td>
                <td colspan="2" style="text-align: right; color: black;"
                  >{formatCurrency(lastInvoice.gst)}</td
                >
              </tr>
              <tr
                style="font-size: 13px; font-weight: bold; border-top: 1px dashed black; border-bottom: 2px dashed black;"
              >
                <td style="padding: 8px 0; color: black;">Grand Total</td>
                <td
                  colspan="2"
                  style="text-align: right; padding: 8px 0; color: black;"
                  >{formatCurrency(lastInvoice.total)}</td
                >
              </tr>
            </tbody>
          </table>

          <div
            style="text-align: center; font-size: 9px; margin-top: 25px; border-top: 1px dashed black; padding-top: 10px; color: #444;"
          >
            Thank you for shopping at Sunrise Fine Jewells!
            <br />
            This is a computer-generated invoice transaction.
          </div>
        </div>

        <div
          style="display: flex; gap: 12px; padding: 20px 30px; background-color: var(--color-surface-low); border-top: 1px solid var(--color-outline-variant);"
        >
          <button
            type="button"
            class="btn btn-primary"
            style="flex-grow: 1; display: flex; align-items: center; justify-content: center; gap: 8px;"
            onclick={() => window.print()}
          >
            <span class="material-symbols-outlined" style="font-size: 16px;"
              >print</span
            >
            Print Invoice
          </button>
          <button
            type="button"
            class="btn btn-secondary"
            onclick={() => (showInvoiceModal = false)}
          >
            Close Receipt
          </button>
        </div>
      </div>
    </div>
  </div>
{/if}

<footer
  style="margin-top: 60px; background-color: var(--color-surface-low); border-top: 2px solid var(--color-outline); padding: 20px 0; font-size: 11px; font-weight: 500; letter-spacing: 0.08em; text-transform: uppercase; color: var(--color-on-surface-variant); text-align: center;"
>
  <div class="container">
    Sunrise Fine Jewells &mdash; Internal Management Portal &copy; 2026
  </div>
</footer>

<style>
  /* Scoped style enhancements for Stock Entry Panel & Sidebar Rates */
  .entry-form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }

  .form-full-width {
    grid-column: span 2;
  }

  @media (max-width: 600px) {
    .entry-form-grid {
      grid-template-columns: 1fr;
    }
    .form-full-width {
      grid-column: span 1;
    }
  }

  .barcode-preview-card {
    background: var(--color-surface-lowest);
    border: 1px dashed var(--color-primary);
    border-radius: var(--radius-sm);
    padding: 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 120px;
    margin-top: 12px;
    text-align: center;
  }

  .barcode-svg-container {
    background-color: white;
    padding: 8px;
    border-radius: var(--radius-sm);
    max-width: 100%;
    height: auto;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .alert-box {
    padding: 12px 16px;
    border-radius: var(--radius-sm);
    font-size: 13px;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
    animation: fadeIn 0.3s ease-out;
  }

  .alert-error {
    background-color: rgba(255, 180, 171, 0.1);
    color: var(--color-error);
    border: 1px solid rgba(255, 180, 171, 0.25);
  }

  .alert-success {
    background-color: rgba(76, 175, 80, 0.1);
    color: var(--color-success);
    border: 1px solid rgba(76, 175, 80, 0.25);
  }

  /* Unified Bullion Rates Sidebar Panel */
  .sidebar-rate-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 0;
    border-bottom: 1px solid rgba(153, 144, 124, 0.1);
    transition: var(--transition-smooth);
  }

  .sidebar-rate-row:last-child {
    border-bottom: none;
  }

  .sidebar-rate-row:hover {
    background-color: rgba(242, 202, 80, 0.03);
    padding-left: 4px;
    padding-right: 4px;
  }

  .rate-row-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .rate-purity-badge {
    font-size: 10px;
    font-weight: 700;
    padding: 4px 8px;
    border-radius: var(--radius-sm);
    text-transform: uppercase;
  }

  .rate-value-display {
    font-family: var(--font-headline);
    font-size: 18px;
    font-weight: 600;
    color: var(--color-on-background);
  }

  /* Enhanced Inventory Summary Stats */
  .inventory-summary-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .summary-stat-card {
    background: var(--color-surface-lowest);
    border: 1px solid var(--color-outline-variant);
    border-radius: var(--radius-sm);
    padding: 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    transition: var(--transition-smooth);
  }

  .summary-stat-card:hover {
    border-color: rgba(242, 202, 80, 0.2);
  }

  .summary-stat-label {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--color-on-surface-variant);
  }

  .summary-stat-value {
    font-size: 18px;
    font-weight: 700;
    color: var(--color-on-background);
    text-align: right;
  }

  .summary-stat-subvalue {
    font-size: 11px;
    color: var(--color-on-surface-variant);
    font-weight: normal;
    display: block;
  }

  @keyframes scanLine {
    0% {
      top: 0%;
    }
    50% {
      top: 100%;
    }
    100% {
      top: 0%;
    }
  }

  /* Modal Styles */
  .modal-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background-color: rgba(0, 0, 0, 0.75);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    animation: fadeIn 0.2s ease-out;
  }

  .modal-backdrop-close {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: transparent;
    cursor: default;
    border: none;
  }

  .modal-content {
    position: relative;
    background-color: var(--color-surface);
    border: 1px solid var(--color-outline-variant);
    border-radius: var(--radius-lg);
    width: 90%;
    max-width: 600px;
    padding: 32px;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
    z-index: 1010;
    animation: scaleIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  .modal-close-btn {
    position: absolute;
    top: 16px;
    right: 16px;
    background: none;
    border: none;
    color: var(--color-on-surface-variant);
    cursor: pointer;
    transition: var(--transition-smooth);
    padding: 4px;
    border-radius: var(--radius-sm);
  }

  .modal-close-btn:hover {
    color: var(--color-primary);
    background-color: rgba(255, 255, 255, 0.05);
  }

  .modal-body {
    display: flex;
    flex-direction: column;
    gap: 24px;
    max-height: 80vh;
    overflow-y: auto;
  }

  @keyframes scaleIn {
    from {
      transform: scale(0.95);
      opacity: 0;
    }
    to {
      transform: scale(1);
      opacity: 1;
    }
  }

  /* Cart Drawer Styles */
  .cart-drawer-wrapper {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    z-index: 1050;
    display: flex;
    justify-content: flex-end;
  }

  .cart-drawer-backdrop {
    position: absolute;
    top: 0;
    left: 0;
    width: calc(100% - 420px);
    height: 100%;
    background-color: rgba(0, 0, 0, 0.4);
    backdrop-filter: blur(4px);
    -webkit-backdrop-filter: blur(4px);
    border: none;
    padding: 0;
    cursor: default;
    z-index: 1;
    animation: fadeIn 0.2s ease-out;
  }

  @media (max-width: 420px) {
    .cart-drawer-backdrop {
      width: 0;
      display: none;
    }
  }

  .cart-drawer {
    position: absolute;
    right: 0;
    top: 0;
    z-index: 2; /* higher than backdrop button */
    width: 100%;
    max-width: 420px;
    height: 100%;
    background-color: rgba(255, 255, 255, 0.75);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-left: 1.5px solid var(--color-outline);
    box-shadow: -4px 0 24px rgba(26, 22, 18, 0.15);
    display: flex;
    flex-direction: column;
    animation: slideInRight 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  }

  @keyframes slideInRight {
    from {
      transform: translateX(100%);
    }
    to {
      transform: translateX(0);
    }
  }

  .cart-header {
    padding: 20px 24px;
    border-bottom: 1.5px solid var(--color-outline);
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: rgba(240, 235, 227, 0.45);
  }

  .cart-body {
    flex-grow: 1;
    overflow-y: auto;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .cart-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 14px;
    background-color: var(--color-surface-dim);
    border: 1px solid var(--color-outline-variant);
    border-radius: var(--radius-sm);
    gap: 12px;
  }

  .cart-item-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex-grow: 1;
  }

  .cart-item-name {
    font-weight: 600;
    font-size: 13px;
    color: var(--color-on-background);
  }

  .cart-item-specs {
    font-size: 11px;
    color: var(--color-on-surface-variant);
  }

  .cart-item-price {
    font-weight: 700;
    font-size: 13px;
    color: var(--color-primary);
  }

  .cart-item-remove {
    background: none;
    border: none;
    color: var(--color-on-surface-variant);
    cursor: pointer;
    padding: 4px;
    border-radius: var(--radius-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: var(--transition-fast);
  }

  .cart-item-remove:hover {
    color: var(--color-error);
    background-color: rgba(185, 28, 28, 0.05);
  }

  .cart-footer {
    padding: 24px;
    border-top: 1.5px solid var(--color-outline);
    background-color: rgba(240, 235, 227, 0.45);
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .cart-summary-row {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    color: var(--color-on-surface-variant);
  }

  .cart-total-row {
    display: flex;
    justify-content: space-between;
    font-size: 16px;
    font-weight: 700;
    color: var(--color-on-background);
    border-top: 1.5px dashed var(--color-outline);
    padding-top: 12px;
  }

  .invoice-modal {
    max-width: 480px !important;
    padding: 0 !important;
    overflow: hidden;
  }

  .invoice-container {
    background-color: white;
    color: black;
    font-family: "Courier New", Courier, monospace;
    padding: 24px;
    border: 1px solid #ddd;
    font-size: 12px;
    line-height: 1.5;
    box-sizing: border-box;
    width: 100%;
  }

  .invoice-header {
    text-align: center;
    margin-bottom: 20px;
    border-bottom: 1.5px dashed black;
    padding-bottom: 15px;
  }

  .invoice-logo {
    font-family: var(--font-headline);
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 1px;
    color: black;
  }

  .invoice-details {
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin-bottom: 15px;
  }

  .invoice-table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 15px;
  }

  .invoice-table th,
  .invoice-table td {
    text-align: left;
    padding: 6px 0;
    color: black;
  }

  .invoice-table th {
    border-bottom: 1px dashed black;
  }

  .invoice-table td {
    font-size: 11px;
  }

  .invoice-table tr.total-border td {
    border-top: 1px dashed black;
    font-weight: bold;
  }

  @media print {
    @page {
      margin: 10mm !important;
    }
    :global(body) {
      background: white !important;
      color: black !important;
      margin: 0 !important;
      padding: 0 !important;
      width: 100% !important;
    }
    :global(.dashboard-container),
    :global(footer),
    :global(.cart-drawer-wrapper),
    :global(.modal-backdrop:not(:has(#printable-invoice))) {
      display: none !important;
    }
    :global(.modal-backdrop:has(#printable-invoice)) {
      position: static !important;
      display: block !important;
      width: 100% !important;
      height: auto !important;
      background: white !important;
      backdrop-filter: none !important;
      z-index: auto !important;
      box-shadow: none !important;
      padding: 0 !important;
      margin: 0 !important;
    }
    :global(.modal-backdrop:has(#printable-invoice) .modal-content) {
      position: static !important;
      display: block !important;
      width: 100% !important;
      max-width: 100% !important;
      box-shadow: none !important;
      border: none !important;
      padding: 0 !important;
      margin: 0 !important;
      background: white !important;
      animation: none !important;
    }
    :global(.modal-backdrop:has(#printable-invoice) .modal-content > div) {
      max-height: none !important;
      overflow: visible !important;
      display: block !important;
    }
    :global(.modal-backdrop:has(#printable-invoice) .modal-close-btn),
    :global(.modal-backdrop:has(#printable-invoice) button),
    :global(.modal-backdrop:has(#printable-invoice) .modal-backdrop-close) {
      display: none !important;
    }
    :global(#printable-invoice) {
      display: block !important;
      border: none !important;
      padding: 0 !important;
      margin: 0 !important;
      width: 100% !important;
      box-sizing: border-box !important;
    }
  }
</style>
