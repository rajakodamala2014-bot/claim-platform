import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError } from '../api';
import { statusTone } from '../format';

export function StatusBadge({ status, label }: { status: string; label: string }) {
  return <span className={`badge tone-${statusTone(status)}`}>{label}</span>;
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  if (error instanceof ApiError) {
    return (
      <div className="alert alert-bad" role="alert">
        <strong>{error.detail}</strong>
        {error.fieldErrors.length > 0 && (
          <ul>
            {error.fieldErrors.map((e) => (
              <li key={e.field + e.message}>
                <code>{e.field}</code> {e.message}
              </li>
            ))}
          </ul>
        )}
        <div className="muted small">
          {error.code}
          {error.correlationId ? ` · ref ${error.correlationId}` : ''}
        </div>
      </div>
    );
  }
  return (
    <div className="alert alert-bad" role="alert">
      {error instanceof Error ? error.message : String(error)}
    </div>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading" aria-live="polite">
      <span className="spinner" /> {label}
    </div>
  );
}

export function Card({ title, actions, children }: { title?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-head">
          {title && <h2>{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}

/** Loads data with a reload handle; `deps` re-trigger the load. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  // the caller's deps decide when `load` changes
  const run = useCallback(load, deps);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await run());
      setError(null);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [run]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, error, loading, reload, setData };
}

/** Mirrors claims-service core.domain.Document (ALLOWED_CONTENT_TYPES, MAX_SIZE_BYTES); the server stays the authority. */
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/heic', 'application/pdf'];
export const MAX_UPLOAD_BYTES = 15 * 1024 * 1024;

export function uploadProblem(file: { type: string; size: number }): string | null {
  if (!ACCEPTED_TYPES.includes(file.type)) return 'Only JPEG, PNG, HEIC or PDF files can be uploaded';
  if (file.size <= 0 || file.size > MAX_UPLOAD_BYTES) return 'Files must be between 1 byte and 15 MB';
  return null;
}

/** File picker that uploads each file immediately (C2 + PUT) and reports the uploadIds. */
export function FileUploader({ onChange }: { onChange: (uploadIds: string[]) => void }) {
  const [files, setFiles] = useState<{ name: string; size: number; uploadId?: string; error?: string }[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    onChange(files.filter((f) => f.uploadId).map((f) => f.uploadId!));
  }, [files, onChange]);

  async function add(list: FileList | null) {
    if (!list) return;
    setBusy(true);
    for (const file of Array.from(list)) {
      const entry = { name: file.name, size: file.size } as { name: string; size: number; uploadId?: string; error?: string };
      const problem = uploadProblem(file);
      if (problem) {
        entry.error = problem;
      } else {
        try {
          entry.uploadId = await api.upload(file);
        } catch (e) {
          entry.error = e instanceof Error ? e.message : 'Upload failed';
        }
      }
      setFiles((prev) => [...prev, entry]);
    }
    setBusy(false);
  }

  return (
    <div className="uploader">
      <label className="btn btn-secondary">
        {busy ? 'Uploading…' : 'Add files'}
        <input type="file" multiple hidden disabled={busy} accept={ACCEPTED_TYPES.join(',')} onChange={(e) => { void add(e.target.files); e.target.value = ''; }} />
      </label>
      {files.length > 0 && (
        <ul className="file-list">
          {files.map((f, i) => (
            <li key={i} className={f.error ? 'file-bad' : ''}>
              <span>{f.name}</span>
              <span className="muted small">{f.error ?? `${Math.max(1, Math.round(f.size / 1024))} KB · uploaded`}</span>
              <button type="button" className="link" onClick={() => setFiles((prev) => prev.filter((_, j) => j !== i))}>
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
