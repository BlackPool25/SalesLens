import { Link } from 'react-router-dom'

export function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center h-full space-y-4">
      <h1 className="text-h2 text-text-primary">Page not found</h1>
      <p className="text-body text-text-secondary">The page you're looking for doesn't exist.</p>
      <Link to="/sources" className="text-body" style={{ color: 'var(--accent-primary)' }}>
        Go to Sources
      </Link>
    </div>
  )
}
