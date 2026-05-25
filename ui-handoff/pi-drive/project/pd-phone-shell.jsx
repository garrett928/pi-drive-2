// pi-drive — phone shell. Custom dark Android frame + internal router.
// Each artboard mounts its own <PhoneShell initialRoute=...>.
//
// Routes:
//   home  · trips  · connect  · settings  · settings/server · settings/home-layout
//   · settings/aa-layout · settings/thresholds

const PD_ROUTES = {
  'home':                 { title: 'Live',              tab: 'home',     screen: 'home',         action: 'liveDot' },
  'trips':                { title: 'Trips',             tab: 'trips',    screen: 'trips',        action: 'filter'  },
  'connect':              { title: 'Pair dongle',       tab: 'home',     screen: 'connect',      back: 'home', noNav: true },
  'connect/scan':         { title: 'Pair dongle',       tab: 'home',     screen: 'connect-scan', back: 'home', noNav: true },
  'connect/pair':         { title: 'Pair dongle',       tab: 'home',     screen: 'connect-pair', back: 'home', noNav: true },
  'connect/done':         { title: 'Pair dongle',       tab: 'home',     screen: 'connect-done', back: null,   noNav: true },
  'settings':             { title: 'Settings',          tab: 'settings', screen: 'settings'      },
  'settings/server':      { title: 'Telemetry server',  tab: 'settings', screen: 'server',       back: 'settings' },
  'settings/home-layout': { title: 'Phone home layout', tab: 'settings', screen: 'home-layout',  back: 'settings' },
  'settings/aa-layout':   { title: 'Android Auto',      tab: 'settings', screen: 'aa-layout',    back: 'settings' },
  'settings/thresholds':  { title: 'Thresholds',        tab: 'settings', screen: 'thresholds',   back: 'settings' },
};

function PhoneShell({ initialRoute = 'home', theme = 'dark', accent = 'orange', frameStyle = 'pixel' }) {
  const [route, setRoute] = React.useState(initialRoute);
  const def = PD_ROUTES[route] || PD_ROUTES.home;

  const go = (r) => setRoute(r);

  // map route → action node (top-right of app bar)
  const action = (() => {
    if (def.action === 'liveDot') return <PDPill color="success" dot>LIVE</PDPill>;
    if (def.action === 'filter') return (
      <button style={{
        width: 36, height: 36, borderRadius: 18, border: 'none', background: 'transparent',
        color: 'currentColor', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
      }}><Icon d={I.filter} size={18} /></button>
    );
    return null;
  })();

  return (
    <PDProvider theme={theme} accent={accent}>
      <PixelFrame frameStyle={frameStyle}>
        <PDStatusBar />
        <PDTopBar
          title={def.title}
          onBack={def.back !== undefined && def.back !== null ? () => go(def.back) : null}
          action={action}
          dense={def.noNav}
        />
        <ScreenRouter screen={def.screen} go={go} />
        {!def.noNav && <PDBottomNav route={def.tab} go={go} />}
        <NavHandle />
      </PixelFrame>
    </PDProvider>
  );
}

// Screen router — picks the right screen for the current route.
function ScreenRouter({ screen, go }) {
  switch (screen) {
    case 'home':         return <ScreenHome go={go} />;
    case 'trips':        return <ScreenTrips go={go} />;
    case 'connect':
    case 'connect-scan': return <ScreenConnect go={go} initialStep="scan" />;
    case 'connect-pair': return <ScreenConnect go={go} initialStep="pair" />;
    case 'connect-done': return <ScreenConnect go={go} initialStep="done" />;
    case 'settings':     return <ScreenSettings go={go} />;
    case 'server':       return <ScreenServer go={go} />;
    case 'home-layout':  return <ScreenHomeLayout go={go} />;
    case 'aa-layout':    return <ScreenAALayout go={go} />;
    case 'thresholds':   return <ScreenThresholds go={go} />;
    default:             return <ScreenHome go={go} />;
  }
}

// ─── Pixel-style frame ─────────────────────────────────────
function PixelFrame({ children, frameStyle = 'pixel' }) {
  const { t } = usePD();
  // 360 × 800 logical phone surface; outer frame adds 12px bezels.
  const W = 360, H = 800;
  return (
    <div style={{
      width: W + 16, height: H + 16, padding: 8, boxSizing: 'border-box',
      background: 'oklch(0.08 0 0)', borderRadius: 44,
      boxShadow: 'inset 0 0 0 1.5px oklch(0.22 0 0), 0 24px 50px rgba(0,0,0,0.25), 0 6px 12px rgba(0,0,0,0.18)',
      position: 'relative',
    }}>
      <div style={{
        width: '100%', height: '100%', borderRadius: 36, overflow: 'hidden',
        background: t.bg, color: t.fg, position: 'relative',
        display: 'flex', flexDirection: 'column',
        boxShadow: 'inset 0 0 0 1px oklch(0.18 0.005 60)',
      }}>
        {/* Camera punch hole */}
        <div style={{
          position: 'absolute', top: 8, left: '50%', transform: 'translateX(-50%)',
          width: 11, height: 11, borderRadius: 6, background: 'oklch(0.05 0 0)', zIndex: 5,
          boxShadow: 'inset 0 0 0 0.5px oklch(0.20 0.005 60)',
        }} />
        {children}
      </div>
      {/* Side button hints */}
      <div style={{ position: 'absolute', right: -2, top: 130, width: 3, height: 70, borderRadius: 1.5, background: 'oklch(0.18 0 0)' }} />
      <div style={{ position: 'absolute', right: -2, top: 220, width: 3, height: 40, borderRadius: 1.5, background: 'oklch(0.18 0 0)' }} />
      <div style={{ position: 'absolute', left: -2, top: 160, width: 3, height: 90, borderRadius: 1.5, background: 'oklch(0.18 0 0)' }} />
    </div>
  );
}

function NavHandle() {
  const { t } = usePD();
  return (
    <div style={{ height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      <div style={{ width: 110, height: 4, borderRadius: 2, background: t.fg, opacity: 0.45 }} />
    </div>
  );
}

Object.assign(window, { PhoneShell });
