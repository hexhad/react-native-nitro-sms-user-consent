import Foundation
import NitroModules

// iOS has no API for apps to read SMS. One-time codes are offered by the system
// QuickType bar on inputs with `textContentType="oneTimeCode"`, so this is a no-op.
class HybridNitroSmsUserConsent: HybridNitroSmsUserConsentSpec {

    var isSupported: Bool { false }

    func startOtpListener(
        handler: @escaping (SmsConsentEvent) -> Void,
        senderPhoneNumber: String?
    ) throws -> Promise<Void> {
        return Promise.resolved()
    }

    func stopOtpListener() throws -> Promise<Void> {
        return Promise.resolved()
    }
}
