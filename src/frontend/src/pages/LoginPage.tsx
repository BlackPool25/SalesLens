import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '@/lib/auth-context';
import { useNavigate, Navigate } from 'react-router-dom';
import { apiClient } from '@/lib/api-client';

const loginSchema = z.object({
  identifier: z.string().min(1, 'Email or username is required'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof loginSchema>;

const registerSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
});

type RegisterForm = z.infer<typeof registerSchema>;

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [apiError, setApiError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const {
    register: registerLogin,
    handleSubmit: handleLoginSubmit,
    formState: { errors: loginErrors, isSubmitting: isLoginSubmitting },
    reset: resetLogin,
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const {
    register: registerRegister,
    handleSubmit: handleRegisterSubmit,
    formState: { errors: registerErrors, isSubmitting: isRegisterSubmitting },
    reset: resetRegister,
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
  });

  if (isAuthenticated) {
    return <Navigate to="/sources" replace />;
  }

  const onLoginSubmit = async (data: LoginForm) => {
    setApiError(null);
    setSuccessMessage(null);
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

  const onRegisterSubmit = async (data: RegisterForm) => {
    setApiError(null);
    setSuccessMessage(null);
    try {
      await apiClient.post('/auth/register', data);
      setSuccessMessage('User registered successfully! You can now sign in.');
      setMode('login');
      resetRegister();
    } catch (error: any) {
      if (error.response?.status === 409) {
        setApiError('Username or email already exists');
      } else {
        setApiError('An error occurred during registration');
      }
    }
  };

  const toggleMode = () => {
    setMode(mode === 'login' ? 'register' : 'login');
    setApiError(null);
    setSuccessMessage(null);
    resetLogin();
    resetRegister();
  };

  return (
    <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--surface-base)' }}>
      <div 
        className="rounded-md p-8 max-w-sm w-full" 
        style={{ backgroundColor: 'var(--surface-elevated)' }}
      >
        <div className="text-center mb-6">
          <h1 className="text-h1 mb-2" style={{ color: 'var(--text-primary)' }}>SalesLens</h1>
          <p className="text-body" style={{ color: 'var(--text-secondary)' }}>
            {mode === 'login' ? 'Sign in to your account' : 'Create a new account'}
          </p>
        </div>

        {apiError && (
          <div 
            className="mb-4 p-3 rounded text-body-sm" 
            style={{ backgroundColor: 'var(--semantic-error)', color: 'white' }}
          >
            {apiError}
          </div>
        )}

        {successMessage && (
          <div 
            className="mb-4 p-3 rounded text-body-sm" 
            style={{ backgroundColor: 'var(--semantic-success)', color: 'white' }}
          >
            {successMessage}
          </div>
        )}

        {mode === 'login' ? (
          <form onSubmit={handleLoginSubmit(onLoginSubmit)} className="space-y-4">
            <div>
              <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                Email or Username
              </label>
              <input
                type="text"
                placeholder="admin@saleslens.local"
                {...registerLogin('identifier')}
                className="w-full rounded p-2 text-body outline-none transition-colors"
                style={{ 
                  backgroundColor: 'var(--surface-input)', 
                  border: `1px solid var(--border-default)`,
                  color: 'var(--text-primary)'
                }}
                onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
              />
              {loginErrors.identifier && (
                <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                  {loginErrors.identifier.message}
                </p>
              )}
            </div>

            <div>
              <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                Password
              </label>
              <input
                type="password"
                {...registerLogin('password')}
                className="w-full rounded p-2 text-body outline-none transition-colors"
                style={{ 
                  backgroundColor: 'var(--surface-input)', 
                  border: `1px solid var(--border-default)`,
                  color: 'var(--text-primary)'
                }}
                onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
              />
              {loginErrors.password && (
                <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                  {loginErrors.password.message}
                </p>
              )}
            </div>

            <button
              type="submit"
              disabled={isLoginSubmitting}
              className="w-full font-medium py-2 px-4 rounded transition-colors text-body mt-2"
              style={{ 
                backgroundColor: 'var(--accent-primary)', 
                color: 'white',
                opacity: isLoginSubmitting ? 0.5 : 1
              }}
              onMouseEnter={(e) => {
                if (!isLoginSubmitting) e.currentTarget.style.backgroundColor = 'var(--accent-hover)';
              }}
              onMouseLeave={(e) => {
                if (!isLoginSubmitting) e.currentTarget.style.backgroundColor = 'var(--accent-primary)';
              }}
            >
              {isLoginSubmitting ? 'Signing in...' : 'Sign In'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegisterSubmit(onRegisterSubmit)} className="space-y-4">
            <div>
              <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                Username
              </label>
              <input
                type="text"
                placeholder="johndoe"
                {...registerRegister('username')}
                className="w-full rounded p-2 text-body outline-none transition-colors"
                style={{ 
                  backgroundColor: 'var(--surface-input)', 
                  border: `1px solid var(--border-default)`,
                  color: 'var(--text-primary)'
                }}
                onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
              />
              {registerErrors.username && (
                <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                  {registerErrors.username.message}
                </p>
              )}
            </div>

            <div>
              <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                Email
              </label>
              <input
                type="email"
                placeholder="john@example.com"
                {...registerRegister('email')}
                className="w-full rounded p-2 text-body outline-none transition-colors"
                style={{ 
                  backgroundColor: 'var(--surface-input)', 
                  border: `1px solid var(--border-default)`,
                  color: 'var(--text-primary)'
                }}
                onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
              />
              {registerErrors.email && (
                <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                  {registerErrors.email.message}
                </p>
              )}
            </div>

            <div>
              <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                Password
              </label>
              <input
                type="password"
                {...registerRegister('password')}
                className="w-full rounded p-2 text-body outline-none transition-colors"
                style={{ 
                  backgroundColor: 'var(--surface-input)', 
                  border: `1px solid var(--border-default)`,
                  color: 'var(--text-primary)'
                }}
                onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
              />
              {registerErrors.password && (
                <p className="mt-1 text-meta" style={{ color: 'var(--semantic-error)' }}>
                  {registerErrors.password.message}
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                  First Name
                </label>
                <input
                  type="text"
                  placeholder="John"
                  {...registerRegister('firstName')}
                  className="w-full rounded p-2 text-body outline-none transition-colors"
                  style={{ 
                    backgroundColor: 'var(--surface-input)', 
                    border: `1px solid var(--border-default)`,
                    color: 'var(--text-primary)'
                  }}
                  onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                  onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
                />
              </div>
              <div>
                <label className="block text-label mb-1" style={{ color: 'var(--text-primary)' }}>
                  Last Name
                </label>
                <input
                  type="text"
                  placeholder="Doe"
                  {...registerRegister('lastName')}
                  className="w-full rounded p-2 text-body outline-none transition-colors"
                  style={{ 
                    backgroundColor: 'var(--surface-input)', 
                    border: `1px solid var(--border-default)`,
                    color: 'var(--text-primary)'
                  }}
                  onFocus={(e) => e.target.style.borderColor = 'var(--border-accent)'}
                  onBlur={(e) => e.target.style.borderColor = 'var(--border-default)'}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={isRegisterSubmitting}
              className="w-full font-medium py-2 px-4 rounded transition-colors text-body mt-2"
              style={{ 
                backgroundColor: 'var(--accent-primary)', 
                color: 'white',
                opacity: isRegisterSubmitting ? 0.5 : 1
              }}
              onMouseEnter={(e) => {
                if (!isRegisterSubmitting) e.currentTarget.style.backgroundColor = 'var(--accent-hover)';
              }}
              onMouseLeave={(e) => {
                if (!isRegisterSubmitting) e.currentTarget.style.backgroundColor = 'var(--accent-primary)';
              }}
            >
              {isRegisterSubmitting ? 'Registering...' : 'Register'}
            </button>
          </form>
        )}

        <div className="mt-6 text-center">
          <button
            type="button"
            onClick={toggleMode}
            className="text-body-sm hover:underline"
            style={{ color: 'var(--accent-primary)' }}
          >
            {mode === 'login' 
              ? "Don't have an account? Register" 
              : "Already have an account? Sign in"}
          </button>
        </div>
      </div>
    </div>
  );
}
