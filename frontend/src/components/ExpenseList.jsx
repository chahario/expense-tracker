/**
 * ExpenseList
 *
 * Receives ALL expenses from the parent (no server-side filter in use here).
 * Client-side filtering gives instant response without a loading state on filter change.
 * The server still supports ?category= for API clients that need server-side filtering.
 *
 * Design decision: client-side filter is correct for a personal tracker.
 * A single user's data is hundreds of records at most — negligible to filter in JS.
 */

const INR = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
});

function formatDate(iso) {
  // iso is "YYYY-MM-DD" from LocalDate serialisation
  return new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export default function ExpenseList({
  expenses,
  loading,
  error,
  categoryFilter,
  onCategoryChange,
  onRetry,
}) {
  // All unique categories from the unfiltered list — always complete
  const categories = [...new Set(expenses.map((e) => e.category))].sort();

  // Client-side filter
  const visible = categoryFilter
    ? expenses.filter(
        (e) => e.category.toLowerCase() === categoryFilter.toLowerCase(),
      )
    : expenses;

  // Total of currently visible entries
  const total = visible.reduce((sum, e) => sum + parseFloat(e.amount), 0);

  return (
    <section className="card">
      {/* Header with filter control */}
      <div className="list-header">
        <h2 className="section-title" style={{ margin: 0 }}>Expenses</h2>
        <div className="filter-group">
          <label htmlFor="category-filter">Category</label>
          <select
            id="category-filter"
            value={categoryFilter}
            onChange={(e) => onCategoryChange(e.target.value)}
          >
            <option value="">All</option>
            {categories.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Loading skeleton */}
      {loading && (
        <div className="skeleton-wrapper" aria-busy="true" aria-label="Loading expenses">
          {[1, 2, 3].map((i) => (
            <div key={i} className="skeleton-row" />
          ))}
        </div>
      )}

      {/* Error state */}
      {!loading && error && (
        <div className="state-center">
          <p className="alert alert-error">{error}</p>
          <button className="btn btn-outline" onClick={onRetry}>
            Retry
          </button>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && visible.length === 0 && (
        <div className="state-center empty-state">
          <span className="empty-icon">📊</span>
          <p>
            {categoryFilter
              ? `No expenses in "${categoryFilter}"`
              : 'No expenses yet. Add your first one above!'}
          </p>
        </div>
      )}

      {/* Table */}
      {!loading && !error && visible.length > 0 && (
        <>
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Date ↓</th>
                  <th>Category</th>
                  <th>Description</th>
                  <th className="text-right">Amount</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((exp) => (
                  <tr key={exp.id}>
                    <td className="date-cell">{formatDate(exp.date)}</td>
                    <td>
                      <span className="badge">{exp.category}</span>
                    </td>
                    <td className="desc-cell" title={exp.description}>
                      {exp.description}
                    </td>
                    <td className="amount-cell text-right">
                      {INR.format(exp.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Total bar */}
          <div className="total-bar">
            <span>
              {categoryFilter ? `${categoryFilter} · ` : 'All · '}
              <small>{visible.length} {visible.length === 1 ? 'entry' : 'entries'}</small>
            </span>
            <span className="total-amount">{INR.format(total)}</span>
          </div>
        </>
      )}
    </section>
  );
}
