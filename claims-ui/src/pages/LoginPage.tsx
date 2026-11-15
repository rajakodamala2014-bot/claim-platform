import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/ui';

const ACCOUNTS = [
  ['priya', 'Customer, Singapore'],
  ['tan', 'Customer, Singapore'],
  ['wong', 'Customer, Hong Kong'],
  ['aisyah', 'Customer, Malaysia'],
  ['arun', 'Handler, Singapore'],
  ['kumar', 'Handler, Singapore'],
  ['faiz', 'Handler, Malaysia'],
  ['meera', 'Team lead, Singapore and Malaysia'],
  ['ho', 'Team lead, Hong Kong'],
  ['lee', 'Manager, all markets'],
] as const;

/** Username and password against platform.app_user. The gateway forwards POST /auth/login to claims-service. */
export function LoginPage() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function signIn(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <div className="login-card">
        <img src="/favicon.svg" alt="" width={40} height={40} />
        <h1>Claims</h1>
        <p className="muted">Sign in with your account. Customers, handlers, team leads and managers each see only their own work.</p>
        <ErrorBox error={error} />
        <form className="stack" onSubmit={(e) => void signIn(e)}>
          <label>Username<input value={username} autoComplete="username" onChange={(e) => setUsername(e.target.value)} required /></label>
          <label>Password<input type="password" value={password} autoComplete="current-password" onChange={(e) => setPassword(e.target.value)} required /></label>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        </form>
        <p className="small muted">Seeded accounts use the password <code>ChangeMe123!</code></p>
        <ul className="persona-list">
          {ACCOUNTS.map(([id, label]) => (
            <li key={id} className="small"><code>{id}</code> · {label}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}
