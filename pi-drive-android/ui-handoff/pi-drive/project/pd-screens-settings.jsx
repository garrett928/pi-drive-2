// pi-drive — settings screens (root, server, home customize, AA layout, thresholds)

// All available metrics — used by the customize screens.
const PD_METRICS = [
  { id: 'speed',       label: 'Speed',          unit: 'mph',  icon: I.speed,   pid: '0D' },
  { id: 'mpg',         label: 'MPG · instant',  unit: 'mpg',  icon: I.fuel,    pid: 'calc' },
  { id: 'mpgTrip',     label: 'MPG · trip avg', unit: 'mpg',  icon: I.fuel,    pid: 'calc' },
  { id: 'mpgManual',   label: 'MPG · manual avg', unit: 'mpg',  icon: I.fuel,    pid: 'calc' },
  { id: 'rpm',         label: 'RPM',            unit: 'rpm',  icon: I.refresh, pid: '0C' },
  { id: 'throttle',    label: 'Throttle',       unit: '%',    icon: I.graph,   pid: '11' },
  { id: 'coolant',     label: 'Coolant temp',   unit: '°F',   icon: I.thermo,  pid: '05' },
  { id: 'intake',      label: 'Intake temp',    unit: '°F',   icon: I.thermo,  pid: '0F' },
  { id: 'oilTemp',     label: 'Oil temp',       unit: '°F',   icon: I.thermo,  pid: '5C' },
  { id: 'battery',     label: 'Battery',        unit: 'V',    icon: I.battery, pid: 'ATRV' },
  { id: 'fuel',        label: 'Fuel level',     unit: '%',    icon: I.fuel,    pid: '2F' },
  { id: 'maf',         label: 'MAF air flow',   unit: 'g/s',  icon: I.refresh, pid: '10' },
  { id: 'gforce',      label: 'G-force',        unit: 'g',    icon: I.gforce,  pid: 'sensor' },
  { id: 'accel',       label: 'Linear accel',   unit: 'm/s²', icon: I.gforce,  pid: 'fusion' },
  { id: 'distance',    label: 'Trip distance',  unit: 'mi',   icon: I.pin,     pid: 'calc' },
  { id: 'manualTrip',  label: 'Manual trip',    unit: 'mi',   icon: I.pin,     pid: 'calc' },
];

// ─── Settings root ─────────────────────────────────────────
function ScreenSettings({ go }) {
  const { t, a } = usePD();

  return (
    <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
      {/* Vehicle card */}
      <PDCard style={{ marginBottom: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 44, height: 44, borderRadius: 12, background: t.surface2, color: t.fgMuted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon d={I.car} size={22} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 14, color: t.fg, fontWeight: 500, fontFamily: PD_FONT }}>2019 Subaru WRX</div>
            <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 2 }}>OBDLink LX · 9F4C · connected</div>
          </div>
          <PDPill color="success" dot>LIVE</PDPill>
        </div>
        <div style={{ height: 1, background: t.borderS, margin: '12px -14px' }} />
        <button onClick={() => go('connect')} style={{
          display: 'flex', width: '100%', alignItems: 'center', gap: 10,
          background: 'transparent', border: 'none', padding: '2px 0',
          color: a.base, cursor: 'pointer', fontFamily: PD_FONT, fontSize: 13, fontWeight: 600,
        }}>
          <Icon d={I.bluetooth} size={14} stroke={2} />Pair a new dongle
        </button>
      </PDCard>

      {/* Section: data & display */}
      <SettingsSection title="Data & Display">
        <PDRow icon={I.home}   label="Phone home layout"     sub="7 tiles · speed & MPG featured" onClick={() => go('settings/home-layout')} />
        <SettingsDivider />
        <PDRow icon={I.car}    label="Android Auto layout"   sub="Dials · Graphs · swipeable"   onClick={() => go('settings/aa-layout')} />
        <SettingsDivider />
        <PDRow icon={I.refresh} label="Manual trip counter"  sub="248.6 mi · since May 18"      right={
          <button onClick={(e) => e.stopPropagation()} style={{
            padding: '4px 10px', borderRadius: 99, border: `1px solid ${t.border}`,
            background: 'transparent', color: t.fgMuted, cursor: 'pointer',
            fontFamily: PD_FONT, fontSize: 11, fontWeight: 600, letterSpacing: 0.3,
          }}>Reset</button>
        } />
        <SettingsDivider />
        <PDRow icon={I.graph}  label="Telemetry stream"      sub="13 signals · 30 Hz"           onClick={() => go('settings/server')} />
      </SettingsSection>

      {/* Section: server & cloud */}
      <SettingsSection title="Cloud & Server">
        <PDRow icon={I.cloudUp} label="Telemetry server"    sub="fleet.acme.io · authenticated" onClick={() => go('settings/server')} />
        <SettingsDivider />
        <PDRow icon={I.wifi}    label="Network policy"      sub="Stream on cellular + Wi-Fi"    onClick={() => {}} />
        <SettingsDivider />
        <PDRow icon={I.shield}  label="Privacy & retention" sub="Keep trips for 90 days"        onClick={() => {}} />
      </SettingsSection>

      {/* Section: alerts */}
      <SettingsSection title="Driving alerts">
        <PDRow icon={I.bell}    label="Thresholds" sub="Hard brake · acceleration · speed" onClick={() => go('settings/thresholds')} />
        <SettingsDivider />
        <PDRow icon={I.info}    label="Diagnostic codes" sub="2 active · last read 8 min ago" onClick={() => {}} right={<PDPill color="warn">2</PDPill>} />
      </SettingsSection>

      <SettingsSection title="App">
        <PDRow icon={I.info}  label="About"     sub="pi-drive · v 0.3.1 · build 412" onClick={() => {}} />
        <SettingsDivider />
        <PDRow icon={I.trash} label="Reset all settings" danger />
      </SettingsSection>
    </div>
  );
}

function SettingsSection({ title, children }) {
  const { t } = usePD();
  return (
    <>
      <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase', padding: '6px 4px 8px' }}>{title}</div>
      <PDCard padded={false} style={{ padding: '4px 10px', marginBottom: 14 }}>{children}</PDCard>
    </>
  );
}
function SettingsDivider() {
  const { t } = usePD();
  return <div style={{ height: 1, background: t.borderS, marginLeft: 44 }} />;
}

// ─── Server config ─────────────────────────────────────────
function ScreenServer({ go }) {
  const { t, a } = usePD();
  const [streamLive, setStreamLive] = React.useState(true);
  const [bufferOffline, setBufferOffline] = React.useState(true);
  const [wifiOnly, setWifiOnly] = React.useState(false);
  const [compress, setCompress] = React.useState(true);
  const [rate, setRate] = React.useState(30);

  return (
    <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
      <SettingsSection title="Endpoint">
        <Field label="Server URL" value="https://fleet.acme.io/v2/telemetry" />
        <SettingsDivider />
        <Field label="Device ID" value="pd-rxv7a3-k9892" mono />
        <SettingsDivider />
        <Field label="API key" value="••••••••••••••••  ck_live_8f72" mono right={<PDPill color="success" dot>VERIFIED</PDPill>} />
      </SettingsSection>

      {/* Test connection */}
      <PDCard style={{ marginBottom: 14, display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ width: 36, height: 36, borderRadius: 10, background: 'oklch(0.74 0.16 150 / 0.18)', color: t.success, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon d={I.cloudUp} size={18} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>Connection healthy</div>
          <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 2 }}>62 ms · last sync 0s ago</div>
        </div>
        <PDButton size="sm" variant="secondary" icon={I.refresh}>Test</PDButton>
      </PDCard>

      <SettingsSection title="Streaming">
        <ToggleRow icon={I.cloudUp}  label="Stream live while driving"  sub="Sends to server in real time"          value={streamLive}     onChange={setStreamLive} />
        <SettingsDivider />
        <ToggleRow icon={I.shield}   label="Buffer when offline"        sub="Writes to disk, uploads later"         value={bufferOffline} onChange={setBufferOffline} />
        <SettingsDivider />
        <ToggleRow icon={I.wifi}     label="Upload on Wi-Fi only"       sub="Skip cellular for queued uploads"      value={wifiOnly}       onChange={setWifiOnly} />
        <SettingsDivider />
        <ToggleRow icon={I.graph}    label="Compress payloads"          sub="zstd · ~3.4× smaller"                  value={compress}       onChange={setCompress} />
      </SettingsSection>

      <SettingsSection title="Sample rate">
        <div style={{ padding: '12px 4px 6px' }}>
          <PDSlider value={rate} min={1} max={60} step={1} unit=" Hz" onChange={setRate}
            format={v => v} />
        </div>
        <div style={{ display: 'flex', gap: 6, padding: '4px 4px 8px', flexWrap: 'wrap' }}>
          {[1, 5, 10, 30, 60].map(r => (
            <button key={r} onClick={() => setRate(r)} style={{
              padding: '5px 12px', borderRadius: 99, fontFamily: PD_FONT, fontSize: 12,
              border: `1px solid ${rate === r ? a.base : t.border}`,
              background: rate === r ? a.soft : 'transparent',
              color: rate === r ? a.base : t.fgMuted,
              fontWeight: 600, cursor: 'pointer',
            }}>{r} Hz</button>
          ))}
        </div>
      </SettingsSection>

      <SettingsSection title="Signals sent to server">
        <div style={{ padding: 6 }}>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', padding: '2px 4px 6px' }}>OBD · live PIDs</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
            {[
              ['Speed · 0D', true], ['RPM · 0C', true], ['Throttle · 11', true], ['Coolant · 05', true],
              ['Intake · 0F', true], ['Oil temp · 5C', true], ['MAF · 10', true], ['Fuel rate · 5E', false],
              ['Fuel level · 2F', true], ['Battery · ATRV', true],
            ].map(([name, on]) => (
              <div key={name} style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '7px 10px', background: on ? a.soft : t.surface,
                color: on ? a.base : t.fgMuted, borderRadius: 8, fontFamily: PD_FONT, fontSize: 12, fontWeight: 500,
              }}>
                {on && <Icon d={I.check} size={11} stroke={3} />}
                {name}
              </div>
            ))}
          </div>

          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', padding: '12px 4px 6px' }}>Calculated · phone-side</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
            {[
              ['Fuel economy MPG', true], ['Fuel economy km/L', true],
              ['Accel (m/s²)', true], ['Trip distance', true],
              ['Manual trip', true], ['Driving events', true],
            ].map(([name, on]) => (
              <div key={name} style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '7px 10px', background: on ? a.soft : t.surface,
                color: on ? a.base : t.fgMuted, borderRadius: 8, fontFamily: PD_FONT, fontSize: 12, fontWeight: 500,
              }}>
                {on && <Icon d={I.check} size={11} stroke={3} />}
                {name}
              </div>
            ))}
          </div>

          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', padding: '12px 4px 6px' }}>Phone sensors</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
            {[
              ['GPS lat/lng + speed', true], ['Accelerometer XYZ', true],
            ].map(([name, on]) => (
              <div key={name} style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '7px 10px', background: on ? a.soft : t.surface,
                color: on ? a.base : t.fgMuted, borderRadius: 8, fontFamily: PD_FONT, fontSize: 12, fontWeight: 500,
              }}>
                {on && <Icon d={I.check} size={11} stroke={3} />}
                {name}
              </div>
            ))}
          </div>

          <div style={{ padding: '12px 4px 4px', display: 'flex', alignItems: 'center', gap: 8 }}>
            <Icon d={I.info} size={12} style={{ color: t.fgMuted }} />
            <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, lineHeight: 1.4 }}>
              Fuel rate PID (5E) isn't supported on this vehicle — MPG is calculated from MAF.
            </div>
          </div>
        </div>
      </SettingsSection>
    </div>
  );
}

function Field({ label, value, mono, right }) {
  const { t } = usePD();
  return (
    <div style={{ padding: '10px 4px', display: 'flex', alignItems: 'center', gap: 10 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>{label}</div>
        <div style={{
          fontSize: 13, color: t.fg, marginTop: 4,
          fontFamily: mono ? PD_FONT_MONO : PD_FONT,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>{value}</div>
      </div>
      {right}
      {!right && <Icon d={I.pencil} size={14} style={{ color: t.fgDim }} />}
    </div>
  );
}

function ToggleRow({ icon, label, sub, value, onChange }) {
  const { t } = usePD();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 4px' }}>
      {icon && (
        <div style={{ width: 32, height: 32, borderRadius: 10, background: t.surface2, color: t.fgMuted, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <Icon d={icon} size={16} />
        </div>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>{label}</div>
        {sub && <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 2 }}>{sub}</div>}
      </div>
      <PDToggle value={value} onChange={onChange} />
    </div>
  );
}

// ─── Phone home layout customizer ──────────────────────────
function ScreenHomeLayout({ go }) {
  const { t, a } = usePD();
  const [featured, setFeatured] = React.useState('speed');
  const tiles = [
    { id: 't1', metric: 'rpm',       widget: 'Dial' },
    { id: 't2', metric: 'throttle',  widget: 'Bar' },
    { id: 't3', metric: 'coolant',   widget: 'Bar' },
    { id: 't4', metric: 'battery',   widget: 'Number' },
    { id: 't5', metric: 'fuel',      widget: 'Bar' },
    { id: 't6', metric: 'gforce',    widget: 'XY' },
  ];

  return (
    <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
      <SettingsSection title="Featured tile">
        <div style={{ padding: 6, display: 'flex', gap: 8, overflowX: 'auto' }}>
          {PD_METRICS.slice(0, 6).map(m => {
            const sel = featured === m.id;
            return (
              <button key={m.id} onClick={() => setFeatured(m.id)} style={{
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5,
                minWidth: 70, padding: '8px 6px', borderRadius: 12,
                border: `1px solid ${sel ? a.base : t.border}`,
                background: sel ? a.soft : 'transparent',
                color: sel ? a.base : t.fgMuted, cursor: 'pointer',
                fontFamily: PD_FONT, fontSize: 11, fontWeight: 500,
              }}>
                <Icon d={m.icon} size={16} stroke={sel ? 2 : 1.6} />{m.label}
              </button>
            );
          })}
        </div>
      </SettingsSection>

      <SettingsSection title="Tiles (long-press to reorder)">
        <div style={{ padding: 6, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          {tiles.map(tile => {
            const m = PD_METRICS.find(x => x.id === tile.metric);
            return (
              <div key={tile.id} style={{
                background: t.surface, borderRadius: 12, border: `1px solid ${t.borderS}`,
                padding: 10, display: 'flex', flexDirection: 'column', gap: 8, position: 'relative',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Icon d={m.icon} size={13} style={{ color: t.fgMuted }} />
                  <span style={{ flex: 1, fontSize: 11, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>{m.label}</span>
                  <Icon d={I.drag} size={14} style={{ color: t.fgDim }} />
                </div>
                <div style={{ height: 36, background: t.surface2, borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {tile.widget === 'Dial' && <svg width="28" height="28" viewBox="0 0 28 28"><path d="M5 22 A 10 10 0 1 1 23 22" stroke={t.fgDim} strokeWidth="2.2" strokeLinecap="round" fill="none" /><path d="M5 22 A 10 10 0 0 1 16 9" stroke={a.base} strokeWidth="2.2" strokeLinecap="round" fill="none" /></svg>}
                  {tile.widget === 'Bar'  && <svg width="34" height="14" viewBox="0 0 34 14"><rect width="34" height="6" y="4" fill={t.fgDim} opacity="0.3" rx="3" /><rect width="22" height="6" y="4" fill={a.base} rx="3" /></svg>}
                  {tile.widget === 'Number' && <span style={{ fontFamily: PD_FONT_MONO, fontSize: 16, color: t.fg, fontWeight: 500 }}>14.2</span>}
                  {tile.widget === 'XY' && <svg width="28" height="28" viewBox="0 0 28 28"><circle cx="14" cy="14" r="10" fill="none" stroke={t.fgDim} strokeWidth="1" /><circle cx="18" cy="11" r="2.4" fill={a.base} /></svg>}
                </div>
                <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT_MONO, letterSpacing: 0.2 }}>{tile.widget}</div>
              </div>
            );
          })}
          {/* Add tile */}
          <button style={{
            background: 'transparent', border: `1px dashed ${t.border}`,
            borderRadius: 12, color: t.fgMuted, padding: 10,
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6,
            minHeight: 100, cursor: 'pointer', fontFamily: PD_FONT, fontSize: 11, fontWeight: 500,
          }}>
            <Icon d={I.plus} size={18} />Add tile
          </button>
        </div>
      </SettingsSection>

      <PDCard style={{ background: 'transparent', borderStyle: 'dashed' }}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <Icon d={I.info} size={14} style={{ color: t.fgMuted, marginTop: 1, flexShrink: 0 }} />
          <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, lineHeight: 1.5 }}>
            Tiles render with the same data stream as Android Auto. Configure independently below.
          </div>
        </div>
      </PDCard>
    </div>
  );
}

// ─── Android Auto layout editor ────────────────────────────
function ScreenAALayout({ go }) {
  const { t, a } = usePD();
  const [screen, setScreen] = React.useState(1);
  const [selected, setSelected] = React.useState('w1');
  // Split-screen — page 1 (fixed layout, configurable slots) + page 2 (free tiles).
  const [splitPage1, setSplitPage1] = React.useState(['mpg', 'mpgTrip', 'mpgManual', 'distance', 'manualTrip', 'accel']);
  const [splitPage2, setSplitPage2] = React.useState(['rpm', 'coolant', 'throttle', 'fuel', 'battery', 'oilTemp']);
  const [splitSel, setSplitSel] = React.useState({ page: 1, idx: 0 });

  // widgets per screen
  const screens = {
    1: [
      { id: 'w1', type: 'Dial',  metric: 'speed',    pos: 'left'   },
      { id: 'w2', type: 'Dial',  metric: 'rpm',      pos: 'center' },
      { id: 'w3', type: 'Dial',  metric: 'coolant',  pos: 'right'  },
      { id: 'w4', type: 'Text',  metric: 'distance', pos: 'bottom' },
    ],
    2: [
      { id: 'w5', type: 'Graph', metric: 'throttle', pos: 'top'    },
      { id: 'w6', type: 'Graph', metric: 'gforce',   pos: 'bottom' },
      { id: 'w7', type: 'Text',  metric: 'battery',  pos: 'right'  },
    ],
  };

  const widgets = screens[screen] || [];

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Screen tabs — three tabs now: Dials, Graphs, Split */}
      <div style={{ padding: '0 16px 10px', display: 'flex', gap: 6 }}>
        {[
          { n: 1,       label: 'Dials',  sub: 'Full screen' },
          { n: 2,       label: 'Graphs', sub: 'Full screen' },
          { n: 'split', label: 'Split',  sub: '⅓ width' },
        ].map(item => {
          const sel = screen === item.n;
          return (
            <button key={item.n} onClick={() => setScreen(item.n)} style={{
              flex: 1, padding: '8px 4px', borderRadius: 10,
              border: `1px solid ${sel ? a.base : t.border}`,
              background: sel ? a.soft : 'transparent',
              color: sel ? a.base : t.fgMuted, cursor: 'pointer',
              fontFamily: PD_FONT, fontSize: 12, fontWeight: 600,
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2,
            }}>
              <span>{item.label}</span>
              <span style={{ fontSize: 9, fontWeight: 500, opacity: 0.75 }}>{item.sub}</span>
            </button>
          );
        })}
      </div>

      {screen === 'split' ? (
        <SplitLayoutEditor
          page1={splitPage1} setPage1={setSplitPage1}
          page2={splitPage2} setPage2={setSplitPage2}
          sel={splitSel} setSel={setSplitSel} />
      ) : (
        <>
          {/* Preview canvas — a tiny head-unit */}
          <div style={{ padding: '0 16px 12px' }}>
            <div style={{
              aspectRatio: '16 / 9', background: t.bg, borderRadius: 10,
              border: `1px solid ${t.border}`, position: 'relative', overflow: 'hidden',
            }}>
              {screen === 1 ? (
                <div style={{ position: 'absolute', inset: 16, display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10, alignItems: 'center' }}>
                  {['w1', 'w2', 'w3'].map(id => {
                    const isSel = selected === id;
                    return (
                      <button key={id} onClick={() => setSelected(id)} style={{
                        aspectRatio: '1', borderRadius: 9, border: `1.5px solid ${isSel ? a.base : t.borderS}`,
                        background: isSel ? a.soft : 'transparent', cursor: 'pointer', padding: 0,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        <svg width="40" height="40" viewBox="0 0 40 40">
                          <path d="M8 32 A 14 14 0 1 1 32 32" stroke={t.fgDim} strokeWidth="2.5" strokeLinecap="round" fill="none" />
                          <path d="M8 32 A 14 14 0 0 1 22 12" stroke={a.base} strokeWidth="2.5" strokeLinecap="round" fill="none" />
                        </svg>
                      </button>
                    );
                  })}
                  <button onClick={() => setSelected('w4')} style={{
                    gridColumn: '1 / -1', padding: '6px 10px', borderRadius: 8,
                    border: `1.5px solid ${selected === 'w4' ? a.base : t.borderS}`,
                    background: selected === 'w4' ? a.soft : 'transparent',
                    color: t.fg, fontFamily: PD_FONT_MONO, fontSize: 11, cursor: 'pointer',
                  }}>TRIP · 14.3 mi · 00:24</button>
                </div>
              ) : (
                <div style={{ position: 'absolute', inset: 16, display: 'grid', gridTemplateColumns: '2fr 1fr', gridTemplateRows: '1fr 1fr', gap: 10 }}>
                  {['w5', 'w6'].map((id, i) => (
                    <button key={id} onClick={() => setSelected(id)} style={{
                      gridColumn: '1', borderRadius: 8, border: `1.5px solid ${selected === id ? a.base : t.borderS}`,
                      background: selected === id ? a.soft : 'transparent', cursor: 'pointer', padding: 6,
                    }}>
                      <svg width="100%" height="100%" viewBox="0 0 100 30" preserveAspectRatio="none">
                        <path d={i === 0 ? "M0 22 L 15 18 L 30 20 L 45 10 L 60 14 L 75 6 L 100 12" : "M0 15 L 20 18 L 40 8 L 60 22 L 80 12 L 100 18"}
                          stroke={a.base} strokeWidth="1.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    </button>
                  ))}
                  <button onClick={() => setSelected('w7')} style={{
                    gridColumn: '2', gridRow: '1 / -1', borderRadius: 8,
                    border: `1.5px solid ${selected === 'w7' ? a.base : t.borderS}`,
                    background: selected === 'w7' ? a.soft : 'transparent', cursor: 'pointer', padding: 6,
                    display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <span style={{ fontFamily: PD_FONT_MONO, fontSize: 16, color: t.fg, fontWeight: 500 }}>14.2V</span>
                    <span style={{ fontFamily: PD_FONT_MONO, fontSize: 12, color: t.fgMuted, marginTop: 2 }}>62% fuel</span>
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Selected widget panel */}
          <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
            <SettingsSection title={`Selected · ${widgets.find(w => w.id === selected)?.type || '—'}`}>
              <Field label="Widget type"   value={widgets.find(w => w.id === selected)?.type || 'Dial'} />
              <SettingsDivider />
              <Field label="Data source"   value={PD_METRICS.find(m => m.id === widgets.find(w => w.id === selected)?.metric)?.label || 'Speed'} />
              <SettingsDivider />
              <Field label="Label" value="auto" />
            </SettingsSection>

            <div style={{ display: 'flex', gap: 8 }}>
              <PDButton variant="secondary" icon={I.plus} full size="sm">Add widget</PDButton>
              <PDButton variant="ghost" full size="sm">Reset screen</PDButton>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

// ─── Split-screen 1/3 layout editor ────────────────────────
// Both pages of the split panel have configurable metric slots.
// Page 1 layout is fixed (hero + 4 pills + graph). Page 2 is a free 2×3 grid.
function SplitLayoutEditor({ page1, setPage1, page2, setPage2, sel, setSel }) {
  const { t, a } = usePD();
  const editingPage = sel.page;
  const currentTiles = editingPage === 1 ? page1 : page2;
  const setCurrent = editingPage === 1 ? setPage1 : setPage2;
  const currentMetricId = currentTiles[sel.idx];
  const slotLabels1 = ['Hero', 'Top L', 'Top R', 'Bottom L', 'Bottom R', 'Graph'];

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Page switcher within the split layout */}
      <div style={{ padding: '0 16px 10px', display: 'flex', gap: 6 }}>
        {[
          { n: 1, label: 'Page 1', sub: 'Hero + pills + graph' },
          { n: 2, label: 'Page 2', sub: '2 × 3 tile grid'      },
        ].map(item => {
          const isOn = editingPage === item.n;
          return (
            <button key={item.n} onClick={() => setSel({ page: item.n, idx: 0 })} style={{
              flex: 1, padding: '7px 4px', borderRadius: 8,
              border: `1px solid ${isOn ? a.base : t.borderS}`,
              background: isOn ? a.soft : 'transparent',
              color: isOn ? a.base : t.fgMuted, cursor: 'pointer',
              fontFamily: PD_FONT, fontSize: 11, fontWeight: 600,
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1,
            }}>
              <span>{item.label}</span>
              <span style={{ fontSize: 9, opacity: 0.75 }}>{item.sub}</span>
            </button>
          );
        })}
      </div>

      <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
        {/* Mini 1/3-panel preview — clickable slots */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 14 }}>
          <div style={{
            width: 132, height: 220, borderRadius: 10,
            border: `1px solid ${t.border}`, background: t.bg,
            display: 'flex', flexDirection: 'column', padding: 6, gap: 4, position: 'relative',
          }}>
            <div style={{ position: 'absolute', top: -10, left: '50%', transform: 'translateX(-50%)', background: t.bg, padding: '0 6px', fontSize: 8, color: t.fgDim, fontFamily: PD_FONT_MONO, letterSpacing: 0.5, textTransform: 'uppercase' }}>1/3 panel · page {editingPage}</div>
            {/* mini header (non-interactive) */}
            <div style={{ height: 12, borderRadius: 3, background: t.surface2, flexShrink: 0 }} />

            {editingPage === 1 ? (
              <>
                <SlotPreview meta={PD_METRICS.find(m => m.id === page1[0])} slotName="Hero"
                  isOn={sel.idx === 0} onClick={() => setSel({ page: 1, idx: 0 })} h={36} accentBig />
                <div style={{ display: 'flex', gap: 3 }}>
                  <SlotPreview meta={PD_METRICS.find(m => m.id === page1[1])} slotName="Top L"
                    isOn={sel.idx === 1} onClick={() => setSel({ page: 1, idx: 1 })} h={20} grow />
                  <SlotPreview meta={PD_METRICS.find(m => m.id === page1[2])} slotName="Top R"
                    isOn={sel.idx === 2} onClick={() => setSel({ page: 1, idx: 2 })} h={20} grow />
                </div>
                <div style={{ display: 'flex', gap: 3 }}>
                  <SlotPreview meta={PD_METRICS.find(m => m.id === page1[3])} slotName="Bot L"
                    isOn={sel.idx === 3} onClick={() => setSel({ page: 1, idx: 3 })} h={20} grow />
                  <SlotPreview meta={PD_METRICS.find(m => m.id === page1[4])} slotName="Bot R"
                    isOn={sel.idx === 4} onClick={() => setSel({ page: 1, idx: 4 })} h={20} grow />
                </div>
                <SlotPreview meta={PD_METRICS.find(m => m.id === page1[5])} slotName="Graph"
                  isOn={sel.idx === 5} onClick={() => setSel({ page: 1, idx: 5 })} grow graph />
              </>
            ) : (
              <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 3, minHeight: 0 }}>
                {page2.map((id, i) => (
                  <SlotPreview key={i} meta={PD_METRICS.find(m => m.id === id)}
                    isOn={sel.idx === i} onClick={() => setSel({ page: 2, idx: i })} grow flexHeight />
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Selected slot info */}
        <SettingsSection title={
          editingPage === 1
            ? `${slotLabels1[sel.idx]} · ${PD_METRICS.find(m => m.id === currentMetricId)?.label || '—'}`
            : `Tile ${sel.idx + 1} of 6 · ${PD_METRICS.find(m => m.id === currentMetricId)?.label || '—'}`
        }>
          <div style={{ padding: 6, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 6 }}>
            {PD_METRICS.map(m => {
              const isOn = currentMetricId === m.id;
              const usedElsewhere = currentTiles.some((tid, i) => tid === m.id && i !== sel.idx);
              return (
                <button key={m.id} onClick={() => {
                  const next = currentTiles.slice();
                  next[sel.idx] = m.id;
                  setCurrent(next);
                }} disabled={usedElsewhere && !isOn} style={{
                  display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 3,
                  padding: '7px 8px', borderRadius: 8,
                  border: `1px solid ${isOn ? a.base : t.border}`,
                  background: isOn ? a.soft : 'transparent',
                  color: isOn ? a.base : usedElsewhere ? t.fgDim : t.fg,
                  cursor: usedElsewhere && !isOn ? 'not-allowed' : 'pointer',
                  opacity: usedElsewhere && !isOn ? 0.4 : 1,
                  fontFamily: PD_FONT, textAlign: 'left',
                }}>
                  <Icon d={m.icon} size={12} stroke={isOn ? 2 : 1.6} />
                  <span style={{ fontSize: 10, fontWeight: 500, lineHeight: 1.2 }}>{m.label}</span>
                </button>
              );
            })}
          </div>
        </SettingsSection>

        <PDCard style={{ background: 'transparent', borderStyle: 'dashed' }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <Icon d={I.info} size={14} style={{ color: t.fgMuted, marginTop: 1, flexShrink: 0 }} />
            <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, lineHeight: 1.5 }}>
              {editingPage === 1
                ? 'Page 1 layout is fixed — hero on top, two pill rows, graph at the bottom. Each slot\'s metric is independently configurable.'
                : 'Page 2 is a free 2 × 3 tile grid — any metric in any cell.'}
            </div>
          </div>
        </PDCard>
      </div>
    </div>
  );
}

// Mini-preview slot — accepts a metric and renders a small chip showing its
// icon + label, with selected highlight. Used in both Page 1 (fixed shapes)
// and Page 2 (grid) previews.
function SlotPreview({ meta, slotName, isOn, onClick, h, grow, accentBig, graph, flexHeight }) {
  const { t, a } = usePD();
  return (
    <button onClick={onClick} style={{
      ...(grow ? { flex: 1, minHeight: flexHeight ? 0 : undefined } : {}),
      ...(h ? { height: h, flexShrink: 0 } : {}),
      borderRadius: 4, padding: '2px 4px',
      border: `1.4px solid ${isOn ? a.base : t.borderS}`,
      background: isOn ? a.soft : t.surface, cursor: 'pointer',
      display: 'flex', flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center',
      gap: 1, minWidth: 0, overflow: 'hidden',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 3, color: isOn ? a.base : t.fgMuted, width: '100%' }}>
        {meta && <Icon d={meta.icon} size={8} stroke={2} />}
        <span style={{
          fontSize: 7, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.2,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', flex: 1, textAlign: 'left',
        }}>{meta ? meta.label.replace(' · ', ' ').slice(0, 14) : slotName}</span>
      </div>
      {graph && (
        <svg width="100%" height="60%" viewBox="0 0 100 30" preserveAspectRatio="none" style={{ marginTop: 2 }}>
          <path d="M 0 22 L 12 18 L 24 22 L 36 14 L 48 18 L 60 10 L 72 14 L 84 8 L 100 12"
            stroke={isOn ? a.base : t.fgMuted} strokeWidth="1.2" fill="none" />
        </svg>
      )}
      {accentBig && (
        <span style={{ fontFamily: PD_FONT_MONO, fontSize: 12, color: isOn ? a.base : t.fg, fontWeight: 500, letterSpacing: -0.3, lineHeight: 1 }}>00</span>
      )}
    </button>
  );
}

// ─── Thresholds ────────────────────────────────────────────
function ScreenThresholds({ go }) {
  const { t, a } = usePD();
  // Strategy 1 — Acceleration (mph/s)
  const [accelOn, setAccelOn] = React.useState(true);
  const [accelHardAccel, setAccelHardAccel] = React.useState(9);
  const [accelHardBrake, setAccelHardBrake] = React.useState(9);
  // Strategy 2 — G-Force
  const [gOn, setGOn] = React.useState(false);
  const [gHardAccel, setGHardAccel] = React.useState(22);    // 0.22 g
  const [gHardBrake, setGHardBrake] = React.useState(265);   // 0.265 g (stored ×1000)
  const [gSevere, setGSevere] = React.useState(50);          // 0.50 g
  // Shared
  const [minDuration, setMinDuration] = React.useState(5);   // 0.5 s ×10
  const [speedAlert, setSpeedAlert] = React.useState(75);
  const [highRpm, setHighRpm] = React.useState(6500);
  const [enableSpeed, setEnableSpeed] = React.useState(true);
  const [enableSound, setEnableSound] = React.useState(false);
  const [enableHaptic, setEnableHaptic] = React.useState(true);
  const [enableCarToast, setEnableCarToast] = React.useState(true);

  return (
    <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
      <PDCard style={{ marginBottom: 14, background: 'transparent', borderStyle: 'dashed' }}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <Icon d={I.info} size={14} style={{ color: t.fgMuted, marginTop: 1, flexShrink: 0 }} />
          <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, lineHeight: 1.5 }}>
            Two independent strategies — enable either or both. Acceleration uses OBD + GPS speed deltas; G-Force fuses in the phone accelerometer.
          </div>
        </div>
      </PDCard>

      {/* Strategy 1 — Acceleration (mph/s) */}
      <StrategyCard
        title="Acceleration"
        unit="mph/s"
        subtitle="Speed delta over time"
        sources={['OBD speed (PID 0D)', 'GPS speed']}
        on={accelOn} onChange={setAccelOn}>
        <SliderRow icon={I.speed} color={a.base} bg={a.soft} title="Hard acceleration"
          value={accelHardAccel} min={3} max={20} step={1} unit=" mph/s"
          format={v => `${v}`} onChange={setAccelHardAccel} />
        <SettingsDivider />
        <SliderRow icon={I.gforce} color={t.danger} bg="oklch(0.66 0.20 25 / 0.16)" title="Hard braking"
          flag="4× this week"
          value={accelHardBrake} min={3} max={20} step={1} unit=" mph/s"
          format={v => `${v}`} onChange={setAccelHardBrake} />
      </StrategyCard>

      {/* Strategy 2 — G-Force */}
      <StrategyCard
        title="G-Force"
        unit="g"
        subtitle="Sensor fusion · OBD + GPS + accelerometer"
        sources={['OBD speed', 'GPS speed', 'TYPE_LINEAR_ACCELERATION']}
        on={gOn} onChange={setGOn}
        warning={!gOn ? null : { label: 'Calibration needed', action: 'Calibrate now' }}>
        <SliderRow icon={I.speed} color={a.base} bg={a.soft} title="Hard acceleration"
          value={gHardAccel} min={10} max={80} step={1} unit=" g"
          format={v => (v/100).toFixed(2)} onChange={setGHardAccel} />
        <SettingsDivider />
        <SliderRow icon={I.gforce} color={t.danger} bg="oklch(0.66 0.20 25 / 0.16)" title="Hard braking"
          value={gHardBrake} min={10} max={80} step={5} unit=" g"
          format={v => (v/1000).toFixed(3)} onChange={setGHardBrake} />
        <SettingsDivider />
        <SliderRow icon={I.shield} color="oklch(0.66 0.20 25 / 0.9)" bg="oklch(0.66 0.20 25 / 0.16)" title="Severe braking"
          subtitle="Predictive of crash risk · second-tier alert"
          value={gSevere} min={30} max={100} step={5} unit=" g"
          format={v => (v/100).toFixed(2)} onChange={setGSevere} />
      </StrategyCard>

      <SettingsSection title="Shared parameters">
        <div style={{ padding: '8px 4px 4px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, background: t.surface2, color: t.fgMuted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon d={I.refresh} size={14} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>Minimum event duration</div>
              <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 1 }}>Filters out transient bumps</div>
            </div>
          </div>
          <PDSlider value={minDuration} min={2} max={20} step={1} unit=" s" onChange={setMinDuration}
            format={v => (v / 10).toFixed(1)} />
        </div>
      </SettingsSection>

      <SettingsSection title="Speed & RPM">
        <ToggleRow icon={I.speed} label="Speed limit alert" sub={`Trigger above ${speedAlert} mph`} value={enableSpeed} onChange={setEnableSpeed} />
        {enableSpeed && (
          <>
            <SettingsDivider />
            <div style={{ padding: '12px 4px 6px' }}>
              <PDSlider value={speedAlert} min={25} max={130} step={5} unit=" mph" onChange={setSpeedAlert} />
            </div>
          </>
        )}
        <SettingsDivider />
        <div style={{ padding: '12px 4px 6px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, background: t.surface2, color: t.fgMuted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon d={I.refresh} size={14} />
            </div>
            <span style={{ fontSize: 13, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>High RPM</span>
          </div>
          <PDSlider value={highRpm} min={3000} max={9000} step={250} unit=" rpm" onChange={setHighRpm} />
        </div>
      </SettingsSection>

      <SettingsSection title="When triggered">
        <ToggleRow icon={I.bell}    label="Sound alert"          sub="Plays a short beep"                            value={enableSound}    onChange={setEnableSound} />
        <SettingsDivider />
        <ToggleRow icon={I.dot}     label="Haptic feedback"      sub="Short vibration"                               value={enableHaptic}   onChange={setEnableHaptic} />
        <SettingsDivider />
        <ToggleRow icon={I.car}     label="CarToast on Android Auto" sub="Banner alert on the head unit"            value={enableCarToast} onChange={setEnableCarToast} />
        <SettingsDivider />
        <ToggleRow icon={I.cloudUp} label="Flag event in stream" sub="Tag telemetry with event marker"               value={true} onChange={() => {}} />
      </SettingsSection>
    </div>
  );
}

// Strategy card — wraps the two detection algorithms with their on/off toggle.
function StrategyCard({ title, unit, subtitle, sources, on, onChange, warning, children }) {
  const { t, a } = usePD();
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{
        background: t.bgElev, borderRadius: 14, border: `1px solid ${on ? a.base : t.borderS}`,
        boxShadow: on ? `0 0 0 3px ${a.soft}` : 'none',
        overflow: 'hidden', transition: 'border-color 0.2s, box-shadow 0.2s',
      }}>
        {/* header */}
        <div style={{ padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 15, color: t.fg, fontFamily: PD_FONT, fontWeight: 600 }}>{title}</span>
              <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT_MONO, padding: '2px 6px', borderRadius: 4, background: t.surface2 }}>{unit}</span>
            </div>
            <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 3 }}>{subtitle}</div>
          </div>
          <PDToggle value={on} onChange={onChange} />
        </div>
        {/* sources strip */}
        <div style={{ padding: '0 14px 10px', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {sources.map(s => (
            <span key={s} style={{
              padding: '3px 8px', borderRadius: 99, background: t.surface, color: t.fgMuted,
              fontFamily: PD_FONT_MONO, fontSize: 10, fontWeight: 500,
            }}>{s}</span>
          ))}
        </div>
        {warning && (
          <div style={{
            margin: '0 14px 12px', padding: '8px 10px',
            background: 'oklch(0.80 0.15 80 / 0.10)', borderRadius: 8,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <Icon d={I.info} size={12} style={{ color: t.warn }} />
            <span style={{ flex: 1, fontSize: 11, color: t.fg, fontFamily: PD_FONT }}>{warning.label}</span>
            <button style={{
              background: 'transparent', border: 'none', color: t.warn,
              fontFamily: PD_FONT, fontSize: 11, fontWeight: 600, cursor: 'pointer',
            }}>{warning.action}</button>
          </div>
        )}
        {/* sliders body */}
        {on && (
          <div style={{ borderTop: `1px solid ${t.borderS}`, padding: '6px 14px 14px' }}>
            {children}
          </div>
        )}
      </div>
    </div>
  );
}

// Slider with icon, title, optional flag chip, and optional subtitle.
function SliderRow({ icon, color, bg, title, subtitle, flag, value, min, max, step, unit, format, onChange }) {
  const { t } = usePD();
  return (
    <div style={{ padding: '12px 0 4px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0 }}>
          <div style={{
            width: 28, height: 28, borderRadius: 8, background: bg, color: color,
            display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
          }}><Icon d={icon} size={14} /></div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>{title}</div>
            {subtitle && <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 2, lineHeight: 1.4 }}>{subtitle}</div>}
          </div>
        </div>
        {flag && <PDPill color="danger">{flag}</PDPill>}
      </div>
      <PDSlider value={value} min={min} max={max} step={step} unit={unit}
        format={format} onChange={onChange} />
    </div>
  );
}

Object.assign(window, {
  PD_METRICS,
  ScreenSettings, ScreenServer, ScreenHomeLayout, ScreenAALayout, ScreenThresholds,
});
