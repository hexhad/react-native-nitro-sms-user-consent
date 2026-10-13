# Migrating from `react-native-nitro-otp-verify`

`react-native-nitro-otp-verify` uses the **SMS Retriever API**: no user prompt, but every SMS must end with the app's 11-character hash, and that hash differs between debug, release and Play App Signing builds.

`react-native-nitro-sms-user-consent` uses the **SMS User Consent API**: one tap on "Allow", and no hash.

## Steps

1. **Swap the package:**
   ```sh
   bun remove react-native-nitro-otp-verify
   bun add react-native-nitro-sms-user-consent
   ```
   Rebuild natively (`pod install` and a Gradle build).
2. **Change the import.** The hook name and its existing fields are the same:
   ```diff
   - import { useOtpVerify } from 'react-native-nitro-otp-verify'
   + import { useOtpVerify } from 'react-native-nitro-sms-user-consent'
   ```
   If the app imported the module object directly, rename it:
   ```diff
   - import { NitroOtpVerify } from 'react-native-nitro-otp-verify'
   + import { NitroSmsUserConsent } from 'react-native-nitro-sms-user-consent'
   ```
   The `startOtpListener` callback now receives a `SmsConsentEvent` (`{ status, message?, error? }`) instead of a plain string.
3. **Remove the app hash:**
   - Delete code that reads `hash` or calls `getHash()` / `requestHint()`. Neither exists any more, and `hash` is always `null`.
   - Ask the backend to drop the hash line from the SMS template.
4. **Handle the new outcomes:** `denied` (the user declined) and `error`. Show a retry that calls `startListener()`.
5. **Resend:** call `startListener()` before each resend. A session reads exactly one SMS.

## API mapping

| otp-verify | sms-user-consent |
|---|---|
| `useOtpVerify({ numberOfDigits })` | same, plus `senderPhoneNumber`, `autoStart`, `extractOtp` |
| `otp`, `message`, `timeoutError`, `startListener`, `stopListener` | same |
| `hash` | always `null` (deprecated) |
| (none) | `denied`, `error`, `isSupported`, `isListening` |
| `NitroOtpVerify.getHash()` / `requestHint()` | removed |
| `startOtpListener((sms: string) => …)` | `startOtpListener((e: SmsConsentEvent) => …, sender?)` |

## Behaviour differences

- **Consent sheet:** the user now sees a consent sheet. It is one tap, and the result is reported as `denied` if the user declines.
- **Code extraction:** it now looks for a number with exactly `numberOfDigits` digits. The old hook took the first 4–8 digit number and then checked its length, so an earlier number like "2026" broke it.
- **Senders in contacts:** SMS from a sender in the user's contacts never triggers the sheet.
