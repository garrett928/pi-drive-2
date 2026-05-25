// pi-drive — phone screens: Home, Trips, Connect flow

// ─── HOME — live dashboard ──────────────────────────────────
// Featured big readout + grid of customizable tiles + a recording status bar.
function ScreenHome({ go }) {
  const { t, a } = usePD();
  // Live-ish data — wobbles a bit so the screen feels alive when running.
  const [tick, setTick] = React.useState(0);
  React.useEffect(() => {
    const i = setInterval(() => setTick(x => x + 1), 1200);
    return () => clearInterval(i);
  }, []);
  const wob = (base, amp) => Math.round(base + Math.sin(tick * 0.7) * amp);

  const speed = wob(58, 4);
  const rpm = wob(2750, 180);

  return (
    <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '4px 16px 12px' }}>
      {/* Connection / trip status banner */}
      <button onClick={() => go('connect')} style={{
        background: 'transparent', border: 'none', padding: 0, width: '100%', cursor: 'pointer',
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px',
          background: t.bgElev, borderRadius: 14, border: `1px solid ${t.borderS}`,
          marginBottom: 14,
        }}>
          <div style={{
            width: 28, height: 28, borderRadius: 14, background: a.soft,
            color: a.base, display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}><Icon d={I.bluetooth} size={14} stroke={2} /></div>
          <div style={{ flex: 1, textAlign: 'left' }}>
            <div style={{ fontSize: 13, color: t.fg, fontWeight: 500, fontFamily: PD_FONT }}>OBDLink LX · 9F4C</div>
            <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, display: 'flex', alignItems: 'center', gap: 6, marginTop: 1 }}>
              <span style={{ width: 6, height: 6, borderRadius: 3, background: t.success, boxShadow: `0 0 0 3px oklch(0.74 0.16 150 / 0.22)` }} />
              Streaming · CAN 500k · 30 Hz
            </div>
          </div>
          <Icon d={I.chevR} size={14} style={{ color: t.fgDim }} />
        </div>
      </button>

      {/* Featured metric */}
      <div style={{
        background: t.bgElev, borderRadius: 18, border: `1px solid ${t.borderS}`,
        padding: '18px 18px 14px', position: 'relative', overflow: 'hidden', marginBottom: 12,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>Speed</div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 4 }}>
              <span style={{ fontSize: 76, fontFamily: PD_FONT_MONO, fontWeight: 500, color: t.fg, letterSpacing: -2.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{speed}</span>
              <span style={{ fontSize: 16, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>mph</span>
            </div>
          </div>
          <PDPill color="accent" dot>LIVE</PDPill>
        </div>
        {/* mini speed history */}
        <div style={{ marginTop: 6, marginLeft: -4, marginRight: -4 }}>
          <PDLine data={[42,46,50,55,52,58,62,60,57,55,58,60,57,54,58]} width={300} height={36} />
        </div>
      </div>

      {/* MPG row — instant + trip + manual */}
      <div style={{
        background: t.bgElev, borderRadius: 14, border: `1px solid ${t.borderS}`,
        padding: '12px 14px', marginBottom: 10, display: 'grid',
        gridTemplateColumns: '1.3fr 1fr 1fr', gap: 14, alignItems: 'center',
      }}>
        <div>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', marginBottom: 2, display: 'flex', alignItems: 'center', gap: 4 }}>
            MPG · instant
            <span style={{ fontSize: 8, color: t.fgDim, fontFamily: PD_FONT_MONO, letterSpacing: 0, textTransform: 'none' }}>calc</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, color: a.base }}>
            <span style={{ fontSize: 36, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -1, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{(28 + Math.sin(tick * 0.5) * 6).toFixed(1)}</span>
            <span style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 500 }}>mpg</span>
          </div>
        </div>
        <div style={{ borderLeft: `1px solid ${t.borderS}`, paddingLeft: 12 }}>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>Trip avg</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 3, marginTop: 4 }}>
            <span style={{ fontSize: 22, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>26.4</span>
            <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT }}>mpg</span>
          </div>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 3 }}>14.3 mi</div>
        </div>
        <div style={{ borderLeft: `1px solid ${t.borderS}`, paddingLeft: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>Manual</div>
            <button onClick={(e) => e.stopPropagation()} title="Reset manual trip" style={{
              padding: '1px 6px', borderRadius: 99, border: `1px solid ${t.border}`,
              background: 'transparent', color: t.fgMuted, cursor: 'pointer',
              fontFamily: PD_FONT, fontSize: 9, fontWeight: 600, letterSpacing: 0.3, textTransform: 'uppercase',
              display: 'inline-flex', alignItems: 'center', gap: 3,
            }}><Icon d={I.refresh} size={8} stroke={2.4} />Reset</button>
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 3, marginTop: 4 }}>
            <span style={{ fontSize: 22, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>248.6</span>
            <span style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT }}>mi</span>
          </div>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 3 }}>since May 18</div>
        </div>
      </div>

      {/* Tile grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 12 }}>
        <PDCard padded={false} style={{ padding: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 4 }}>
            <PDDial value={Math.round(rpm/100)/10} min={0} max={8} label="rpm" unit="× 1000" size={102} thickness={7} warnAt={6.5} />
          </div>
        </PDCard>
        <PDCard padded={false} style={{ padding: 12, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <PDReadout label="THROTTLE" value={`${wob(34, 6)}`} unit="%" size="md" />
          <PDBar value={wob(34, 6)} label="" unit="%" />
          <div style={{ height: 4 }} />
          <PDBar value={wob(12, 4)} label="" unit="%" />
        </PDCard>

        <PDCard padded={false} style={{ padding: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Icon d={I.thermo} size={14} style={{ color: t.fgMuted }} />
            <span style={{ fontSize: 10, color: t.fgMuted, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', fontFamily: PD_FONT }}>Coolant</span>
          </div>
          <PDReadout value={wob(192, 2)} unit="°F" size="md" />
          <div style={{ marginTop: 8 }}>
            <PDBar value={wob(192, 2)} min={120} max={240} label="" unit="°F" marks={[0.7]} />
          </div>
        </PDCard>

        <PDCard padded={false} style={{ padding: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Icon d={I.battery} size={14} style={{ color: t.fgMuted }} />
            <span style={{ fontSize: 10, color: t.fgMuted, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', fontFamily: PD_FONT }}>Battery</span>
          </div>
          <PDReadout value="14.2" unit="V" size="md" />
          <div style={{ marginTop: 8 }}>
            <PDBar value={142} min={110} max={150} label="" unit="" />
          </div>
        </PDCard>

        <PDCard padded={false} style={{ padding: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Icon d={I.fuel} size={14} style={{ color: t.fgMuted }} />
            <span style={{ fontSize: 10, color: t.fgMuted, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', fontFamily: PD_FONT }}>Fuel</span>
          </div>
          <PDReadout value="62" unit="%" size="md" />
          <div style={{ marginTop: 8 }}>
            <PDBar value={62} label="" unit="%" />
          </div>
        </PDCard>

        <PDCard padded={false} style={{ padding: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Icon d={I.gforce} size={14} style={{ color: t.fgMuted }} />
            <span style={{ fontSize: 10, color: t.fgMuted, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', fontFamily: PD_FONT }}>G-force</span>
          </div>
          {/* simple g-force crosshair */}
          <div style={{ position: 'relative', height: 64, marginTop: 4 }}>
            <svg viewBox="0 0 80 64" width="100%" height="100%">
              <circle cx="40" cy="32" r="28" fill="none" stroke={t.surface2} strokeWidth="1" />
              <circle cx="40" cy="32" r="18" fill="none" stroke={t.surface2} strokeWidth="1" />
              <circle cx="40" cy="32" r="9"  fill="none" stroke={t.surface2} strokeWidth="1" />
              <line x1="12" y1="32" x2="68" y2="32" stroke={t.surface2} strokeWidth="0.6" />
              <line x1="40" y1="4"  x2="40" y2="60" stroke={t.surface2} strokeWidth="0.6" />
              <circle cx={40 + Math.sin(tick*0.7)*8} cy={32 - Math.cos(tick*0.5)*4} r="4" fill={a.base} />
            </svg>
          </div>
        </PDCard>
      </div>

      {/* Recording / sync */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px',
        background: t.bgElev, borderRadius: 14, border: `1px solid ${t.borderS}`,
      }}>
        <div style={{
          width: 28, height: 28, borderRadius: 14, background: 'oklch(0.74 0.16 150 / 0.18)',
          color: t.success, display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}><Icon d={I.cloudUp} size={14} stroke={2} /></div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, color: t.fg, fontWeight: 500, fontFamily: PD_FONT }}>Streaming to fleet.acme.io</div>
          <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 1 }}>14 signals · 30 Hz · 0 queued</div>
        </div>
        <span style={{ fontFamily: PD_FONT_MONO, fontSize: 12, color: t.fgMuted, fontVariantNumeric: 'tabular-nums' }}>{`02:${(14 + tick) % 60}`.replace(/(\d+)$/, (m) => m.padStart(2, '0'))}</span>
      </div>
    </div>
  );
}

// ─── CONNECT FLOW ──────────────────────────────────────────
// 3 steps: scan → pairing → done. Internal state machine.
function ScreenConnect({ go, initialStep = 'scan' }) {
  const { t, a } = usePD();
  const [step, setStep] = React.useState(initialStep);
  const [selected, setSelected] = React.useState(null);

  if (step === 'scan') {
    const devices = [
      { id: 'a', name: 'OBDLink LX 9F4C', rssi: -42, badge: 'paired' },
      { id: 'b', name: 'OBDLink MX+ 2811', rssi: -58 },
      { id: 'c', name: 'Galaxy Buds Pro',  rssi: -68, notObd: true },
      { id: 'd', name: 'OBDII A12B',       rssi: -82 },
    ];
    return (
      <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '4px 16px 16px' }}>
        {/* Bluetooth pulse */}
        <div style={{ display: 'flex', justifyContent: 'center', padding: '20px 0 10px' }}>
          <div style={{ position: 'relative', width: 130, height: 130, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ position: 'absolute', inset: 0, borderRadius: 65, border: `1px solid ${a.base}`, opacity: 0.15 }} />
            <div style={{ position: 'absolute', inset: 18, borderRadius: 47, border: `1px solid ${a.base}`, opacity: 0.25 }} />
            <div style={{ position: 'absolute', inset: 36, borderRadius: 29, border: `1px solid ${a.base}`, opacity: 0.45 }} />
            <div style={{
              width: 54, height: 54, borderRadius: 27, background: a.soft, color: a.base,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}><Icon d={I.bluetooth} size={26} stroke={2} /></div>
          </div>
        </div>
        <div style={{ textAlign: 'center', marginBottom: 18 }}>
          <div style={{ fontSize: 20, fontWeight: 600, color: t.fg, fontFamily: PD_FONT, letterSpacing: -0.3 }}>Pair your OBDLink LX</div>
          <div style={{ fontSize: 13, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 6, lineHeight: 1.4 }}>
            Plug the adapter into your OBD-II port, then press its <strong style={{ color: t.fg, fontWeight: 600 }}>Pair</strong> button until the blue LED blinks.
          </div>
        </div>

        <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase', padding: '0 4px 8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>Bonded devices · 4</span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, textTransform: 'none', letterSpacing: 0, fontWeight: 500, fontSize: 11, color: a.base }}>
            <span style={{ width: 6, height: 6, borderRadius: 3, background: a.base, animation: 'pdpulse 1.2s infinite' }} />Scanning
          </span>
        </div>

        <PDCard padded={false} style={{ overflow: 'hidden' }}>
          {devices.map((d, i) => (
            <button key={d.id} onClick={() => { setSelected(d); setStep('pair'); }} disabled={d.notObd} style={{
              display: 'flex', alignItems: 'center', gap: 12, width: '100%',
              padding: '12px 14px', background: 'transparent', border: 'none',
              borderTop: i > 0 ? `1px solid ${t.borderS}` : 'none',
              cursor: d.notObd ? 'default' : 'pointer', textAlign: 'left',
              opacity: d.notObd ? 0.4 : 1,
            }}>
              <div style={{ width: 32, height: 32, borderRadius: 16, background: t.surface2, color: t.fgMuted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon d={I.bluetooth} size={14} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, color: t.fg, fontWeight: 500, fontFamily: PD_FONT, display: 'flex', alignItems: 'center', gap: 8 }}>
                  {d.name}
                  {d.badge && <PDPill color="accent">Paired</PDPill>}
                  {d.notObd && <PDPill>not OBD</PDPill>}
                </div>
                <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 2 }}>{d.rssi} dBm · SPP</div>
              </div>
              {/* signal bars based on rssi */}
              <div style={{ display: 'flex', gap: 2, alignItems: 'flex-end' }}>
                {[1,2,3,4].map(n => (
                  <div key={n} style={{
                    width: 3, height: 4 + n*3, borderRadius: 1,
                    background: (-d.rssi < 50 ? 4 : -d.rssi < 65 ? 3 : -d.rssi < 75 ? 2 : 1) >= n ? a.base : t.surface2,
                  }} />
                ))}
              </div>
            </button>
          ))}
        </PDCard>

        <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
          <PDButton variant="secondary" onClick={() => go('home')} full>Cancel</PDButton>
          <PDButton variant="ghost" icon={I.plus} full>Pair via system</PDButton>
        </div>

        <style>{`@keyframes pdpulse {0%,100%{opacity:1}50%{opacity:.3}}`}</style>
      </div>
    );
  }

  if (step === 'pair') {
    return (
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '4px 16px 16px' }}>
        <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0 16px' }}>
          <div style={{
            width: 84, height: 84, borderRadius: 42, background: a.soft, color: a.base,
            display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative',
          }}>
            <Icon d={I.bluetooth} size={36} stroke={2} />
            <svg width="84" height="84" style={{ position: 'absolute', inset: 0, animation: 'pdspin 1.6s linear infinite' }}>
              <circle cx="42" cy="42" r="40" fill="none" stroke={a.base} strokeWidth="2"
                strokeDasharray="40 220" strokeLinecap="round" />
            </svg>
          </div>
        </div>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: 20, fontWeight: 600, color: t.fg, fontFamily: PD_FONT, letterSpacing: -0.3 }}>Initializing adapter…</div>
          <div style={{ fontSize: 13, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 6 }}>{selected?.name || 'OBDLink LX 9F4C'}</div>
        </div>

        <PDCard>
          {[
            { label: 'RFCOMM socket open',     done: true,  sub: 'SPP UUID 0000-1101 · 9600 baud' },
            { label: 'ATZ · reset adapter',    done: true,  sub: 'ELM327 v1.4b · STN1155' },
            { label: 'ATE0 ATL0 ATS0 ATH0',    done: true,  sub: 'Echo / line feeds / spaces / headers off' },
            { label: 'ATSP 0 · auto-detect',   done: true,  sub: 'ISO 15765-4 (CAN, 500 kbps)' },
            { label: 'Supported PID bitmask',  done: false, sub: '01 00 · 01 20 · 01 40 — 42 / 64 found' },
            { label: 'Mode 09 · read VIN',     done: null },
          ].map((s, i, arr) => (
            <div key={i} style={{ display: 'flex', gap: 12, padding: '8px 0', borderBottom: i < arr.length - 1 ? `1px solid ${t.borderS}` : 'none' }}>
              <div style={{
                width: 20, height: 20, borderRadius: 10, flexShrink: 0,
                background: s.done === true ? 'oklch(0.74 0.16 150 / 0.2)' : s.done === false ? a.soft : t.surface2,
                color: s.done === true ? t.success : s.done === false ? a.base : t.fgDim,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                {s.done === true && <Icon d={I.check} size={12} stroke={2.5} />}
                {s.done === false && (
                  <svg width="12" height="12" style={{ animation: 'pdspin 0.9s linear infinite' }} viewBox="0 0 12 12">
                    <circle cx="6" cy="6" r="4.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeDasharray="8 12" strokeLinecap="round" />
                  </svg>
                )}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13, color: s.done === null ? t.fgDim : t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>{s.label}</div>
                {s.sub && <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 2 }}>{s.sub}</div>}
              </div>
            </div>
          ))}
        </PDCard>

        <div style={{ flex: 1 }} />
        <PDButton variant="primary" full onClick={() => setStep('done')}>Skip to done (demo)</PDButton>
        <style>{`@keyframes pdspin {to { transform: rotate(360deg); }}`}</style>
      </div>
    );
  }

  // done
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '4px 16px 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0 16px' }}>
        <div style={{
          width: 84, height: 84, borderRadius: 42, background: 'oklch(0.74 0.16 150 / 0.18)',
          color: t.success, display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}><Icon d={I.check} size={42} stroke={2.5} /></div>
      </div>
      <div style={{ textAlign: 'center', marginBottom: 22 }}>
        <div style={{ fontSize: 22, fontWeight: 600, color: t.fg, fontFamily: PD_FONT, letterSpacing: -0.3 }}>Connected</div>
        <div style={{ fontSize: 13, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 6 }}>You're ready to record your first trip.</div>
      </div>

      <PDCard>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 44, height: 44, borderRadius: 12, background: t.surface2, color: t.fgMuted, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon d={I.car} size={22} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>Detected vehicle</div>
            <div style={{ fontSize: 15, color: t.fg, fontFamily: PD_FONT, fontWeight: 500, marginTop: 2 }}>2019 Subaru WRX</div>
            <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 2 }}>VIN · JF1VA1A6•K9•••892</div>
          </div>
          <PDPill color="success" dot>OK</PDPill>
        </div>
        <div style={{ height: 1, background: t.borderS, margin: '12px 0' }} />
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>Supported signals</div>
            <div style={{ fontSize: 18, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, marginTop: 2 }}>64</div>
          </div>
          <div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase' }}>Protocol</div>
            <div style={{ fontSize: 14, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, marginTop: 5 }}>CAN 500k</div>
          </div>
        </div>
      </PDCard>

      <div style={{ flex: 1 }} />
      <PDButton variant="primary" full onClick={() => go('home')}>Continue to live view</PDButton>
      <div style={{ height: 8 }} />
      <PDButton variant="ghost" full onClick={() => go('settings/server')}>Configure telemetry server</PDButton>
    </div>
  );
}

// ─── TRIPS — history list ──────────────────────────────────
function ScreenTrips({ go }) {
  const { t, a } = usePD();
  const trips = [
    { day: 'Today',     items: [
      { title: 'Home → Office',  time: '08:42',  dur: '24 min', dist: '12.4 mi', max: 64, status: 'live' },
    ]},
    { day: 'Yesterday', items: [
      { title: 'Office → Home',  time: '18:11',  dur: '38 min', dist: '14.1 mi', max: 68, status: 'synced' },
      { title: 'Costco run',     time: '12:30',  dur: '52 min', dist: '18.6 mi', max: 71, status: 'synced', flags: 1 },
      { title: 'Home → Office',  time: '08:32',  dur: '22 min', dist: '12.4 mi', max: 62, status: 'synced' },
    ]},
    { day: 'Sat · May 23', items: [
      { title: 'Road trip — Sea Ranch', time: '07:04', dur: '3 h 12', dist: '142.8 mi', max: 78, status: 'pending', flags: 3 },
    ]},
    { day: 'Fri · May 22', items: [
      { title: 'Office → Home',  time: '18:46',  dur: '41 min', dist: '14.2 mi', max: 66, status: 'synced' },
    ]},
  ];

  return (
    <div className="pd-scroll" style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
      {/* Summary card */}
      <PDCard style={{ marginBottom: 14, background: t.bgElev }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase' }}>This week</div>
          <PDPill color="accent">7 trips</PDPill>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
          <div>
            <div style={{ fontSize: 20, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums' }}>218<span style={{ fontSize: 10, color: t.fgMuted, marginLeft: 3 }}>mi</span></div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 1 }}>Distance</div>
          </div>
          <div>
            <div style={{ fontSize: 20, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums' }}>6.4<span style={{ fontSize: 10, color: t.fgMuted, marginLeft: 3 }}>h</span></div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 1 }}>Drive time</div>
          </div>
          <div>
            <div style={{ fontSize: 20, color: a.base, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums' }}>27.8<span style={{ fontSize: 10, color: t.fgMuted, marginLeft: 3 }}>mpg</span></div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 1 }}>Avg MPG</div>
          </div>
          <div>
            <div style={{ fontSize: 20, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums' }}>4</div>
            <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, marginTop: 1 }}>Hard brakes</div>
          </div>
        </div>
      </PDCard>

      {/* Day-grouped list */}
      {trips.map(group => (
        <div key={group.day} style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: t.fgMuted, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: 0.5, textTransform: 'uppercase', padding: '4px 4px 8px' }}>{group.day}</div>
          <PDCard padded={false} style={{ overflow: 'hidden' }}>
            {group.items.map((it, i) => (
              <div key={i} style={{
                padding: '12px 14px',
                borderTop: i > 0 ? `1px solid ${t.borderS}` : 'none',
                display: 'flex', alignItems: 'center', gap: 12,
              }}>
                {/* tiny route svg */}
                <svg width="28" height="40" viewBox="0 0 28 40" style={{ flexShrink: 0 }}>
                  <circle cx="14" cy="6" r="3" fill={a.base} />
                  <path d="M14 9 C 14 14, 6 18, 14 24 S 22 32, 14 34" fill="none" stroke={t.border} strokeWidth="1.3" strokeDasharray="2 3" />
                  <rect x="11" y="33" width="6" height="6" rx="1" fill={t.fgMuted} />
                </svg>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 14, color: t.fg, fontFamily: PD_FONT, fontWeight: 500 }}>{it.title}</span>
                    {it.flags && <PDPill color="warn">⚠ {it.flags}</PDPill>}
                  </div>
                  <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT_MONO, marginTop: 3, display: 'flex', gap: 8 }}>
                    <span>{it.time}</span>
                    <span>·</span>
                    <span>{it.dur}</span>
                    <span>·</span>
                    <span>{it.dist}</span>
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 11, color: t.fgMuted, fontFamily: PD_FONT }}>max</div>
                  <div style={{ fontSize: 15, color: t.fg, fontFamily: PD_FONT_MONO, fontWeight: 500, fontVariantNumeric: 'tabular-nums' }}>{it.max}<span style={{ fontSize: 10, color: t.fgMuted, marginLeft: 2 }}>mph</span></div>
                  {it.status === 'live'    && <PDPill color="accent"  dot style={{ marginTop: 4 }}>LIVE</PDPill>}
                  {it.status === 'pending' && <PDPill color="warn"    dot style={{ marginTop: 4 }}>QUEUED</PDPill>}
                  {it.status === 'synced'  && (
                    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 3, marginTop: 4, color: t.fgDim, fontFamily: PD_FONT, fontSize: 10 }}>
                      <Icon d={I.cloud} size={10} stroke={2} />synced
                    </div>
                  )}
                </div>
              </div>
            ))}
          </PDCard>
        </div>
      ))}
    </div>
  );
}

Object.assign(window, { ScreenHome, ScreenConnect, ScreenTrips });
