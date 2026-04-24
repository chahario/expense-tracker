import { useState, useRef } from 'react';
import { createExpense } from '../api/expenseApi';

const CATEGORY_SUGGESTIONS = [
  'Food & Dining',
  'Transport',
  'Entertainment',
  'Shopping',
  'Health & Medical',
  'Utilities',
  'Rent & Housing',
  'Education',
  'Travel',
  'Subscriptions',
  'Personal Care',
  'Other',
];

function todayISO() {
  return new Date().toISOString().split('T')[0];
}

/**
 * ExpenseForm
 *
 * Idempotency key strategy:
 *   • A crypto.randomUUID() is generated when the component mounts (or after reset).
 *   • The SAME key is reused on network-failure retries — backend deduplicates.
 *   • A NEW key is only generated after a successful submission.
 *   • submitting flag + disabled button guard against double-click at UI level.
 *
 * These two layers (UI guard + server idempotency) together handle all three failure modes:
 *   fast double-click → UI guard blocks second submit.
 *   slow network retry → same key, server deduplicates.
 *   page refresh mid-submit → key is in a ref; lost on refresh. Server only received
 *   at-most-one request (network either delivered it or didn't).
 */
export default function ExpenseForm({ onCreated }) {
  const [form, setForm] = useState({
    amount: '',
    category: '',
    description: '',
    date: todayISO(),
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Stable ref: doesn't cause re-renders, survives within one form session
  const idempotencyKeyRef = useRef(crypto.randomUUID());

  function handleChange(e) {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    // Clear feedback on any change so user knows their edit was registered
    setError(null);
    setSuccessMsg(null);
  }

  function resetForm() {
    setForm({ amount: '', category: '', description: '', date: todayISO() });
    // Only rotate key on success — retries must reuse the current key
    idempotencyKeyRef.current = crypto.randomUUID();
  }

  async function handleSubmit(e) {
    e.preventDefault();

    // UI-level double-click guard
    if (submitting) return;

    // Client-side amount validation (belt-and-suspenders over server validation)
    const amount = parseFloat(form.amount);
    if (!form.amount || isNaN(amount) || amount <= 0) {
      setError('Amount must be a positive number.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setSuccessMsg(null);

    try {
      await createExpense(
        {
          amount,
          category: form.category.trim(),
          description: form.description.trim(),
          date: form.date,
        },
        idempotencyKeyRef.current,
      );

      // SUCCESS: rotate key, reset form, notify parent
      resetForm();
      setSuccessMsg('Expense saved!');
      onCreated();
    } catch (err) {
      // FAILURE: intentionally keep the same idempotency key so a retry is safe
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="card">
      <h2 className="section-title">Add Expense</h2>

      <form onSubmit={handleSubmit} noValidate>
        <div className="form-row two-col">
          <div className="field">
            <label htmlFor="amount">Amount (₹) *</label>
            <input
              id="amount"
              name="amount"
              type="number"
              inputMode="decimal"
              min="0.01"
              step="0.01"
              placeholder="0.00"
              value={form.amount}
              onChange={handleChange}
              disabled={submitting}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="date">Date *</label>
            <input
              id="date"
              name="date"
              type="date"
              value={form.date}
              onChange={handleChange}
              disabled={submitting}
              required
            />
          </div>
        </div>

        <div className="field">
          <label htmlFor="category">Category *</label>
          <input
            id="category"
            name="category"
            type="text"
            list="category-suggestions"
            placeholder="e.g. Food & Dining, Transport…"
            value={form.category}
            onChange={handleChange}
            disabled={submitting}
            required
          />
          <datalist id="category-suggestions">
            {CATEGORY_SUGGESTIONS.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
        </div>

        <div className="field">
          <label htmlFor="description">Description *</label>
          <input
            id="description"
            name="description"
            type="text"
            placeholder="What was this expense for?"
            value={form.description}
            onChange={handleChange}
            disabled={submitting}
            maxLength={500}
            required
          />
        </div>

        {error && (
          <div className="alert alert-error" role="alert">
            {error}
          </div>
        )}
        {successMsg && (
          <div className="alert alert-success" role="status">
            {successMsg}
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? (
            <span className="btn-loading">
              <span className="spinner" aria-hidden="true" /> Saving…
            </span>
          ) : (
            'Add Expense'
          )}
        </button>
      </form>
    </section>
  );
}
