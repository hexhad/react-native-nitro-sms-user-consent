import type { HybridObject } from 'react-native-nitro-modules'

/**
 * Outcome of one SMS User Consent session.
 * - `received`: the user allowed reading the SMS — `message` holds its full text
 * - `denied`: the user dismissed or denied the consent sheet
 * - `timeout`: no matching SMS arrived within the 5-minute window
 * - `error`: the consent sheet could not be shown — see `error`
 */
export type SmsConsentStatus = 'received' | 'denied' | 'timeout' | 'error'

export interface SmsConsentEvent {
  status: SmsConsentStatus
  message?: string
  error?: string
}

export interface NitroSmsUserConsent extends HybridObject<{
  ios: 'swift'
  android: 'kotlin'
}> {
  /** `false` on iOS, or on Android devices without Google Play services. */
  readonly isSupported: boolean
  /**
   * Starts a one-shot SMS User Consent session (Android). `handler` is called
   * at most once per session. Pass `senderPhoneNumber` to only accept SMS from
   * that sender. No-op on iOS.
   */
  startOtpListener(
    handler: (event: SmsConsentEvent) => void,
    senderPhoneNumber?: string
  ): Promise<void>
  stopOtpListener(): Promise<void>
}
