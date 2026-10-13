# SMS format and iOS autofill

## Android: when the consent sheet is shown

Google Play services shows the sheet only if the SMS:

1. arrives within **5 minutes** of `startListener()` / `startOtpListener()`;
2. contains a **4–10 character alphanumeric string with at least one digit**;
3. comes from a sender who is **not in the user's contacts**;
4. comes from `senderPhoneNumber`, if one was passed.

Recommended backend template (it also works with iOS autofill):

```
Your <App> verification code is 123456

@app.example.com #123456
```

- **No app hash.** That was only for the SMS Retriever API, which `react-native-nitro-otp-verify` used.
- **Optional last line:** `@domain #code` is Apple's domain-bound code format. It is harmless on Android.

## iOS

iOS has no API for apps to read SMS. The library is a no-op there. To get the system's "From Messages: 123456" suggestion above the keyboard, set these props on the **app's own** input:

```tsx
<TextInput
  textContentType="oneTimeCode"
  autoComplete={Platform.OS === 'android' ? 'sms-otp' : 'one-time-code'}
  keyboardType="number-pad"
/>
```

- **Platform handling:** iOS reads `textContentType="oneTimeCode"`. `autoComplete="sms-otp"` adds Android autofill suggestions alongside User Consent.
- **Multi-box inputs:** for an input made of several boxes, put these props on the single hidden or real `TextInput` that receives focus, not on each box.
- **Domain-bound codes:** when the SMS ends with `@your-domain #code` and the app has Associated Domains for that domain, iOS offers the code only in your app or site.
- **Testing:** autofill can't be tested in the iOS Simulator. Use a physical iPhone and a real SMS.

The same screen code works on both platforms. The hook does nothing on iOS, and the input's autofill does the job.
