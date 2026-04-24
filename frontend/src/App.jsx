import { useState, useEffect, useCallback } from 'react';
import ExpenseForm from './components/ExpenseForm';
import ExpenseList from './components/ExpenseList';
import CategorySummary from './components/CategorySummary';
import { fetchExpenses, fetchSummary } from './api/expenseApi';
import './App.css';

/**
 * App
 *
 * State ownership:
 *   expenses, summary  → fetched from backend, held here, passed down as props
 *   categoryFilter     → controlled here; ExpenseList uses it for client-side filter
 *   loading / error    → separate per resource (list vs summary) for granular UX
 *
 * Data flow:
 *   ExpenseForm.onCreated → loadAll() → re-fetch both list and summary
 *   categoryFilter change → no API call; ExpenseList filters in-memory
 */
export default function App() {
  const [expenses, setExpenses] = useState([]);
  const [summary, setSummary] = useState(null);
  const [listLoading, setListLoading] = useState(true);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [listError, setListError] = useState(null);
  const [summaryError, setSummaryError] = useState(null);
  const [categoryFilter, setCategoryFilter] = useState('');

  const loadExpenses = useCallback(async () => {
    setListLoading(true);
    setListError(null);
    try {
      const data = await fetchExpenses({ sort: 'date_desc' });
      setExpenses(data);
    } catch (err) {
      setListError(err.message);
    } finally {
      setListLoading(false);
    }
  }, []);

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true);
    setSummaryError(null);
    try {
      const data = await fetchSummary();
      setSummary(data);
    } catch (err) {
      setSummaryError(err.message);
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  // Fetch both in parallel on mount
  useEffect(() => {
    loadExpenses();
    loadSummary();
  }, [loadExpenses, loadSummary]);

  // After form submission, refresh both resources
  function handleCreated() {
    loadExpenses();
    loadSummary();
  }

  return (
    <div className="app-wrapper">
      <header className="app-header">
        <div className="header-inner">
          <span className="header-logo" aria-hidden="true">💰</span>
          <div>
            <h1 className="header-title">Expense Tracker</h1>
            <p className="header-sub">Know where your money is going</p>
          </div>
        </div>
      </header>

      <main className="app-main">
        <div className="layout-grid">
          {/* Left column: form */}
          <aside className="form-col">
            <ExpenseForm onCreated={handleCreated} />
          </aside>

          {/* Right column: list + summary */}
          <div className="list-col">
            <ExpenseList
              expenses={expenses}
              loading={listLoading}
              error={listError}
              categoryFilter={categoryFilter}
              onCategoryChange={setCategoryFilter}
              onRetry={loadExpenses}
            />

            <CategorySummary
              summary={summary}
              loading={summaryLoading}
              error={summaryError}
            />
          </div>
        </div>
      </main>

      <footer className="app-footer">
        Backed by PostgreSQL · Retries safely deduplicated · Data persists across refreshes
      </footer>
    </div>
  );
}
