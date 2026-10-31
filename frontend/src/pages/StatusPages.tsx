import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSearchParams } from 'react-router-dom'
import { Button, Callout, Logo } from '../components/ui'

export function NotFoundPage({ forbidden = false }: { forbidden?: boolean }) {
  return (
    <main className="status-page">
      <Logo />
      <div className="status-card">
        <p className="eyebrow">{forbidden ? '403 · ACCESS RESTRICTED' : '404 · PAGE NOT FOUND'}</p>
        <h1>{forbidden ? 'You don’t have access' : 'We can’t find that page'}</h1>
        <p>{forbidden ? 'Your account does not have permission for this workspace action. If this seems wrong, contact an organization administrator.' : 'The link may be incorrect or the page may have moved.'}</p>
        {forbidden && <Callout title="Access decisions are made by your organization">Your account permissions are verified by the server, not just this interface.</Callout>}
        <Link to="/"><Button>Go to home</Button></Link>
      </div>
    </main>
  )
}

export function RetryNoticePage({ rateLimited = false }: { rateLimited?: boolean }) {
  const [params] = useSearchParams()
  const initial = Number(params.get('retrySeconds'))
  const [seconds, setSeconds] = useState(Number.isFinite(initial) && initial > 0 ? Math.min(initial, 3600) : 0)
  useEffect(() => {
    if (seconds <= 0) return
    const timer = window.setTimeout(() => setSeconds((value) => value - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [seconds])
  return (
    <main className="status-page">
      <Logo />
      <div className="status-card">
        <p className="eyebrow">{rateLimited ? '429 · RATE LIMITED' : '423 · ACCOUNT LOCKED'}</p>
        <h1>{rateLimited ? 'Please wait a moment' : 'Account temporarily locked'}</h1>
        <p>{rateLimited ? 'Too many requests were made in a short time.' : 'Sign-in access is temporarily unavailable for this account.'}</p>
        <Callout title={rateLimited ? 'Rate limit reached' : 'Account locked'}>{seconds > 0 ? `Try again in ${seconds} seconds.` : rateLimited ? 'Wait briefly before retrying. Your account is not being reported as compromised.' : 'Try again after the temporary lock period or contact your organization administrator.'}</Callout>
        <Link to="/login"><Button>Return to sign in</Button></Link>
      </div>
    </main>
  )
}

export function GenericErrorPage() {
  return (
    <main className="status-page">
      <Logo />
      <div className="status-card">
        <p className="eyebrow">REQUEST ERROR</p>
        <h1>We couldn’t complete that request</h1>
        <p>Your previous information has not been changed. Try again or return to your workspace.</p>
        <Callout tone="danger" title="Something went wrong">If the problem continues, contact your organization administrator.</Callout>
        <Link to="/"><Button>Go to home</Button></Link>
      </div>
    </main>
  )
}
