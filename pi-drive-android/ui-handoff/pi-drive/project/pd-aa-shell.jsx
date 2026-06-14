// pi-drive — Android Auto landscape shell. Swipeable between two screens.

function AAShell({ initialScreen = 1, theme = 'dark', accent = 'orange' }) {
  const [screen, setScreen] = React.useState(initialScreen);

  return (
    <PDProvider theme={theme} accent={accent}>
      <AAFrame>
        <AAChrome screen={screen} setScreen={setScreen} />
        <div style={{
          flex: 1, position: 'relative', display: 'block', overflow: 'hidden',
        }}>
          {/* Slide container */}
          <div style={{
            display: 'flex', width: '200%', height: '100%',
            transition: 'transform 0.45s cubic-bezier(0.2, 0.8, 0.2, 1)',
            transform: `translateX(${screen === 1 ? '0%' : '-50%'})`,
          }}>
            <AAScreenWrap><AAScreenDials /></AAScreenWrap>
            <AAScreenWrap><AAScreenGraphs /></AAScreenWrap>
          </div>
        </div>
      </AAFrame>
    </PDProvider>
  );
}

function AAScreenWrap({ children }) {
  return <div style={{ width: '50%', flexShrink: 0, display: 'flex', flexDirection: 'column' }}>{children}</div>;
}

// ─── AA chrome ─────────────────────────────────────────────
// Left rail: app icon + screen dots. Top: time / signal / car indicators.
function AAChrome({ screen, setScreen }) {
  const { t, a } = usePD();
  return (
    <div style={{
      height: 56, padding: '0 18px', display: 'flex', alignItems: 'center', gap: 18,
      borderBottom: `1px solid ${t.borderS}`, background: t.bg, flexShrink: 0,
    }}>
      {/* App brand */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 28, height: 28, borderRadius: 7, background: a.base,
          color: 'oklch(0.18 0.02 60)', display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: PD_FONT, fontWeight: 700, fontSize: 16, letterSpacing: -0.5,
        }}>π</div>
        <span style={{ fontSize: 15, color: t.fg, fontFamily: PD_FONT, fontWeight: 600, letterSpacing: -0.2 }}>pi-drive</span>
      </div>

      <div style={{ width: 1, height: 22, background: t.borderS }} />

      {/* Screen dots / swipe affordance */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        {[1, 2].map(n => {
          const sel = screen === n;
          return (
            <button key={n} onClick={() => setScreen(n)} style={{
              display: 'flex', alignItems: 'center', gap: 6,
              padding: '5px 10px', borderRadius: 99,
              border: `1px solid ${sel ? a.base : t.borderS}`,
              background: sel ? a.soft : 'transparent',
              color: sel ? a.base : t.fgMuted,
              cursor: 'pointer', fontFamily: PD_FONT, fontSize: 12, fontWeight: 600,
            }}>
              <span style={{ width: 6, height: 6, borderRadius: 3, background: sel ? a.base : t.fgDim }} />
              {n === 1 ? 'Dials' : 'Graphs'}
            </button>
          );
        })}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: t.fgDim, marginLeft: 6 }}>
          <Icon d={I.swipe} size={14} />
          <span style={{ fontSize: 10, fontFamily: PD_FONT, fontWeight: 500, letterSpacing: 0.4, textTransform: 'uppercase' }}>swipe</span>
        </div>
      </div>

      <div style={{ flex: 1 }} />

      <div style={{ display: 'flex', alignItems: 'center', gap: 14, color: t.fgMuted }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontFamily: PD_FONT, fontSize: 12 }}>
          <Icon d={I.bluetooth} size={13} stroke={2} />OBDLink LX
        </span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontFamily: PD_FONT, fontSize: 12 }}>
          <Icon d={I.cloudUp} size={13} stroke={2} style={{ color: t.success }} />Streaming
        </span>
        <span style={{ fontFamily: PD_FONT_MONO, fontSize: 13, color: t.fg, fontVariantNumeric: 'tabular-nums' }}>9:41</span>
      </div>
    </div>
  );
}

// AA outer frame — landscape "head unit" framed in a car-dash bezel.
function AAFrame({ children }) {
  const { t } = usePD();
  return (
    <div style={{
      width: 880, height: 500, padding: 14, boxSizing: 'border-box',
      background: 'oklch(0.10 0 0)', borderRadius: 14,
      boxShadow: 'inset 0 0 0 1px oklch(0.22 0 0), 0 30px 60px rgba(0,0,0,0.3)',
      position: 'relative',
    }}>
      <div style={{
        width: '100%', height: '100%', borderRadius: 6, overflow: 'hidden',
        background: t.bg, color: t.fg, display: 'flex', flexDirection: 'column',
      }}>
        {children}
      </div>
      {/* small dash hint — speaker grille on the right side */}
      <div style={{
        position: 'absolute', right: -10, top: 30, bottom: 30, width: 6, borderRadius: 3,
        background: 'oklch(0.07 0 0)',
        backgroundImage: 'repeating-linear-gradient(0deg, oklch(0.15 0 0) 0 2px, transparent 2px 6px)',
      }} />
    </div>
  );
}

Object.assign(window, { AAShell });
