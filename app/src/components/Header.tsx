import React from 'react';
import {
  Volume2,
  VolumeX,
  Settings,
  History,
  HelpCircle,
  CheckSquare,
  Radio,
  Smartphone,
  Image as ImageIcon,
  Sparkles,
  Sun,
  Moon
} from 'lucide-react';
import { AppSettings } from '../types';
import { PWAInstallButton } from './PWAInstallButton';

interface Props {
  settings: AppSettings;
  onToggleVoice: () => void;
  onOpenSettings: () => void;
  onOpenHistory: () => void;
  onOpenTests: () => void;
  onOpenLimitationInfo: () => void;
  onOpenBackendSync: () => void;
  onOpenNativeAndroid: () => void;
  onOpenPlayStoreAssets: () => void;
  onOpenWalkthrough: () => void;
  onToggleTheme: () => void;
  sseConnected: boolean;
  txCount: number;
}

export const Header: React.FC<Props> = ({
  settings,
  onToggleVoice,
  onOpenSettings,
  onOpenHistory,
  onOpenTests,
  onOpenLimitationInfo,
  onOpenBackendSync,
  onOpenNativeAndroid,
  onOpenPlayStoreAssets,
  onOpenWalkthrough,
  onToggleTheme,
  sseConnected,
  txCount,
}) => {
  return (
    <header className="sticky top-0 z-40 w-full bg-white/90 dark:bg-slate-950/80 backdrop-blur-xl border-b border-slate-200 dark:border-slate-800/80 transition-colors duration-200">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex items-center justify-between gap-3">
        {/* Brand logo & title */}
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="relative flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 via-emerald-500 to-teal-400 p-0.5 shadow-lg shadow-emerald-500/20 shrink-0">
            <div className="w-full h-full bg-slate-900 dark:bg-slate-950 rounded-[10px] flex items-center justify-center">
              <span className="font-extrabold text-lg text-emerald-400">₹</span>
            </div>
            {settings.voiceAlertsEnabled && (
              <span className="absolute -top-1 -right-1 flex h-3 w-3">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500 border-2 border-white dark:border-slate-950"></span>
              </span>
            )}
          </div>

          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <h1 className="text-sm sm:text-base font-extrabold text-slate-900 dark:text-white tracking-tight truncate">
                UPI Sound Box
              </h1>
              <span className="hidden xs:inline-block px-1.5 py-0.5 text-[9px] font-bold tracking-wider uppercase bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30 rounded">
                Live
              </span>
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate flex items-center gap-1 font-medium">
              <span>Payment Alert • తెలుగు • हिन्दी</span>
            </p>
          </div>
        </div>

        {/* Action icons */}
        <div className="flex items-center gap-1.5 shrink-0">
          {/* Play Store Graphics button */}
          <button
            onClick={onOpenPlayStoreAssets}
            title="Download Google Play Store Graphics (Icon, Feature Banner, Screenshots)"
            className="px-2.5 py-1.5 rounded-xl bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-700 dark:text-emerald-300 font-bold text-xs border border-emerald-500/30 dark:border-emerald-500/40 shadow-sm flex items-center gap-1.5 transition"
          >
            <ImageIcon className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
            <span className="hidden xs:inline">Play Graphics</span>
          </button>

          {/* Native Android Plug & Play button */}
          <button
            onClick={onOpenNativeAndroid}
            title="Native Android App (Plug & Play)"
            className="px-2 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 font-semibold text-xs border border-slate-200 dark:border-slate-700 flex items-center gap-1 transition"
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Android APK</span>
          </button>

          {/* Live SMS Gateway status button */}
          <button
            onClick={onOpenBackendSync}
            title={sseConnected ? 'Phone SMS Gateway: Connected' : 'Phone SMS Gateway: Setup'}
            className={`p-2 rounded-xl transition flex items-center justify-center border ${
              sseConnected
                ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/25'
                : 'bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-800 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            <Radio className={`w-4 h-4 ${sseConnected ? 'animate-pulse' : ''}`} />
          </button>

          {/* In-app Install Button if available */}
          <PWAInstallButton variant="compact" />

          {/* Voice Toggle Button */}
          <button
            id="header-voice-btn"
            onClick={onToggleVoice}
            title={settings.voiceAlertsEnabled ? 'Voice Alerts Active' : 'Voice Alerts Muted'}
            className={`p-2 rounded-xl transition flex items-center justify-center border ${
              settings.voiceAlertsEnabled
                ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/25'
                : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-700 hover:bg-slate-200 dark:hover:bg-slate-700'
            }`}
          >
            {settings.voiceAlertsEnabled ? (
              <Volume2 className="w-4 h-4" />
            ) : (
              <VolumeX className="w-4 h-4" />
            )}
          </button>

          {/* Theme Toggle Button (Light/Dark Mode) */}
          <button
            onClick={onToggleTheme}
            title={
              settings.theme === 'dark'
                ? 'Switch to Light Mode (లైట్ మోడ్)'
                : 'Switch to Dark Mode (డార్క్ మోడ్)'
            }
            className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-900 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-800 transition"
            aria-label="Toggle Theme"
          >
            {settings.theme === 'dark' ? (
              <Sun className="w-4 h-4 text-amber-400" />
            ) : (
              <Moon className="w-4 h-4 text-indigo-500" />
            )}
          </button>

          {/* History Button */}
          <button
            onClick={onOpenHistory}
            title="Transaction History"
            className="relative p-2 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-900 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-800 transition"
          >
            <History className="w-4 h-4" />
            {txCount > 0 && (
              <span className="absolute -top-1 -right-1 flex items-center justify-center min-w-4 h-4 px-1 text-[9px] font-bold bg-emerald-500 text-slate-950 rounded-full">
                {txCount > 99 ? '99+' : txCount}
              </span>
            )}
          </button>

          {/* Settings Button */}
          <button
            id="header-settings-btn"
            onClick={onOpenSettings}
            title="Settings"
            className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-900 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-800 transition"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Sub-bar with quick navigation shortcuts */}
      <div className="bg-slate-50/90 dark:bg-slate-900/60 border-t border-slate-200 dark:border-slate-800/60 px-4 sm:px-6 lg:px-8 py-1.5 transition-colors duration-200">
        <div className="max-w-6xl mx-auto flex items-center justify-between text-[11px] text-slate-600 dark:text-slate-400">
          <button
            onClick={onOpenWalkthrough}
            className="flex items-center gap-1 text-teal-600 dark:text-teal-400 hover:text-teal-700 dark:hover:text-teal-300 font-bold transition"
            title="Interactive App Walkthrough & Tutorial"
          >
            <Sparkles className="w-3 h-3 text-teal-500 dark:text-teal-400" />
            <span>Walkthrough</span>
          </button>

          <button
            onClick={onOpenNativeAndroid}
            className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400 hover:text-emerald-700 dark:hover:text-emerald-300 font-semibold transition"
          >
            <Smartphone className="w-3 h-3" />
            <span>Native App</span>
          </button>

          <button
            onClick={onOpenBackendSync}
            className="flex items-center gap-1 text-slate-600 dark:text-slate-300 hover:text-emerald-600 dark:hover:text-emerald-300 font-medium transition"
          >
            <Radio className="w-3 h-3 text-emerald-600 dark:text-emerald-400" />
            <span>SMS Gateway {sseConnected ? '(Live)' : ''}</span>
          </button>

          <button
            onClick={onOpenLimitationInfo}
            className="flex items-center gap-1 hover:text-emerald-600 dark:hover:text-emerald-400 transition"
          >
            <HelpCircle className="w-3 h-3 text-slate-400" />
            <span>Info</span>
          </button>

          <button
            onClick={onOpenTests}
            className="flex items-center gap-1 hover:text-emerald-600 dark:hover:text-emerald-400 transition"
          >
            <CheckSquare className="w-3 h-3 text-slate-400" />
            <span>Tests (6/6)</span>
          </button>
        </div>
      </div>
    </header>
  );
};
