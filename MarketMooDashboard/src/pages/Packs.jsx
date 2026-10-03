import { Card, Loading, Page, fmtBytes, fmtDate, useApi } from '../ui.jsx'

export default function Packs() {
  const packs = useApi('/v1/manager/packs')
  return (
    <Page title="Data packs" subtitle="Per-district packs produced by the GIS pipeline and downloaded by the phones. Phones verify the checksum after download.">
      <Loading state={packs} />
      {packs.data && (
        <div className="table-wrap"><table>
          <thead><tr><th>District</th><th>Kind</th><th>Version</th><th>Size</th><th>Stored in</th><th>Checksum (sha256)</th><th>Registered</th></tr></thead>
          <tbody>
            {packs.data.results.length === 0 && <tr><td colSpan="7" className="muted">No packs registered.</td></tr>}
            {packs.data.results.map((p) => (
              <tr key={p.id}><td><b>{p.district}</b></td><td>{p.kind}</td><td>{p.version}</td><td>{fmtBytes(p.size_bytes)}</td><td>{p.stored_in === 'b2' ? 'Backblaze B2' : 'server disk'}</td>
                <td className="mono small">{p.sha256.slice(0, 16)}...</td><td className="small">{fmtDate(p.created_at)}</td></tr>
            ))}
          </tbody>
        </table></div>
      )}
      <Card title="Publishing a new pack">
        <p className="small">Run the GIS pipeline, then on the server: <code>python manage.py register_packs ../build/results/packs --pack-version YYYY.MM.DD</code>. The command uploads the files to the bucket and registers size and checksum. Phones see the new version under Account, Check for pack updates.</p>
      </Card>
    </Page>
  )
}
