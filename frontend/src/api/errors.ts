import axios from 'axios'

const friendly: Record<string, string> = {
  EMAIL_NOT_VERIFIED: 'Verify your email address before signing in.',
  FACE_ENROLLMENT_REQUIRED: 'Face enrolment is required before this sign-in can continue.',
  FACE_NOT_ENROLLED: 'Enrol your face before using face verification.',
  ACCOUNT_LOCKED: 'This account is temporarily locked. Try again after the lock period ends.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment before trying again.',
  INVALID_OTP: 'That code is not correct. Check the email and try again.',
  OTP_EXPIRED: 'That code has expired. Request a new verification code.',
  OTP_ATTEMPTS_EXCEEDED: 'Too many incorrect codes. Request a new code to continue.',
  FACE_MODELS_UNAVAILABLE: 'Face verification assets are missing. Run npm run models:copy, then reload the page.',
  FACE_API_RUNTIME_UNAVAILABLE: 'The face verification runtime could not be loaded. Run npm run models:copy and try again.',
  NO_FACE: 'No face was detected. Center your face in the frame and improve the lighting.',
  MULTIPLE_FACES: 'More than one face was detected. Make sure only you are visible in the camera.',
  INVALID_DESCRIPTOR: 'A valid face sample could not be created. Adjust your position and try again.',
  CHALLENGE_EXPIRED: 'This verification challenge expired. Return to the previous step and request a new one.',
  FACE_MISMATCH: 'The face sample did not match this account. Try again in good lighting.',
  FORBIDDEN: 'You do not have permission to perform this action.',
  NOT_FOUND: 'The requested item could not be found.',
  CONFLICT: 'This change conflicts with the current account state.',
  VALIDATION_ERROR: 'Check the information entered and try again.',
  INVALID_CREDENTIALS: 'The email or password is incorrect.',
}

export function getApiError(error: unknown): { code: string; message: string; status?: number; retryAfterSeconds?: number } {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: { code?: string; message?: string }; code?: string } | undefined
    const code = body?.error?.code ?? body?.code ?? `HTTP_${error.response?.status ?? 'ERROR'}`
    const retryHeader = error.response?.headers?.['retry-after']
    const retryValue = Array.isArray(retryHeader) ? retryHeader[0] : retryHeader
    const retryAfterSeconds = retryValue ? Number.isFinite(Number(retryValue))
      ? Math.max(0, Number(retryValue))
      : Math.max(0, Math.ceil((new Date(String(retryValue)).getTime() - Date.now()) / 1000)) : undefined
    return {
      code,
      message: friendly[code] ?? body?.error?.message ?? 'We could not complete that request. Please try again.',
      status: error.response?.status,
      retryAfterSeconds: Number.isFinite(retryAfterSeconds) ? retryAfterSeconds : undefined,
    }
  }
  const code = error instanceof Error ? error.message : 'UNKNOWN_ERROR'
  return { code, message: friendly[code] ?? 'We could not complete that request. Please try again.' }
}

export function riskReasonText(reason: string): string {
  const labels: Record<string, string> = {
    UNRECOGNIZED_DEVICE: 'This device has not been recognized before.',
    UNRECOGNIZED_IP: 'This network location is new for your account.',
    HIGH_RISK_SCORE: 'This sign-in needs an additional identity check.',
    IMPOSSIBLE_TRAVEL: 'The sign-in location changed unusually quickly.',
  }
  return labels[reason] ?? 'An additional identity check is needed for this sign-in.'
}
