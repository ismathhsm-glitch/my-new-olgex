import * as DialogPrimitive from '@radix-ui/react-dialog'
import {
  AlertCircle, CheckCircle2, LoaderCircle, Plus, Shield, X, type LucideIcon,
} from 'lucide-react'
import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TableHTMLAttributes } from 'react'
import { Link } from 'react-router-dom'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'destructive' | 'unavailable'
  loading?: boolean
  invite?: boolean
}

export function Button({ variant = 'primary', loading, invite, children, className = '', disabled, ...props }: ButtonProps) {
  const classes = {
    primary: 'button button-primary',
    secondary: 'button button-secondary',
    destructive: 'button button-danger',
    unavailable: 'button button-disabled',
  }[variant]
  return (
    <button className={`${classes} ${className}`} disabled={disabled || loading || variant === 'unavailable'} {...props}>
      {loading ? <LoaderCircle aria-hidden className="spin button-loading-icon" size={16} /> : invite ? <Plus aria-hidden size={16} /> : null}
      <span className={loading ? 'button-loading-source' : undefined}>{children}</span>
      {loading && <span className="button-loading-label">Verifying...</span>}
    </button>
  )
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  helper?: string
  error?: string
  id: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ id, label, helper, error, className = '', ...props }, ref) {
  const helperId = helper ? `${id}-helper` : undefined
  const errorId = error ? `${id}-error` : undefined
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input ref={ref} id={id} className={`input ${error ? 'input-error' : ''} ${className}`} aria-invalid={Boolean(error)} aria-describedby={[helperId, errorId].filter(Boolean).join(' ') || undefined} {...props} />
      {helper && <p className="field-helper" id={helperId}>{helper}</p>}
      {error && <p className="field-error" id={errorId}>{error}</p>}
    </div>
  )
})

const badgeClass: Record<string, string> = {
  active: 'badge badge-success',
  invited: 'badge badge-warning',
  'not verified': 'badge badge-danger',
  'read only': 'badge badge-neutral',
  trusted: 'badge badge-success',
  untrusted: 'badge badge-warning',
  success: 'badge badge-success',
  failed: 'badge badge-danger',
  pending: 'badge badge-warning',
}

export function Badge({ children, tone }: { children: ReactNode; tone?: 'success' | 'warning' | 'danger' | 'neutral' }) {
  const label = String(children).toLowerCase()
  const colorClass = tone ? `badge badge-${tone}` : badgeClass[label] ?? 'badge badge-neutral'
  return <span className={colorClass}>{children}</span>
}

export function MetricCard({ label, value, context, badge }: { label: string; value: ReactNode; context: string; badge?: ReactNode }) {
  return (
    <section className="metric-card">
      <p className="metric-label">{label}</p>
      <p className="metric-value">{value}</p>
      <div className="metric-context">{badge}{context && <span>{context}</span>}</div>
    </section>
  )
}

export function DataTable({ children, className = '', ...props }: TableHTMLAttributes<HTMLTableElement>) {
  return <div className="table-scroll"><table className={`data-table ${className}`} {...props}>{children}</table></div>
}

export function Dialog({ open, onOpenChange, title, description, children, className = '' }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children: ReactNode
  className?: string
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="dialog-overlay" />
        <DialogPrimitive.Content className={`dialog-content ${className}`}>
          <div className="dialog-heading">
            <div>
              <DialogPrimitive.Title className="dialog-title">{title}</DialogPrimitive.Title>
              {description && <DialogPrimitive.Description className="dialog-description">{description}</DialogPrimitive.Description>}
            </div>
            <DialogPrimitive.Close className="icon-button" aria-label="Close dialog"><X size={18} /></DialogPrimitive.Close>
          </div>
          {children}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

export function Callout({ tone = 'warning', title, children }: { tone?: 'warning' | 'success' | 'danger' | 'info'; title: string; children: ReactNode }) {
  const Icon = tone === 'success' ? CheckCircle2 : tone === 'danger' ? AlertCircle : Shield
  return (
    <div className={`callout callout-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon aria-hidden size={18} />
      <div><strong>{title}</strong><p>{children}</p></div>
    </div>
  )
}

export function EmptyState({ title = 'No events match your filters', description, onClear }: {
  title?: string
  description: string
  onClear?: () => void
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon" aria-hidden>⌕</span>
      <h3>{title}</h3>
      <p>{description}</p>
      {onClear && <Button variant="secondary" onClick={onClear}>Clear filters</Button>}
    </div>
  )
}

export function Feedback({ tone, title, children }: { tone: 'success' | 'error'; title: string; children: ReactNode }) {
  const Icon = tone === 'success' ? CheckCircle2 : AlertCircle
  return (
    <div className={`feedback feedback-${tone}`} role={tone === 'error' ? 'alert' : 'status'} aria-live="polite">
      <Icon aria-hidden size={18} />
      <div><strong>{title}</strong><p>{children}</p></div>
    </div>
  )
}

export function NavItem({ to, icon: Icon, children, active, count, onClick }: {
  to: string
  icon: LucideIcon
  children: ReactNode
  active: boolean
  count?: number
  onClick?: () => void
}) {
  return (
    <Link to={to} className={`nav-item ${active ? 'nav-item-active' : ''}`} aria-current={active ? 'page' : undefined} onClick={onClick}>
      <Icon aria-hidden size={18} /><span>{children}</span>{count !== undefined && <span className="nav-count">{count}</span>}
    </Link>
  )
}

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className={`logo ${light ? 'logo-light' : ''}`} aria-label="Olgax Invoice home">
      <span className="logo-mark" aria-hidden><span>◈</span></span>
      <span className="logo-word">olgax <small>INVOICE</small></span>
    </Link>
  )
}

export function SectionHeading({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="section-heading">
      <div><h2>{title}</h2>{description && <p>{description}</p>}</div>
      {action}
    </div>
  )
}

export function Skeleton({ lines = 3 }: { lines?: number }) {
  return <div className="skeleton-stack" aria-label="Loading" role="status">{Array.from({ length: lines }, (_, index) => <span className="skeleton-line" key={index} />)}</div>
}
