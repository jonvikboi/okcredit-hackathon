# Sunrise Fine Jewells - React Rebuild Project Brief

This document is written as a detailed handoff brief for AI Studio, Antigravity, or any AI coding tool that needs to recreate this project as a production-ready React application.

Important instruction for the builder: rebuild this project using the React framework. Do not rebuild it in Svelte. The existing repository is a SvelteKit hackathon prototype, but the new app should be implemented with React.

Recommended stack:

- Frontend: React with Vite, or Next.js if the builder wants one full-stack React framework.
- UI state: React hooks plus Context/Zustand for cart, live rates, and inventory state.
- Data fetching: TanStack Query or simple fetch hooks.
- Backend: Node.js API routes. If using Next.js, use App Router API route handlers. If using React + Vite, use an Express/Fastify backend.
- Database: MongoDB Atlas or a local MongoDB instance for development.
- Deployment: Vercel, Render, Railway, or any Node-compatible host.
- Barcode scanning: `html5-qrcode`.
- Barcode generation: Code 39 barcode generation in SVG/canvas, or a maintained barcode library.

## 1. Project Summary

The project is an internal jewellery store management portal for Sunrise Fine Jewells. It helps a jeweller manage stock, calculate live gold/silver valuations, generate barcode labels, scan items, sell items, and print invoices.

The main use case is this:

1. The store owner or staff enters jewellery stock into the system.
2. Each stock item gets a unique barcode ID and printable jewellery tag.
3. The app fetches live bullion rates for gold and silver.
4. Every item valuation updates automatically when the market rate changes.
5. Staff can search or scan a barcode to find an item.
6. Staff can open a full valuation audit for the item.
7. Staff can add items to a cart, complete checkout, remove sold stock from available inventory, and print an invoice.
8. All stock and rate data must be connected to a database, not kept only in local frontend state.

The app should feel like a premium but practical dashboard for a jewellery shop: fast, clear, reliable, and easy to use at a store counter.

## 2. Existing Prototype Overview

The current prototype contains these major pieces:

- A main dashboard page with live rates, stock catalog, stock entry, cart, invoice, scanner, and calculator.
- A MongoDB helper used to connect to database `okcredit_inventory`.
- Product persistence in the MongoDB `products` collection.
- A `rates` collection that stores the latest gold/silver rate document.
- A rate fetcher that connects to the Ambicaa bullion WebSocket feed.
- API endpoints for product creation/deletion and rate retrieval.
- A Vercel cron route for refreshing rates in the background.
- A local `products.json` fallback for development.
- A large `ambicaa_rates.csv` file containing captured historic rate ticks.

The React rebuild should keep the same business purpose and feature set, but the implementation should be cleaner and production-oriented.

## 3. Core App Name and Positioning

Suggested product name:

Sunrise Fine Jewells Inventory and Bullion Dashboard

Alternative shorter name:

Sunrise Jewells POS Dashboard

Purpose:

An internal jewellery inventory, valuation, barcode, and sales dashboard that recalculates product prices from live gold/silver market rates.

Target users:

- Jewellery shop owner
- Store manager
- Sales staff
- Back-office inventory staff

Primary environment:

- Desktop or laptop browser at shop counter
- Tablet support
- Mobile responsive support for quick checks

## 4. Must Use React

Build the new application using React.

Suggested React architecture:

- `App.jsx` as the root app shell if using Vite.
- `pages` or route components for Dashboard, Inventory, Sales, and Settings.
- Reusable components for rate cards, product table, product form, barcode label, scanner modal, cart drawer, invoice modal, and valuation breakdown modal.
- Custom hooks:
  - `useRates()`
  - `useProducts()`
  - `useCart()`
  - `useBarcodeScanner()`
  - `useValuation(product, rates)`
- API client module:
  - `api/products.js`
  - `api/rates.js`
  - `api/sales.js`

If using Next.js:

- Use React Server Components only where helpful.
- Put interactive dashboard pieces behind `"use client"`.
- Use `/app/api/.../route.js` for backend routes.
- Use server-side MongoDB connection helpers.

If using React + Vite:

- Build the UI in Vite.
- Create a separate Node/Express backend for `/api/products`, `/api/rates`, `/api/sales`, and `/api/cron/fetch-rates`.
- Proxy API calls from Vite dev server to the backend.

## 5. Main Features

### 5.1 Live Bullion Rates

The app must show live rates for:

- 24K gold, per gram
- 22K gold, per gram
- 18K gold, per gram
- Silver, per gram

The rates should update automatically and drive every valuation in the app.

Existing prototype logic:

- Gold 24K rate is derived from futures ask price.
- Gold 22K = `Math.round(gold24k * (22 / 24))`
- Gold 18K = `Math.round(gold24k * (18 / 24))`
- Silver = converted from ask price to per-gram value.

Rate conversion formulas:

```js
const gold24kPerGram = Math.round(goldAsk / 10);
const gold22kPerGram = Math.round(gold24kPerGram * (22 / 24));
const gold18kPerGram = Math.round(gold24kPerGram * (18 / 24));
const silverPerGram = Math.round((silverAsk / 1000) * 100) / 100;
```

The UI must show whether rates are:

- Live
- Polling from database/API
- Stale
- Manually overridden by owner

### 5.2 Manual Rate Override

The app needs an owner override toggle.

When override is off:

- Use live database/API rates.

When override is on:

- Owner can manually enter 24K gold rate per gram.
- Owner can manually enter silver rate per gram.
- 22K and 18K rates are recalculated from the manual 24K value.
- All inventory valuations and calculator results update immediately.

This is important because jewellery stores sometimes sell using owner-approved rates instead of raw market feed rates.

### 5.3 Stock/Inventory Connected to Database

Stock must be connected to a database. It must not be hardcoded only in frontend arrays.

Use MongoDB collections:

- `products`
- `rates`
- `rate_history`
- `sales`
- `users` or `staff` if authentication is added
- `audit_logs`

Products should load from the database on page load.

New stock entries should be saved to the database.

Sold items should be updated in the database.

Recommended production behavior:

- Do not permanently delete products during checkout.
- Mark sold products with `status: "sold"` and create a sale/invoice record.
- Keep the item history for audit and accounting.

The prototype currently deletes sold items from available inventory after checkout. The rebuild should improve this by marking items as sold while hiding them from available stock views.

### 5.4 Merchant Stock Entry

The dashboard must include a stock entry form for adding new jewellery items.

Fields:

- Product category
- Metal type
- Purity
- Weight in grams
- Making charge percentage
- Fixed gemstone/accent value
- Product name
- Description
- Image URL or uploaded product/label image

Categories from the prototype:

- Necklace
- Ring
- Bracelet
- Earrings
- Watch

Additional useful categories:

- Pendant
- Chain
- Bangle
- Coin
- Silver Item
- Custom

Purity values:

- 24K
- 22K
- 18K
- Silver

Auto-generated names:

- Example: `22K Gold Ring`
- Example: `18K Gold Earrings`

Auto-generated descriptions:

- Example: `Hand-crafted 22K gold ring with premium finish.`

Manual edits should be allowed.

### 5.5 Barcode ID Generation

Every item must have a unique barcode ID.

Current ID format:

```txt
PREFIX-YYYYMMDD-SEQ
```

Examples:

```txt
RNG-20260610-001
NKL-20260610-001
BRC-20260610-001
ERR-20260610-001
WCH-20260610-001
```

Prefix map:

```js
const prefixMap = {
  Ring: "RNG",
  Necklace: "NKL",
  Bracelet: "BRC",
  Earrings: "ERR",
  Watch: "WCH",
  Pendant: "PND",
  Chain: "CHN",
  Bangle: "BNG",
  Coin: "CON",
  Silver: "SLV",
  Custom: "GEN"
};
```

The app must check existing products in the database before assigning the next sequence number. Avoid duplicate IDs.

### 5.6 Barcode Label Generation

The app should generate a printable jewellery label for each item.

Label should include:

- Store name: `SUNRISE FINE JEWELLS`
- Barcode
- Item ID
- Optional weight/purity if space allows

Prototype uses Code 39 barcode generated as SVG and then combines it into a canvas PNG label.

React rebuild options:

- Use a barcode library such as `jsbarcode`.
- Or implement Code 39 generation manually.
- Generate preview as SVG in the UI.
- Allow downloading the label as PNG.
- Allow printing labels.

Label output:

- PNG download named `{itemId}_label.png`
- Clear black-on-white barcode
- Works on normal desktop printers and small jewellery tag printers

### 5.7 Stock Valuation Catalog

The main dashboard needs a stock catalog/table.

The table should show:

- Thumbnail or barcode preview
- Item ID
- Product name
- Category
- Purity
- Weight
- Rate used
- Metal value
- Making charge
- Fixed value
- GST
- Total valuation
- Actions

Actions:

- Add to cart
- Open full valuation audit modal
- Download/print label
- Edit item
- Mark as unavailable/sold if user has permission

Search:

- Search by product name
- Search by barcode/item ID

Filters:

- All
- Category
- Purity
- Available/sold status
- Metal type

Sort options:

- Newest first
- Highest value
- Lowest value
- Weight high to low
- Weight low to high

### 5.8 Valuation Audit Modal

Every product should have a full audit view.

The modal should show:

- Barcode preview
- Item name
- Item ID
- Category
- Purity
- Weight
- Description
- Current rate per gram
- Metal value calculation
- Making charge calculation
- Fixed gemstone/accent value
- Subtotal
- GST
- Final total
- Rate source and timestamp

Use this calculation:

```js
const metalValue = weightGrams * ratePerGram;
const makingCharges = metalValue * (makingChargePercent / 100);
const fixedValue = gemstoneOrAccentValue || 0;
const subtotal = metalValue + makingCharges + fixedValue;
const gst = subtotal * 0.03;
const total = subtotal + gst;
```

Round display values to whole INR unless the field is a per-gram rate that needs decimals.

GST:

- Use 3% GST for jewellery valuation.
- Store this as a configurable setting if possible.

### 5.9 Walk-in Calculator

The app needs a quick walk-in calculator for estimating jewellery pricing without adding an inventory item.

Fields:

- Weight in grams
- Purity
- Making charge percentage
- Fixed gemstone/accent value

Output:

- Estimated total with GST

This calculator must use the same active rates as the inventory valuation:

- Live rates if override is off
- Manual owner rates if override is on

### 5.10 Cart and Checkout

The app should include a cart drawer.

Users can add items from:

- Stock table
- Full audit modal
- Barcode scan result

Cart should show:

- Item name
- Item ID
- Purity
- Weight
- Current total valuation
- Remove button

Cart totals:

- Total weight
- Subtotal
- GST
- Grand total

Checkout form:

- Customer name
- Customer phone number
- Optional payment method

Checkout behavior:

1. Validate cart is not empty.
2. Capture a rate snapshot at checkout time.
3. Create a `sales` record in the database.
4. Mark products as `sold`.
5. Show invoice modal.
6. Clear cart.

Do not allow the same item to be added to the cart twice.

### 5.11 Invoice Receipt

After checkout, show an invoice modal.

Invoice should include:

- Store name
- Invoice number
- Date and time
- Customer name
- Customer phone
- Item rows
- Item ID
- Purity/weight
- Price
- Subtotal
- GST
- Grand total
- Thank you message

Add a print button using `window.print()`, or use a PDF generation library if needed.

Recommended invoice number:

```txt
SRF-YYYYMMDD-000001
```

The prototype uses a random invoice number such as `SRF-123456`. The rebuild should use a database-backed sequence or timestamp-based ID to avoid duplicate invoices.

### 5.12 Webcam Barcode Scanner

The app should include a scanner modal using webcam access.

Use library:

```txt
html5-qrcode
```

Scanner behavior:

1. User clicks Scan Barcode.
2. Browser asks for camera permission.
3. App lists available cameras if more than one exists.
4. User points camera at jewellery tag.
5. Scanner reads barcode text.
6. App searches the product database/current inventory by item ID.
7. If product exists and is available, open valuation modal or add it to cart.
8. If product is not found, show a clear error message.

Barcode scanner UX:

- Show camera preview.
- Show scan box/reticle.
- Show errors for no camera, denied permission, unsupported browser, or item not found.
- Stop camera stream when modal closes.

### 5.13 Live Feed Audit Console

The dashboard should show recent market feed ticks.

Each log row should include:

- Timestamp
- Symbol
- Bid
- Ask
- LTP

The logs help the owner see what market data is feeding the valuation.

## 6. Live Rate Integration Details

The prototype connects to Ambicaa's bullion feed:

```txt
ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web
```

Frontend dev proxy in the prototype:

```txt
/ws-bullion -> ws://ambicaaspot.com:1001/bullion
```

The feed behaves like a SignalR-style WebSocket.

Initial handshake:

```js
ws.send('{"protocol":"json","version":1}\x1e');
```

Subscription message after handshake:

```js
ws.send(JSON.stringify({
  arguments: ["ambicaa"],
  invocationId: "0",
  target: "client",
  type: 1
}) + "\x1e");
```

Relevant message targets:

```txt
workerPublish
workerPublishCoin
referanceDetails
symbolDetails
```

The message payload can contain a base64-encoded gzip JSON string. Decode it before reading rates.

Browser decoding approach:

```js
const binaryString = atob(encoded);
const bytes = new Uint8Array(binaryString.length);
for (let i = 0; i < binaryString.length; i++) {
  bytes[i] = binaryString.charCodeAt(i);
}
// Then use DecompressionStream("gzip") and JSON.parse(...)
```

Node decoding approach:

```js
import zlib from "zlib";

const compressed = Buffer.from(encoded, "base64");
const decompressed = zlib.gunzipSync(compressed);
const payload = JSON.parse(decompressed.toString("utf-8"));
```

Relevant symbols:

Gold:

```txt
GOLD26JUNFUT
117574919
GOLD26AUGFUT
119445255
```

Silver:

```txt
SILVER26JULFUT
118822407
SILVER26SEPFUT
120761607
```

Suggested production architecture for rates:

1. Backend worker connects to the bullion WebSocket.
2. Backend extracts gold and silver rates.
3. Backend stores latest rates in MongoDB.
4. Backend stores recent ticks in `rate_history` or embedded `logs`.
5. Frontend polls `/api/rates` every 5 seconds or subscribes via Server-Sent Events/WebSocket.
6. If the feed is unavailable, frontend continues using latest database rates and marks them stale.
7. If the database has no rate yet, use safe fallback rates so the app does not crash.

Recommended rate freshness:

- Fresh: less than 60 seconds old
- Warning/stale: older than 5 minutes
- Offline: no successful update for 30 minutes

The current prototype has a cron route. Its comment says it should run every minute, but its Vercel schedule is currently daily:

```json
{
  "crons": [
    {
      "path": "/api/cron/fetch-rates",
      "schedule": "0 0 * * *"
    }
  ]
}
```

For live-ish production behavior, change this to every minute if the deployment platform allows it:

```json
{
  "crons": [
    {
      "path": "/api/cron/fetch-rates",
      "schedule": "* * * * *"
    }
  ]
}
```

If true real-time updates are required, use a persistent backend worker instead of relying only on serverless cron.

## 7. Database Design

Use MongoDB. The app should read/write stock and sales data from the database.

### 7.1 `products` Collection

Recommended schema:

```json
{
  "_id": "ObjectId",
  "itemCode": "RNG-20260610-001",
  "name": "22K Gold Ring",
  "category": "Ring",
  "metal": "Gold",
  "purity": "22K",
  "weightGrams": 5.25,
  "makingChargePercent": 12,
  "fixedValue": 0,
  "description": "Hand-crafted 22K gold ring with premium finish.",
  "imageUrl": "https://example.com/image.jpg",
  "labelImage": "data:image/png;base64,...",
  "status": "available",
  "createdAt": "2026-06-10T00:00:00.000Z",
  "updatedAt": "2026-06-10T00:00:00.000Z",
  "soldAt": null,
  "createdBy": "staff-user-id"
}
```

Allowed product statuses:

```txt
available
reserved
sold
archived
```

Important:

- Keep `itemCode` unique.
- Add an index on `itemCode`.
- Add indexes on `status`, `category`, and `createdAt`.

MongoDB indexes:

```js
db.products.createIndex({ itemCode: 1 }, { unique: true });
db.products.createIndex({ status: 1 });
db.products.createIndex({ category: 1 });
db.products.createIndex({ createdAt: -1 });
```

If matching the current prototype exactly, the old field names are:

```json
{
  "id": "GLD-R002",
  "name": "Stellar Diamond Ring",
  "purity": "18K",
  "weight": 5,
  "makingCharge": 0.15,
  "fixedValue": 55000,
  "category": "Ring",
  "description": "An elegant solitaire diamond ring...",
  "image": "https://example.com/image.jpg"
}
```

For the rebuild, prefer the clearer production schema with `itemCode`, `weightGrams`, and `makingChargePercent`.

### 7.2 `rates` Collection

Store the latest rate document:

```json
{
  "_id": "ObjectId",
  "key": "latest_rates",
  "gold24kPerGram": 15485,
  "gold22kPerGram": 14195,
  "gold18kPerGram": 11614,
  "silverPerGram": 266.16,
  "source": "ambicaa",
  "timestamp": "2026-06-10T00:00:00.000Z",
  "stale": false,
  "logs": [
    {
      "timestamp": "2026-06-10T10:23:07.000Z",
      "symbol": "GOLD26AUGFUT",
      "bid": 154773,
      "ask": 154809,
      "ltp": 154816
    }
  ]
}
```

Index:

```js
db.rates.createIndex({ key: 1 }, { unique: true });
```

### 7.3 `rate_history` Collection

Store rate ticks for audit and charts:

```json
{
  "_id": "ObjectId",
  "symbol": "GOLD26AUGFUT",
  "bid": 154773,
  "ask": 154809,
  "ltp": 154816,
  "derivedRatePerGram": 15481,
  "metal": "Gold",
  "timestamp": "2026-06-10T10:23:07.000Z",
  "source": "ambicaa"
}
```

Indexes:

```js
db.rate_history.createIndex({ timestamp: -1 });
db.rate_history.createIndex({ symbol: 1, timestamp: -1 });
```

### 7.4 `sales` Collection

Store checkout/invoice data:

```json
{
  "_id": "ObjectId",
  "invoiceNo": "SRF-20260610-000001",
  "customer": {
    "name": "Walk-in Customer",
    "phone": "9999999999"
  },
  "items": [
    {
      "productId": "ObjectId",
      "itemCode": "RNG-20260610-001",
      "name": "22K Gold Ring",
      "category": "Ring",
      "purity": "22K",
      "weightGrams": 5.25,
      "ratePerGram": 14195,
      "metalValue": 74524,
      "makingCharge": 8943,
      "fixedValue": 0,
      "subtotal": 83467,
      "gst": 2504,
      "total": 85971
    }
  ],
  "rateSnapshot": {
    "gold24kPerGram": 15485,
    "gold22kPerGram": 14195,
    "gold18kPerGram": 11614,
    "silverPerGram": 266.16,
    "timestamp": "2026-06-10T10:30:00.000Z",
    "isManualOverride": false
  },
  "subtotal": 83467,
  "gst": 2504,
  "total": 85971,
  "paymentMethod": "cash",
  "status": "completed",
  "createdAt": "2026-06-10T10:30:00.000Z",
  "createdBy": "staff-user-id"
}
```

Indexes:

```js
db.sales.createIndex({ invoiceNo: 1 }, { unique: true });
db.sales.createIndex({ createdAt: -1 });
db.sales.createIndex({ "customer.phone": 1 });
```

### 7.5 `audit_logs` Collection

Store important actions:

```json
{
  "_id": "ObjectId",
  "type": "product.created",
  "actorId": "staff-user-id",
  "message": "Created product RNG-20260610-001",
  "metadata": {
    "itemCode": "RNG-20260610-001"
  },
  "createdAt": "2026-06-10T10:30:00.000Z"
}
```

Useful audit types:

```txt
product.created
product.updated
product.sold
product.archived
rate.updated
rate.override.enabled
rate.override.disabled
sale.completed
invoice.printed
scanner.item_found
scanner.item_not_found
```

## 8. API Requirements

Use JSON APIs.

### 8.1 Product APIs

`GET /api/products`

Query params:

- `status=available`
- `category=Ring`
- `search=RNG`
- `purity=22K`

Response:

```json
{
  "success": true,
  "products": []
}
```

`POST /api/products`

Creates a new product and barcode ID.

Request:

```json
{
  "name": "22K Gold Ring",
  "category": "Ring",
  "metal": "Gold",
  "purity": "22K",
  "weightGrams": 5.25,
  "makingChargePercent": 12,
  "fixedValue": 0,
  "description": "Hand-crafted 22K gold ring with premium finish.",
  "imageUrl": ""
}
```

Response:

```json
{
  "success": true,
  "product": {}
}
```

`PATCH /api/products/:itemCode`

Updates an item.

`DELETE /api/products/:itemCode`

Archive only. Avoid hard delete in production.

`POST /api/products/:itemCode/mark-sold`

Marks one item as sold.

### 8.2 Rate APIs

`GET /api/rates`

Returns latest rates:

```json
{
  "success": true,
  "source": "mongodb",
  "stale": false,
  "ageSeconds": 12,
  "gold24kPerGram": 15485,
  "gold22kPerGram": 14195,
  "gold18kPerGram": 11614,
  "silverPerGram": 266.16,
  "timestamp": "2026-06-10T10:30:00.000Z",
  "logs": []
}
```

`POST /api/rates/refresh`

Protected endpoint used by cron or admin action.

Headers:

```txt
Authorization: Bearer CRON_SECRET
```

Behavior:

- Connect to Ambicaa WebSocket.
- Wait for a valid tick.
- Save latest rates to MongoDB.
- Save recent logs/history.
- Return saved rates.

`GET /api/rates/stream`

Optional Server-Sent Events endpoint for live updates.

### 8.3 Sales APIs

`POST /api/sales/checkout`

Request:

```json
{
  "customer": {
    "name": "Walk-in Customer",
    "phone": ""
  },
  "itemCodes": ["RNG-20260610-001"],
  "paymentMethod": "cash",
  "manualRateOverride": null
}
```

Response:

```json
{
  "success": true,
  "sale": {},
  "invoice": {}
}
```

Checkout must:

- Validate all items exist.
- Validate all items are `available`.
- Recalculate totals on backend using the active rate snapshot.
- Mark items as `sold`.
- Create one `sales` document.
- Return invoice data.

`GET /api/sales`

List historical sales.

`GET /api/sales/:invoiceNo`

Open invoice details.

### 8.4 Barcode APIs

Barcode generation can happen on frontend, but the backend should still own the unique ID generation.

Suggested endpoint:

`GET /api/products/next-code?category=Ring`

Response:

```json
{
  "success": true,
  "itemCode": "RNG-20260610-001"
}
```

Or generate the code during `POST /api/products`.

## 9. React Component Breakdown

Suggested component structure:

```txt
src/
  App.jsx
  main.jsx
  styles/
    globals.css
  api/
    products.js
    rates.js
    sales.js
  hooks/
    useRates.js
    useProducts.js
    useCart.js
    useValuation.js
    useBarcodeScanner.js
  components/
    AppShell.jsx
    DashboardHeader.jsx
    MetricCard.jsx
    LiveRatesPanel.jsx
    RateOverrideControls.jsx
    StockEntryForm.jsx
    BarcodeLabel.jsx
    ProductCatalog.jsx
    ProductFilters.jsx
    ProductTable.jsx
    ProductRow.jsx
    ValuationAuditModal.jsx
    ScannerModal.jsx
    CartDrawer.jsx
    InvoiceModal.jsx
    WalkInCalculator.jsx
    LiveFeedConsole.jsx
  utils/
    currency.js
    valuation.js
    barcode.js
    date.js
```

Backend structure if using Express:

```txt
server/
  index.js
  db.js
  routes/
    products.js
    rates.js
    sales.js
  services/
    rateFetcher.js
    valuationService.js
    invoiceService.js
    barcodeCodeService.js
  middleware/
    auth.js
```

Backend structure if using Next.js:

```txt
app/
  page.jsx
  api/
    products/
      route.js
    products/[itemCode]/
      route.js
    rates/
      route.js
    rates/refresh/
      route.js
    sales/checkout/
      route.js
lib/
  db.js
  rateFetcher.js
  valuation.js
  invoice.js
```

## 10. UI/UX Requirements

Design style:

- Premium jewellery dashboard.
- Warm off-white background.
- Antique gold accent.
- Clear cards and tables.
- Professional, not flashy.
- Desktop-first but responsive.

Recommended colors from the prototype:

```css
:root {
  --color-bg: #f4f1ec;
  --color-surface: #ffffff;
  --color-surface-muted: #f8f5f0;
  --color-primary: #9a7b3e;
  --color-primary-light: #c4a35a;
  --color-accent: #3b6e5e;
  --color-text: #1a1612;
  --color-text-muted: #6b5e4c;
  --color-border: #d2c8b8;
  --color-error: #b91c1c;
  --color-success: #166534;
}
```

Typography:

- Headings: serif font such as Cormorant Garamond or Playfair Display.
- Body: Inter, Montserrat, or system sans-serif.
- Codes/barcodes/logs: JetBrains Mono or monospace.

Layout:

- Header with app title and cart button.
- Two-column dashboard on desktop:
  - Left: stock entry and catalog.
  - Right: live rates, totals, calculator, logs.
- Single-column layout on tablets/mobile.
- Sticky or easily reachable cart button.

No marketing landing page is needed. The first screen should be the working dashboard.

## 11. Important Business Rules

1. All prices are dynamic and depend on the active rate at the moment of viewing.
2. Sold invoice totals must use a fixed rate snapshot from checkout time, not future live rates.
3. Available inventory valuation should recalculate when live rates change.
4. Manual rate override should affect UI calculations immediately.
5. Manual rate override should be clearly visible so staff know prices are not using live rates.
6. Product IDs/barcodes must be unique.
7. Scanner must not sell unavailable/sold items.
8. Checkout must be recalculated on the backend to prevent frontend manipulation.
9. Database is the source of truth for stock and sales.
10. Rate feed failures must not break the app.

## 12. Valuation Utility

Implement a shared valuation function and use it everywhere.

```js
export function getRateForProduct(product, rates) {
  if (product.metal === "Silver" || product.purity === "Silver") {
    return rates.silverPerGram;
  }

  if (product.purity === "24K") return rates.gold24kPerGram;
  if (product.purity === "22K") return rates.gold22kPerGram;
  if (product.purity === "18K") return rates.gold18kPerGram;

  return rates.gold24kPerGram;
}

export function calculateValuation(product, rates, gstPercent = 3) {
  const ratePerGram = getRateForProduct(product, rates);
  const weightGrams = Number(product.weightGrams || product.weight || 0);
  const makingChargePercent = Number(product.makingChargePercent ?? 0);
  const fixedValue = Number(product.fixedValue || 0);

  const metalValue = weightGrams * ratePerGram;
  const makingCharge = metalValue * (makingChargePercent / 100);
  const subtotal = metalValue + makingCharge + fixedValue;
  const gst = subtotal * (gstPercent / 100);
  const total = subtotal + gst;

  return {
    ratePerGram,
    metalValue: Math.round(metalValue),
    makingCharge: Math.round(makingCharge),
    fixedValue: Math.round(fixedValue),
    subtotal: Math.round(subtotal),
    gst: Math.round(gst),
    total: Math.round(total)
  };
}
```

If importing old prototype data, convert:

```js
const product = {
  itemCode: old.id,
  weightGrams: old.weight,
  makingChargePercent: old.makingCharge <= 1 ? old.makingCharge * 100 : old.makingCharge,
  imageUrl: old.image
};
```

## 13. Environment Variables

Required:

```txt
MONGODB_URI=mongodb+srv://...
DATABASE_NAME=okcredit_inventory
CRON_SECRET=some-secure-secret
```

Optional:

```txt
RATE_FEED_URL=ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web
GST_PERCENT=3
NEXT_PUBLIC_APP_NAME=Sunrise Fine Jewells
```

Never expose `MONGODB_URI` or `CRON_SECRET` to the browser.

## 14. Authentication and Permissions

The prototype does not include full authentication. The production rebuild should ideally add it.

Roles:

- Owner/admin
- Manager
- Sales staff

Permissions:

Owner/admin:

- Add/edit/archive products
- Override rates
- View sales history
- View audit logs
- Manage staff

Manager:

- Add/edit products
- Complete checkout
- View sales

Sales staff:

- Search/scan products
- Add to cart
- Complete checkout
- Print invoice

Manual rate override should require owner/admin permission.

## 15. Error Handling

Handle these cases:

- MongoDB connection failed
- Rate feed unavailable
- Rate document missing
- Product creation validation failed
- Duplicate barcode ID
- Camera permission denied
- No camera found
- Barcode scanned but item not found
- Barcode scanned but item already sold
- Checkout attempted with empty cart
- Checkout attempted with stale product data
- Network request failed

Show clear, human-readable messages.

## 16. Validation Rules

Product form:

- Weight must be greater than 0.
- Making charge must be 0 or greater.
- Fixed value must be 0 or greater.
- Product name required.
- Category required.
- Purity required.
- Item code must be unique.

Checkout:

- Cart must have at least one item.
- Every item must exist.
- Every item must be available.
- Customer phone is optional, but if entered should be valid.

Rate override:

- Gold 24K override must be within a reasonable range.
- Silver override must be within a reasonable range.
- Show that override is active.

Suggested ranges:

```txt
Gold 24K: 1000 to 30000 INR/gram
Silver: 10 to 1000 INR/gram
```

## 17. Acceptance Criteria

The build is complete when:

1. The app is built in React.
2. The dashboard loads products from MongoDB.
3. New stock items are saved to MongoDB.
4. Every new item receives a unique barcode ID.
5. Barcode label preview and download works.
6. Product valuations use live rates.
7. Live rates refresh automatically from API/database.
8. Rate feed fallback works if the WebSocket fails.
9. Manual rate override updates all valuations immediately.
10. The stock catalog can search and filter products.
11. The webcam scanner can find products by barcode.
12. The cart can add/remove items.
13. Checkout creates a sale record in MongoDB.
14. Sold products are no longer shown as available stock.
15. Invoice modal appears after checkout and can be printed.
16. Walk-in calculator uses the same active rates.
17. Recent feed logs are shown in the dashboard.
18. The app is responsive on desktop, tablet, and mobile.
19. Sensitive database credentials remain server-side only.
20. The app can be deployed with documented environment variables.

## 18. Suggested AI Studio / Antigravity Prompt

Use this prompt to generate the React version:

```txt
Build a production-ready React application for Sunrise Fine Jewells, a jewellery store inventory, live bullion valuation, barcode, and sales dashboard.

Important: Use the React framework. Do not use Svelte. Prefer React + Vite with a Node/Express backend, or Next.js with API routes if you want a full-stack React framework.

The app must connect stock inventory to MongoDB. Products must be loaded from the database, new products must be saved to the database, and sold products must be marked as sold in the database.

Core features:
- Live bullion rates for 24K gold, 22K gold, 18K gold, and silver.
- Backend rate fetcher that reads the Ambicaa bullion WebSocket feed, decodes base64 gzip JSON payloads, extracts gold/silver ask prices, stores latest rates in MongoDB, and exposes GET /api/rates.
- Frontend should refresh rates automatically every 5 seconds or use SSE/WebSocket streaming.
- Manual owner rate override for 24K gold and silver.
- Product valuation recalculates automatically from active rates.
- Stock entry form with category, metal, purity, weight, making charge percent, fixed gemstone value, name, description, and image.
- Unique barcode item codes like RNG-YYYYMMDD-001.
- Barcode label preview and PNG download.
- Stock valuation catalog with search, filters, barcode preview, price breakdown, and actions.
- Webcam barcode scanner using html5-qrcode.
- Valuation audit modal showing metal value, making charge, fixed value, subtotal, GST, and total.
- Cart drawer for selling items.
- Checkout flow that creates a sales/invoice record, captures a rate snapshot, marks products as sold, and shows printable invoice.
- Walk-in calculator for quick quotes.
- Recent live feed log console.

Pricing rules:
- gold24kPerGram = Math.round(goldAsk / 10)
- gold22kPerGram = Math.round(gold24kPerGram * 22 / 24)
- gold18kPerGram = Math.round(gold24kPerGram * 18 / 24)
- silverPerGram = Math.round((silverAsk / 1000) * 100) / 100
- metalValue = weightGrams * ratePerGram
- makingCharge = metalValue * (makingChargePercent / 100)
- subtotal = metalValue + makingCharge + fixedValue
- gst = subtotal * 0.03
- total = subtotal + gst

Use MongoDB collections: products, rates, rate_history, sales, audit_logs.

Make the UI a premium internal jewellery dashboard: warm off-white background, antique gold accents, clear tables, responsive layout, and a practical counter-sales workflow. The first screen should be the working dashboard, not a landing page.

Include all required environment variables, setup instructions, and seed data. Make sure the app can run locally and be deployed.
```

## 19. Local Development Notes

For a React + Vite frontend with Express backend:

```txt
npm create vite@latest sunrise-jewells-react -- --template react
cd sunrise-jewells-react
npm install
npm install @tanstack/react-query html5-qrcode jsbarcode zustand
npm install express mongodb ws cors dotenv
npm install -D nodemon concurrently
```

Suggested scripts:

```json
{
  "scripts": {
    "dev": "concurrently \"npm run dev:client\" \"npm run dev:server\"",
    "dev:client": "vite",
    "dev:server": "nodemon server/index.js",
    "build": "vite build",
    "preview": "vite preview"
  }
}
```

For Next.js:

```txt
npx create-next-app@latest sunrise-jewells-react
cd sunrise-jewells-react
npm install mongodb html5-qrcode jsbarcode zustand @tanstack/react-query
```

## 20. Seed Data

Use sample products similar to:

```json
[
  {
    "itemCode": "GLD-N001",
    "name": "Celestial Gold Necklace",
    "category": "Necklace",
    "metal": "Gold",
    "purity": "22K",
    "weightGrams": 45,
    "makingChargePercent": 12,
    "fixedValue": 0,
    "description": "A traditional 22K gold necklace with intricate floral patterns.",
    "imageUrl": "",
    "status": "available"
  },
  {
    "itemCode": "GLD-R002",
    "name": "Stellar Diamond Ring",
    "category": "Ring",
    "metal": "Gold",
    "purity": "18K",
    "weightGrams": 5,
    "makingChargePercent": 15,
    "fixedValue": 55000,
    "description": "An elegant solitaire diamond ring with an 18K gold band.",
    "imageUrl": "",
    "status": "available"
  }
]
```

Seed rates:

```json
{
  "key": "latest_rates",
  "gold24kPerGram": 15485,
  "gold22kPerGram": 14195,
  "gold18kPerGram": 11614,
  "silverPerGram": 266.16,
  "source": "seed",
  "timestamp": "2026-06-10T00:00:00.000Z",
  "stale": true,
  "logs": []
}
```

## 21. Final Build Checklist

Before considering the rebuild done:

- Run lint and build.
- Test adding a product.
- Confirm the product appears after browser refresh.
- Confirm the database contains the product.
- Confirm barcode label preview/download.
- Confirm live rate API returns data.
- Confirm valuations change when rates change.
- Confirm manual override changes valuations.
- Confirm scanner can find an item.
- Confirm checkout creates a sale.
- Confirm sold item disappears from available catalog.
- Confirm invoice prints cleanly.
- Confirm mobile layout does not overflow.
- Confirm environment variables are documented.

