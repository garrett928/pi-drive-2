// pi-drive — Android Auto split-screen layout.
// In split mode each app controls only its own area's header — the nav app
// owns the chrome above its 2/3, pi-drive owns only the chrome above its 1/3.
// The 1/3 panel is itself swipeable between two pages.

function AASplitShell({ theme = 'dark', accent = 'oklch(0.72 0.17 55)' }) {
  return (
    <PDProvider theme={theme} accent={accent}>
      <AAFrame>
        <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>
          <NavPlaceholder />
          <PiDriveSidePanel />
        </div>
      </AAFrame>
    </PDProvider>
  );
}

// ─── Nav placeholder (2/3 width) ──────────────────────────
// The nav app owns its own header — we render a stand-in chrome strip
// at the top, then map content + turn card below.
function NavPlaceholder() {
  const { t, a } = usePD();
  return (
    <div style={{
      flex: '0 0 66%', position: 'relative', overflow: 'hidden',
      background: 'oklch(0.22 0.012 130)',  // muted map-ish green-grey
      borderRight: `1px solid ${t.borderS}`,
      display: 'flex', flexDirection: 'column', minWidth: 0,
    }}>
      {/* Nav-app chrome — this header belongs to the navigation app, not pi-drive */}
      <div style={{
        height: 44, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 12,
        background: 'oklch(0.18 0.01 130)', borderBottom: `1px solid oklch(0.30 0.012 130)`,
        flexShrink: 0, whiteSpace: 'nowrap', position: 'relative', zIndex: 1,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* generic map glyph — no real brand */}
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="oklch(0.85 0.005 130)" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z" />
            <path d="M9 4v14M15 6v14" />
          </svg>
          <span style={{ fontSize: 13, color: 'oklch(0.92 0.005 130)', fontFamily: PD_FONT, fontWeight: 600 }}>Navigation</span>
        </div>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 11, color: 'oklch(0.68 0.005 130)', fontFamily: PD_FONT_MONO, fontVariantNumeric: 'tabular-nums' }}>ETA · 4:55 PM</span>
        <span style={{ fontSize: 11, color: 'oklch(0.68 0.005 130)', fontFamily: PD_FONT_MONO, fontVariantNumeric: 'tabular-nums' }}>9.2 mi</span>
      </div>

      <div style={{ position: 'relative', flex: 1, minHeight: 0 }}>
        {/* faint road grid */}
        <svg width="100%" height="100%" style={{ position: 'absolute', inset: 0 }} preserveAspectRatio="none" viewBox="0 0 580 400">
          <defs>
            <pattern id="map-grid" width="40" height="40" patternUnits="userSpaceOnUse">
              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="oklch(0.30 0.015 130)" strokeWidth="0.6" />
            </pattern>
          </defs>
          <rect width="580" height="400" fill="url(#map-grid)" />

          {/* fake roads */}
          <path d="M 0 80 Q 140 70 280 130 T 580 220"             stroke="oklch(0.32 0.014 130)" strokeWidth="14" fill="none" strokeLinecap="round" />
          <path d="M 60 400 L 100 290 L 210 250 L 290 180 L 380 140 L 460 90 L 540 30" stroke="oklch(0.32 0.014 130)" strokeWidth="22" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M 0 280 Q 200 260 320 330 T 580 360"           stroke="oklch(0.30 0.014 130)" strokeWidth="10" fill="none" strokeLinecap="round" />
          <path d="M 240 0 L 260 80 L 230 160 L 250 250 L 230 340 L 250 400" stroke="oklch(0.30 0.014 130)" strokeWidth="9" fill="none" strokeLinecap="round" />

          {/* route highlight */}
          <path d="M 60 400 L 100 290 L 210 250 L 290 180 L 380 140 L 460 90 L 540 30"
            stroke={a.base} strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round"
            opacity="0.95" />
          <path d="M 60 400 L 100 290 L 210 250 L 290 180 L 380 140 L 460 90 L 540 30"
            stroke={a.base} strokeWidth="14" fill="none" strokeLinecap="round" strokeLinejoin="round"
            opacity="0.18" />

          {/* current location */}
          <circle cx="210" cy="250" r="9" fill={a.base} stroke="white" strokeWidth="2" />
        </svg>

        {/* turn card */}
        <div style={{
          position: 'absolute', top: 14, left: 14, right: 14,
          background: 'oklch(0.18 0.01 130 / 0.92)', backdropFilter: 'blur(8px)',
          borderRadius: 10, padding: '10px 14px',
          display: 'flex', alignItems: 'center', gap: 12,
          border: `1px solid oklch(0.32 0.01 130)`,
        }}>
          <div style={{
            width: 40, height: 40, borderRadius: 8,
            background: a.base, color: 'oklch(0.18 0.02 60)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 20 L 5 12 Q 5 6, 11 6 L 17 6" />
              <path d="M13 2 L 19 6 L 13 10" />
            </svg>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 11, color: 'oklch(0.7 0.005 130)', fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>In 0.4 mi</div>
            <div style={{ fontSize: 16, color: 'oklch(0.96 0.005 130)', fontFamily: PD_FONT, fontWeight: 600, marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>Turn right onto US-101 N</div>
          </div>
        </div>

        <div style={{
          position: 'absolute', bottom: 10, left: 14,
          fontFamily: PD_FONT_MONO, fontSize: 10, color: 'oklch(0.55 0.01 130)',
          letterSpacing: 0.5, textTransform: 'uppercase',
        }}>Nav app · ⅔ width · owns own header</div>
      </div>
    </div>
  );
}

// ─── pi-drive 1/3 panel ───────────────────────────────────
// Owns only its own column header (top strip) + content.
// Swipeable between Page 1 (Hero + accel graph) and Page 2 (Tile grid).
//
// Page-1 slots — fixed layout shape, configurable metric per slot.
// Slot order: [hero, pillTL, pillTR, pillBL, pillBR, graph]
const SPLIT_PAGE1_DEFAULTS = ['mpg', 'mpgTrip', 'mpgManual', 'distance', 'manualTrip', 'accel'];
// Page-2 tile assignments — free 2×3 grid, any metric per cell.
const SPLIT_PAGE2_DEFAULTS = ['rpm', 'coolant', 'throttle', 'fuel', 'battery', 'oilTemp'];

// ─── Metric mock data + time series ─────────────────────────
// Shared between page 1 (hero/pills/graph) and page 2 (tiles) so any metric
// can render anywhere. In a real build this is the live data stream.
function metricDisplay(id, tick) {
  const wob = (b, amp) => Math.round(b + Math.sin(tick * 0.6) * amp);
  const table = {
    speed:      { v: `${wob(58, 4)}`,                                     unit: 'mph' },
    mpg:        { v: (28 + Math.sin(tick * 0.5) * 6).toFixed(1),          unit: 'mpg', accent: true },
    mpgTrip:    { v: '26.4',                                              unit: 'mpg' },
    mpgManual:  { v: '31.4',                                              unit: 'mpg' },
    rpm:        { v: `${wob(2750, 180)}`,                                 unit: 'rpm' },
    throttle:   { v: `${wob(34, 6)}`,                                     unit: '%'   },
    coolant:    { v: `${wob(192, 2)}`,                                    unit: '°F'  },
    intake:     { v: `${wob(82, 3)}`,                                     unit: '°F'  },
    oilTemp:    { v: `${wob(195, 2)}`,                                    unit: '°F'  },
    battery:    { v: '14.2',                                              unit: 'V'   },
    fuel:       { v: '62',                                                unit: '%'   },
    maf:        { v: (8.4 + Math.sin(tick * 0.5) * 1.6).toFixed(1),       unit: 'g/s' },
    gforce:     { v: (Math.sin(tick * 0.5) * 0.4).toFixed(2),             unit: 'g'   },
    accel:      { v: (Math.sin(tick * 0.4) * 2.4).toFixed(2),             unit: 'm/s²' },
    distance:   { v: '14.3',                                              unit: 'mi'  },
    manualTrip: { v: '248.6',                                             unit: 'mi'  },
  };
  return { ...(table[id] || { v: '—', unit: '' }) };
}

function metricSeries(id, tick) {
  // Tuned ranges per metric. `sym` marks zero-centered metrics (rendered with
  // a midline graph). Anything not listed falls back to a moderate range.
  const cfg = ({
    accel:     { c: 0,    a: 2.4,  f: 0.4, sym: true },
    gforce:    { c: 0,    a: 0.4,  f: 0.5, sym: true },
    speed:     { c: 58,   a: 8,    f: 0.4 },
    rpm:       { c: 2750, a: 350,  f: 0.4 },
    throttle:  { c: 30,   a: 22,   f: 0.4 },
    mpg:       { c: 28,   a: 10,   f: 0.4 },
    mpgTrip:   { c: 26.4, a: 1.2,  f: 0.2 },
    mpgManual: { c: 31.4, a: 0.6,  f: 0.15 },
    coolant:   { c: 192,  a: 3,    f: 0.3 },
    intake:    { c: 82,   a: 4,    f: 0.3 },
    oilTemp:   { c: 195,  a: 3,    f: 0.3 },
    battery:   { c: 14.1, a: 0.25, f: 0.3 },
    fuel:      { c: 62,   a: 0.3,  f: 0.1 },
    maf:       { c: 8.4,  a: 2,    f: 0.4 },
    distance:  { c: 14,   a: 0.05, f: 0.1 },
    manualTrip:{ c: 248,  a: 0.1,  f: 0.1 },
  })[id] || { c: 50, a: 8, f: 0.4 };
  const data = Array.from({ length: 60 }, (_, i) =>
    cfg.c + Math.sin((i + tick) * cfg.f) * cfg.a + Math.cos((i + tick) * cfg.f * 0.42) * cfg.a * 0.4
  );
  return { data, symmetric: !!cfg.sym };
}

function PiDriveSidePanel() {
  const { t, a } = usePD();
  const [page, setPage] = React.useState(1);
  const [tick, setTick] = React.useState(0);
  React.useEffect(() => {
    const i = setInterval(() => setTick(x => x + 1), 700);
    return () => clearInterval(i);
  }, []);

  return (
    <div style={{
      flex: '0 0 34%', minWidth: 0, background: t.bg, display: 'flex', flexDirection: 'column',
    }}>
      {/* pi-drive owns its own header — only over the 1/3 column */}
      <SidePanelHeader page={page} setPage={setPage} />

      {/* Swipe container */}
      <div style={{ flex: 1, minHeight: 0, position: 'relative', overflow: 'hidden' }}>
        <div style={{
          display: 'flex', width: '200%', height: '100%',
          transition: 'transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1)',
          transform: `translateX(${page === 1 ? '0%' : '-50%'})`,
        }}>
          <div style={{ width: '50%', flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
            <SidePanelPage1 tick={tick} />
          </div>
          <div style={{ width: '50%', flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
            <SidePanelPage2 tick={tick} tileIds={SPLIT_PAGE2_DEFAULTS} />
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── 1/3 panel header ─────────────────────────────────────
// Compact: brand, page dots/buttons, status icons.
function SidePanelHeader({ page, setPage }) {
  const { t, a } = usePD();
  return (
    <div style={{
      height: 44, padding: '0 10px', display: 'flex', alignItems: 'center', gap: 8,
      borderBottom: `1px solid ${t.borderS}`, flexShrink: 0, whiteSpace: 'nowrap',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
        <div style={{
          width: 22, height: 22, borderRadius: 6, background: a.base,
          color: 'oklch(0.18 0.02 60)', display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: PD_FONT, fontWeight: 700, fontSize: 13, letterSpacing: -0.5,
        }}>π</div>
        <span style={{ fontSize: 12, color: t.fg, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: -0.1 }}>pi-drive</span>
      </div>

      <div style={{ flex: 1 }} />

      {/* page dots */}
      <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
        {[1, 2].map(n => {
          const sel = page === n;
          return (
            <button key={n} onClick={() => setPage(n)} style={{
              width: sel ? 20 : 6, height: 6, padding: 0,
              borderRadius: 3, border: 'none',
              background: sel ? a.base : t.surface2,
              cursor: 'pointer', transition: 'width 0.2s',
            }} />
          );
        })}
      </div>

      <div style={{ width: 1, height: 16, background: t.borderS, margin: '0 2px', flexShrink: 0 }} />

      <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: t.success, flexShrink: 0 }}
           title="Dongle connected · streaming">
        <Icon d={I.bluetooth} size={11} stroke={2.2} />
        <Icon d={I.cloudUp}   size={11} stroke={2.2} />
      </div>
    </div>
  );
}

// ─── Page 1 — Fixed layout shape, slot metrics configurable ──
// Slot order matches SPLIT_PAGE1_DEFAULTS:
//   slots[0] = hero readout
//   slots[1..2] = top pill row (left, right)
//   slots[3..4] = bottom pill row (left, right)
//   slots[5] = graph
function SidePanelPage1({ tick, slots = SPLIT_PAGE1_DEFAULTS }) {
  const { t, a } = usePD();
  const hero = metricDisplay(slots[0], tick);
  const heroMeta = PD_METRICS.find(m => m.id === slots[0]);
  const graphMeta = PD_METRICS.find(m => m.id === slots[5]);
  const graphSeries = metricSeries(slots[5], tick);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '10px 12px 12px', gap: 8, minHeight: 0 }}>
      {/* Status strip — dongle + server (always present, not a configurable slot) */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 6,
        background: t.bgElev, border: `1px solid ${t.borderS}`, borderRadius: 10,
        padding: '6px 8px',
      }}>
        <span style={{ width: 6, height: 6, borderRadius: 3, background: t.success,
          boxShadow: `0 0 0 3px oklch(0.74 0.16 150 / 0.22)`, flexShrink: 0 }} />
        <Icon d={I.bluetooth} size={11} stroke={2} style={{ color: t.fgMuted }} />
        <span style={{ fontSize: 10, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, whiteSpace: 'nowrap' }}>9F4C</span>
        <span style={{ flex: 1 }} />
        <Icon d={I.cloudUp} size={11} stroke={2} style={{ color: t.success }} />
        <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500, whiteSpace: 'nowrap' }}>30 Hz</span>
      </div>

      {/* Hero slot — big readout */}
      <div style={{
        background: t.bgElev, border: `1px solid ${t.borderS}`, borderRadius: 12,
        padding: '10px 14px',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>{heroMeta?.label || slots[0]}</span>
          <span style={{ fontSize: 9, color: t.fgDim, fontFamily: PD_FONT_MONO }}>{heroMeta?.pid || ''}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 2, color: hero.accent ? a.base : t.fg }}>
          <span style={{ fontSize: 44, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -1.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{hero.v}</span>
          <span style={{ fontSize: 12, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>{hero.unit}</span>
        </div>
      </div>

      {/* Pill rows */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <SlotPill metricId={slots[1]} tick={tick} />
        <SlotPill metricId={slots[2]} tick={tick} />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <SlotPill metricId={slots[3]} tick={tick} />
        <SlotPill metricId={slots[4]} tick={tick} />
      </div>

      {/* Graph slot */}
      <div style={{
        flex: 1, minHeight: 0,
        background: t.bgElev, border: `1px solid ${t.borderS}`, borderRadius: 12,
        padding: '10px 12px 8px', display: 'flex', flexDirection: 'column',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>{graphMeta?.label || slots[5]}</span>
          <span style={{ fontFamily: PD_FONT_MONO, fontSize: 14, color: t.fg, fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
            {graphSeries.data[graphSeries.data.length - 1].toFixed(graphSeries.symmetric ? 2 : 1)}<span style={{ fontSize: 9, color: t.fgMuted, marginLeft: 3 }}>{graphMeta?.unit || ''}</span>
          </span>
        </div>
        <div style={{ flex: 1, marginTop: 4, position: 'relative' }}>
          <MetricGraph data={graphSeries.data} symmetric={graphSeries.symmetric} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
          <span style={{ fontSize: 9, color: t.fgDim, fontFamily: PD_FONT_MONO }}>-30s</span>
          <span style={{ fontSize: 9, color: t.fgDim, fontFamily: PD_FONT_MONO }}>now</span>
        </div>
      </div>
    </div>
  );
}

// Pill — slot-aware (renders metric label + value pulled from metricDisplay).
function SlotPill({ metricId, tick }) {
  const { t, a } = usePD();
  const m = PD_METRICS.find(x => x.id === metricId);
  const d = metricDisplay(metricId, tick);
  // Compact label — drop the "· instant" suffix and replace "· trip avg" with "· trip".
  const label = (m?.label || metricId)
    .replace(' · instant', '')
    .replace(' · trip avg', ' · trip')
    .replace(' · manual avg', ' · manual');
  return (
    <div style={{
      background: t.bgElev, border: `1px solid ${t.borderS}`, borderRadius: 10,
      padding: '6px 10px', display: 'flex', flexDirection: 'column', gap: 1, minWidth: 0,
    }}>
      <span style={{ fontSize: 9, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{label}</span>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 3 }}>
        <span style={{
          fontSize: 20, color: d.accent ? a.base : t.fg, fontFamily: PD_FONT_MONO,
          fontWeight: 500, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums', lineHeight: 1.1,
        }}>{d.v}</span>
        <span style={{ fontSize: 9, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>{d.unit}</span>
      </div>
    </div>
  );
}

// ─── Page 2 — Tile grid (configurable in settings) ────────
// 2 columns × 3 rows of compact metric tiles. Each tile is a single metric
// selectable from PD_METRICS in the settings/aa-layout editor.
function SidePanelPage2({ tick, tileIds }) {
  const { t, a } = usePD();
  // mock values for each metric id
  const wob = (b, amp) => Math.round(b + Math.sin(tick * 0.6) * amp);
  const values = {
    speed:      { v: `${wob(58, 4)}`,            unit: 'mph' },
    rpm:        { v: `${wob(2750, 180)}`,        unit: 'rpm' },
    throttle:   { v: `${wob(34, 6)}`,            unit: '%'   },
    coolant:    { v: `${wob(192, 2)}`,           unit: '°F'  },
    intake:     { v: `${wob(82, 3)}`,            unit: '°F'  },
    oilTemp:    { v: `${wob(195, 2)}`,           unit: '°F'  },
    battery:    { v: `14.2`,                     unit: 'V'   },
    fuel:       { v: `62`,                       unit: '%'   },
    maf:        { v: `${(8.4 + Math.sin(tick * 0.5) * 1.6).toFixed(1)}`, unit: 'g/s' },
    gforce:     { v: `${(Math.sin(tick * 0.5) * 0.4).toFixed(2)}`,        unit: 'g'   },
    mpg:        { v: `${(28 + Math.sin(tick * 0.5) * 6).toFixed(1)}`,     unit: 'mpg', accent: true },
    mpgTrip:    { v: '26.4',                     unit: 'mpg' },
    distance:   { v: '14.3',                     unit: 'mi'  },
    manualTrip: { v: '248.6',                    unit: 'mi'  },
  };

  return (
    <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', gridAutoRows: '1fr', gap: 6, padding: '10px 12px 12px', minHeight: 0 }}>
      {tileIds.map((id, i) => {
        const m = PD_METRICS.find(x => x.id === id);
        const val = values[id] || { v: '—', unit: '' };
        return (
          <div key={i} style={{
            background: t.bgElev, border: `1px solid ${t.borderS}`, borderRadius: 10,
            padding: '8px 10px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', minHeight: 0,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, color: t.fgMuted }}>
              {m && <Icon d={m.icon} size={11} stroke={2} />}
              <span style={{ fontSize: 9, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.35, textTransform: 'uppercase', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m ? m.label.replace(' · trip avg', '·trip').replace(' · instant', '') : id}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 3, marginTop: 2 }}>
              <span style={{
                fontSize: 22, color: val.accent ? a.base : t.fg, fontFamily: PD_FONT_MONO,
                fontWeight: 500, letterSpacing: -0.6, fontVariantNumeric: 'tabular-nums', lineHeight: 1,
              }}>{val.v}</span>
              <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>{val.unit}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function SidePill({ label, value }) {
  const { t } = usePD();
  return (
    <div style={{
      background: t.bgElev, border: `1px solid ${t.borderS}`, borderRadius: 10,
      padding: '6px 10px', display: 'flex', flexDirection: 'column', gap: 1,
    }}>
      <span style={{ fontSize: 9, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>{label}</span>
      <span style={{ fontSize: 20, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums', lineHeight: 1.1 }}>{value}</span>
    </div>
  );
}

// Generic time-series graph for the page-1 graph slot. Auto-handles symmetric
// (zero-centered) and non-symmetric metrics.
function MetricGraph({ data, symmetric }) {
  const { t, a } = usePD();
  const [w, setW] = React.useState(280);
  const [h, setH] = React.useState(80);
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver(es => {
      const r = es[0].contentRect;
      setW(Math.max(60, r.width));
      setH(Math.max(40, r.height));
    });
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);

  const pad = 2;
  let lo, hi;
  if (symmetric) {
    const peak = Math.max(...data.map(Math.abs)) + 0.3;
    lo = -peak; hi = peak;
  } else {
    const dMin = Math.min(...data);
    const dMax = Math.max(...data);
    const span = (dMax - dMin) || 1;
    lo = dMin - span * 0.15;
    hi = dMax + span * 0.15;
  }
  const range = (hi - lo) || 1;
  const points = data.map((v, i) => {
    const x = pad + (i / (data.length - 1)) * (w - pad * 2);
    const y = pad + (1 - (v - lo) / range) * (h - pad * 2);
    return [x, y];
  });
  const path = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  const last = points[points.length - 1];
  const baselineY = symmetric ? h / 2 : h - pad;

  return (
    <div ref={ref} style={{ position: 'absolute', inset: 0 }}>
      <svg width={w} height={h} style={{ display: 'block' }}>
        <defs>
          <linearGradient id={`split-graph-fill-${symmetric ? 'sym' : 'asym'}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"  stopColor={a.base} stopOpacity={symmetric ? '0.30' : '0.32'} />
            {symmetric && <stop offset="50%" stopColor={a.base} stopOpacity="0" />}
            <stop offset="100%" stopColor={a.base} stopOpacity={symmetric ? '0.30' : '0'} />
          </linearGradient>
        </defs>
        {symmetric && <line x1={0} x2={w} y1={baselineY} y2={baselineY} stroke={t.fgDim} strokeWidth={0.7} strokeDasharray="2 3" />}
        {!symmetric && [1,2,3].map(i => (
          <line key={i} x1={0} x2={w} y1={(i) * h / 4} y2={(i) * h / 4}
            stroke={t.borderS} strokeWidth={0.5} strokeDasharray="1 3" />
        ))}
        <path d={`${path} L ${last[0]} ${baselineY} L ${points[0][0]} ${baselineY} Z`} fill={`url(#split-graph-fill-${symmetric ? 'sym' : 'asym'})`} />
        <path d={path} fill="none" stroke={a.base} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
        <circle cx={last[0]} cy={last[1]} r="2.5" fill={a.base} />
        <circle cx={last[0]} cy={last[1]} r="5" fill={a.base} opacity="0.25" />
      </svg>
    </div>
  );
}

Object.assign(window, { AASplitShell, SPLIT_PAGE1_DEFAULTS, SPLIT_PAGE2_DEFAULTS });
