// pi-drive primitives — small atoms reused across screens.

// ─── Icons ───────────────────────────────────────────────────
const Icon = ({ d, size = 20, stroke = 1.6, fill = 'none', style }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill}
    stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" style={style}>
    {typeof d === 'string' ? <path d={d} /> : d}
  </svg>
);
const I = {
  home:    'M3 11l9-8 9 8M5 9.5V21h14V9.5',
  trips:   'M12 8v5l3 2M21 12a9 9 0 1 1-3-6.7M21 4v5h-5',
  cog:     'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1A1.7 1.7 0 0 0 4.6 8.6a1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  bluetooth:'M6.5 17.5L17.5 6.5 12 1v22l5.5-5.5L6.5 6.5',
  chevR:   'M9 6l6 6-6 6',
  chevL:   'M15 6l-6 6 6 6',
  chevD:   'M6 9l6 6 6-6',
  plus:    'M12 5v14M5 12h14',
  check:   'M5 12l5 5L20 7',
  x:       'M6 6l12 12M18 6L6 18',
  drag:    'M9 5h.01M9 12h.01M9 19h.01M15 5h.01M15 12h.01M15 19h.01',
  car:     'M5 17h14M5 17l1.5-5.5a2 2 0 0 1 2-1.5h7a2 2 0 0 1 2 1.5L19 17M5 17v2.5h2V17M19 17v2.5h-2V17M8 14h8',
  speed:   'M12 14l4-4M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z',
  graph:   'M3 17l5-5 4 4 8-9M14 7h6v6',
  wifi:    'M2 9a14 14 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01',
  cloud:   'M17 17a4 4 0 0 0 0-8 6 6 0 0 0-11.5 2A4 4 0 0 0 6 17h11z',
  cloudUp: 'M17 17a4 4 0 0 0 0-8 6 6 0 0 0-11.5 2A4 4 0 0 0 6 17h2M12 21V13M9 16l3-3 3 3',
  shield:  'M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5l8-3z',
  bell:    'M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9zM9 21a3 3 0 0 0 6 0',
  refresh: 'M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5',
  thermo:  'M14 14.8V4a2 2 0 0 0-4 0v10.8a4 4 0 1 0 4 0z',
  fuel:    'M3 21h12V3H3v18zM15 8h2a2 2 0 0 1 2 2v8a2 2 0 0 0 4 0V7l-3-3',
  battery: 'M2 7h16v10H2zM18 10v4h2v-4z',
  gforce:  'M12 4v4M12 16v4M4 12h4M16 12h4M12 12m-3 0a3 3 0 1 0 6 0 3 3 0 1 0-6 0',
  pin:     'M12 21s-7-7-7-12a7 7 0 0 1 14 0c0 5-7 12-7 12zM12 9a2 2 0 1 0 0 4 2 2 0 0 0 0-4',
  back:    'M19 12H5M12 19l-7-7 7-7',
  signal:  'M2 20h2v-4H2v4zm5 0h2v-8H7v8zm5 0h2v-12h-2v12zm5 0h2V4h-2v16z',
  dot:     'M12 12m-3 0a3 3 0 1 0 6 0 3 3 0 1 0-6 0',
  search:  'M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16zM21 21l-4.3-4.3',
  info:    'M12 8v.01M12 11v5M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  filter:  'M3 5h18M6 12h12M10 19h4',
  trash:   'M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6',
  pencil:  'M12 20h9M16.5 3.5a2.1 2.1 0 1 1 3 3L7 19l-4 1 1-4z',
  swipe:   'M4 12a8 8 0 0 1 16 0M9 12l-3 3-3-3M21 12l-3 3-3-3',
};

// ─── Status bar (custom, replaces android-frame's default) ──
function PDStatusBar({ time = '9:41' }) {
  const { t } = usePD();
  return (
    <div style={{
      height: 28, padding: '0 18px', display: 'flex', alignItems: 'center',
      justifyContent: 'space-between', color: t.fg, fontSize: 13,
      fontFamily: PD_FONT, fontWeight: 500, letterSpacing: 0.1, flexShrink: 0,
    }}>
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>{time}</span>
      <div style={{ display: 'flex', gap: 5, alignItems: 'center', opacity: 0.85 }}>
        <Icon d={I.signal} size={13} stroke={1.8} fill="currentColor" />
        <Icon d={I.wifi} size={13} stroke={1.8} />
        <svg width="20" height="11" viewBox="0 0 22 11" fill="none">
          <rect x="0.5" y="0.5" width="18" height="10" rx="2.2" stroke="currentColor" />
          <rect x="2" y="2" width="14" height="7" rx="1" fill="currentColor" />
          <rect x="19.5" y="3.5" width="1.5" height="4" rx="0.5" fill="currentColor" />
        </svg>
      </div>
    </div>
  );
}

// ─── Top bar ────────────────────────────────────────────────
function PDTopBar({ title, subtitle, onBack, action, dense }) {
  const { t } = usePD();
  return (
    <div style={{
      padding: dense ? '8px 16px 10px' : '12px 16px 14px',
      display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0,
    }}>
      {onBack && (
        <button onClick={onBack} style={{
          width: 36, height: 36, borderRadius: 18, border: 'none',
          background: t.surface, color: t.fg, display: 'flex',
          alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0,
        }}>
          <Icon d={I.back} size={18} />
        </button>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: dense ? 18 : 20, fontWeight: 600, color: t.fg, letterSpacing: -0.3 }}>{title}</div>
        {subtitle && <div style={{ fontSize: 12, color: t.fgMuted, marginTop: 2 }}>{subtitle}</div>}
      </div>
      {action}
    </div>
  );
}

// ─── Bottom nav (Home / Trips / Settings) ───────────────────
function PDBottomNav({ route, go }) {
  const { t, a } = usePD();
  const items = [
    { id: 'home',     label: 'Live',     icon: I.home  },
    { id: 'trips',    label: 'Trips',    icon: I.trips },
    { id: 'settings', label: 'Settings', icon: I.cog   },
  ];
  return (
    <div style={{
      borderTop: `1px solid ${t.borderS}`,
      background: t.bg,
      padding: '6px 4px 8px',
      display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
      flexShrink: 0,
    }}>
      {items.map(it => {
        const active = route.startsWith(it.id);
        return (
          <button key={it.id} onClick={() => go(it.id)} style={{
            background: 'transparent', border: 'none', cursor: 'pointer',
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3,
            padding: '6px 0', color: active ? a.base : t.fgMuted,
            fontFamily: PD_FONT, fontSize: 11, fontWeight: 500,
          }}>
            <Icon d={it.icon} size={20} stroke={active ? 2 : 1.6} />
            <span>{it.label}</span>
          </button>
        );
      })}
    </div>
  );
}

// ─── Card / Row primitives ──────────────────────────────────
function PDCard({ children, style, padded = true }) {
  const { t } = usePD();
  return (
    <div style={{
      background: t.bgElev,
      borderRadius: 14,
      border: `1px solid ${t.borderS}`,
      padding: padded ? 14 : 0,
      ...style,
    }}>{children}</div>
  );
}

function PDRow({ label, sub, right, icon, onClick, danger }) {
  const { t, a } = usePD();
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 12, width: '100%',
      background: 'transparent', border: 'none', textAlign: 'left',
      padding: '12px 4px', color: danger ? t.danger : t.fg,
      cursor: onClick ? 'pointer' : 'default', fontFamily: PD_FONT,
    }}>
      {icon !== undefined && (
        <div style={{
          width: 32, height: 32, borderRadius: 10,
          background: t.surface2, color: danger ? t.danger : t.fgMuted,
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        }}><Icon d={icon} size={16} /></div>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 500 }}>{label}</div>
        {sub && <div style={{ fontSize: 12, color: t.fgMuted, marginTop: 2 }}>{sub}</div>}
      </div>
      {right !== undefined ? right : onClick && <Icon d={I.chevR} size={16} style={{ color: t.fgDim }} />}
    </Tag>
  );
}

// ─── Toggle / Switch ────────────────────────────────────────
function PDToggle({ value, onChange }) {
  const { t, a } = usePD();
  return (
    <button onClick={() => onChange(!value)} style={{
      width: 40, height: 24, borderRadius: 12, border: 'none',
      background: value ? a.base : t.surface2, position: 'relative', cursor: 'pointer',
      transition: 'background 0.18s',
    }}>
      <span style={{
        position: 'absolute', top: 3, left: value ? 19 : 3,
        width: 18, height: 18, borderRadius: 9, background: '#fff',
        transition: 'left 0.18s', boxShadow: '0 1px 2px rgba(0,0,0,0.3)',
      }} />
    </button>
  );
}

// ─── Slider ─────────────────────────────────────────────────
function PDSlider({ value, min = 0, max = 100, step = 1, onChange, unit = '', format }) {
  const { t, a } = usePD();
  const pct = ((value - min) / (max - min)) * 100;
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
        <span style={{ fontSize: 12, color: t.fgMuted, fontFamily: PD_FONT }}>{min}{unit}</span>
        <span style={{ fontSize: 22, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, fontVariantNumeric: 'tabular-nums' }}>
          {format ? format(value) : value}{unit}
        </span>
        <span style={{ fontSize: 12, color: t.fgMuted, fontFamily: PD_FONT }}>{max}{unit}</span>
      </div>
      <div style={{ position: 'relative', height: 18, display: 'flex', alignItems: 'center' }}>
        <div style={{ position: 'absolute', left: 0, right: 0, height: 4, borderRadius: 2, background: t.surface2 }} />
        <div style={{ position: 'absolute', left: 0, width: `${pct}%`, height: 4, borderRadius: 2, background: a.base }} />
        <div style={{
          position: 'absolute', left: `calc(${pct}% - 9px)`,
          width: 18, height: 18, borderRadius: 9, background: '#fff',
          boxShadow: `0 0 0 4px ${a.soft}, 0 1px 3px rgba(0,0,0,0.4)`,
        }} />
        <input type="range" min={min} max={max} step={step} value={value}
          onChange={e => onChange(Number(e.target.value))}
          style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%' }} />
      </div>
    </div>
  );
}

// ─── Button ────────────────────────────────────────────────
function PDButton({ children, onClick, variant = 'primary', icon, full, size = 'md' }) {
  const { t, a } = usePD();
  const isP = variant === 'primary';
  const isG = variant === 'ghost';
  const isS = variant === 'secondary';
  const pad = size === 'sm' ? '6px 12px' : size === 'lg' ? '14px 18px' : '10px 16px';
  const fs  = size === 'sm' ? 13 : size === 'lg' ? 16 : 14;
  return (
    <button onClick={onClick} style={{
      width: full ? '100%' : 'auto', padding: pad, borderRadius: 999,
      fontFamily: PD_FONT, fontWeight: 600, fontSize: fs,
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
      border: isS ? `1px solid ${t.border}` : 'none',
      background: isP ? a.base : isS ? 'transparent' : 'transparent',
      color: isP ? 'oklch(0.18 0.02 60)' : isG ? a.base : t.fg,
      cursor: 'pointer',
    }}>
      {icon && <Icon d={icon} size={fs + 2} stroke={2} />}
      {children}
    </button>
  );
}

// ─── Pill / Chip ───────────────────────────────────────────
function PDPill({ children, color = 'default', dot, style }) {
  const { t, a } = usePD();
  const colors = {
    default: { bg: t.surface, fg: t.fgMuted, dot: t.fgMuted },
    accent:  { bg: a.soft,    fg: a.base,    dot: a.base    },
    success: { bg: 'oklch(0.74 0.16 150 / 0.16)', fg: t.success, dot: t.success },
    danger:  { bg: 'oklch(0.66 0.20 25 / 0.16)',  fg: t.danger,  dot: t.danger  },
    warn:    { bg: 'oklch(0.80 0.15 80 / 0.16)',  fg: t.warn,    dot: t.warn    },
  }[color];
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '4px 10px', borderRadius: 999,
      background: colors.bg, color: colors.fg,
      fontFamily: PD_FONT, fontSize: 11, fontWeight: 600, letterSpacing: 0.1,
      ...style,
    }}>
      {dot && <span style={{ width: 6, height: 6, borderRadius: 3, background: colors.dot }} />}
      {children}
    </span>
  );
}

// ─── Big number readout ────────────────────────────────────
function PDReadout({ value, unit, label, size = 'lg', accent }) {
  const { t, a } = usePD();
  const sizes = {
    sm: { v: 22, u: 11, l: 10 },
    md: { v: 32, u: 13, l: 11 },
    lg: { v: 56, u: 14, l: 11 },
    xl: { v: 84, u: 18, l: 12 },
  }[size];
  return (
    <div>
      {label && <div style={{ fontSize: sizes.l, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500, letterSpacing: 0.4, textTransform: 'uppercase', marginBottom: 4 }}>{label}</div>}
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, color: accent ? a.base : t.fg }}>
        <span style={{ fontSize: sizes.v, fontFamily: PD_FONT_MONO, fontWeight: 500, fontVariantNumeric: 'tabular-nums', letterSpacing: -1, lineHeight: 1 }}>{value}</span>
        {unit && <span style={{ fontSize: sizes.u, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>{unit}</span>}
      </div>
    </div>
  );
}

// ─── Mini dial (for grid tiles) ────────────────────────────
function PDDial({ value, min = 0, max = 100, label, unit, size = 96, thickness = 8, accent = true, warnAt }) {
  const { t, a } = usePD();
  const pct = Math.max(0, Math.min(1, (value - min) / (max - min)));
  const arc = 270; // degrees
  const startA = 135;
  const r = (size - thickness) / 2;
  const c = size / 2;
  // arc as svg path (sweep clockwise)
  const polar = (deg) => {
    const rad = (deg - 90) * Math.PI / 180;
    return [c + r * Math.cos(rad), c + r * Math.sin(rad)];
  };
  const [x0, y0] = polar(startA);
  const [xE, yE] = polar(startA + arc);
  const [xV, yV] = polar(startA + arc * pct);
  const large = arc > 180 ? 1 : 0;
  const largeV = arc * pct > 180 ? 1 : 0;
  const over = warnAt != null && value >= warnAt;
  const stroke = over ? t.danger : (accent ? a.base : t.fg);
  return (
    <div style={{ width: size, height: size, position: 'relative' }}>
      <svg width={size} height={size}>
        <path d={`M ${x0} ${y0} A ${r} ${r} 0 ${large} 1 ${xE} ${yE}`}
          fill="none" stroke={t.surface2} strokeWidth={thickness} strokeLinecap="round" />
        <path d={`M ${x0} ${y0} A ${r} ${r} 0 ${largeV} 1 ${xV} ${yV}`}
          fill="none" stroke={stroke} strokeWidth={thickness} strokeLinecap="round" />
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontSize: size * 0.28, fontFamily: PD_FONT_MONO, fontWeight: 500, color: t.fg, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{value}</div>
        {unit && <div style={{ fontSize: size * 0.10, color: t.fgMuted, marginTop: 2, fontFamily: PD_FONT }}>{unit}</div>}
        {label && <div style={{ fontSize: size * 0.10, color: t.fgDim, marginTop: 3, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>{label}</div>}
      </div>
    </div>
  );
}

// ─── Bar gauge ─────────────────────────────────────────────
function PDBar({ value, min = 0, max = 100, label, unit, marks }) {
  const { t, a } = usePD();
  const pct = Math.max(0, Math.min(1, (value - min) / (max - min))) * 100;
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
        <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>{label}</span>
        <span style={{ fontFamily: PD_FONT_MONO, fontSize: 16, color: t.fg, fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
          {value}<span style={{ fontSize: 10, color: t.fgMuted, marginLeft: 3 }}>{unit}</span>
        </span>
      </div>
      <div style={{ height: 6, borderRadius: 3, background: t.surface2, position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${pct}%`, background: a.base, borderRadius: 3 }} />
        {marks && marks.map((m, i) => (
          <div key={i} style={{ position: 'absolute', left: `${m * 100}%`, top: -1, bottom: -1, width: 1, background: t.border }} />
        ))}
      </div>
    </div>
  );
}

// ─── Line graph (sparkline / full) ─────────────────────────
function PDLine({ data, width = 200, height = 60, label, current, unit, axes }) {
  const { t, a } = usePD();
  if (!data || !data.length) return null;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const pad = 2;
  const range = max - min || 1;
  const points = data.map((v, i) => {
    const x = pad + (i / (data.length - 1)) * (width - pad * 2);
    const y = pad + (1 - (v - min) / range) * (height - pad * 2);
    return [x, y];
  });
  const path = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  const area = `${path} L ${points[points.length - 1][0]} ${height - pad} L ${points[0][0]} ${height - pad} Z`;
  return (
    <div>
      {(label || current != null) && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
          {label && <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>{label}</span>}
          {current != null && (
            <span style={{ fontFamily: PD_FONT_MONO, fontSize: 14, color: t.fg, fontVariantNumeric: 'tabular-nums' }}>
              {current}<span style={{ fontSize: 10, color: t.fgMuted, marginLeft: 3 }}>{unit}</span>
            </span>
          )}
        </div>
      )}
      <svg width={width} height={height} style={{ display: 'block' }}>
        <defs>
          <linearGradient id={`pdg-${a.base}-${width}-${height}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={a.base} stopOpacity="0.32" />
            <stop offset="100%" stopColor={a.base} stopOpacity="0" />
          </linearGradient>
        </defs>
        {axes && Array.from({ length: 4 }).map((_, i) => (
          <line key={i} x1={0} x2={width} y1={(i+1) * height / 5} y2={(i+1) * height / 5}
            stroke={t.borderS} strokeWidth={0.6} strokeDasharray="2 4" />
        ))}
        <path d={area} fill={`url(#pdg-${a.base}-${width}-${height})`} />
        <path d={path} fill="none" stroke={a.base} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  );
}

Object.assign(window, {
  Icon, I,
  PDStatusBar, PDTopBar, PDBottomNav,
  PDCard, PDRow,
  PDToggle, PDSlider, PDButton, PDPill,
  PDReadout, PDDial, PDBar, PDLine,
});
