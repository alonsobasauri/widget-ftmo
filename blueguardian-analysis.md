# Análisis de https://trader.blueguardian.com/shared/{sharedId}

## Cómo se sirve la página

`https://trader.blueguardian.com/shared/{sharedId}` es una app **Next.js 15 (App Router,
build con Turbopack)** detrás de Cloudflare. La ruta **no tiene datos server-side**: el HTML
que llega son ~51 KB de cascarón cuyo único texto visible es `Loading`. El payload RSC
(`self.__next_f`) sólo trae providers (`ReduxStoreProvider`, `ThemeProvider`,
`LoadingBoundaryProvider`, …) y ni un número de la cuenta.

Todo se pide **desde el cliente**, con RTK Query, contra el gateway `api.trader.blueguardian.com`
(hardcodeado en el bundle como `serverUrl`, no como `NEXT_PUBLIC_*` en runtime).

Es decir: **igual que FTMO, hay API pública sin auth** — y aquí incluso es más limpio,
porque no hay nada de SSR que parsear.

## El identificador

`6a532e9fa613655dff936fc5` son 24 hex = **ObjectId de MongoDB**. Sus primeros 4 bytes
(`0x6a532e9f` = 1783836319) dan `2026-07-12T06:05:19Z`, y la respuesta trae
`account.startDate = "2026-07-12T06:05:19.473Z"` — coinciden al segundo.

**El id del share es el `_id` del documento de la cuenta**, no un token de compartir aparte.
Se confirma porque `GET /v1/accounts/{mismo-id}/stats` devuelve **401** (no 404): la ruta
reconoce el id, sólo exige sesión. Consecuencias prácticas:

- La config del widget para Blue Guardian es **un solo campo** (no `login` + `code` como FTMO).
- No hay secreto rotable: quien tenga el id de la cuenta puede leer el endpoint `shared`.
  Si algún día quieres "dejar de compartir", probablemente sea un flag del lado servidor,
  no un cambio de token. Dato a tener en cuenta, no bloquea nada.

## Endpoints públicos (sin auth, sólo el `sharedId`)

Base: `https://api.trader.blueguardian.com` — backend **Express** (`x-powered-by: Express`).

### 1) Estadísticas completas — lo que pinta la pantalla

```
GET /v1/accounts/shared/{sharedId}
```

Devuelve `{ statistics, payout, account, addons }`. ~4 KB.

### 2) Serie temporal para el gráfico

```
POST /v1/accounts/shared/{sharedId}/growth
Content-Type: application/json

{"filter":"daily"}
```

Devuelve un array plano `[{date, value, name}]` con **6 series** multiplexadas por `name`.

> **Ojo con el método**: es `POST`, no `GET`. Un `GET` devuelve 404 y es fácil concluir
> que el endpoint no existe. El `Content-Type: application/json` **es obligatorio** para
> que el `filter` surta efecto: sin él Express no parsea el body y cae al agrupamiento
> por defecto, que etiqueta todas las fechas como `"2026"` (mismas 144 filas, label inútil).

### Lo que NO es público

| Ruta | Sin auth |
|---|---|
| `/v1/accounts/{id}/stats` | 401 |
| `/v1/accounts/{id}/daily-summary` | 401 |
| `/v1/accounts/{id}/trade-summary` | 401 |
| `/v1/accounts/shared/{id}/daily-summary` | 500 |
| `/v1/accounts/shared/{id}/trades` | 404 |

Sólo los dos endpoints `shared` de arriba son anónimos. No hay lista de trades ni
daily-summary público.

## Headers requeridos

**Ninguno.** Un `curl` pelado sin `Accept`, sin `Referer`, sin `Origin` y sin cookies
devuelve 200. La respuesta trae `vary: Origin` y `access-control-allow-credentials: true`,
pero no hay CORS restrictivo ni challenge de Cloudflare (`cf-cache-status: DYNAMIC`).
Para el POST el único header necesario es `Content-Type: application/json`.

## Forma más simple de scrapearlo

```bash
ID=6a532e9fa613655dff936fc5

# 1) estadísticas
curl -s "https://api.trader.blueguardian.com/v1/accounts/shared/$ID"

# 2) curva diaria
curl -s -X POST "https://api.trader.blueguardian.com/v1/accounts/shared/$ID/growth" \
  -H 'Content-Type: application/json' -d '{"filter":"daily"}'
```

Errores: id inexistente → `400 {"error":true,"message":"No account found with login","statusCode":400}`.

---

## Shape del JSON

### `GET /v1/accounts/shared/{sharedId}`

**Todos los montos son floats planos en la moneda de `account.programCurrency`.**
No hay envoltorio `{value, decimal, currency}` como en FTMO — `Format.kt` y `Money`
**no aplican** aquí. Los porcentajes vienen ya en escala 0–100 (`winRate: 33.9198…`),
no como fracción.

Los floats llegan sin redondear (`todayPnL: -1.6500000000014552`), hay que formatear al pintar.

#### `statistics` (~90 campos; los relevantes)

**Estado actual**

| Campo | Ejemplo | Nota |
|---|---|---|
| `currentBalance` / `balance` | `24212.27` | duplicados, mismo valor |
| `currentEquity` / `equity` | `24210.62` | duplicados |
| `startingBalance` | `25000` | |
| `dailyTotalPnL` | `-1.65` | **es el "today's P&L" que pinta el banner** |
| `todayPnL` | `-1.6500000000014552` | mismo valor sin redondear |
| `dailyTotalRealizedPnL` | `0` | sólo cerrado |
| `currentProfit` | `-789.38` | P&L acumulado vs. balance inicial |
| `currentProfitPercent` | `-3.16` | |
| `allTimeTotalPnL` | `-839.5` | |
| `growth` | `-3.15` | % |
| `weeklyTotalPnL` | `81.88` | |

**Límites (niveles absolutos ya calculados por el servidor)**

| Campo | Ejemplo |
|---|---|
| `maxDailyLossPercent` | `3` |
| `maxDailyLossLimitEquityLevel` | `23550.12` |
| `maxDailyLossLimitPnLLevel` | `-750` |
| `maxLossPercent` | `6` |
| `maxLossLimitEquityLevel` | `23550.12` |
| `maxLossLimitPnLLevel` | `-1500` |
| `profitTargetRequiredPnLLevel` | `0` (cuenta funded, sin target) |
| `minTradingDays` | `5` |
| `activeTradingDays` | `0` |
| `dailyLossResetUTCTime` | `"2026-08-04T21:00:00.000Z"` |
| `unrealizedPLFromMLLTSR` | `-839.5` (P&L contra el max-loss trailing) |

**Rendimiento**

`trades: 1023`, `tradesPlacedToday: 0`, `winRate: 33.9198…`, `lossRate: 66.08…`,
`profitFactor: 0.63`, `expectancy: -0.7633…`, `averageWin: 3.7518…`, `averageLoss: -3.0810…`,
`averageRRR: 1.2177…`, `riskReward: "1.22"` (**string**), `bestTrade: 35.97`, `worstTrade: -54.16`,
`longWonTrades`, `shortWonTrades`, `daysElapsed: 22`, `daysSinceFirstTrade: 21`.

**HWM / drawdown**

`highWaterMark: 25063.93`, `hwmClosedTrades`, `lwmClosedTrades`, `lwmAllTrades: 24101.91`,
`currentDrawdownFromHWM: 839.5`, `highestBalance`, `highestEquity`, `lowestBalance`,
`lowestEquity`, `dailyHighestEquity`, `dailyLowestEquity`, `priorDaysEquity`, `priorDaysBalance`.

**Consistencia / payout**

`currentConsistencyPercent: 100`, `currentBestWorstDayProfitPercent: 100`,
`consistencyTopDayRealizedProfit`, `worstDayRealizedLoss`, `profitSharePercentage: 90`,
`daysSinceLastPayout`, `activeTradingDaysSinceLastPayout`, `maxWithdrawal`.

#### `account`

```jsonc
{
  "name": "A B.",                    // iniciales, ya anonimizado por el servidor
  "programCurrency": "USD",
  "status": "ACTIVE",                // UPGRADED|BREACHED|ACTIVE|PENDING_ACTIVATION|Passed|RESET|…
  "label": "25k - Instant - MT5",
  "description": "25k - Instant - MT5",
  "platform": "MetaTrader5",
  "startDate": "2026-07-12T06:05:19.473Z",
  "firstTradeDate": "2026-07-13T21:54:36.000Z",
  "haveOpenTrades": true,
  "breachedAt": "", "passedAt": "", "breachReason": null, "breachMessage": null,
  "softBreachCount": 0, "softBreachLimit": 2,
  "programDetails": {
    "fundingBalance": 25000,
    "programName": "instant funding",
    "profitTargetPercentage": 0,     // 0 ⇒ cuenta funded, sin profit target
    "maxLossLimit": 6,               // %
    "maxLoss": 23500,                // nivel de equity
    "maxDailyLossLimitPercent": 3,
    "maxDailyLossLimit": 24250,      // nivel de equity
    "maxLossLimitMode": "EndOfDayBalance",
    "isStaticMaxLossEnabled": false,
    "isTrailBeyondStartBalance": false,
    "consistencyType": "TopDayProfit",   // TopDayProfit|BestWorstDayProfit|OpenTimeProfit
    "maxConsistencyPercent": 20,
    "inactivityDaysMax": 30,
    "payoutTrigger": "first"
  }
}
```

`payout` puede ser `null` (lo es aquí). `addons` es `["90 profit split"]`.

#### Cómo calcula el sitio las barras de progreso

Está en el módulo de la página (`430091` en el bundle). Lo transcribo porque es
justo lo que necesita el layout Medium — **no hay un array `objectives[]` en la
respuesta, hay que derivarlo**:

```js
dailyLoss = {
  limitLevel: Math.abs(maxDailyLossLimitPnLLevel),
  current:    todayPnL < 0 ? Math.abs(todayPnL) : 0,
  isDone:     todayPnL < 0 && todayPnL <= maxDailyLossLimitPnLLevel,   // breach
}
overallLoss = {
  limitLevel: Math.abs(maxLossLimitPnLLevel),
  current:    isStaticMaxLossEnabled
                ? Math.max(0, startingBalance - lowestEquity)
                : Math.abs(unrealizedPLFromMLLTSR > 0 ? 0 : unrealizedPLFromMLLTSR),
  isDone:     currentProfit < 0 && currentProfit <= maxLossLimitPnLLevel,
}
profitTarget = {
  limitLevel: Math.abs(profitTargetRequiredPnLLevel),
  current:    currentProfit > 0 ? currentProfit : 0,
  isDone:     currentProfit > 0 && currentProfit >= profitTargetRequiredPnLLevel,
}
minTradingDays = {
  limitLevel: minTradingDays,
  current:    (profitTargetPercentage === 0 && payout?.haveRecentWithdrawal)
                ? activeTradingDaysSinceLastPayout
                : activeTradingDays,
  isDone:     current >= limitLevel,
}

isChallenge = programDetails.profitTargetPercentage !== 0
isFunded    = programDetails.profitTargetPercentage === 0
```

En cuentas funded (`profitTargetPercentage === 0`) el profit target queda en 0 y
**no se debe pintar** — el propio sitio esconde esa barra.

El banner superior usa `currentBalance`, `currentEquity` y `dailyTotalPnL`.

### `POST /v1/accounts/shared/{sharedId}/growth`

Array plano, 6 series multiplexadas por `name`:

```json
[
  {"date":"12-07-2026","value":25000,   "name":"BALANCE"},
  {"date":"12-07-2026","value":25000,   "name":"EQUITY"},
  {"date":"12-07-2026","value":25000,   "name":"PROFIT TARGET"},
  {"date":"12-07-2026","value":23500,   "name":"MAX LOSS LIMIT"},
  {"date":"12-07-2026","value":24250,   "name":"DAILY LOSS LIMIT"},
  {"date":"12-07-2026","value":25000,   "name":"HWM BALANCE"}
]
```

- `name` ∈ `BALANCE | EQUITY | PROFIT TARGET | MAX LOSS LIMIT | DAILY LOSS LIMIT | HWM BALANCE`
- `value`: float plano.
- **No viene ordenado por fecha**; el cliente hace `sort` por `new Date(date)` antes de pintar
  (y su propio chart se queda con los últimos 25 puntos).

El `filter` sólo cambia el **formato/agrupación de la etiqueta `date`**:

| `filter` | formato de `date` | filas (cuenta de 24 días) |
|---|---|---|
| `"daily"` | `"12-07-2026"` (**DD-MM-YYYY**, no ISO) | 144 = 24 días × 6 |
| `"weekly"` | `"2026-07-06 to 2026-07-12"` | 30 = 5 semanas × 6 |
| `"monthly"` | `"July"` | 12 = 2 meses × 6 |
| ausente / sin `Content-Type` | `"2026"` para todas | 144 (etiqueta inservible) |
| `"hourly"` | — | `[]` |

Para el sparkline y para `history.json` el que sirve es `"daily"`. Devolvió la serie
completa desde el arranque de la cuenta (24 días), sin ventana recortada.

**`"12-07-2026"` es DD-MM-YYYY**: parsearlo con `new Date()` o con el parser de `Format.shortDate`
(que espera ISO) da resultados equivocados. Hay que partir por `-` e invertir.

---

## Respuestas a las preguntas del brief

| Pregunta | Respuesta |
|---|---|
| **¿SSR o XHR?** | **XHR puro.** El HTML sólo dice `Loading`. Hay API limpia; cero parseo de HTML. |
| **¿Qué host sirve el JSON?** | `https://api.trader.blueguardian.com` — hardcodeado en el bundle como `serverUrl`. |
| **Formato de montos** | **Floats planos**, moneda en `account.programCurrency`. Nada de `{value, decimal, currency}`; el desescalado de `Format.kt` **no aplica**. Porcentajes ya en 0–100, no fracciones. |
| **¿Trae objetivos?** | Sí, pero **no como array `objectives[]`**: vienen como niveles sueltos en `statistics` (`maxDailyLossLimitPnLLevel`, `maxLossLimitPnLLevel`, `profitTargetRequiredPnLLevel`, `minTradingDays`) y hay que derivar `{limit, current, isDone}` con la fórmula de arriba. |
| **¿Daily summary / curva de balance?** | Curva **sí** (`/growth` con `filter:"daily"` → BALANCE + EQUITY + HWM + los tres límites, un punto por día). Daily summary por trade **no**: `/shared/{id}/daily-summary` da 500 y `/shared/{id}/trades` da 404. Para el sparkline sobra; para P&L por día habría que derivarlo diffeando la serie BALANCE. |
| **¿Headers?** | **Ninguno** para el GET. Para el POST sólo `Content-Type: application/json` (si falta, el `filter` se ignora). Sin auth, sin cookies, sin Referer, sin challenge de Cloudflare. |

## Cómo lo descubrí (resumen del proceso)

1. `node -e "fetch(...)"` al share → 200, `x-powered-by: Next.js`, `vary: rsc, next-router-state-tree`.
   App Router. Egress permitido.
2. Bajé el HTML y le quité tags: el texto visible era literalmente `Loading` → CSR, no SSR.
3. Decodifiqué el payload RSC de `self.__next_f.push([1,"…"])` (concatenar y `JSON.parse` cada
   string). Sólo providers, ni una URL de API.
4. Saqué los 28 `<script src>` de `/_next/static/chunks/` y los bajé todos.
   `grep -oh 'https://[^"]*blueguardian[^"]*'` → **`https://api.trader.blueguardian.com`**
   (aparece como `serverUrl` en el blob de config y como `let n="https://api.trader.blueguardian.com"`).
5. `grep` de plantillas de URL → `` `/v1/accounts/shared/${t}` `` y `` `/v1/accounts/shared/${e}/growth` ``,
   dentro del slice de RTK Query (chunk `0iku0mf3nhr3m.js`), con el catálogo entero de endpoints.
6. El GET a `/shared/{id}` respondió 200 a la primera. El de `/growth` daba 404 — porque en la
   definición pone `method:"POST"`, que es fácil pasar por alto.
7. Faltaba el body. El componente de la página estaba en el **mismo** chunk pero como módulo
   perezoso: lo localicé por su id de módulo en el flight (`I[430091,[…],"default"]`, la fila con
   más chunks, la que incluye el slice de API y el de charts) y busqué `430091,t=>{` en el bundle.
   Ahí estaba el `useEffect` completo: `T({login:t, body:{filter:"daily"}})`.
   De paso salió toda la lógica de las barras de progreso.
8. Verifiqué con `curl` pelado que no hace falta ningún header, y que las rutas no-`shared`
   dan 401 (confirma que el id del share es el `_id` de la cuenta).

> Nota: Playwright/Chromium **no** funcionó en este entorno (`ERR_CONNECTION_RESET` contra el
> proxy de egress, incluso con `--proxy-server`; el proxy sólo acepta CONNECT y Chromium no
> llegó a establecerlo). Todo lo de arriba salió de leer los bundles con `curl` + `grep`, sin
> navegador. Es reproducible tal cual.
