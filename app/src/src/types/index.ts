export type TransactionType = 'CREDIT' | 'DEBIT' | 'UNKNOWN';

export interface ParsedTransaction {
  id: string;
  amount: number;
  currency: string;
  type: TransactionType;
  rawAmount: string;
  bank: string;
  accountMasked: string;
  payerName?: string;
  timestamp: number;
  date: string;
  time: string;
  rawTextMasked: string;
  balanceAmount?: number;
  isUPI?: boolean;
  upiRef?: string;
  announced: boolean;
  reasonNotAnnounced?: string;
}

export type SpeechLanguage = 'te-IN' | 'en-IN';
export type SpeechSpeed = 'slow' | 'normal' | 'fast';
export type AppTheme = 'dark' | 'light' | 'system';

export interface AppSettings {
  voiceAlertsEnabled: boolean;
  language: SpeechLanguage;
  speechSpeed: SpeechSpeed;
  minAmount: number;
  playChime: boolean;
  repeatAnnouncement: boolean;
  theme: AppTheme;
}

export interface VoiceStatus {
  supported: boolean;
  hasTeluguVoice: boolean;
  availableVoices: SpeechSynthesisVoice[];
  activeVoiceName?: string;
}

export interface TestCaseResult {
  id: number;
  name: string;
  input: string;
  expectedType: TransactionType;
  expectedAmount: number | null;
  expectedSpeak: boolean;
  actualType: TransactionType;
  actualAmount: number | null;
  actualSpeak: boolean;
  passed: boolean;
  notes?: string;
}
