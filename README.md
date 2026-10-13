# react-native-nitro-sms-user-consent

> One-tap OTP reading on Android with Google's [SMS User Consent API](https://developers.google.com/identity/sms-retriever/user-consent/request), built on [Nitro Modules](https://nitro.margelo.com). No app hash, no SMS permissions.

[![Version](https://img.shields.io/npm/v/react-native-nitro-sms-user-consent.svg)](https://www.npmjs.com/package/react-native-nitro-sms-user-consent)
[![Downloads](https://img.shields.io/npm/dm/react-native-nitro-sms-user-consent.svg)](https://www.npmjs.com/package/react-native-nitro-sms-user-consent)
[![License](https://img.shields.io/npm/l/react-native-nitro-sms-user-consent.svg)](https://github.com/hexhad/react-native-nitro-sms-user-consent/blob/main/LICENSE)

---

## Features

- 📩 Reads the OTP SMS after a one-tap user consent on Android
- 🔓 No app hash in the SMS template, and no `READ_SMS`/`RECEIVE_SMS` permissions
- ⚡ Built with Nitro Modules: no bridge overhead
- 🪝 Drop-in `useOtpVerify` hook (same shape as `react-native-nitro-otp-verify`)
- ⏱️ Reports allow, deny, timeout and error outcomes
- 🍎 iOS-safe: no-op on iOS, where the system autofill handles OTPs

---

## Requirements

|                              | Minimum                  |
| ---------------------------- | ------------------------ |
| React Native                 | 0.76.0 (New Architecture) |
| Android                      | API 23+ with Google Play services |
| `react-native-nitro-modules` | 0.35.0+                  |

---

## Installation

```sh
npm install react-native-nitro-sms-user-consent react-native-nitro-modules
# or
bun add react-native-nitro-sms-user-consent react-native-nitro-modules
```

No extra setup is needed. The Play services dependency is included automatically.

---

## Usage

```tsx
import { useOtpVerify } from 'react-native-nitro-sms-user-consent'

export default function OtpScreen() {
  const { otp, denied, timeoutError, startListener } = useOtpVerify({
    numberOfDigits: 6,
  })

  useEffect(() => {
    if (otp) submitCode(otp)
  }, [otp])

  // Render your own OTP input. Pass `otp` into it when it arrives.
  // Show a "Try again" button that calls startListener() when `denied`/`timeoutError`.
}
```

### How it works

1. On mount, the hook starts a 5-minute SMS User Consent session.
2. When an SMS with a code arrives, Google Play services shows a bottom sheet: _"Allow &lt;App&gt; to read the message below and enter the code?"_
3. If the user taps **Allow**, the full SMS text is returned and the code is extracted into `otp`.
4. **One message per session.** After allow, deny or timeout the session ends. Call `startListener()` to listen again.

If the SMS arrives while the app is in the background, the consent sheet opens when the user returns to the app.

### Hook API

```ts
const {
  otp,          // string | null: extracted code
  message,      // string | null: full SMS text
  denied,       // boolean: user tapped "Deny"
  timeoutError, // boolean: no SMS within 5 minutes
  error,        // string | null: e.g. no Play services
  isSupported,  // boolean: false on iOS / without Play services
  isListening,  // boolean
  startListener,
  stopListener,
  hash,         // always null: kept for migration, deprecated
} = useOtpVerify({
  numberOfDigits: 6,           // length of the code to extract (default 6)
  senderPhoneNumber: '+94...', // only accept SMS from this sender (optional)
  autoStart: true,             // start on mount (default true)
  extractOtp: (sms) => ...,    // custom extraction (optional)
})
```

### Module API

```ts
import { NitroSmsUserConsent } from 'react-native-nitro-sms-user-consent'

NitroSmsUserConsent.isSupported // boolean

await NitroSmsUserConsent.startOtpListener((event) => {
  // event.status: 'received' | 'denied' | 'timeout' | 'error'
  // event.message: full SMS text when 'received'
  // event.error: reason when 'error'
}, senderPhoneNumber /* optional */)

await NitroSmsUserConsent.stopOtpListener() // safe to call any time
```

---

## SMS requirements

The consent sheet is shown only if the SMS:

- contains a **4–10 character alphanumeric code with at least one digit**;
- comes from a sender **not in the user's contacts**;
- matches `senderPhoneNumber`, if you set it.

```
Your verification code is 123456
```

No app hash is needed.

---

## iOS

iOS has no API for apps to read SMS. The system detects the code itself and offers it above the keyboard. Enable it on your own OTP input:

```tsx
<TextInput
  textContentType="oneTimeCode"
  autoComplete={Platform.OS === 'android' ? 'sms-otp' : 'one-time-code'}
  keyboardType="number-pad"
/>
```

Optionally end the SMS with a [domain-bound code](https://developer.apple.com/news/?id=z0i801mg) line so iOS only offers it on your domain:

```
Your verification code is 123456

@yourdomain.com #123456
```

On iOS the hook is a no-op (`isSupported: false`), so the same screen code runs on both platforms.

---

## Migrating from `react-native-nitro-otp-verify`

1. Swap the package and the import:
   ```diff
   - import { useOtpVerify } from 'react-native-nitro-otp-verify'
   + import { useOtpVerify } from 'react-native-nitro-sms-user-consent'
   ```
2. Remove the 11-character app hash from your SMS template. `hash` is now always `null`.
3. Handle `denied`: the user can decline the consent sheet. Offer a retry with `startListener()`.
4. Remember that each session reads one message. Call `startListener()` again for "Resend code".

---

## Permissions

None. Google Play services reads the SMS, and the app only receives the message the user approves. The library's broadcast receiver is protected by `com.google.android.gms.auth.api.phone.permission.SEND`, so only Play services can deliver to it.

---

## Platform Support

| Platform | Status                                         |
| -------- | ---------------------------------------------- |
| Android  | ✅ SMS User Consent API                        |
| iOS      | ✅ Safe no-op; use `textContentType="oneTimeCode"` |

---

## AI agent skill

[`skills/react-native-nitro-sms-user-consent`](./skills/react-native-nitro-sms-user-consent/SKILL.md) is an agent skill (Claude Code and other `SKILL.md`-compatible agents). It covers the hook, the SMS format, iOS autofill, migrating from otp-verify, and troubleshooting. To install it, copy or symlink the folder into `~/.claude/skills/`:

```sh
npx degit hexhad/react-native-nitro-sms-user-consent/skills/react-native-nitro-sms-user-consent ~/.claude/skills/react-native-nitro-sms-user-consent
```

---

## Credits

Bootstrapped with [create-nitro-module](https://github.com/patrickkabwe/create-nitro-module).

---

## License

MIT
