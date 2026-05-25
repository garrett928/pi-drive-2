// pi-drive design tokens
// Dark, warm neutrals; single warm orange accent. Geist + Geist Mono.

const PD_THEMES = {
  dark: {
    bg:        'oklch(0.155 0.005 60)',   // near-black, slight warm
    bgElev:    'oklch(0.195 0.005 60)',   // card surface
    surface:   'oklch(0.215 0.005 60)',   // elevated card
    surface2:  'oklch(0.255 0.005 60)',   // input bg / row hover
    border:    'oklch(0.30 0.005 60)',    // subtle border
    borderS:   'oklch(0.24 0.005 60)',    // even more subtle
    fg:        'oklch(0.97 0.005 80)',
    fgMuted:   'oklch(0.70 0.005 70)',
    fgDim:     'oklch(0.50 0.005 70)',
    danger:    'oklch(0.66 0.20 25)',
    success:   'oklch(0.74 0.16 150)',
    warn:      'oklch(0.80 0.15 80)',
  },
  light: {
    bg:        'oklch(0.985 0.003 80)',
    bgElev:    'oklch(0.97 0.003 80)',
    surface:   'oklch(1.00 0 0)',
    surface2:  'oklch(0.955 0.004 80)',
    border:    'oklch(0.89 0.005 70)',
    borderS:   'oklch(0.93 0.005 70)',
    fg:        'oklch(0.18 0.005 60)',
    fgMuted:   'oklch(0.42 0.006 60)',
    fgDim:     'oklch(0.62 0.006 60)',
    danger:    'oklch(0.55 0.22 25)',
    success:   'oklch(0.50 0.16 150)',
    warn:      'oklch(0.55 0.18 80)',
  },
};

const PD_ACCENTS = {
  'oklch(0.72 0.17 55)':  { base: 'oklch(0.72 0.17 55)',  soft: 'oklch(0.72 0.17 55 / 0.16)',  strong: 'oklch(0.78 0.18 55)'  },
  'oklch(0.65 0.21 22)':  { base: 'oklch(0.65 0.21 22)',  soft: 'oklch(0.65 0.21 22 / 0.16)',  strong: 'oklch(0.72 0.22 22)'  },
  'oklch(0.80 0.16 75)':  { base: 'oklch(0.80 0.16 75)',  soft: 'oklch(0.80 0.16 75 / 0.16)',  strong: 'oklch(0.85 0.17 75)'  },
  'oklch(0.78 0.13 210)': { base: 'oklch(0.78 0.13 210)', soft: 'oklch(0.78 0.13 210 / 0.16)', strong: 'oklch(0.85 0.14 210)' },
};
const PD_ACCENT_DEFAULT = 'oklch(0.72 0.17 55)';

// Default — overridden via context in <PDApp theme accent>
const PD_DEFAULT = { theme: 'dark', accent: PD_ACCENT_DEFAULT };

const PD_FONT      = "'Geist', system-ui, -apple-system, sans-serif";
const PD_FONT_MONO = "'Geist Mono', ui-monospace, 'JetBrains Mono', monospace";

// Context — every screen reads palette via usePD()
const PDCtx = React.createContext({ t: PD_THEMES.dark, a: PD_ACCENTS[PD_ACCENT_DEFAULT], theme: 'dark', accent: PD_ACCENT_DEFAULT });
const usePD = () => React.useContext(PDCtx);

function PDProvider({ theme = 'dark', accent = PD_ACCENT_DEFAULT, children }) {
  const value = React.useMemo(() => ({
    t: PD_THEMES[theme] || PD_THEMES.dark,
    a: PD_ACCENTS[accent] || PD_ACCENTS[PD_ACCENT_DEFAULT],
    theme, accent,
  }), [theme, accent]);
  return <PDCtx.Provider value={value}>{children}</PDCtx.Provider>;
}

Object.assign(window, { PD_THEMES, PD_ACCENTS, PD_ACCENT_DEFAULT, PD_DEFAULT, PD_FONT, PD_FONT_MONO, PDCtx, usePD, PDProvider });
