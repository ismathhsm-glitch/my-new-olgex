import { Camera as CameraIcon } from 'lucide-react'
import { forwardRef, useEffect, useState } from 'react'
import { Button, Dialog } from './ui'

const Camera = forwardRef<HTMLVideoElement, { onError?: (message: string) => void; className?: string }>(function Camera({ onError, className = '' }, ref) {
  const [video, setVideo] = useState<HTMLVideoElement | null>(null)

  useEffect(() => {
    if (!video) return
    let stream: MediaStream | null = null
    let active = true
    if (!navigator.mediaDevices?.getUserMedia) {
      onError?.('This browser does not support camera access. Use an up-to-date browser on HTTPS or localhost.')
      return
    }
    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
      .then((result) => {
        if (!active) {
          result.getTracks().forEach((track) => track.stop())
          return
        }
        stream = result
        video.srcObject = result
        return video.play()
      })
      .catch((error: unknown) => {
        const denied = error instanceof DOMException && error.name === 'NotAllowedError'
        onError?.(denied ? 'Camera access is blocked. Allow camera access in your browser settings, then try again.' : 'No camera could be started. Connect a camera and try again.')
      })
    return () => {
      active = false
      stream?.getTracks().forEach((track) => track.stop())
      video.srcObject = null
    }
  }, [onError, video])

  return (
    <div className={`camera-frame ${className}`}>
      <video ref={(element) => {
        setVideo(element)
        if (typeof ref === 'function') ref(element)
        else if (ref) ref.current = element
      }} autoPlay muted playsInline aria-label="Live camera preview" />
      <span className="camera-hint"><CameraIcon size={15} aria-hidden /> Position your face in the frame</span>
    </div>
  )
})

export default Camera

export function CameraPermissionHelp() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}>Camera permission help</Button>
      <Dialog open={open} onOpenChange={setOpen} title="Allow camera access" description="Your camera is used only to compute a face descriptor in this browser.">
        <div className="dialog-body">
          <ol className="camera-help-list">
            <li>Use a secure page (HTTPS or localhost) and connect a working camera.</li>
            <li>Choose the camera icon beside the browser address bar and allow access for this site.</li>
            <li>Close other apps using the camera, then reload this page and try again.</li>
          </ol>
          <div className="dialog-actions"><Button onClick={() => setOpen(false)}>Got it</Button></div>
        </div>
      </Dialog>
    </>
  )
}
