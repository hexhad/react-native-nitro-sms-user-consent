# `useOtpVerify` hook

Source: `src/useOtpVerify.ts`

```ts
import { useOtpVerify } from 'react-native-nitro-sms-user-consent'

const {
  otp,          // string | null   extracted code (null until received, or if extraction failed)
  message,      // string | null   full SMS text the user approved
  denied,       // boolean         user tapped "Deny" / dismissed the sheet
  timeoutError, // boolean         no matching SMS within 5 minutes
  error,        // string | null   start failed, no Play services, untrusted intent, etc.
  isSupported,  // boolean         false on iOS or without Google Play services
  isListening,  // boolean         a session is active
  startListener,// () => void      start a new session (no-op if already listening / iOS)
  stopListener, // () => void      end the session (safe to call any time)
  hash,         // null            deprecated, kept for otp-verify compatibility
} = useOtpVerify({
  numberOfDigits: 6,           // default 6, length of the code to extract
  senderPhoneNumber: undefined,// only accept SMS from this number (optional)
  autoStart: true,             // default true, start on mount, stop on unmount
  extractOtp: undefined,       // (message: string) => string | null, custom extraction
})
```

## Behaviour

- **Starting a session:**
  - With `autoStart` (the default), a session starts on mount and stops on unmount.
  - Calling `startListener()` resets `otp`, `message`, `denied`, `timeoutError` and `error`.
- **Ending a session:** every session ends after exactly one outcome:
  - `otp` and `message` are set (received);
  - `denied` is true;
  - `timeoutError` is true;
  - `error` is set.
- **Default extraction:** the first run of exactly `numberOfDigits` digits that has no digit on either side. For "Code 482913, valid until 2026" it returns `482913` when `numberOfDigits: 6`.
- **Inline options:** options are stored in a ref, so passing an inline `extractOtp` does not restart the listener.
- **Late results are ignored:** a result from a session you already stopped is dropped.
- **iOS:** everything is a no-op, `isSupported` is `false`, and no error is set.

## Patterns

**Wire into the app's own input**

```tsx
const [code, setCode] = useState('')
const { otp } = useOtpVerify({ numberOfDigits: 6 })
useEffect(() => { if (otp) { setCode(otp); verify(otp) } }, [otp])
// <ExistingOtpInput value={code} onChangeText={setCode} />
```

**Start just before requesting the SMS (recommended for "Send code" flows)**

```tsx
const { startListener, stopListener } = useOtpVerify({ autoStart: false })
const sendCode = async () => {
  startListener()            // listen first
  await api.requestOtp(phone)
}
```

**Resend / retry**

```tsx
const { denied, timeoutError, startListener } = useOtpVerify()
const onResend = async () => {
  startListener()            // a new session is needed for every SMS
  await api.requestOtp(phone)
}
```

**Alphanumeric or custom codes**

```tsx
useOtpVerify({ extractOtp: (sms) => sms.match(/\b([A-Z0-9]{8})\b/)?.[1] ?? null })
```

**Restrict the sender**

```tsx
useOtpVerify({ senderPhoneNumber: '+94771234567' })
```

The sender filter is read when a session starts, so changing it only takes effect on the next `startListener()`.
