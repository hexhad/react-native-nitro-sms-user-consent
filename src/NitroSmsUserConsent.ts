import { NitroModules } from 'react-native-nitro-modules'
import type { NitroSmsUserConsent as NitroSmsUserConsentSpec } from './specs/nitro-sms-user-consent.nitro'

export const NitroSmsUserConsent =
  NitroModules.createHybridObject<NitroSmsUserConsentSpec>('NitroSmsUserConsent')
