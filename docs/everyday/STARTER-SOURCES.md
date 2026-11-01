# Starter source validation

The host-only [validation script](validate-starter-sources.py) fetched and parsed all eleven candidates successfully on 1 November 2026. [Machine-readable evidence](starter-source-validation.json) retains exact endpoints, UTC check times, HTTP status, final URL, ETag/Last-Modified when present, response byte count, item count and SHA-256. Article bodies are not checked into this evidence. This proves endpoint availability and structure at the recorded time, not the unfinished app collector or device behavior.

| Source | Language | Observed format / selection intent |
|---|---|---|
| LSM news | Latvian | RSS, 50 items; endpoint from the [publisher catalogue](https://www.lsm.lv/barotnes/rss/) |
| LSM Russian | Russian | RSS, 50 items; live publisher feed |
| LSM Politics / Society | English | Two RSS feeds, 50 items each, separately labelled; endpoints from the [English catalogue](https://eng.lsm.lv/feeds/rss/) |
| BBC World | English | RSS, 26 items; open world-news replacement instead of claiming unsupported Reuters/AP feeds |
| NASA | English | RSS, 10 items; [publisher feed directory](https://www.nasa.gov/rss-feeds/) |
| Ars Technica | English | RSS, 20 items |
| The Verge | English | Atom, 10 items |
| Hacker News | English | RSS, 30 items; link aggregation remains attributed to its source |
| CERT.LV warnings | Latvian | RSS, 30 items; Latvian public cybersecurity notices |
| Open-Meteo Riga probe | Weather data | JSON, current weather and three forecast dates; probe coordinates are test input, not an inferred user location |

No source is silently selected or fetched in Local mode. A selection stores catalogue/configuration only until Power is active. Published language and source labels remain visible even when the app UI uses another language. The catalogue must distinguish categories, freshness, mute, disable and remove-from-Screen. A failure must preserve the last successful content timestamp and mark stale data; a refresh attempt is not a successful retrieval.

Open-Meteo's public endpoint supports the initial non-commercial no-key experience. Its [service terms](https://open-meteo.com/en/terms) distinguish the free non-commercial service from commercial service; [the provider](https://open-meteo.com/) currently publishes a 10,000-call daily free limit and requires attribution for its data. Use a much lower per-device refresh budget, visible Open-Meteo attribution, and a configurable provider boundary. This record does not purchase or authorize a commercial service. Forecasts are not official weather warnings; a separate verified warning source is required before claiming alerts.

All source text, URLs and markup are untrusted data. The reader uses sanitized local text, does not execute embedded markup or automatically fetch images, and retains publisher links for deliberate external opening. Validation output is not a license grant to republish entire articles.

## Weather warning candidate

On 1 November 2026 at 18:36 UTC, the [MeteoAlarm feed catalogue](https://feeds.meteoalarm.org/) listed the Latvia Atom endpoint, which returned HTTP 200 and a valid empty Atom feed. An empty warning feed must be shown as no published items at its check time, not a forecast or a promise of safety. [Validation metadata](weather-alert-source-validation.json) preserves that result. A second `/api/v1/warnings/feeds-latvia` endpoint returned JSON rather than Atom and is not admitted by the feed parser; its warning dates and schema still need review before adopting it. No live warning content is fabricated from the empty Atom feed.
