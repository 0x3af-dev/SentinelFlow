import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { LookupPage } from '@/pages/LookupPage'
import { TransactionPage } from '@/pages/TransactionPage'
import { InvestigationsPage } from '@/pages/InvestigationsPage'
import { InvestigationPage } from '@/pages/InvestigationPage'
import { SystemHealthPage } from '@/pages/SystemHealthPage'
import { LoginPage } from '@/pages/LoginPage'
import { AuthProvider } from '@/auth/AuthProvider'
import { RequireAuth, RequireRole } from '@/auth/guards'
import type { Role } from '@/api/types'

const router = createBrowserRouter([
  {
    element: (
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    ),
    children: [
      { path: '/login', element: <LoginPage /> },
      {
        element: <RequireAuth><Outlet /></RequireAuth>,
        children: [
          { path: '/', element: <LookupPage /> },
          { path: '/transactions/:reference', element: <TransactionPage /> },
          { path: '/investigations', element: <RequireRole roles={['ANALYST', 'INVESTIGATOR', 'ADMIN'] as Role[]}><InvestigationsPage /></RequireRole> },
          { path: '/investigations/:id', element: <RequireRole roles={['ANALYST', 'INVESTIGATOR', 'ADMIN'] as Role[]}><InvestigationPage /></RequireRole> },
          { path: '/system-health', element: <RequireRole roles={['OPERATOR', 'ADMIN'] as Role[]}><SystemHealthPage /></RequireRole> },
          { path: '*', element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
])

export function App() {
  return <RouterProvider router={router} />
}