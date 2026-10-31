import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation, Outlet } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import AppShell from './components/AppShell'
import { StepUpProvider } from './components/StepUp'
import { Skeleton } from './components/ui'
const LoginPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.LoginPage })))
const RegisterPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.RegisterPage })))
const VerifyEmailPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.VerifyEmailPage })))
const ForgotPasswordPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.ResetPasswordPage })))
const AcceptInvitationPage = lazy(() => import('./pages/AuthPages').then((module) => ({ default: module.AcceptInvitationPage })))
const HomePage = lazy(() => import('./pages/WorkspacePages').then((module) => ({ default: module.HomePage })))
const FacePage = lazy(() => import('./pages/WorkspacePages').then((module) => ({ default: module.FacePage })))
const ProfilePage = lazy(() => import('./pages/WorkspacePages').then((module) => ({ default: module.ProfilePage })))
const OrganizationPage = lazy(() => import('./pages/WorkspacePages').then((module) => ({ default: module.OrganizationPage })))
const AuditPageView = lazy(() => import('./pages/WorkspacePages').then((module) => ({ default: module.AuditPageView })))
const DesignSystemPage = lazy(() => import('./pages/DesignSystemPage'))
const NotFoundPage = lazy(() => import('./pages/StatusPages').then((module) => ({ default: module.NotFoundPage })))
const RetryNoticePage = lazy(() => import('./pages/StatusPages').then((module) => ({ default: module.RetryNoticePage })))
const GenericErrorPage = lazy(() => import('./pages/StatusPages').then((module) => ({ default: module.GenericErrorPage })))

function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()
  if (isLoading) return <div className="route-loading"><Skeleton lines={5} /></div>
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <AppShell><Outlet /></AppShell>
}

export default function App() {
  const { isAuthenticated } = useAuth()
  return (
    <StepUpProvider>
      <Suspense fallback={<div className="route-loading"><Skeleton lines={5} /></div>}>
      <Routes>
        <Route path="/login" element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />} />
        <Route path="/register" element={isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/accept-invitation" element={<AcceptInvitationPage />} />
        <Route path="/locked" element={<RetryNoticePage />} />
        <Route path="/rate-limited" element={<RetryNoticePage rateLimited />} />
        <Route path="/error" element={<GenericErrorPage />} />
        <Route path="/design-system" element={<DesignSystemPage />} />
        <Route element={<ProtectedRoute />}>
          <Route index element={<HomePage />} />
          <Route path="/face" element={<FacePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/organization" element={<OrganizationPage />} />
          <Route path="/security-audit" element={<AuditPageView />} />
          <Route path="/forbidden" element={<NotFoundPage forbidden />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      </Suspense>
    </StepUpProvider>
  )
}
