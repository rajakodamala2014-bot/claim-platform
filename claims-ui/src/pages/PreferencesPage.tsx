import { useEffect, useState, type FormEvent } from 'react';
import { api, type Preferences } from '../api';
import { Card, ErrorBox, Loading, useLoad } from '../components/ui';

const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'zh', label: '中文 (简体)' },
  { code: 'zh-Hant', label: '中文 (繁體)' },
  { code: 'ms', label: 'Bahasa Melayu' },
  { code: 'th', label: 'ไทย' },
  { code: 'id', label: 'Bahasa Indonesia' },
];

/** N3 read, N4 update notification preferences. */
export function PreferencesPage() {
  const loaded = useLoad(() => api.preferences(), []);
  const [prefs, setPrefs] = useState<Preferences | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (loaded.data) setPrefs(loaded.data);
  }, [loaded.data]);

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!prefs) return;
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      setPrefs(await api.savePreferences(prefs));
      setSaved(true);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  const channel = (key: 'email' | 'sms' | 'push', patch: Partial<Preferences['email']>) =>
    setPrefs((p) => p && { ...p, [key]: { ...p[key], ...patch } });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Settings</h1>
          <p className="muted">Choose how and in which language we contact you.</p>
        </div>
      </div>
      <ErrorBox error={loaded.error} />
      {loaded.loading && !prefs && <Loading />}
      {prefs && (
        <form onSubmit={(e) => void save(e)} className="stack">
          <Card title="Language">
            <label>
              <span>Messages are sent in</span>
              <select value={prefs.language} onChange={(e) => setPrefs({ ...prefs, language: e.target.value })}>
                {LANGUAGES.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
              </select>
            </label>
          </Card>
          <Card title="Channels">
            <p className="muted small">In-app messages are always on.</p>
            <div className="channel">
              <label className="toggle">
                <input type="checkbox" checked={prefs.email.enabled} onChange={(e) => channel('email', { enabled: e.target.checked })} /> Email
              </label>
              <input type="email" placeholder="you@example.com" value={prefs.email.address ?? ''}
                onChange={(e) => channel('email', { address: e.target.value || null })} disabled={!prefs.email.enabled} />
            </div>
            <div className="channel">
              <label className="toggle">
                <input type="checkbox" checked={prefs.sms.enabled} onChange={(e) => channel('sms', { enabled: e.target.checked })} /> SMS
              </label>
              <input type="tel" placeholder="+65…" value={prefs.sms.address ?? ''}
                onChange={(e) => channel('sms', { address: e.target.value || null })} disabled={!prefs.sms.enabled} />
            </div>
            <div className="channel">
              <label className="toggle">
                <input type="checkbox" checked={prefs.push.enabled} onChange={(e) => channel('push', { enabled: e.target.checked })} /> Push notifications
              </label>
            </div>
          </Card>
          <ErrorBox error={error} />
          <div className="row end">
            {saved && <span className="muted small" role="status">Saved</span>}
            <button className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
          </div>
        </form>
      )}
    </>
  );
}
