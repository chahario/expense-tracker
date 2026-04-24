import { useState } from 'react';

const INR = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
});

/**
 * CategorySummary
 *
 * Displays a condensed breakdown: per-category totals + grand total.
 * Data comes from GET /expenses/summary (aggregated server-side).
 *
 * Collapsible to save space when not needed.
 */
export default function CategorySummary({ summary, loading, error }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <section className="card">
      <div className="list-header">
        <h2 className="section-title" style={{ margin: 0 }}>Summary by Category</h2>
        <button
          className="btn-text"
          onClick={() => setCollapsed((c) => !c)}
          aria-expanded={!collapsed}
        >
          {collapsed ? 'Show ▾' : 'Hide ▴'}
        </button>
      </div>

      {!collapsed && (
        <>
          {loading && (
            <div className="skeleton-wrapper">
              {[1, 2, 3].map((i) => <div key={i} className="skeleton-row short" />)}
            </div>
          )}

          {!loading && error && (
            <p className="alert alert-error">{error}</p>
          )}

          {!loading && !error && (!summary || summary.categoryBreakdown.length === 0) && (
            <p className="state-center empty-state" style={{ padding: '1rem 0' }}>
              No data yet.
            </p>
          )}

          {!loading && !error && summary && summary.categoryBreakdown.length > 0 && (
            <>
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>Category</th>
                      <th className="text-right">Entries</th>
                      <th className="text-right">Total</th>
                      <th className="text-right">% of Spend</th>
                    </tr>
                  </thead>
                  <tbody>
                    {summary.categoryBreakdown.map((row) => {
                      const pct =
                        summary.grandTotal > 0
                          ? ((parseFloat(row.total) / parseFloat(summary.grandTotal)) * 100).toFixed(1)
                          : '0.0';
                      return (
                        <tr key={row.category}>
                          <td>
                            <span className="badge">{row.category}</span>
                          </td>
                          <td className="text-right muted">{row.count}</td>
                          <td className="amount-cell text-right">{INR.format(row.total)}</td>
                          <td className="text-right">
                            <div className="pct-wrapper">
                              <div
                                className="pct-bar"
                                style={{ width: `${pct}%` }}
                                aria-label={`${pct}%`}
                              />
                              <span className="pct-label">{pct}%</span>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="total-bar">
                <span>Grand Total · <small>{summary.totalCount} entries</small></span>
                <span className="total-amount">{INR.format(summary.grandTotal)}</span>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
