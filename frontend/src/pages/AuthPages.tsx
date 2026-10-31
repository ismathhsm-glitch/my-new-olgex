import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { passwordRules } from './validation'
import { getApiError, riskReasonText } from '../api/errors'
import { sendData } from '../api/requests'
import type { AuthResult } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { getDeviceHash } from '../auth/deviceHash'
import Camera, { CameraPermissionHelp } from '../components/Camera'
import { Button, Callout, Feedback, Input, Logo } from '../components/ui'
import { captureDescriptor } from '../face/descriptor'

function AuthLayout({ children, title, subtitle }: { children: React.ReactNode; title: string; subtitle: string }) {
  return (
    <main className="auth-page">
      <div className="auth-brand"><Logo /></div>
      <section className="auth-card">
        <h1>{title}</h1>
        <p className="auth-subtitle">{subtitle}</p>
        {children}
      </section>
      <p className="auth-footnote">Secure access to your Olgax workspace</p>
    </main>
  )
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const returnTo = params.get('returnTo')
  const videoRef = useRef<HTMLVideoElement>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [faceChallenge, setFaceChallenge] = useState<AuthResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [cameraError, setCameraError] = useState('')
  const [email, setEmail] = useState(params.get('email') ?? '')
  const [password, setPassword] = useState('')
  const stateMessage = (location.state as { message?: string } | null)?.message

  const finishLogin = (result: AuthResult) => {
    if (!result.accessToken || !result.refreshToken) throw new Error('The sign-in response did not include session tokens.')
    login(result.accessToken, result.refreshToken)
    navigate(returnTo?.startsWith('/') && !returnTo.startsWith('//') ? returnTo : '/', { replace: true })
  }

  const submitLogin = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const deviceHash = await getDeviceHash()
      const result = await sendData<AuthResult>('/auth/login', { email, password, deviceHash })
      if (result.status === 'SUCCESS') finishLogin(result)
      else setFaceChallenge(result)
    } catch (reason) {
      const apiError = getApiError(reason)
      if (apiError.code === 'EMAIL_NOT_VERIFIED') navigate(`/verify-email?email=${encodeURIComponent(email)}`)
      else if (apiError.status === 423 || apiError.code === 'ACCOUNT_LOCKED') navigate(`/locked${apiError.retryAfterSeconds !== undefined ? `?retrySeconds=${apiError.retryAfterSeconds}` : ''}`)
      else if (apiError.status === 429 || apiError.code === 'RATE_LIMITED') navigate(`/rate-limited${apiError.retryAfterSeconds !== undefined ? `?retrySeconds=${apiError.retryAfterSeconds}` : ''}`)
      else if (apiError.code === 'FACE_ENROLLMENT_REQUIRED' || apiError.code === 'FACE_NOT_ENROLLED') setError('Face enrolment is required before this sign-in can continue. Sign in from a trusted device or contact your organization administrator.')
      else setError(apiError.message)
    } finally {
      setBusy(false)
    }
  }

  const verifyFace = async () => {
    if (!faceChallenge?.challengeId || !faceChallenge.challengeNonce || !videoRef.current) return
    setBusy(true)
    setError('')
    try {
      const embedding = await captureDescriptor(videoRef.current)
      const result = await sendData<AuthResult>('/auth/login/face-verify', {
        challengeId: faceChallenge.challengeId,
        nonce: faceChallenge.challengeNonce,
        timestamp: new Date().toISOString(),
        embedding,
      })
      finishLogin(result)
    } catch (reason) {
      const apiError = getApiError(reason)
      if (apiError.status === 429 || apiError.code === 'RATE_LIMITED') navigate('/rate-limited')
      else setError(apiError.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={faceChallenge ? 'Verify your identity' : 'Welcome back'} subtitle={faceChallenge ? 'We need one more check to protect your account.' : 'Sign in to your Olgax Invoice workspace.'}>
      {stateMessage && <Feedback tone="success" title="Notice">{stateMessage}</Feedback>}
      {faceChallenge ? (
        <div className="stack">
          <Callout title="Additional verification requested">Your sign-in needs an extra identity check. This warning asks for review; it does not mean your account was compromised.</Callout>
          {faceChallenge.riskReasons.length > 0 && <ul className="risk-reasons">{faceChallenge.riskReasons.map((reason) => <li key={reason}>{riskReasonText(reason)}</li>)}</ul>}
          <Camera ref={videoRef} onError={setCameraError} />
          {cameraError && <><Feedback tone="error" title="Camera unavailable">{cameraError}</Feedback><CameraPermissionHelp /></>}
          {error && <Feedback tone="error" title="Verification could not be completed">{error}</Feedback>}
          <Button loading={busy} onClick={() => void verifyFace()} disabled={Boolean(cameraError)}>Verify with face</Button>
          <Button variant="secondary" onClick={() => { setFaceChallenge(null); setError('') }}>Back to sign in</Button>
        </div>
      ) : (
        <form className="stack" onSubmit={(event) => void submitLogin(event)}>
          <Input id="email" label="Email address" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
          <div className="field">
            <label htmlFor="password">Password</label>
            <div className="password-input">
              <input id="password" className="input" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />
              <button type="button" className="password-toggle" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button>
            </div>
            <div className="field-trailing"><Link to="/forgot-password">Forgot password?</Link></div>
          </div>
          {error && <Feedback tone="error" title="Sign-in failed">{error}</Feedback>}
          <Button type="submit" loading={busy}>Sign in</Button>
          <p className="auth-switch">New to Olgax? <Link to="/register">Create an account</Link></p>
        </form>
      )}
    </AuthLayout>
  )
}

function PasswordChecklist({ value }: { value: string }) {
  const checks = [
    ['12–72 characters', value.length >= 12 && value.length <= 72],
    ['An uppercase letter', /[A-Z]/.test(value)],
    ['A lowercase letter', /[a-z]/.test(value)],
    ['A number', /\d/.test(value)],
    ['A symbol', /[^A-Za-z0-9\s]/.test(value)],
    ['No whitespace', !/\s/.test(value)],
  ] as const
  return <ul className="password-rules" aria-live="polite">{checks.map(([text, ok]) => <li key={text} className={ok ? 'rule-ok' : ''}><span aria-hidden>{ok ? '✓' : '○'}</span>{text}</li>)}</ul>
}

const registerSchema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name.').max(255),
  email: z.string().email('Enter a valid email address.'),
  password: passwordRules,
  organizationName: z.string().trim().min(1, 'Enter an organization name.').max(255),
})
type RegisterFields = z.infer<typeof registerSchema>

export function RegisterPage() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<RegisterFields>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '', organizationName: '' },
  })
  const password = watch('password')

  const submit = handleSubmit(async (values) => {
    setError('')
    try {
      await sendData('/auth/register', values)
      navigate(`/verify-email?email=${encodeURIComponent(values.email)}`, { state: { message: 'A verification code has been sent to your email.' } })
    } catch (reason) {
      setError(getApiError(reason).message)
    }
  })

  return (
    <AuthLayout title="Create your account" subtitle="Set up your user profile and workspace.">
      <form className="stack" onSubmit={(event) => void submit(event)}>
        <Input id="fullName" label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        <Input id="registerEmail" label="Work email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Input id="organizationName" label="Organization name" autoComplete="organization" error={errors.organizationName?.message} {...register('organizationName')} />
        <Input id="registerPassword" label="Password" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
        <PasswordChecklist value={password} />
        {error && <Feedback tone="error" title="Registration failed">{error}</Feedback>}
        <Button type="submit" loading={isSubmitting}>Create account</Button>
        <p className="auth-switch">Already registered? <Link to="/login">Sign in</Link></p>
      </form>
    </AuthLayout>
  )
}

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const email = params.get('email') ?? ''
  const [digits, setDigits] = useState(Array(6).fill('') as string[])
  const [seconds, setSeconds] = useState(60)
  const [busy, setBusy] = useState(false)
  const [forceResend, setForceResend] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const refs = useRef<Array<HTMLInputElement | null>>([])

  useEffect(() => {
    if (seconds <= 0) return
    const timer = window.setTimeout(() => setSeconds((value) => value - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [seconds])

  const updateDigit = (index: number, value: string) => {
    if (!/^\d?$/.test(value)) return
    setDigits((current) => current.map((digit, position) => position === index ? value : digit))
    if (value && index < 5) refs.current[index + 1]?.focus()
  }

  const handlePaste = (event: React.ClipboardEvent<HTMLInputElement>) => {
    const pasted = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    if (!pasted) return
    event.preventDefault()
    setDigits(Array.from({ length: 6 }, (_, index) => pasted[index] ?? ''))
    refs.current[Math.min(pasted.length, 5)]?.focus()
  }

  const verify = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await sendData('/auth/verify-email', { email, otp: digits.join('') })
      navigate('/login', { state: { message: 'Your email is verified. You can now sign in.' } })
    } catch (reason) {
      const result = getApiError(reason)
      setError(result.message)
      if (result.code === 'OTP_ATTEMPTS_EXCEEDED' || result.code === 'OTP_EXPIRED') setForceResend(true)
    } finally {
      setBusy(false)
    }
  }

  const resend = async () => {
    setError('')
    setBusy(true)
    try {
      await sendData('/auth/resend-verification', { email })
      setSeconds(60)
      setForceResend(false)
      setNotice('If this address can receive a verification code, one has been sent.')
    } catch (reason) {
      setError(getApiError(reason).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Verify your email" subtitle={`Enter the 6-digit code sent to ${email || 'your email address'}.`}>
      {!email && <Callout title="Email address missing">Return to registration and provide an email address to verify.</Callout>}
      <form className="stack" onSubmit={(event) => void verify(event)}>
        <div className="otp-row" role="group" aria-label="Six-digit verification code">
          {digits.map((digit, index) => (
            <input key={index} ref={(element) => { refs.current[index] = element }} className="otp-digit" inputMode="numeric" autoComplete={index === 0 ? 'one-time-code' : 'off'} aria-label={`Code digit ${index + 1}`} maxLength={1} value={digit} onChange={(event) => updateDigit(index, event.target.value)} onKeyDown={(event) => { if (event.key === 'Backspace' && !digit && index > 0) refs.current[index - 1]?.focus() }} onPaste={index === 0 ? handlePaste : undefined} />
          ))}
        </div>
        {notice && <Feedback tone="success" title="Code requested">{notice}</Feedback>}
        {error && <Feedback tone="error" title="Verification failed">{error}</Feedback>}
        <Button type="submit" loading={busy} disabled={!email || digits.join('').length !== 6}>Verify email</Button>
      </form>
      <div className="resend-row">
        <span>{seconds > 0 ? `Resend available in ${seconds}s` : 'Didn’t receive the code?'}</span>
        <Button variant="secondary" disabled={seconds > 0 && !forceResend || !email || busy} onClick={() => void resend()}>Resend code</Button>
      </div>
      <p className="auth-switch"><Link to="/login">Back to sign in</Link></p>
    </AuthLayout>
  )
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [complete, setComplete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await sendData('/auth/forgot-password', { email })
      setComplete(true)
    } catch (reason) {
      setError(getApiError(reason).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthLayout title="Forgot password" subtitle="We’ll send reset instructions if an account matches.">
      {complete ? <Feedback tone="success" title="Check your email">If an account exists for this address, password reset instructions have been sent.</Feedback> : (
        <form className="stack" onSubmit={(event) => void submit(event)}>
          <Input id="forgotEmail" label="Email address" type="email" required autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} />
          {error && <Feedback tone="error" title="Request failed">{error}</Feedback>}
          <Button type="submit" loading={busy}>Send reset instructions</Button>
        </form>
      )}
      <p className="auth-switch"><Link to="/login">Back to sign in</Link></p>
    </AuthLayout>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await sendData('/auth/reset-password', { token, newPassword: password })
      setMessage('Your password has been updated. Sign in with your new password.')
      window.setTimeout(() => navigate('/login', { replace: true, state: { message: 'Your password has been updated.' } }), 1600)
    } catch (reason) {
      setError(getApiError(reason).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthLayout title="Reset password" subtitle="Choose a strong new password for your account.">
      {message ? <Feedback tone="success" title="Password updated">{message}</Feedback> : (
        <form className="stack" onSubmit={(event) => void submit(event)}>
          {!token && <Callout tone="danger" title="Reset link is invalid">The password reset token is missing from this link.</Callout>}
          <Input id="newPassword" label="New password" type="password" required value={password} onChange={(event) => setPassword(event.target.value)} />
          <PasswordChecklist value={password} />
          {error && <Feedback tone="error" title="Could not reset password">{error}</Feedback>}
          <Button type="submit" loading={busy} disabled={!token}>Set new password</Button>
        </form>
      )}
    </AuthLayout>
  )
}

export function AcceptInvitationPage() {
  const [params] = useSearchParams()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const token = params.get('token') ?? ''
  const accept = async () => {
    if (!token) return setError('The invitation token is missing from this link.')
    setBusy(true)
    setError('')
    try {
      await sendData('/organizations/invitations/accept', { token })
      setMessage('Your invitation has been accepted. Your workspace is ready.')
    } catch (reason) {
      setError(getApiError(reason).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthLayout title="Accept invitation" subtitle="Join your organization workspace.">
      {message ? <Feedback tone="success" title="Invitation accepted">{message}</Feedback> : (
        <div className="stack">
          {!isAuthenticated && <Callout title="Sign in required">Sign in to the invited account before accepting this invitation.</Callout>}
          {error && <Feedback tone="error" title="Could not accept invitation">{error}</Feedback>}
          {isAuthenticated ? <Button loading={busy} disabled={!token} onClick={() => void accept()}>Accept invitation</Button> : <Button onClick={() => navigate(`/login?returnTo=${encodeURIComponent(`/accept-invitation?token=${token}`)}`)}>Sign in to accept</Button>}
        </div>
      )}
    </AuthLayout>
  )
}

export function AccessNoticePage({ rateLimited = false }: { rateLimited?: boolean }) {
  return (
    <AuthLayout title={rateLimited ? 'Please wait a moment' : 'Account temporarily locked'} subtitle={rateLimited ? 'Too many requests were made in a short time.' : 'Sign-in access is temporarily unavailable for this account.'}>
      <Callout tone="warning" title={rateLimited ? 'Rate limit reached' : 'Account locked'}>{rateLimited ? 'Wait briefly before retrying. Your account is not being reported as compromised.' : 'Try again after the temporary lock period or contact your organization administrator.'}</Callout>
      <p className="auth-switch"><Link to="/login">Return to sign in</Link></p>
    </AuthLayout>
  )
}
