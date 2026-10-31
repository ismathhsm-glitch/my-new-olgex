export type Envelope<T> = { success: boolean; data: T | null; error: ApiError | null }
export type ApiError = { code: string; message: string }

export type AuthResult = {
  status: 'SUCCESS' | 'FACE_VERIFICATION_REQUIRED'
  accessToken?: string
  refreshToken?: string
  tokenType?: string
  expiresInSeconds?: number
  challengeId?: string
  challengeNonce?: string
  riskScore?: number
  riskReasons: string[]
}

export type Profile = {
  id: number
  email: string
  fullName: string
  status: string
  emailVerified: boolean
  faceEnrolled: boolean
}

export type FaceStatus = { enrolled: boolean; samplesStored: number }
export type Device = {
  id: number
  deviceName: string
  maskedIp: string | null
  trusted: boolean
  firstSeen: string
  lastSeen: string
}
export type Organization = {
  id: number
  name: string
  logoUrl: string | null
  currency: string
  taxSettings: string | null
  maskedBankDetails: string | null
}
export type OrgRole = 'ORG_ADMIN' | 'ACCOUNTANT' | 'STAFF' | 'VIEWER' | 'SUPER_ADMIN'
export type Member = {
  userId: number
  email: string
  fullName: string
  role: OrgRole
  status: string
  invitedAt: string | null
}
export type AuditEntry = {
  id: number
  actorId: number
  action: string
  entityType: string | null
  entityId: string | null
  ipAddress: string | null
  device: string | null
  location: string | null
  riskScore: number | null
  verificationResult: string | null
  details: string | null
  timestamp: string
}
export type AuditPage = { content: AuditEntry[]; page: number; size: number; totalElements: number }
export type RiskyLogin = {
  email: string
  ipAddress: string | null
  riskScore: number
  riskReasons: string
  outcome: string
  timestamp: string
}
export type DashboardSummary = {
  loginsPerDay: { day: string; total: number; failed: number }[]
  failedLogins: number
  faceVerificationSuccessRate: number
  highRiskLogins: number
  stepUpCount: number
  topRiskyUsers: { userId: number; email: string; failedAttempts: number; maxRiskScore: number }[]
  topRiskyDevices: { deviceReference: string; attempts: number; maxRiskScore: number }[]
}

export function unwrap<T>(envelope: Envelope<T>): T {
  if (!envelope.success || envelope.data === null) {
    throw new Error(envelope.error?.code ?? 'UNKNOWN_ERROR')
  }
  return envelope.data
}
