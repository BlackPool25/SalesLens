import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '@/lib/auth-context';
import { useNavigate, Navigate } from 'react-router-dom';

const loginSchema = z.object({
  identifier: z.string().min(1, 'Email or username is required').email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof loginSchema>;

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  if (isAuthenticated) {
    return <Navigate to="/sources" replace />;
  }

  const onSubmit = async (data: LoginForm) => {
    setApiError(null);
    try {
      await login(data.identifier, data.password);
      navigate('/sources');
    } catch (error: any) {
      if (error.response?.status === 401) {
        setApiError('Invalid credentials');
      } else {
        setApiError('An error occurred during login');
      }
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--surface-base)' }}>
      <div 
        className="rounded-md p-8 max-w-sm w-full" 
        style={{ backgroundColor: 'var(--surface-elevated)' }}
      >
        <div className="text-center mb-6">
          <h1 className="text-h1 mb-2" style={{ color: 'var(--text-primary)' }}>SalesLens</h1>
          <p className="text-body" style={{ color: 'var(--text-secondary)' }}>Sign in to your account</p>
        </div>

        {apiError && (
          <div 
            className="mb-4 p-3 rounded text-body-sm" 
            style={{ backgroundColor: 'var(--semantic-error)', color: 'white' }}
          >
            {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
              Email or Username
            </label>
            <input
              type="text"
              placeholder="admin@saleslens.local"
              {...register('identifier')}
              className="w-full rounded p-2 text-body outline-none transition-colors"
              style={{ 
                backgroundColor: 'var(--surface-input)', 
                border: `1px solid var(--border-default)`,
                color: 'var(--text-primary)'
              }}
              onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
              onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
            />
            {errors.identifier && (
              <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                {errors.identifier.message}
              </p>
            )}
          </div>

          <div>
            <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
              Password
            </label>
            <input
              type="password"
              {...register('password')}
              className="w-full rounded p-2 text-body outline-none transition-colors"
              style={{ 
                backgroundColor: 'var(--surface-input)', 
                border: `1px solid var(--border-default)`,
                color: 'var(--text-primary)'
              }}
              onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
              onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
            />
            {errors.password && (
              <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                {errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full font-medium py-2 px-4 rounded transition-colors text-body"
            style={{ 
              backgroundColor: 'var(--accent-primary)', 
              color: 'white',
              opacity: isSubmitting ? 0.5 : 1
            }}
            onMouseEnter={(e) => {
              if (!isSubmitting) e.currentTarget.style.backgroundColor = 'var(--accent-hover)';
            }}
            onMouseLeave={(e) => {
              if (!isSubmitting) e.currentTarget.style.backgroundColor = 'var(--accent-primary)';
            }}
          >
            {isSubmitting ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}
