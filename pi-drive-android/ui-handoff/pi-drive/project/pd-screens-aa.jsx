// pi-drive — Android Auto screens (Dials + Graphs) + landscape shell

// AA screen 1 — big dials
function AAScreenDials() {
  const { t, a } = usePD();
  const [tick, setTick] = React.useState(0);
  React.useEffect(() => {
    const i = setInterval(() => setTick(x => x + 1), 1100);
    return () => clearInterval(i);
  }, []);
  const w = (b, amp) => Math.round(b + Math.sin(tick * 0.7) * amp);

  return (
    <div style={{
      flex: 1, padding: '14px 20px 16px',
      display: 'grid', gridTemplateRows: '1fr auto', gap: 12,
    }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
        {/* Speed */}
        <AABigDial label="Speed" unit="mph" value={w(58, 4)} min={0} max={120} primary />
        {/* RPM */}
        <AABigDial label="RPM"   unit="× 1000" value={Math.round(w(2750, 180)/100)/10} min={0} max={8} warnAt={6.5} />
        {/* Coolant */}
        <AABigDial label="Coolant" unit="°F"  value={w(192, 2)} min={120} max={240} />
      </div>
      {/* Bottom strip */}
      <div style={{
        background: t.bgElev, borderRadius: 14, padding: '10px 16px',
        display: 'flex', alignItems: 'center', gap: 24, border: `1px solid ${t.borderS}`,
      }}>
        <AAStat icon={I.pin}     label="Trip"     value="14.3 mi" />
        <AAStatDiv />
        <AAStat icon={I.refresh} label="Manual"   value="248.6 mi" mono />
        <AAStatDiv />
        <AAStat icon={I.fuel}    label="MPG · trip" value="26.4" mono />
        <AAStatDiv />
        <AAStat icon={I.fuel}    label="MPG · now"  value={`${(28 + Math.sin(tick * 0.5) * 6).toFixed(1)}`} mono accent />
        <AAStatDiv />
        <AAStat icon={I.battery} label="Battery"  value="14.2 V" />
        <div style={{ flex: 1 }} />
        <PDPill color="success" dot>STREAMING</PDPill>
      </div>
    </div>
  );
}

function AABigDial({ label, unit, value, min, max, primary, warnAt }) {
  const { t, a } = usePD();
  const pct = Math.max(0, Math.min(1, (value - min) / (max - min)));
  const over = warnAt != null && value >= warnAt;
  const stroke = over ? t.danger : a.base;
  const arc = 270, startA = 135;
  const size = 200, thickness = 14;
  const r = (size - thickness) / 2, c = size / 2;
  const polar = deg => {
    const rad = (deg - 90) * Math.PI / 180;
    return [c + r * Math.cos(rad), c + r * Math.sin(rad)];
  };
  const [x0, y0] = polar(startA);
  const [xE, yE] = polar(startA + arc);
  const [xV, yV] = polar(startA + arc * pct);
  const large = arc > 180 ? 1 : 0;
  const largeV = arc * pct > 180 ? 1 : 0;

  return (
    <div style={{
      background: t.bgElev, borderRadius: 16, border: `1px solid ${t.borderS}`,
      padding: 14, display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', position: 'relative',
    }}>
      <div style={{ position: 'absolute', top: 12, left: 14, fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>{label}</div>
      <div style={{ position: 'relative', width: size, height: size }}>
        <svg width={size} height={size}>
          <path d={`M ${x0} ${y0} A ${r} ${r} 0 ${large} 1 ${xE} ${yE}`}
            fill="none" stroke={t.surface2} strokeWidth={thickness} strokeLinecap="round" />
          <path d={`M ${x0} ${y0} A ${r} ${r} 0 ${largeV} 1 ${xV} ${yV}`}
            fill="none" stroke={stroke} strokeWidth={thickness} strokeLinecap="round" />
          {/* tick marks */}
          {Array.from({ length: 11 }).map((_, i) => {
            const [tx, ty] = polar(startA + arc * (i / 10));
            const [tx2, ty2] = polar(startA + arc * (i / 10));
            // outer tick — offset inward by ~10
            const rad = ((startA + arc * (i/10)) - 90) * Math.PI / 180;
            const r2 = r - thickness/2 - 6;
            const x1 = c + r2 * Math.cos(rad);
            const y1 = c + r2 * Math.sin(rad);
            return <circle key={i} cx={x1} cy={y1} r="1.4" fill={t.fgDim} />;
          })}
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{
            fontSize: primary ? 76 : 56, fontFamily: PD_FONT_MONO,
            fontWeight: 500, color: t.fg, fontVariantNumeric: 'tabular-nums', lineHeight: 1, letterSpacing: -2,
          }}>{value}</div>
          <div style={{ fontSize: 14, color: t.fgMuted, marginTop: 8, fontFamily: PD_FONT }}>{unit}</div>
        </div>
      </div>
    </div>
  );
}

function AAStat({ icon, label, value, mono, accent }) {
  const { t, a } = usePD();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <Icon d={icon} size={16} style={{ color: accent ? a.base : t.fgMuted }} />
      <div>
        <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>{label}</div>
        <div style={{ fontSize: 16, color: accent ? a.base : t.fg, fontFamily: mono ? PD_FONT_MONO : PD_FONT, fontWeight: 500, marginTop: 1, fontVariantNumeric: 'tabular-nums' }}>{value}</div>
      </div>
    </div>
  );
}
function AAStatDiv() {
  const { t } = usePD();
  return <div style={{ width: 1, height: 28, background: t.borderS }} />;
}

// AA screen 2 — graphs
function AAScreenGraphs() {
  const { t, a } = usePD();
  const [tick, setTick] = React.useState(0);
  React.useEffect(() => {
    const i = setInterval(() => setTick(x => x + 1), 700);
    return () => clearInterval(i);
  }, []);
  // generate rolling data
  const data1 = Array.from({ length: 48 }, (_, i) => 30 + 25 * Math.sin((i + tick) * 0.3) + Math.cos((i + tick) * 0.7) * 8);
  const data2 = Array.from({ length: 48 }, (_, i) => Math.sin((i + tick) * 0.5) * 0.35 + Math.cos((i + tick) * 0.21) * 0.15);

  return (
    <div style={{
      flex: 1, padding: '14px 20px 16px',
      display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 14,
    }}>
      <div style={{ display: 'grid', gridTemplateRows: '1fr 1fr', gap: 12 }}>
        <AAGraphCard
          label="Throttle"
          unit="%"
          current={Math.round(data1[data1.length - 1])}
          data={data1.map(v => Math.max(0, Math.min(100, v)))}
        />
        <AAGraphCard
          label="G-force (lateral)"
          unit="g"
          current={data2[data2.length - 1].toFixed(2)}
          data={data2}
          symmetric
        />
      </div>
      <div style={{ display: 'grid', gridTemplateRows: '1fr 1fr', gap: 12 }}>
        <AAMpgBox instant={(28 + Math.sin(tick * 0.5) * 6).toFixed(1)} trip="26.4" />
        <AAStatBox label="Manual trip" value="248.6" unit="mi" sub="since May 18 · 31.4 mpg avg" reset />
      </div>
    </div>
  );
}

function AAMpgBox({ instant, trip }) {
  const { t, a } = usePD();
  return (
    <div style={{
      background: t.bgElev, borderRadius: 14, border: `1px solid ${t.borderS}`,
      padding: '14px 16px', display: 'flex', flexDirection: 'column', justifyContent: 'center',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>MPG · instant</div>
        <span style={{ fontSize: 9, color: t.fgDim, fontFamily: PD_FONT_MONO }}>calc · MAF</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 4, color: a.base }}>
        <span style={{ fontSize: 48, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -1.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{instant}</span>
        <span style={{ fontSize: 14, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>mpg</span>
      </div>
      <div style={{ marginTop: 10, paddingTop: 8, borderTop: `1px solid ${t.borderS}`, display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <span style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>Trip avg</span>
        <span style={{ fontFamily: PD_FONT_MONO, fontSize: 18, color: t.fg, fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
          {trip}<span style={{ fontSize: 11, color: t.fgMuted, marginLeft: 3 }}>mpg</span>
        </span>
      </div>
    </div>
  );
}

function AAGraphCard({ label, unit, current, data, symmetric }) {
  const { t, a } = usePD();
  const [w, setW] = React.useState(400);
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver(es => setW(Math.max(100, es[0].contentRect.width)));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);

  const h = 110;
  const pad = 4;
  const min = symmetric ? -Math.max(...data.map(Math.abs)) - 0.05 : Math.min(...data);
  const max = symmetric ?  Math.max(...data.map(Math.abs)) + 0.05 : Math.max(...data);
  const range = (max - min) || 1;
  const points = data.map((v, i) => {
    const x = pad + (i / (data.length - 1)) * (w - pad * 2);
    const y = pad + (1 - (v - min) / range) * (h - pad * 2);
    return [x, y];
  });
  const path = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  const area = `${path} L ${points[points.length - 1][0]} ${h - pad} L ${points[0][0]} ${h - pad} Z`;
  const midY = symmetric ? pad + (h - pad * 2) / 2 : h - pad;

  return (
    <div ref={ref} style={{
      background: t.bgElev, borderRadius: 14, border: `1px solid ${t.borderS}`,
      padding: 14, display: 'flex', flexDirection: 'column', gap: 6, overflow: 'hidden',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <span style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>{label}</span>
        <span style={{ fontSize: 24, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, fontVariantNumeric: 'tabular-nums', letterSpacing: -0.5 }}>
          {current}<span style={{ fontSize: 12, color: t.fgMuted, marginLeft: 4 }}>{unit}</span>
        </span>
      </div>
      <svg width={w} height={h} style={{ display: 'block' }}>
        <defs>
          <linearGradient id={`aag-${label}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={a.base} stopOpacity="0.32" />
            <stop offset="100%" stopColor={a.base} stopOpacity="0" />
          </linearGradient>
        </defs>
        {/* grid */}
        {[1,2,3].map(i => (
          <line key={i} x1={0} x2={w} y1={(i) * h / 4} y2={(i) * h / 4}
            stroke={t.borderS} strokeWidth={0.6} strokeDasharray="2 4" />
        ))}
        {symmetric && <line x1={0} x2={w} y1={midY} y2={midY} stroke={t.fgDim} strokeWidth={0.6} />}
        <path d={area} fill={`url(#aag-${label})`} />
        <path d={path} fill="none" stroke={a.base} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
        {/* current point */}
        <circle cx={points[points.length - 1][0]} cy={points[points.length - 1][1]} r="3" fill={a.base} />
        <circle cx={points[points.length - 1][0]} cy={points[points.length - 1][1]} r="6" fill={a.base} opacity="0.25" />
      </svg>
    </div>
  );
}

function AAStatBox({ label, value, unit, sub, reset }) {
  const { t } = usePD();
  return (
    <div style={{
      background: t.bgElev, borderRadius: 14, border: `1px solid ${t.borderS}`,
      padding: '14px 16px', display: 'flex', flexDirection: 'column', justifyContent: 'center', position: 'relative',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>{label}</div>
        {reset && (
          <button style={{
            padding: '2px 10px', borderRadius: 99, border: `1px solid ${t.border}`,
            background: 'transparent', color: t.fgMuted, cursor: 'pointer',
            fontFamily: PD_FONT, fontSize: 10, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase',
            display: 'inline-flex', alignItems: 'center', gap: 4,
          }}><Icon d={I.refresh} size={9} stroke={2.4} />Reset</button>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 6 }}>
        <span style={{ fontSize: 44, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -1.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{value}</span>
        <span style={{ fontSize: 14, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>{unit}</span>
      </div>
      {sub && <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 6 }}>{sub}</div>}
    </div>
  );
}

Object.assign(window, { AAScreenDials, AAScreenGraphs });
