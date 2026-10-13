import { useCallback, useEffect, useRef, useState } from 'react'
import { Platform } from 'react-native'
import { NitroSmsUserConsent } from './NitroSmsUserConsent'
import type { SmsConsentEvent } from './specs/nitro-sms-user-consent.nitro'

// Native sends TIMEOUT after 5 minutes; this is only a backstop
const BACKSTOP_TIMEOUT_MS = 5 * 60 * 1000 + 10 * 1000

export interface UseOtpVerifyOptions {
  /** Length of the code to extract from the SMS. Defaults to 6. */
  numberOfDigits?: number
  /** Only accept SMS from this sender (Android). */
  senderPhoneNumber?: string
  /** Start listening on mount. Defaults to `true`. */
  autoStart?: boolean
  /** Custom code extraction from the full SMS text. */
  extractOtp?: (message: string) => string | null
}

export interface UseOtpVerifyResult {
  otp: string | null
  message: string | null
  /** @deprecated SMS User Consent needs no app hash. Always `null`. */
  hash: string[] | null
  timeoutError: boolean
  /** The user denied the consent sheet. Call `startListener()` to retry. */
  denied: boolean
  error: string | null
  isSupported: boolean
  isListening: boolean
  startListener: () => void
  stopListener: () => void
}

export function extractDigits(message: string, numberOfDigits: number) {
  const match = message.match(
    new RegExp(`(?:^|\\D)(\\d{${numberOfDigits}})(?!\\d)`)
  )
  return match?.[1] ?? null
}

let supportedCache: boolean | undefined
function getIsSupported() {
  if (supportedCache === undefined) {
    supportedCache =
      Platform.OS === 'android' && NitroSmsUserConsent.isSupported
  }
  return supportedCache
}

export function useOtpVerify({
  numberOfDigits = 6,
  senderPhoneNumber,
  autoStart = true,
  extractOtp,
}: UseOtpVerifyOptions = {}): UseOtpVerifyResult {
  const [otp, setOtp] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [timeoutError, setTimeoutError] = useState(false)
  const [denied, setDenied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSupported] = useState(getIsSupported)
  const [isListening, setIsListening] = useState(false)

  // Options in a ref so inline callbacks don't restart the listener
  const optionsRef = useRef({ numberOfDigits, senderPhoneNumber, extractOtp })
  optionsRef.current = { numberOfDigits, senderPhoneNumber, extractOtp }

  // Increments per session so late events from a stopped session are ignored
  const sessionRef = useRef(0)
  const isListeningRef = useRef(false)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearBackstop = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
  }

  const stopListener = useCallback(() => {
    if (!isListeningRef.current) return
    isListeningRef.current = false
    setIsListening(false)
    sessionRef.current++
    clearBackstop()
    NitroSmsUserConsent.stopOtpListener().catch(() => {})
  }, [])

  const startListener = useCallback(() => {
    if (Platform.OS !== 'android') return
    if (isListeningRef.current) return
    if (!getIsSupported()) {
      setError('Google Play services is not available')
      return
    }

    const session = ++sessionRef.current
    isListeningRef.current = true
    setIsListening(true)
    setOtp(null)
    setMessage(null)
    setTimeoutError(false)
    setDenied(false)
    setError(null)

    const endSession = () => {
      isListeningRef.current = false
      setIsListening(false)
      clearBackstop()
    }

    const onEvent = (event: SmsConsentEvent) => {
      if (session !== sessionRef.current) return
      endSession()
      switch (event.status) {
        case 'received': {
          const sms = event.message ?? ''
          const { numberOfDigits: digits, extractOtp: extract } =
            optionsRef.current
          setMessage(sms)
          setOtp(extract ? extract(sms) : extractDigits(sms, digits))
          break
        }
        case 'denied':
          setDenied(true)
          break
        case 'timeout':
          setTimeoutError(true)
          break
        case 'error':
          setError(event.error ?? 'Unknown error')
          break
      }
    }

    NitroSmsUserConsent.startOtpListener(
      onEvent,
      optionsRef.current.senderPhoneNumber
    ).catch((err: Error) => {
      if (session !== sessionRef.current) return
      endSession()
      setError(err.message)
    })

    timeoutRef.current = setTimeout(() => {
      if (session !== sessionRef.current || !isListeningRef.current) return
      setTimeoutError(true)
      stopListener()
    }, BACKSTOP_TIMEOUT_MS)
  }, [stopListener])

  useEffect(() => {
    if (autoStart) startListener()
    return () => stopListener()
  }, [autoStart, startListener, stopListener])

  return {
    otp,
    message,
    hash: null,
    timeoutError,
    denied,
    error,
    isSupported,
    isListening,
    startListener,
    stopListener,
  }
}
