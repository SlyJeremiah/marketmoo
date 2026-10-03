import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, Loading, Page, Stat, useApi } from '../ui.jsx'

const short = (d) => d.slice(5)

function Series({ data, kind = 'line', color = '#1f7a3a' }) {
  const Chart = kind === 'bar' ? BarChart : LineChart
  return (
    <ResponsiveContainer width="100%" height={200}>
      <Chart data={data} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e5e0d5" />
        <XAxis dataKey="date" tickFormatter={short} fontSize={11} />
        <YAxis allowDecimals={false} fontSize={11} />
        <Tooltip />
        {kind === 'bar' ? <Bar dataKey="count" fill={color} radius={[3, 3, 0, 0]} /> : <Line type="monotone" dataKey="count" stroke={color} strokeWidth={2} dot={false} />}
      </Chart>
    </ResponsiveContainer>
  )
}

export default function Overview() {
  const s = useApi('/v1/manager/stats')
  const d = s.data
  const byStatus = d ? Object.entries(d.listings.by_status).map(([status, count]) => ({ status, count })) : []
  const byDistrict = d ? Object.entries(d.users.by_district).map(([district, count]) => ({ district, count })) : []
  return (
    <Page title="Overview" subtitle="What is happening across the three pilot districts" actions={<button className="btn ghost" onClick={s.reload}>Refresh</button>}>
      <Loading state={s} />
      {d && (
        <>
          <div className="stats">
            <Stat label="Registered users" value={d.users.total} hint={`${d.users.verified} verified`} />
            <Stat label="Listings" value={d.listings.total} hint={`${d.listings.by_status.pending || 0} waiting for review`} tone={d.listings.by_status.pending ? 'warn' : ''} />
            <Stat label="New outbreak reports" value={d.reports.new} hint={`${d.reports.total} in total`} tone={d.reports.new ? 'warn' : ''} />
            <Stat label="Active disease notices" value={d.outbreaks_active} />
            <Stat label="Open pools" value={d.pools_open} />
            <Stat label="Farm boundaries mapped" value={d.boundaries?.count ?? 0} hint={`${d.boundaries?.total_ha ?? 0} hectares (outlines are private)`} />
            <Stat label="Farm records synced" value={d.records_total} hint={`${d.sync.rejected_total} operations rejected`} />
          </div>
          <div className="grid2">
            <Card title="Sign-ups, last 14 days"><Series data={d.series.signups} /></Card>
            <Card title="New listings, last 14 days"><Series data={d.series.listings} kind="bar" color="#8b5e34" /></Card>
            <Card title="Sync operations received, last 14 days"><Series data={d.series.sync_ops} color="#1e88e5" /></Card>
            <Card title="Listings by status">
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={byStatus} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e0d5" />
                  <XAxis dataKey="status" fontSize={11} /><YAxis allowDecimals={false} fontSize={11} /><Tooltip />
                  <Bar dataKey="count" fill="#1f7a3a" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Card>
            <Card title="Users by district">
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={byDistrict} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e0d5" />
                  <XAxis dataKey="district" fontSize={11} /><YAxis allowDecimals={false} fontSize={11} /><Tooltip />
                  <Bar dataKey="count" fill="#e0a800" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Card>
          </div>
        </>
      )}
    </Page>
  )
}
