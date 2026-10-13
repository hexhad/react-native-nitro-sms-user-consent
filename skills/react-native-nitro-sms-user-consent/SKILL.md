---
name: react-native-nitro-sms-user-consent
description: One-tap SMS OTP reading for React Native via Google's SMS User Consent API, built on Nitro Modules — react-native-nitro-sms-user-consent. Covers the useOtpVerify hook, the NitroSmsUserConsent module, SMS format requirements, iOS oneTimeCode autofill, migrating from react-native-nitro-otp-verify (SMS Retriever / app hash), and troubleshooting a consent sheet that never appears. Use when implementing OTP / verification-code auto-fill, phone number verification, or SMS code reading in a React Native app.
license: MIT
metadata:
  author: hexhad
  scope: react-native-nitro-sms-user-consent
  tags: react-native, nitro, otp, sms, verification-code, sms-user-consent, sms-retriever, autofill, android, ios, oneTimeCode
---

# react-native-nitro-sms-user-consent

A reference for AI coding assistants working in a project that uses `react-native-nitro-sms-user-consent`. Answer with the real APIs below, not invented ones. Load the matching `references/*.md` file before writing code.

## Mental model

- **Android:** Google Play services watches incoming SMS for 5 minutes after `startSmsUserConsent`. When an SMS with a code arrives, it shows a bottom sheet: *"Allow &lt;App&gt; to read the message below and enter the code?"*. On **Allow**, the app receives the full SMS text.
  - No `READ_SMS`/`RECEIVE_SMS` permissions are needed.
  - The SMS template needs no app hash.
- **One message per session.** After allow, deny or timeout the session ends. Call `startListener()` again for "Resend code".
- **iOS:** apps cannot read SMS at all, so the module is a **no-op** (`isSupported === false`). iOS autofill comes from the app's own input having `textContentType="oneTimeCode"`.
- **The library ships no UI and no TextInput.** The app renders its own OTP field and feeds `otp` into it.

## Routing table: problem to reference

| User is asking about… | Read |
|---|---|
| Adding OTP auto-read to a screen, `useOtpVerify` options and return values, retry after deny/timeout, resend code, custom code extraction, `senderPhoneNumber` | [`references/hook-usage.md`](./references/hook-usage.md) |
| Calling the native module directly (`NitroSmsUserConsent.startOtpListener`, `SmsConsentEvent`), using it outside React components | [`references/module-api.md`](./references/module-api.md) |
| What the SMS must look like, backend template, iOS autofill, `oneTimeCode`, domain-bound codes | [`references/sms-format-and-ios.md`](./references/sms-format-and-ios.md) |
| Replacing `react-native-nitro-otp-verify`, removing the app hash, SMS Retriever vs User Consent | [`references/migrate-from-otp-verify.md`](./references/migrate-from-otp-verify.md) |
| The sheet never appears, `denied` right away, Play services errors, testing on an emulator | [`references/troubleshooting.md`](./references/troubleshooting.md) |

If nothing matches, start with [`references/hook-usage.md`](./references/hook-usage.md).

## Installation

```bash
npm install react-native-nitro-sms-user-consent react-native-nitro-modules
# or
bun add react-native-nitro-sms-user-consent react-native-nitro-modules
```

Then rebuild natively: `pod install` for iOS and a fresh Gradle build for Android. A JS reload is not enough.

Requirements:
- React Native New Architecture, 0.76+
- `react-native-nitro-modules` 0.35+
- Android API 23+ with Google Play services

## Minimal example

```tsx
import { useEffect } from 'react'
import { useOtpVerify } from 'react-native-nitro-sms-user-consent'

function VerifyScreen() {
  const { otp, denied, timeoutError, startListener } = useOtpVerify({ numberOfDigits: 6 })

  useEffect(() => {
    if (otp) setCode(otp) // feed the app's own OTP input / submit
  }, [otp])

  // denied || timeoutError → show "Try again" that calls startListener()
}
```

## Rules for assistants

- Never add a TextInput or OTP component to the library. Use the app's existing input.
- Don't tell users to add SMS permissions or an app hash. User Consent needs neither.
- Don't call `startListener()` on every render or on every input change. It is idempotent while listening, but each successful session shows the consent sheet once.
- Start listening **before** the backend sends the SMS: on mount, or just before the "Send code" request.
