> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# RFC Process

## What is an RFC?

The "Request for Comments" (RFC) process is intended to provide a consistent and controlled path for new features to enter the Halo ecosystem.

Many changes, including bug fixes and documentation improvements can be implemented and reviewed via the normal GitHub pull request workflow.

Some changes though are "substantial", and we ask that these be put through a bit of a design process and produce a consensus among the Halo community and core team.

## When to follow this process

You need to follow this process if you intend to make "substantial" changes to Halo or its documentation. What constitutes a "substantial" change is evolving, but may include the following:

* A new feature that creates new API surface area, and would require a feature flag if introduced.
* The removal of features that already shipped as part of a release.
* The introduction of new idiomatic usage or conventions, even if they do not include code changes to Halo itself.
* Changes to the architecture, such as adding a new engine or modifying the Event Bus.
* Adding a new SDK or modifying the Driver/Provider/Plugin SDK contract.

Some changes do not require an RFC:

* Rephrasing, reorganizing or refactoring
* Addition or removal of warnings
* Additions that strictly improve objective, numerical quality criteria (speedup, better browser support)
* Additions only likely to be noticed by other implementors-of-Halo, invisible to users-of-Halo.

## The Process

1. Fork the RFC repository (or just this project).
2. Copy `0000-template.md` to `docs/rfc/0000-my-feature.md` (where 'my-feature' is descriptive).
3. Fill in the RFC.
4. Submit a Pull Request.
5. Build consensus and integrate feedback.
6. The core team decides whether to accept, reject, or postpone the RFC.
7. Once accepted, the feature can be implemented.

## Template

See `0000-template.md` for the format.
