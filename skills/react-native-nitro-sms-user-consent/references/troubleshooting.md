# Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Sheet never appears | Check the SMS format first: a 4–10 character code with a digit ([sms-format-and-ios.md](./sms-format-and-ios.md)).<br>The sender may be **in the user's contacts**.<br>The SMS may have arrived **before** `startListener()` or **more than 5 minutes** after it.<br>`senderPhoneNumber` may not match.<br>The previous session may already be finished; call `startListener()` again. |
| `isSupported === false` on Android | The device or emulator has no Google Play services. Use an emulator image with Google APIs or Google Play. |
| `error` set right after start | `startSmsUserConsent` was rejected, for example because Play services is outdated or missing. The message comes from Play services. |
| `error: "Untrusted consent intent"` | The consent intent did not resolve to `com.google.android.gms`, or it carried URI-grant flags. Check that the app's manifest merge kept the library's `<queries>` entry for `com.google.android.gms`. |
| `denied` without the user seeing a sheet | Fixed in 0.1.0 (launches made from the background are deferred to resume). If it reappears, check logcat for `Background activity launch blocked`. |
| Nothing on iOS | Expected: iOS can't read SMS. Use `textContentType="oneTimeCode"` on the input. |
| Kotlin error "binary version of its metadata is 2.3.0, expected 2.1.0" | The app forces a newer `play-services-auth-api-phone` (18.3.x is built with Kotlin 2.3). Keep 18.2.0, or upgrade the app's Kotlin. |
| Hook keeps restarting | Make sure `autoStart` isn't toggling. Inline `extractOtp` / options don't restart it. |

## Testing on an Android emulator

Use an emulator image with Google Play services.

```sh
# start listening in the app, then:
adb -s emulator-5554 emu sms send 5551234 "Your verification code is 482913"
```

The consent sheet appears within a few seconds. Exercise all four paths:
- Allow;
- Deny;
- background the app before sending, then reopen it (the sheet should appear on resume);
- wait 5 minutes (`timeoutError`).

The example app in `example/` shows all of these states as text.
