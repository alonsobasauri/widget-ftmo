# Análisis de https://trader.ftmo.com/live-metrix/{login}/share/{sharingCode}

## Cómo se sirve la página

`https://trader.ftmo.com/live-metrix/...` es una app **React Router v7 (Remix-style) con SSR** servida en `cdn.ftmo.com`. La ruta `metrix/$account.share.$sharingCode` **no tiene loader** — los datos se traen del cliente con TanStack Query (`useFTMOQuery`) contra el gateway `gw2.ftmo.com`. La config de bases viene incrustada en el HTML en `<script id="__ENVIRONMENT__">` como `window.environment` (todas las `VITE_*_URL`).

## Endpoints públicos (sin auth, solo `sharingCode` + `login`)

### 1) Métricas completas — lo que pinta la pantalla

```
GET https://gw2.ftmo.com/public-api/v1/metrix/{login}/{sharingCode}
```

Respuesta (`metrixData`):

- `info` → `accountStart`, `accountStatus`, `accountProductType`, `productLine`, `initialBalance`, `minAccountEquityLimit`, `minTodaysEquityLimit`, `maxMidnightBalance`, `todaysProfit`
- `statistics` → `equity`, `balance`, `winRate`, `avgProfit`, `avgLoss`, `lots`, `avgRiskToRewardRate`, `expectancy`, `sharpeRate`, `profitFactor`, `tradesCount`, `openTradesCount`, `totalTradesCount`
- `objectives` → `maxLoss`, `profit`, `maxDailyLoss`, `bestDayRule`, `maxMidnightBalanceMaxLoss` (c/u con `limit`, `result`, `percentage`, `status`)
- `dailySummary[]`, `bestDayRuleDetails`, `UTCtoServerTime[]`, `timestamp`, `currency`, `imageUrl`

### 2) Curva para el gráfico

```
GET https://gw2.ftmo.com/public-api/v1/account/{login}/{sharingCode}/balance-curve
```

Devuelve `balanceCurve.balance[]`, `balanceCurve.time[]`, `balanceCurve.ticket[]`.

## Notas importantes sobre el formato

Los valores monetarios vienen como `{ value, decimal, currency }` y hay que dividir `value / 10^decimal`. Por ejemplo `equity = 973285 / 10^2 = 9732.85` USD. Algunos vienen con `decimal:8` (precisión interna), no es bug. Los porcentajes vienen como `{ value, type:"fraction" }`.

## Forma más simple de scrapearlo

```bash
curl -s 'https://gw2.ftmo.com/public-api/v1/metrix/531303305/019e5049-800b-7191-b41c-3e27d7304655' \
  -H 'Accept: application/json' -H 'Referer: https://trader.ftmo.com/'
```

Solo necesitas el `Accept: application/json`; el `Referer` no es obligatorio pero ayuda si más adelante añaden CORS/anti-bot. Nada de auth, cookies ni Cloudflare challenge para este endpoint. Si necesitas refrescos en vivo, hay además un WS en `wss://gw2.ftmo.com/fss/socket.io/` y `wss://gw2.ftmo.com/` (de `VITE_FTMO_ACCOUNT_DATA_WS`), pero para datos puntuales los dos GETs de arriba bastan.

## Cómo lo descubrí (resumen del proceso)

1. Abrí la URL con Playwright/Chrome y filtré XHR en DevTools — solo aparecía `/public-api/v1/account/.../balance-curve`. Las métricas no salían en XHR porque ya venían en el HTML SSR.
2. Inspeccioné el HTML: `data-sentry-source-file="root.tsx"`, `__manifest?paths=...`, `window.__reactRouterContext/Manifest/RouteModules`, `window.environment` con todas las `VITE_*_URL`.
3. En el manifest, la ruta `metrix/$account.share.$sharingCode` declara `hasLoader:false` → los datos los pide el cliente, no el server.
4. Bajé el bundle de la ruta (`_account.share._sharingCode-KHAFWPit.js`) y encontré la plantilla de URL: `` `v1/metrix/${platformLogin}/${sharingCode}` ``.
5. Probé esa ruta contra cada base de `gw2.ftmo.com` — `public-api` respondió 200 con todo el `metrixData`.
