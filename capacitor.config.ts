import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.kdsmedia.beruang',
  appName: 'BERUANG',
  webDir: '.',
  // index.html lives at the repo root, so webDir is the current directory.
  // Capacitor will copy index.html (+ icon.png) into the native project.
  android: {
    allowMixedContent: true,
  },
  plugins: {
    AdMob: {
      // Real AdMob App ID (Android). Same value is also referenced at runtime
      // by the ADMOB module in index.html for initialize().
      appId: 'ca-app-pub-6881903056221433~1794482255',
    },
  },
};

export default config;
