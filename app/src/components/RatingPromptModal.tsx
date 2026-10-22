import React, { useState } from 'react';
import { Star, X, ExternalLink, CheckCircle2, Sparkles } from 'lucide-react';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  lastPaymentAmount?: number;
  payerName?: string;
}

export const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=com.telugu.soundbox';
export const RATED_STORAGE_KEY = 'telugu_soundbox_rated';
export const SNOOZE_STORAGE_KEY = 'telugu_soundbox_rate_snooze';

export const RatingPromptModal: React.FC<Props> = ({
  isOpen,
  onClose,
  lastPaymentAmount,
  payerName,
}) => {
  const [hoveredStar, setHoveredStar] = useState<number | null>(null);
  const [selectedRating, setSelectedRating] = useState<number>(5);
  const [hasSubmittedFeedback, setHasSubmittedFeedback] = useState(false);

  if (!isOpen) return null;

  const handleRateOnPlayStore = (stars: number = selectedRating) => {
    try {
      localStorage.setItem(RATED_STORAGE_KEY, 'true');
    } catch {}

    // Open Play Store listing
    window.open(PLAY_STORE_URL, '_blank', 'noopener,noreferrer');
    setHasSubmittedFeedback(true);
    setTimeout(() => {
      onClose();
    }, 1800);
  };

  const handleRemindLater = () => {
    try {
      // Snooze for 48 hours
      const snoozeUntil = Date.now() + 48 * 60 * 60 * 1000;
      localStorage.setItem(SNOOZE_STORAGE_KEY, snoozeUntil.toString());
    } catch {}
    onClose();
  };

  const handleDontAskAgain = () => {
    try {
      localStorage.setItem(RATED_STORAGE_KEY, 'true');
    } catch {}
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 dark:bg-black/80 backdrop-blur-sm p-3 sm:p-4 animate-in fade-in duration-200">
      <div
        className="w-full max-w-md bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl shadow-2xl overflow-hidden flex flex-col text-slate-900 dark:text-slate-100 animate-in slide-in-from-bottom-4 duration-300 transition-colors"
        role="dialog"
        aria-modal="true"
      >
        {/* Top Header */}
        <div className="p-4 sm:p-5 border-b border-slate-100 dark:border-slate-800/80 flex items-center justify-between gap-3 bg-gradient-to-r from-amber-500/10 via-transparent to-emerald-500/10">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-2xl bg-amber-500/15 border border-amber-500/30 flex items-center justify-center text-amber-500 shadow-sm">
              <Star className="w-5 h-5 fill-amber-500 text-amber-500" />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] font-extrabold uppercase tracking-wider text-amber-600 dark:text-amber-400">
                  Payment Confirmed!
                </span>
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              </div>
              <h3 className="text-sm sm:text-base font-bold text-slate-900 dark:text-white">
                Rate Your Soundbox App
              </h3>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition"
            aria-label="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-5 space-y-4">
          {hasSubmittedFeedback ? (
            <div className="py-6 text-center space-y-2 animate-in zoom-in-95 duration-200">
              <div className="w-12 h-12 mx-auto rounded-full bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
                <CheckCircle2 className="w-7 h-7" />
              </div>
              <h4 className="text-base font-bold text-slate-900 dark:text-white">
                ధన్యవాదాలు! (Thank you!)
              </h4>
              <p className="text-xs text-slate-600 dark:text-slate-300 max-w-xs mx-auto">
                Opening Google Play Store. Your rating helps keep this UPI Soundbox 100% free for all shopkeepers!
              </p>
            </div>
          ) : (
            <>
              {/* Payment Success highlight */}
              {lastPaymentAmount && (
                <div className="p-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-500/30 flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-4 h-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
                    <span className="text-slate-700 dark:text-slate-300">
                      Successfully announced payment of{' '}
                      <strong className="text-emerald-700 dark:text-emerald-300 font-mono font-bold">
                        ₹{lastPaymentAmount}
                      </strong>
                      {payerName ? ` from ${payerName}` : ''}
                    </span>
                  </div>
                </div>
              )}

              {/* Rationale on why ratings are valuable */}
              <div className="space-y-1.5 text-center sm:text-left">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Why Your Feedback Matters (మీ రేటింగ్ ఎందుకు ముఖ్యం?)
                </h4>
                <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
                  As an independent utility for Telugu business owners, <strong>your 5-star review helps other local shopkeepers discover this free Soundbox</strong> without buying expensive monthly hardware. It also directly guides our next updates (support for more bank SMS formats and voice dialects).
                </p>
              </div>

              {/* Interactive 5-Star Selection */}
              <div className="py-2 flex flex-col items-center justify-center gap-2 bg-slate-50 dark:bg-slate-950/60 rounded-2xl border border-slate-200 dark:border-slate-800 p-4">
                <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Tap stars to rate your experience:
                </span>
                <div className="flex items-center gap-2">
                  {[1, 2, 3, 4, 5].map((star) => {
                    const isFilled = (hoveredStar ?? selectedRating) >= star;
                    return (
                      <button
                        key={star}
                        type="button"
                        onMouseEnter={() => setHoveredStar(star)}
                        onMouseLeave={() => setHoveredStar(null)}
                        onClick={() => {
                          setSelectedRating(star);
                          if (star >= 4) {
                            handleRateOnPlayStore(star);
                          }
                        }}
                        className="p-1 rounded-lg transition-transform hover:scale-125 active:scale-95 focus:outline-none"
                        aria-label={`${star} star`}
                      >
                        <Star
                          className={`w-7 h-7 sm:w-8 sm:h-8 transition-colors ${
                            isFilled
                              ? 'fill-amber-400 text-amber-500 drop-shadow-[0_2px_8px_rgba(251,191,36,0.5)]'
                              : 'text-slate-300 dark:text-slate-700'
                          }`}
                        />
                      </button>
                    );
                  })}
                </div>
                <span className="text-[11px] font-bold text-amber-600 dark:text-amber-400">
                  {selectedRating === 5
                    ? '★★★★★ Outstanding! 5 Stars'
                    : selectedRating === 4
                    ? '★★★★ Very Good! 4 Stars'
                    : `${selectedRating} Stars`}
                </span>
              </div>

              {/* Action Buttons */}
              <div className="space-y-2 pt-1">
                <button
                  type="button"
                  onClick={() => handleRateOnPlayStore(selectedRating)}
                  className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-amber-500 via-emerald-600 to-teal-500 hover:from-amber-400 hover:via-emerald-500 hover:to-teal-400 text-white font-extrabold text-xs shadow-lg shadow-emerald-500/20 active:scale-98 transition flex items-center justify-center gap-2"
                >
                  <ExternalLink className="w-4 h-4" />
                  <span>Rate on Google Play Store</span>
                </button>

                <div className="flex items-center justify-between text-xs pt-1">
                  <button
                    type="button"
                    onClick={handleRemindLater}
                    className="text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 font-medium py-1 px-2 rounded-lg transition"
                  >
                    Remind Me Later
                  </button>

                  <button
                    type="button"
                    onClick={handleDontAskAgain}
                    className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-400 text-[11px] py-1 px-2 rounded-lg transition"
                  >
                    Don't ask again
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
