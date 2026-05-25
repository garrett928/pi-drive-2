// pi-drive — main app: design canvas with all artboards + tweaks panel.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "theme": "dark",
  "accent": "oklch(0.72 0.17 55)"
}/*EDITMODE-END*/;

function PDApp() {
  const [tw, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const theme = tw.theme || 'dark';
  const accent = tw.accent || 'oklch(0.72 0.17 55)';

  // phone artboard size
  const phoneW = 376, phoneH = 816;
  // AA size matches AAFrame: 880 × 500 outer
  const aaW = 880, aaH = 500;

  return (
    <>
      <DesignCanvas>
        <DCSection id="phone-live" title="Phone · live dashboard, connect flow & trips"
          subtitle="Bottom-tab nav: Live · Trips · Settings. Tap any tile to navigate within the app.">
          <DCArtboard id="home" label="Home — live dashboard" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="home" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="connect-scan" label="Connect — discover dongle" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="connect/scan" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="connect-pair" label="Connect — pairing" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="connect/pair" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="connect-done" label="Connect — vehicle detected" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="connect/done" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="trips" label="Trip history" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="trips" theme={theme} accent={accent} /></Center>
          </DCArtboard>
        </DCSection>

        <DCSection id="phone-settings" title="Phone · settings"
          subtitle="Dongle connection, server config, customize home & Android Auto, thresholds.">
          <DCArtboard id="settings-root" label="Settings — root" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="settings" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="settings-server" label="Telemetry server config" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="settings/server" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="settings-home" label="Customize phone home" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="settings/home-layout" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="settings-aa" label="Android Auto layout editor" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="settings/aa-layout" theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="settings-thresh" label="Driving thresholds" width={phoneW} height={phoneH}>
            <Center><PhoneShell initialRoute="settings/thresholds" theme={theme} accent={accent} /></Center>
          </DCArtboard>
        </DCSection>

        <DCSection id="aa" title="Android Auto · head-unit (landscape)"
          subtitle="Two swipeable screens — modular widgets. Click Dials / Graphs in the chrome to swap.">
          <DCArtboard id="aa-dials" label="Screen 1 — dials" width={aaW} height={aaH}>
            <Center><AAShell initialScreen={1} theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="aa-graphs" label="Screen 2 — graphs" width={aaW} height={aaH}>
            <Center><AAShell initialScreen={2} theme={theme} accent={accent} /></Center>
          </DCArtboard>
          <DCArtboard id="aa-split" label="Split-screen — alongside Maps" width={aaW} height={aaH}>
            <Center><AASplitShell theme={theme} accent={accent} /></Center>
          </DCArtboard>
        </DCSection>
      </DesignCanvas>

      <PDTweaks tw={tw} setTweak={setTweak} />
    </>
  );
}

// Centering helper — phones and AA frames are smaller than the artboard,
// so center them with a soft halo background.
function Center({ children }) {
  return (
    <div style={{
      width: '100%', height: '100%',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'oklch(0.97 0.005 70)',
    }}>{children}</div>
  );
}

// ─── Tweaks ────────────────────────────────────────────────
function PDTweaks({ tw, setTweak }) {
  return (
    <TweaksPanel title="pi-drive · tweaks">
      <TweakSection label="Theme" />
      <TweakRadio
        label="Mode"
        value={tw.theme}
        onChange={v => setTweak('theme', v)}
        options={[
          { value: 'dark',  label: 'Dark'  },
          { value: 'light', label: 'Light' },
        ]}
      />
      <TweakSection label="Accent" />
      <TweakColor
        label="Color"
        value={tw.accent}
        onChange={v => setTweak('accent', v)}
        options={[
          'oklch(0.72 0.17 55)',
          'oklch(0.65 0.21 22)',
          'oklch(0.80 0.16 75)',
          'oklch(0.78 0.13 210)',
        ]}
      />
    </TweaksPanel>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<PDApp />);
