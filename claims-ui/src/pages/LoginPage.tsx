import { useState } from 'react';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/ui';

/**
 * Local stand-in for the customer OTP login: picks one of the claims-service dev personas
 * (POST /dev/token, 'dev' profile only). A real deployment would redirect to the identity provider here.
 */
const PERSONAS = [
  { username: 'priya', name: 'Priya Nair', market: 'SG' },
  { username: 'tan', name: 'Tan Wei Ling', market: 'SG' },
  { username: 'wong', name: 'Wong Siu Ming', market: 'HK' },
  { username: 'aisyah', name: 'Nur Aisyah Binti Ahmad', market: 'MY' },
  { username: 'somchai', name: 'Somchai Jaidee', market: 'TH' },
  { username: 'budi', name: 'Budi Santoso', market: 'ID' },
  { username: 'olivia', name: 'Olivia Smith', market: 'AU' },
];

export function LoginPage() {
  const { login } = useAuth();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  async function signIn(username: string) {
    setBusy(username);
    setError(null);
    try {
      await login(username);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="login">
      <div className="login-card">
        <img src="/favicon.svg" alt="" width={40} height={40} />
        <h1>My Claims</h1>
        <p className="muted">Report an incident, track your claim and reply to your claims handler.</p>
        <p className="small muted">Local demo sign-in: choose a customer.</p>
        <ErrorBox error={error} />
        <ul className="persona-list">
          {PERSONAS.map((p) => (
            <li key={p.username}>
              <button className="persona" disabled={busy !== null} onClick={() => void signIn(p.username)}>
                <span className="avatar">{p.name.charAt(0)}</span>
                <span className="persona-name">{p.name}</span>
                <span className="chip">{p.market}</span>
                {busy === p.username && <span className="spinner" />}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
