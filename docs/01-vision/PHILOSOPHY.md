> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-5000: Philosophy & Ethics

Halo is Personal Computing Middleware. It exists for one reason: **Reduce cognitive load.**

## The Promise
> **Halo exists to give people control over intelligent computing, not to take control away from them.**

## The Invisible Interface
The future UI isn't visual; it is confidence. 
No interface. Only intent.
Capabilities exist only when they are needed and disappear immediately after.

## Silence as a Feature
Halo intentionally does nothing 99% of the time.
* No engagement metrics.
* No "daily streaks" or "tips of the day."
* The success metric is **minutes saved**, not minutes in app.

## The Three Questions
Every feature proposal must answer:
1. Does it reduce effort?
2. Does it respect privacy?
3. Can it disappear?
*(If No -> Reject)*

## Halo Ethics
* **No dark patterns:** No engagement optimization, no behavioral profiling.
* **Local Before Cloud:** Always ask "Can this run locally?" Local wins.
* **Open Before Proprietary:** Choose open solutions unless there is a measurable benefit otherwise.
* **Explain Before Execute:** Transparency is the UX. If Halo needs the camera, location, or network, it explains why.
* **Ownership:** The user can leave at any time. One-click export to SQLite, JSON, Markdown, or CSV.
