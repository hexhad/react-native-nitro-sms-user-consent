# `NitroSmsUserConsent` module

Sources:
- Spec: `src/specs/nitro-sms-user-consent.nitro.ts`
- Android: `android/src/main/java/com/nitrosmsuserconsent/HybridNitroSmsUserConsent.kt`
- iOS: `ios/HybridNitroSmsUserConsent.swift` (no-op)

Use the module directly only outside React components, or when the hook doesn't fit. Prefer `useOtpVerify`.

```ts
import {
  NitroSmsUserConsent,
  extractDigits,
  type SmsConsentEvent,
  type SmsConsentStatus, // 'received' | 'denied' | 'timeout' | 'error'
} from 'react-native-nitro-sms-user-consent'

NitroSmsUserConsent.isSupported // boolean, false on iOS / no Play services

await NitroSmsUserConsent.startOtpListener((event: SmsConsentEvent) => {
  switch (event.status) {
    case 'received': handle(extractDigits(event.message!, 6)); break
    case 'denied':   /* user declined */ break
    case 'timeout':  /* 5 minutes elapsed */ break
    case 'error':    console.warn(event.error); break
  }
}, '+94771234567' /* optional sender filter */)

await NitroSmsUserConsent.stopOtpListener() // idempotent
```

## Contract

- **The handler is called at most once per `startOtpListener` call.** After that the native side cleans itself up.
- **Calling `startOtpListener` again** stops any previous session first.
- **The returned promise rejects** if Play services refuses to start the session (for example, no Play services). It resolves once listening has started, not when the SMS arrives.

## Android internals

These matter when debugging:

- **Broadcast receiver:**
  - It is registered with `ContextCompat.RECEIVER_EXPORTED` plus `SmsRetriever.SEND_PERMISSION`. Exported is required because the broadcast comes from Play services, a different app. The permission means only Play services can deliver to it.
  - The library manifest declares `<queries><package android:name="com.google.android.gms"/></queries>`, so the consent intent can be resolved on Android 11+.
- **Trusted intent check:** the consent intent is launched only if it resolves to `com.google.android.gms` and carries no URI-grant flags (intent-redirection guard). Otherwise the result is `error: "Untrusted consent intent"`.
- **App in the background:** if the SMS arrives while the app is backgrounded, launching is deferred to `onHostResume`. A background start would be blocked by Android and look like a "deny".
- **Context:** it comes from `NitroModules.applicationContext` (a `ReactApplicationContext`), using `currentActivity`, `addActivityEventListener` and `addLifecycleEventListener`.
