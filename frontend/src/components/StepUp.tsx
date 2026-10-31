import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import Camera, { CameraPermissionHelp } from './Camera'
import { captureDescriptor } from '../face/descriptor'
import { sendData } from '../api/requests'
import { getApiError } from '../api/errors'
import { Button, Callout, Dialog, Feedback } from './ui'

type StepUpContextValue = { requestStepUp: (action: string, reason: string) => Promise<string | null> }
type Pending = { action: string; reason: string; resolve: (token: string | null) => void }
const StepUpContext = createContext<StepUpContextValue | null>(null)

export function useStepUp() {
  const value = useContext(StepUpContext)
  if (!value) throw new Error('useStepUp must be used within StepUpProvider')
  return value.requestStepUp
}

export function StepUpProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<Pending | null>(null)
  const requestStepUp = useCallback((action: string, reason: string) => new Promise<string | null>((resolve) => {
    setPending({ action, reason, resolve })
  }), [])
  const finish = (token: string | null) => {
    pending?.resolve(token)
    setPending(null)
  }
  const value = useMemo(() => ({ requestStepUp }), [requestStepUp])
  return (
    <StepUpContext.Provider value={value}>
      {children}
      <StepUpDialog pending={pending} onFinish={finish} />
    </StepUpContext.Provider>
  )
}

function StepUpDialog({ pending, onFinish }: { pending: Pending | null; onFinish: (token: string | null) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [cameraError, setCameraError] = useState('')

  useEffect(() => {
    if (!pending) return
    setError('')
    setCameraError('')
    setBusy(false)
  }, [pending])

  const verify = async () => {
    if (!pending || !videoRef.current) return
    setBusy(true)
    setError('')
    try {
      const challenge = await sendData<{ challengeId: string; nonce: string; action: string }>('/auth/step-up/initiate', { action: pending.action })
      const embedding = await captureDescriptor(videoRef.current)
      const result = await sendData<{ stepUpToken: string }>('/auth/step-up/verify', {
        challengeId: challenge.challengeId,
        action: pending.action,
        nonce: challenge.nonce,
        timestamp: new Date().toISOString(),
        embedding,
      })
      onFinish(result.stepUpToken)
    } catch (reason) {
      setError(getApiError(reason).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={Boolean(pending)} onOpenChange={(open) => { if (!open) onFinish(null) }} title="Confirm it’s you" description={pending?.reason}>
      <div className="dialog-body">
        <Callout title={`Action: ${pending?.action.replaceAll('_', ' ').toLowerCase() ?? 'Sensitive action'}`}>A short-lived token will be used only for this sensitive action.</Callout>
        <Camera ref={videoRef} onError={setCameraError} />
        {cameraError && <><Feedback tone="error" title="Camera unavailable">{cameraError}</Feedback><CameraPermissionHelp /></>}
        {error && <Feedback tone="error" title="Verification could not be completed">{error}</Feedback>}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={() => onFinish(null)}>Cancel</Button>
          <Button loading={busy} onClick={() => void verify()} disabled={Boolean(cameraError)}>Verify and continue</Button>
        </div>
      </div>
    </Dialog>
  )
}
