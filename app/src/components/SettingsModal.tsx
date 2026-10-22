import React from 'react';
import {
  X,
  Volume2,
  Globe,
  Gauge,
  Shield,
  Play,
  BellRing,
  Smartphone,
  CheckCircle,
  AlertTriangle,
  Sparkles,
  Sun,
  Moon,
  Laptop,
  Star,
  ExternalLink
} from 'lucide-react';
import { AppSettings, SpeechLanguage, SpeechSpeed, VoiceStatus, AppTheme } from '../types';
import { PWAInstallButton } from './PWAInstallButton';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  settings: AppSettings;
  onUpdateSettings: (newSettings: AppSettings) => void;
  onTestVoice: () => void;
  voiceStatus: VoiceStatus;
  onOpenWalkthrough?: () => void;
  onOpenRateApp?: () => void;
}

export const SettingsModal: React.FC<Props> = ({
  isOpen,
  onClose,
  settings,
  onUpdateSettings,
  onTestVoice,
  voiceStatus,
  onOpenWalkthrough,
  onOpenRateApp,
}) => {
  if (!isOpen) return null;

  const handleToggleVoice = () => {
    onUpdateSettings({ ...settings, voiceAlertsEnabled: !settings.voiceAlertsEnabled });
  };

  const handleLanguageChange = (lang: SpeechLanguage) => {
    onUpdateSettings({ ...settings, language: lang });
  };

  const handleSpeedChange = (speed: SpeechSpeed) => {
    onUpdateSettings({ ...settings, speechSpeed: speed });
  };

  const handleMinAmountChange = (val: string) => {
    const num = Math.max(1, parseFloat(val) || 1);
    onUpdateSettings({ ...settings, minAmount: num });
  };

  const handleToggleChime = () => {
    onUpdateSettings({ ...settings, playChime: !settings.playChime });
  };

  const handleThemeChange = (newTheme: AppTheme) => {
    onUpdateSettings({ ...settings, theme: newTheme });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/80 backdrop-blur-sm p-0 sm:p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg bg-white dark:bg-slate-900 border-t sm:border border-slate-200 dark:border-slate-800 rounded-t-3xl sm:rounded-3xl max-h-[92vh] flex flex-col shadow-2xl transition-colors duration-200">
        {/* Header */}
        <div className="p-4 sm:p-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900 dark:text-white">Voice &amp; App Settings</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">సెట్టింగ్‌లు &amp; థీమ్ ఎంపికలు</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 rounded-xl transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="overflow-y-auto flex-1 p-4 sm:p-5 space-y-5 text-xs text-slate-700 dark:text-slate-300">
          {/* Section 0: Theme Selection (Light / Dark / System) */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 space-y-2.5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sun className="w-4 h-4 text-amber-500 dark:text-amber-400" />
                <label className="font-bold text-slate-900 dark:text-white text-xs">
                  Theme &amp; Appearance (థీమ్ ఎంపిక):
                </label>
              </div>
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                {settings.theme === 'dark' ? 'Dark' : settings.theme === 'light' ? 'Light' : 'System'}
              </span>
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Choose between Dark mode (comfortable for indoor shop displays) or Light mode (clean high-contrast for daylight).
            </p>

            <div className="grid grid-cols-3 gap-2 pt-1">
              <button
                type="button"
                onClick={() => handleThemeChange('light')}
                className={`p-2.5 rounded-xl border text-center transition flex flex-col items-center gap-1.5 ${
                  settings.theme === 'light'
                    ? 'border-emerald-500 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300 font-bold shadow-sm'
                    : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Sun className="w-4 h-4 text-amber-500" />
                <span className="text-xs">Light (లైట్)</span>
              </button>

              <button
                type="button"
                onClick={() => handleThemeChange('dark')}
                className={`p-2.5 rounded-xl border text-center transition flex flex-col items-center gap-1.5 ${
                  settings.theme === 'dark'
                    ? 'border-emerald-500 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300 font-bold shadow-sm'
                    : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Moon className="w-4 h-4 text-indigo-400" />
                <span className="text-xs">Dark (డార్క్)</span>
              </button>

              <button
                type="button"
                onClick={() => handleThemeChange('system')}
                className={`p-2.5 rounded-xl border text-center transition flex flex-col items-center gap-1.5 ${
                  settings.theme === 'system'
                    ? 'border-emerald-500 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300 font-bold shadow-sm'
                    : 'border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Laptop className="w-4 h-4 text-teal-500" />
                <span className="text-xs">System (ఆటో)</span>
              </button>
            </div>
          </div>

          {/* Section 1: Voice Alerts Master Switch */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div
                className={`p-2 rounded-xl ${
                  settings.voiceAlertsEnabled
                    ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
                    : 'bg-slate-200 dark:bg-slate-800 text-slate-500'
                }`}
              >
                <Volume2 className="w-5 h-5" />
              </div>
              <div>
                <span className="font-bold text-sm text-slate-900 dark:text-white block">
                  Voice Alerts (వాయిస్ అలర్ట్)
                </span>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">
                  {settings.voiceAlertsEnabled ? 'Enabled • Speaks incoming credits' : 'Muted • No sound'}
                </span>
              </div>
            </div>

            <button
              onClick={handleToggleVoice}
              className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                settings.voiceAlertsEnabled ? 'bg-emerald-500' : 'bg-slate-300 dark:bg-slate-700'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out ${
                  settings.voiceAlertsEnabled ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Section 2: Announcement Language */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 space-y-2.5">
            <div className="flex items-center gap-2">
              <Globe className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <label className="font-bold text-slate-900 dark:text-white text-xs">Announcement Language (భాష):</label>
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Default is Telugu (India). Speaks: "మీ ఖాతాలో 500 రూపాయలు జమ అయ్యాయి."
            </p>

            <div className="grid grid-cols-2 gap-2 pt-1">
              <button
                type="button"
                onClick={() => handleLanguageChange('te-IN')}
                className={`p-3 rounded-xl border text-left transition flex items-center justify-between ${
                  settings.language === 'te-IN'
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-800 dark:text-emerald-300 font-bold'
                    : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}
              >
                <div>
                  <span className="block text-xs font-bold">తెలుగు (Telugu)</span>
                  <span className="text-[10px] text-slate-400">te-IN (Default)</span>
                </div>
                {settings.language === 'te-IN' && (
                  <CheckCircle className="w-4 h-4 text-emerald-500 dark:text-emerald-400" />
                )}
              </button>

              <button
                type="button"
                onClick={() => handleLanguageChange('en-IN')}
                className={`p-3 rounded-xl border text-left transition flex items-center justify-between ${
                  settings.language === 'en-IN'
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-800 dark:text-emerald-300 font-bold'
                    : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                }`}
              >
                <div>
                  <span className="block text-xs font-bold">English (India)</span>
                  <span className="text-[10px] text-slate-400">en-IN</span>
                </div>
                {settings.language === 'en-IN' && (
                  <CheckCircle className="w-4 h-4 text-emerald-500 dark:text-emerald-400" />
                )}
              </button>
            </div>
          </div>

          {/* Section 3: Speech Speed */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 space-y-2.5">
            <div className="flex items-center gap-2">
              <Gauge className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <label className="font-bold text-slate-900 dark:text-white text-xs">Speech Speed (వాయిస్ వేగం):</label>
            </div>

            <div className="grid grid-cols-3 gap-2 pt-1">
              {(
                [
                  { id: 'slow', label: 'Slow (నెమ్మదిగా)', rate: '0.85x' },
                  { id: 'normal', label: 'Normal (సాధారణ)', rate: '1.0x' },
                  { id: 'fast', label: 'Fast (వేగంగా)', rate: '1.2x' },
                ] as const
              ).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => handleSpeedChange(item.id)}
                  className={`p-2.5 rounded-xl border text-center transition ${
                    settings.speechSpeed === item.id
                      ? 'bg-emerald-500/15 border-emerald-500 text-emerald-800 dark:text-emerald-300 font-bold'
                      : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <span className="block font-bold text-xs">{item.id.toUpperCase()}</span>
                  <span className="text-[10px] text-slate-500 dark:text-slate-400">{item.rate}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Section 4: Minimum Announcement Amount */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <label className="font-bold text-slate-900 dark:text-white text-xs">
                Minimum Announcement Amount (కనీస మొత్తం):
              </label>
              <span className="text-emerald-600 dark:text-emerald-400 font-bold">₹{settings.minAmount}</span>
            </div>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Transactions below this amount will be saved in history but won't make sound.
            </p>

            <div className="relative pt-1">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 font-bold">
                ₹
              </span>
              <input
                type="number"
                min="1"
                step="1"
                value={settings.minAmount}
                onChange={(e) => handleMinAmountChange(e.target.value)}
                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl py-2 pl-8 pr-3 text-sm font-bold text-slate-900 dark:text-white focus:outline-none focus:border-emerald-500"
              />
            </div>
          </div>

          {/* Section 5: Soundbox Chime Sound */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <BellRing className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <div>
                <span className="font-bold text-xs text-slate-900 dark:text-white block">Payment Chime Tone</span>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">
                  Plays dual-sine chime before voice announcement
                </span>
              </div>
            </div>

            <button
              onClick={handleToggleChime}
              className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                settings.playChime ? 'bg-emerald-500' : 'bg-slate-300 dark:bg-slate-700'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out ${
                  settings.playChime ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Test Voice Button */}
          <div className="pt-1">
            <button
              type="button"
              onClick={onTestVoice}
              className="w-full py-3 px-4 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700/80 active:scale-98 text-emerald-700 dark:text-emerald-400 font-bold text-xs border border-emerald-500/30 flex items-center justify-center gap-2 transition shadow-sm"
            >
              <Play className="w-4 h-4 fill-emerald-600 dark:fill-emerald-400" />
              <span>Test Voice: "మీ ఖాతాలో 100 రూపాయలు జమ అయ్యాయి."</span>
            </button>
          </div>

          {/* Device TTS & Telugu Support Notice */}
          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 space-y-2">
            <div className="flex items-center gap-2">
              {voiceStatus.hasTeluguVoice ? (
                <CheckCircle className="w-4 h-4 text-emerald-500 dark:text-emerald-400 shrink-0" />
              ) : (
                <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0" />
              )}
              <span className="font-bold text-slate-900 dark:text-white text-xs">
                Device Telugu Speech Engine:
              </span>
            </div>

            {voiceStatus.hasTeluguVoice ? (
              <p className="text-[11px] text-emerald-700 dark:text-emerald-300 font-medium">
                Native Telugu voice detected on this device. Voice alerts will play smoothly.
              </p>
            ) : (
              <div className="text-[11px] text-slate-700 dark:text-slate-300 space-y-1.5 bg-amber-500/10 border border-amber-500/20 p-2.5 rounded-xl">
                <p className="font-semibold text-amber-800 dark:text-amber-200">
                  Telugu voice data might not be pre-installed on your phone.
                </p>
                <p className="text-slate-600 dark:text-slate-300">
                  <strong>How to enable Telugu voice on Android:</strong>
                </p>
                <ol className="list-decimal list-inside space-y-0.5 text-[10px] text-slate-500 dark:text-slate-400">
                  <li>Open Android <strong>Settings</strong></li>
                  <li>Go to <strong>System</strong> &gt; <strong>Languages &amp; input</strong></li>
                  <li>Tap <strong>Text-to-speech output</strong> &gt; Settings icon</li>
                  <li>Tap <strong>Install voice data</strong> &gt; select <strong>Telugu</strong></li>
                </ol>
              </div>
            )}
          </div>

          {/* Install App on Android Section */}
          <div className="p-4 rounded-2xl bg-gradient-to-r from-emerald-500/10 to-teal-500/10 dark:from-emerald-950/40 dark:to-teal-950/40 border border-emerald-500/30 space-y-2">
            <div className="flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <span className="font-bold text-slate-900 dark:text-white text-xs">Install App on Phone</span>
            </div>
            <p className="text-[11px] text-slate-600 dark:text-slate-400">
              Install this app directly from Chrome onto your Android home screen. It runs offline without browser toolbars.
            </p>
            <div className="pt-1">
              <PWAInstallButton variant="primary" className="w-full" />
            </div>
          </div>

          {/* Interactive Walkthrough / Tutorial Section */}
          {onOpenWalkthrough && (
            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950/80 border border-teal-500/30 flex items-center justify-between gap-3">
              <div className="flex items-center gap-2.5">
                <div className="p-2 rounded-xl bg-teal-500/10 text-teal-600 dark:text-teal-400">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-900 dark:text-white">App Walkthrough &amp; Guide</h4>
                  <p className="text-[11px] text-slate-500 dark:text-slate-400">ట్యుటోరియల్ &amp; ఫీచర్స్ గైడ్</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onOpenWalkthrough();
                }}
                className="px-3 py-1.5 rounded-xl bg-teal-500/15 hover:bg-teal-500/25 text-teal-700 dark:text-teal-300 font-bold text-xs border border-teal-500/30 transition shrink-0"
              >
                Start Tour
              </button>
            </div>
          )}

          {/* Section: Rate Your App (Play Store Listing) */}
          <div className="p-4 rounded-2xl bg-gradient-to-r from-amber-500/10 via-amber-500/5 to-emerald-500/10 border border-amber-500/30 space-y-2.5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Star className="w-4 h-4 text-amber-500 fill-amber-500" />
                <span className="font-bold text-slate-900 dark:text-white text-xs">
                  Rate Your App (యాప్‌ని రేట్ చేయండి)
                </span>
              </div>
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-amber-600 dark:text-amber-400">
                Play Store
              </span>
            </div>
            <p className="text-[11px] text-slate-600 dark:text-slate-300 leading-relaxed">
              <strong>Why user ratings are valuable:</strong> Your 5-star review on Google Play helps fellow local merchants and shopkeepers discover this free Telugu Soundbox app without paying for costly soundbox hardware. It also guides our next bank SMS updates!
            </p>
            <div className="flex items-center gap-2 pt-1">
              <a
                href="https://play.google.com/store/apps/details?id=com.telugu.soundbox"
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 py-2.5 px-3 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-extrabold text-xs flex items-center justify-center gap-1.5 shadow-sm transition"
              >
                <Star className="w-3.5 h-3.5 fill-slate-950" />
                <span>Rate on Google Play</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
              {onOpenRateApp && (
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    onOpenRateApp();
                  }}
                  className="px-3 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 font-bold text-xs border border-slate-200 dark:border-slate-700 transition"
                >
                  Feedback
                </button>
              )}
            </div>
          </div>

          {/* Privacy & Security Guarantee */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 space-y-2 text-slate-600 dark:text-slate-400">
            <div className="flex items-center gap-2 text-slate-900 dark:text-white">
              <Shield className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <span className="font-bold text-xs">100% Local Privacy Guarantee</span>
            </div>
            <p className="text-[11px] leading-relaxed">
              This application strictly parses bank SMS messages locally in your browser using pure TypeScript algorithms. No bank SMS, account numbers, or balances are EVER sent to external servers or AI APIs.
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-200 dark:border-slate-800 text-center">
          <button
            onClick={onClose}
            className="w-full py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white dark:bg-slate-800 dark:hover:bg-slate-700 font-bold text-xs transition"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
