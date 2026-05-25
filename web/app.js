"use strict";

const PALETTE = {
  green: "#34d399",
  red: "#f87171",
  blue: "#60a5fa",
  amber: "#fbbf24",
  violet: "#a78bfa",
  muted: "#93a1b5",
};

const EXPLAIN = {
  equity:
    "Valor de la cuenta ahora mismo, incluyendo las operaciones abiertas (flotante). Se mueve en tiempo real con el mercado.",
  balance:
    "Dinero realizado: solo cuenta operaciones ya cerradas. No incluye el flotante de posiciones abiertas.",
  todayPnl:
    "Ganancia o pérdida realizada del día (trades cerrados hoy). Se reinicia a 0 en la medianoche del servidor de FTMO.",
  profitPct:
    "Avance hacia el objetivo de profit del challenge. 100% = objetivo alcanzado. Negativo = estás por debajo del balance inicial.",
  maxDailyLossUsedPct:
    "Cuánto del límite de pérdida diaria llevas consumido hoy. 100% = tocaste el límite (cuenta perdida). Mientras más bajo, mejor.",
  maxLossUsedPct:
    "Cuánto del límite de pérdida total (máximo drawdown) llevas consumido. 100% = límite tocado. Mientras más bajo, mejor.",
  winRate: "Porcentaje de operaciones ganadoras sobre el total de trades cerrados.",
  profitFactor:
    "Ganancia bruta ÷ pérdida bruta. Mayor a 1 es rentable; 2 significa que ganas el doble de lo que pierdes.",
  expectancy:
    "Resultado promedio esperado por operación. Positivo = en promedio, cada trade suma.",
  sharpe:
    "Rentabilidad ajustada al riesgo (volatilidad). Más alto = mejores retornos por unidad de riesgo asumido.",
  avgRR:
    "Relación riesgo/beneficio promedio: cuánto ganas, en promedio, por cada unidad que arriesgas.",
  consistency:
    "Métrica de FTMO que mide qué tan parejo es tu trading, sin días que dominen el resultado. Más alto = más consistente.",
  discipline:
    "Métrica de FTMO sobre el apego a las reglas y la gestión del riesgo. Más alto = mejor disciplina.",
  tradingDaysCount:
    "Número de días en los que operaste. Muchos challenges exigen un mínimo de días de trading antes de pasar.",
};

let CURRENCY = "USD";

// ---------- formatters ----------
function sym(c) {
  return { USD: "$", EUR: "€", GBP: "£" }[(c || "USD").toUpperCase()] || `${c} `;
}
function money(v, signed) {
  if (v == null) return "—";
  const abs = Math.abs(v).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  const sign = v < 0 ? "-" : signed && v > 0 ? "+" : "";
  return `${sign}${sym(CURRENCY)}${abs}`;
}
function pct(v, d = 1) {
  return v == null ? "—" : `${v.toFixed(d)}%`;
}
function ratio(v, d = 2) {
  return v == null ? "—" : v.toFixed(d);
}
function intOr(v) {
  return v == null ? "—" : String(Math.round(v));
}
function pad(n) {
  return String(n).padStart(2, "0");
}
function fmtTime(iso) {
  const d = new Date(iso);
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function fmtDay(iso) {
  const d = new Date(iso);
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`;
}
function ago(iso) {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (mins < 1) return "hace <1 min";
  if (mins < 60) return `hace ${mins} min`;
  const h = Math.floor(mins / 60);
  return h < 24 ? `hace ${h} h` : `hace ${Math.floor(h / 24)} d`;
}

async function getJSON(path) {
  try {
    const res = await fetch(`${path}?t=${Date.now()}`, { cache: "no-store" });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

// ---------- header ----------
function renderHeader(latest) {
  document.getElementById("phase").textContent = [latest.phase, latest.status]
    .filter(Boolean)
    .join(" · ");

  const meta = [];
  if (latest.login) meta.push(`<span><b>#${latest.login}</b></span>`);
  if (latest.initialBalance != null)
    meta.push(`<span>Inicial: <b>${money(latest.initialBalance)}</b></span>`);
  if (latest.tradingDaysCount != null)
    meta.push(`<span>Días: <b>${latest.tradingDaysCount}</b></span>`);
  if (latest.result) meta.push(`<span>Resultado: <b>${latest.result}</b></span>`);
  document.getElementById("account").innerHTML = meta.join("");

  document.getElementById("updated").textContent = latest.updatedAt
    ? `Actualizado ${ago(latest.updatedAt)}`
    : "";
  const dot = document.getElementById("status-dot");
  const stale = latest.updatedAt
    ? Date.now() - new Date(latest.updatedAt).getTime() > 2 * 3600 * 1000
    : true;
  dot.className = `dot ${stale ? "stale" : "ok"}`;
}

// ---------- KPI cards ----------
function kpiCard({ label, value, sub, cls, key }) {
  const tip = EXPLAIN[key] ? `<span class="info" title="${EXPLAIN[key]}">i</span>` : "";
  return `<div class="kpi">
    <div class="label">${label}${tip}</div>
    <div class="value ${cls || ""}">${value}</div>
    ${sub ? `<div class="sub">${sub}</div>` : ""}
  </div>`;
}

function signClass(v) {
  if (v == null) return "";
  return v > 0 ? "pos" : v < 0 ? "neg" : "";
}

function renderKpis(latest) {
  const cards = [
    { key: "equity", label: "Equity", value: money(latest.equity) },
    { key: "balance", label: "Balance", value: money(latest.balance) },
    {
      key: "todayPnl",
      label: "P&L de hoy",
      value: money(latest.todayPnl, true),
      cls: signClass(latest.todayPnl),
    },
    {
      key: "profitPct",
      label: "Profit objetivo",
      value: pct(latest.profitPct),
      cls: signClass(latest.profitPct),
      sub: latest.profitTargetLimit != null ? `Meta: ${money(latest.profitTargetLimit)}` : "",
    },
    {
      key: "maxDailyLossUsedPct",
      label: "Pérdida diaria usada",
      value: pct(latest.maxDailyLossUsedPct),
      sub: latest.maxDailyLossLimit != null ? `Límite: ${money(latest.maxDailyLossLimit)}` : "",
    },
    {
      key: "maxLossUsedPct",
      label: "Pérdida total usada",
      value: pct(latest.maxLossUsedPct),
      sub: latest.maxLossLimit != null ? `Límite: ${money(latest.maxLossLimit)}` : "",
    },
    { key: "winRate", label: "Win rate", value: pct(latest.winRate) },
    { key: "profitFactor", label: "Profit factor", value: ratio(latest.profitFactor) },
    { key: "expectancy", label: "Expectancy", value: money(latest.expectancy, true) },
    { key: "sharpe", label: "Sharpe", value: ratio(latest.sharpe) },
    { key: "avgRR", label: "R:R promedio", value: ratio(latest.avgRR) },
    { key: "consistency", label: "Consistencia", value: pct(latest.consistency) },
    { key: "discipline", label: "Disciplina", value: pct(latest.discipline) },
    {
      key: "tradingDaysCount",
      label: "Días de trading",
      value: intOr(latest.tradingDaysCount),
      sub: latest.minTradingDays != null ? `Mínimo: ${Math.round(latest.minTradingDays)}` : "",
    },
  ];
  document.getElementById("kpis").innerHTML = cards.map(kpiCard).join("");
}

// ---------- charts ----------
function setChartDefaults() {
  Chart.defaults.color = PALETTE.muted;
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  Chart.defaults.borderColor = "#273140";
}

function series(history, key) {
  return history.map((h) => (h[key] == null ? null : h[key]));
}

function lineCard({ id, title, hint, wide }) {
  return `<div class="card ${wide ? "wide" : ""}">
    <h3>${title}</h3>
    <p class="hint">${hint}</p>
    <div class="canvas-wrap"><canvas id="${id}"></canvas></div>
  </div>`;
}

function makeLine(id, labels, datasets, opts = {}) {
  const ctx = document.getElementById(id);
  new Chart(ctx, {
    type: "line",
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: { display: datasets.filter((d) => d.label).length > 1, labels: { boxWidth: 12 } },
        tooltip: { callbacks: opts.tooltip },
      },
      scales: {
        x: { ticks: { maxTicksLimit: 8, autoSkip: true }, grid: { display: false } },
        y: {
          ticks: { callback: opts.yfmt },
          grid: { color: "#1d2530" },
          ...(opts.yMin != null ? { min: opts.yMin } : {}),
        },
      },
    },
  });
}

function ds(label, data, color, extra = {}) {
  return {
    label,
    data,
    borderColor: color,
    backgroundColor: color + "22",
    borderWidth: 2,
    pointRadius: 0,
    tension: 0.25,
    spanGaps: true,
    fill: false,
    ...extra,
  };
}

function refLine(label, value, len, color) {
  return ds(label, new Array(len).fill(value), color, { borderDash: [6, 4], borderWidth: 1 });
}

function renderCharts(history, latest) {
  const labels = history.map((h) => fmtTime(h.t));
  const n = labels.length;
  const moneyTick = (v) => sym(CURRENCY) + Number(v).toLocaleString("en-US");
  const pctTick = (v) => v + "%";

  const cards = [
    { id: "c_equity", title: "Equity y Balance", hint: "Crecimiento de la cuenta en el tiempo. Equity incluye lo flotante; balance solo lo realizado.", wide: true },
    { id: "c_profit", title: "Profit objetivo (%)", hint: "Avance hacia el objetivo del challenge. La línea punteada es la meta (100%)." },
    { id: "c_loss", title: "Buffer de pérdida usado (%)", hint: "Qué tanto del límite de pérdida diaria y total llevas consumido. Acercarse a 100% es peligroso." },
    { id: "c_win", title: "Win rate (%)", hint: "Porcentaje de operaciones ganadoras." },
    { id: "c_pf", title: "Profit factor", hint: "Ganancia bruta ÷ pérdida bruta. La línea punteada (1.0) separa rentable de no rentable." },
    { id: "c_exp", title: "Expectancy", hint: "Resultado promedio esperado por operación." },
    { id: "c_sharpe", title: "Sharpe", hint: "Rentabilidad ajustada al riesgo." },
    { id: "c_rr", title: "R:R promedio", hint: "Relación riesgo/beneficio media por operación." },
    { id: "c_scores", title: "Consistencia y Disciplina", hint: "Métricas propias de FTMO sobre lo parejo y disciplinado de tu trading." },
    { id: "c_days", title: "Días de trading", hint: "Días operados acumulados." },
    { id: "c_daily", title: "P&L por día", hint: "Ganancia o pérdida realizada de cada día de trading.", wide: true },
  ];
  document.getElementById("charts").innerHTML = cards.map(lineCard).join("");

  makeLine("c_equity", labels, [
    ds("Equity", series(history, "equity"), PALETTE.green, { fill: true }),
    ds("Balance", series(history, "balance"), PALETTE.blue),
  ], { yfmt: moneyTick });

  makeLine("c_profit", labels, [
    ds("Profit %", series(history, "profitPct"), PALETTE.green, { fill: true }),
    refLine("Meta", 100, n, PALETTE.muted),
  ], { yfmt: pctTick });

  makeLine("c_loss", labels, [
    ds("Pérdida diaria", series(history, "maxDailyLossUsedPct"), PALETTE.amber),
    ds("Pérdida total", series(history, "maxLossUsedPct"), PALETTE.red),
    refLine("Límite", 100, n, PALETTE.muted),
  ], { yfmt: pctTick });

  makeLine("c_win", labels, [ds("Win rate", series(history, "winRate"), PALETTE.blue, { fill: true })], { yfmt: pctTick });

  makeLine("c_pf", labels, [
    ds("Profit factor", series(history, "profitFactor"), PALETTE.violet),
    refLine("Equilibrio", 1, n, PALETTE.muted),
  ]);

  makeLine("c_exp", labels, [ds("Expectancy", series(history, "expectancy"), PALETTE.green)], { yfmt: moneyTick });
  makeLine("c_sharpe", labels, [ds("Sharpe", series(history, "sharpe"), PALETTE.blue)]);
  makeLine("c_rr", labels, [ds("R:R", series(history, "avgRR"), PALETTE.violet)]);

  makeLine("c_scores", labels, [
    ds("Consistencia", series(history, "consistency"), PALETTE.green),
    ds("Disciplina", series(history, "discipline"), PALETTE.blue),
  ], { yfmt: pctTick });

  makeLine("c_days", labels, [ds("Días", series(history, "tradingDaysCount"), PALETTE.blue, { stepped: true })]);

  renderDailyBar("c_daily", latest);
}

function renderDailyBar(id, latest) {
  const daily = (latest.daily || []).filter((d) => d.pnl != null).slice(-60);
  const ctx = document.getElementById(id);
  new Chart(ctx, {
    type: "bar",
    data: {
      labels: daily.map((d) => fmtDay(d.date)),
      datasets: [
        {
          label: "P&L",
          data: daily.map((d) => d.pnl),
          backgroundColor: daily.map((d) => (d.pnl >= 0 ? PALETTE.green : PALETTE.red)),
          borderRadius: 3,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { ticks: { maxTicksLimit: 12, autoSkip: true }, grid: { display: false } },
        y: { ticks: { callback: (v) => sym(CURRENCY) + Number(v).toLocaleString("en-US") }, grid: { color: "#1d2530" } },
      },
    },
  });
}

// ---------- glossary ----------
function renderGlossary() {
  const labels = {
    equity: "Equity",
    balance: "Balance",
    todayPnl: "P&L de hoy",
    profitPct: "Profit objetivo",
    maxDailyLossUsedPct: "Pérdida diaria usada",
    maxLossUsedPct: "Pérdida total usada",
    winRate: "Win rate",
    profitFactor: "Profit factor",
    expectancy: "Expectancy",
    sharpe: "Sharpe",
    avgRR: "R:R promedio",
    consistency: "Consistencia",
    discipline: "Disciplina",
    tradingDaysCount: "Días de trading",
  };
  document.getElementById("glossary").innerHTML = Object.keys(labels)
    .map((k) => `<div class="term"><h4>${labels[k]}</h4><p>${EXPLAIN[k]}</p></div>`)
    .join("");
}

// ---------- boot ----------
async function boot() {
  renderGlossary();
  const [latest, history] = await Promise.all([
    getJSON("data/latest.json"),
    getJSON("data/history.json"),
  ]);

  if (!history || history.length === 0 || !latest) {
    document.getElementById("empty").classList.remove("hidden");
    document.getElementById("kpis").classList.add("hidden");
    document.getElementById("charts").classList.add("hidden");
    return;
  }

  CURRENCY = latest.currency || "USD";
  setChartDefaults();
  renderHeader(latest);
  renderKpis(latest);
  renderCharts(history, latest);
}

boot();
