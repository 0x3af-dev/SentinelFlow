import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { LookupPage } from '@/pages/LookupPage'
import { TransactionPage } from '@/pages/TransactionPage'
import { InvestigationsPage } from '@/pages/InvestigationsPage'
import { InvestigationPage } from '@/pages/InvestigationPage'
import { SystemHealthPage } from '@/pages/SystemHealthPage'

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { path: '/', element: <LookupPage /> },
      { path: '/transactions/:reference', element: <TransactionPage /> },
      { path: '/investigations', element: <InvestigationsPage /> },
      { path: '/investigations/:id', element: <InvestigationPage /> },
      { path: '/system-health', element: <SystemHealthPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
])

export function App() {
  return <RouterProvider router={router} />
}