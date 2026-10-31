import {
  Building2, CircleUserRound, Fingerprint, House, LogOut, Menu, ShieldCheck, X,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getData, sendData } from '../api/requests'
import { getRefreshToken } from '../api/client'
import { getApiError } from '../api/errors'
import type { Organization, Profile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Logo, NavItem, Skeleton } from './ui'

export default function AppShell({ children }: { children: React.ReactNode }) {
  const { role, logout } = useAuth()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const profile = useQuery({ queryKey: ['profile'], queryFn: () => getData<Profile>('/users/me') })
  const organization = useQuery({ queryKey: ['organization'], queryFn: () => getData<Organization>('/organizations/current') })
  const isAdmin = role === 'ORG_ADMIN' || role === 'SUPER_ADMIN'

  const onLogout = async () => {
    const refreshToken = getRefreshToken()
    let warning = ''
    try {
      if (refreshToken) await sendData('/auth/logout', { refreshToken })
    } catch (reason) {
      warning = getApiError(reason).message
    } finally {
      logout()
      navigate('/login', { replace: true, state: warning ? { message: `You are signed out locally. The server could not confirm token revocation: ${warning}` } : { message: 'You have been signed out.' } })
    }
  }

  const nav = (
    <>
      <div className="sidebar-brand"><Logo light /></div>
      <div className="workspace-chip"><strong>{organization.data?.name ?? 'Your workspace'}</strong><span>Business workspace</span></div>
      <nav className="primary-nav" aria-label="Main navigation">
        <NavItem to="/" icon={House} active={location.pathname === '/'} onClick={() => setDrawerOpen(false)}>Home</NavItem>
        <NavItem to="/face" icon={Fingerprint} active={location.pathname === '/face'} onClick={() => setDrawerOpen(false)}>Face Security</NavItem>
        <NavItem to="/profile" icon={CircleUserRound} active={location.pathname === '/profile'} onClick={() => setDrawerOpen(false)}>Profile and Devices</NavItem>
        <NavItem to="/organization" icon={Building2} active={location.pathname === '/organization'} onClick={() => setDrawerOpen(false)}>Organization</NavItem>
        {isAdmin && <NavItem to="/security-audit" icon={ShieldCheck} active={location.pathname === '/security-audit'} onClick={() => setDrawerOpen(false)}>Security Audit</NavItem>}
      </nav>
      <div className="sidebar-footer"><BadgeLine role={role} /><span>Olgax Invoice · Module 1</span></div>
    </>
  )

  return (
    <div className="app-shell">
      <aside className="sidebar desktop-sidebar">{nav}</aside>
      {drawerOpen && <div className="mobile-backdrop" onClick={() => setDrawerOpen(false)}><aside className="sidebar mobile-sidebar" onClick={(event) => event.stopPropagation()}><button className="drawer-close" aria-label="Close navigation" onClick={() => setDrawerOpen(false)}><X size={20} /></button>{nav}</aside></div>}
      <div className="app-main">
        <header className="topbar">
          <button className="mobile-menu icon-button" aria-label="Open navigation" onClick={() => setDrawerOpen(true)}><Menu size={20} /></button>
          <p className="breadcrumbs">Workspace <span>/</span> {location.pathname === '/' ? 'Home' : location.pathname.slice(1).replace('-', ' ')}</p>
          <div className="user-area">
            {organization.isLoading ? <Skeleton lines={1} /> : <span className="topbar-org">{organization.data?.name ?? ''}</span>}
            <div className="user-menu-container">
              <button className="user-button" aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
                <span className="avatar">{profile.data?.fullName?.slice(0, 1).toUpperCase() ?? 'U'}</span>
                <span className="user-name">{profile.data?.fullName ?? (profile.isLoading ? 'Loading profile' : 'Account')}</span>
                <span className="user-role">{role?.replace('_', ' ') ?? 'Member'}</span>
              </button>
              {menuOpen && <div className="user-menu">
                <button onClick={() => { navigate('/profile'); setMenuOpen(false) }}>Profile and devices</button>
                <button onClick={() => void onLogout()}><LogOut size={15} /> Sign out</button>
              </div>}
            </div>
          </div>
        </header>
        <main className="page-content">{children}</main>
      </div>
    </div>
  )
}

function BadgeLine({ role }: { role: string | null }) {
  return <span className="role-pill">{role?.replaceAll('_', ' ') ?? 'Workspace member'}</span>
}
