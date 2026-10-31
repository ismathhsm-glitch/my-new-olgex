import { useState } from 'react'
import { Building2, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  Badge, Button, Callout, DataTable, Dialog, EmptyState, Feedback, Input, Logo, MetricCard, NavItem,
} from '../components/ui'

export default function DesignSystemPage() {
  const [dialogOpen, setDialogOpen] = useState(false)
  return (
    <main className="design-page">
      <header className="design-header">
        <p className="eyebrow">03 / OLGAX INVOICE <span>UNIVERSITY OF MORATUWA · LEVEL 2 INTERIM PROJECT</span></p>
        <h1>Design System</h1>
        <p>Olgax foundations &amp; UI kit · standalone editable specimens · reusable local components</p>
        <div className="design-brand"><Logo /><span>Restrained enterprise clarity. Navy anchors navigation and primary actions; semantic colors communicate outcomes with text and icons, never color alone.</span></div>
      </header>
      <section>
        <h2>01 / Color foundations</h2>
        <div className="swatch-grid">{[
          ['Primary / navy', '#142B4D'], ['Sidebar / deep navy', '#0D1D35'], ['Link & focus', '#2458B8'], ['Canvas', '#F4F6F9'], ['Surface', '#FFFFFF'], ['Border', '#DCE3ED'],
        ].map(([label, color]) => <div className="swatch" key={label}><span style={{ backgroundColor: color }} /><strong>{label}</strong><small>{color}</small></div>)}</div>
        <div className="token-meta">{[['Text', '#172B45'], ['Muted text', '#627187'], ['Success', '#147653'], ['Warning', '#905B0B'], ['Error / destructive', '#B53D3D'], ['Disabled', '#E7EBF1']].map(([label, color]) => <span key={label}><b style={{ backgroundColor: color }} />{label}<small>{color}</small></span>)}</div>
      </section>
      <div className="design-two-col">
        <section className="panel"><h2>02 / Inter typography</h2><p className="display-sample">Display / 40 · 600</p><h3>Page title / 28 · 600</h3><h4>Section heading / 20 · 600</h4><p>Body / 14 / 400 · 1.5 line height</p><small>Caption &amp; metadata / 12 · 400</small></section>
        <section className="panel"><h2>03 / Layout tokens</h2><div className="spacing-samples">{[4, 8, 16, 24, 32, 48, 64].map((space) => <span key={space}><i style={{ height: `${Math.min(space, 64)}px` }} />{space}</span>)}</div><p>8 px base rhythm · 4 px fine spacing</p><small>Desktop 1440 px · Sidebar 232 px · Content inset 32 px · Controls 44–46 px · Card radius 12 px · Input radius 8 px · 1 px border</small></section>
      </div>
      <section className="design-section"><h2>04 / Buttons</h2><div className="button-row"><Button>Primary action</Button><Button variant="secondary">Secondary action</Button><Button variant="destructive">Revoke access</Button><Button variant="unavailable">Unavailable</Button><Button loading>Verifying...</Button><Button invite>Invite member</Button></div></section>
      <section className="design-section"><h2>05 / Input fields</h2><div className="form-grid"><Input id="sampleDefault" label="Default / email address" type="email" placeholder="name@company.com" helper="Use your work email." /><Input id="sampleFocus" label="Focus / email address" defaultValue="nethmi@lankastudio.example" /><Input id="sampleError" label="Error / email address" defaultValue="nethmi@" error="Enter a valid email address." /><Input id="sampleDisabled" label="Disabled / organization ID" value="ORG-LS-1042" disabled helper="Assigned by the system." /></div></section>
      <div className="design-two-col">
        <section className="panel"><h2>06 / Navigation</h2><NavItem to="/security-audit" icon={ShieldCheck} active count={1}>Security audit</NavItem><NavItem to="/organization" icon={Building2} active={false}>Organization</NavItem><small>Active item uses filled surface and semibold text.</small></section>
        <section className="panel"><h2>07 / Metric card</h2><MetricCard label="Additional verification" value="8" context="Last 7 days" badge={<Badge tone="success">6 completed</Badge>} /></section>
        <section className="panel"><h2>08 / Status badges</h2><div className="badge-row"><Badge>Active</Badge><Badge>Invited</Badge><Badge>Not verified</Badge><Badge>Read only</Badge></div><p>Pair semantic color with an unambiguous label. A warning requests review; it does not assert a security incident.</p></section>
      </div>
      <section className="design-section"><h2>09 / Table</h2><div className="panel"><div className="table-heading"><strong>Workspace members</strong><Button invite>Invite member</Button></div><DataTable><thead><tr><th scope="col">Member</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead><tbody><tr><td>Nethmi Perera</td><td>Owner</td><td><Badge>Active</Badge></td><td><button className="text-action">Manage role</button></td></tr><tr><td>Minoli Silva</td><td>Accountant</td><td><Badge>Invited</Badge></td><td><button className="text-action">Resend invite</button></td></tr></tbody></DataTable></div></section>
      <div className="design-two-col">
        <section className="panel"><h2>10 / Dialog · Revoke invitation?</h2><p>Minoli Silva will no longer be able to join using this invitation. You can invite them again later.</p><Callout title="Pending invitation only">This does not remove an active member or change their existing access.</Callout><Button variant="secondary" onClick={() => setDialogOpen(true)}>Preview accessible dialog</Button></section>
        <section className="panel"><h2>11 / Filtered empty state</h2><EmptyState description="Clear a filter or choose a wider date range to see more activity." onClear={() => undefined} /></section>
      </div>
      <section className="design-section"><h2>12 / Feedback messages</h2><div className="feedback-grid"><Feedback tone="success" title="Settings saved">Your workspace security preferences have been updated.</Feedback><Feedback tone="error" title="We couldn’t save your changes">Your previous settings are unchanged. Try again or contact support.</Feedback></div></section>
      <div className="design-handoff">Developer handoff · reusable local components · not published as a library<br />Semantic labels, keyboard navigation, focus trapping and focus return for dialogs, accessible error descriptions and live status announcements.</div>
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen} title="Revoke invitation?" description="Minoli Silva will no longer be able to join using this invitation."><div className="dialog-body"><Callout title="Pending invitation only">This does not remove an active member or change their existing access.</Callout><div className="dialog-actions"><Button variant="secondary" onClick={() => setDialogOpen(false)}>Cancel</Button><Button variant="destructive" onClick={() => setDialogOpen(false)}>Revoke invitation</Button></div></div></Dialog>
      <Link to="/login" className="design-login">Continue to sign in</Link>
    </main>
  )
}
