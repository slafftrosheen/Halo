# #42: HALO-COMMERCE: ~$4.99 paid Play listing and optional feature packs

Original: https://github.com/slafftrosheen/Halo/issues/42 · updated 2026-10-22T12:52:34Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I42-P01: Actual paid listing

In Play Console, review the paid base listing, intended tester availability, localized prices/taxes/countries and approximately USD4.99 target. Attach the released package/version/signature and reviewer evidence.

**Pass evidence:** Actual listing is paid and available in reviewed regions. A debug/sideload install or source price string is not store evidence. No feature pack or recurring Halo subscription is introduced.

Status: NOT_TESTED. Evidence: pending.

### I42-P02: Purchase/reinstall/refund

An authorized human tester uses the actual purchasing Play account for initial install/access, uninstall/reinstall or replacement-device access, and applicable full-refund/support flow. Redact order/account/payment identifiers.

**Pass evidence:** Store delivery/access and refund/support behavior are demonstrated on the released build. No in-app base unlock, token/entitlement service or functional backend is invented. No automated payment or refund action.

Status: NOT_TESTED. Evidence: pending.

### I42-P03: Copy/legal/privacy

Have the release owner review localized privacy, legal and support copy, provider-cost disclosure, support contact and current Play guidance.

**Pass evidence:** No recurring Halo charge or unlimited-third-party-credit promise; no assistant content/provider secret enters commerce. Record reviewer/date and approved copy, separately from legal advice.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I42-R01** (original line 13, Acceptance — keep open until release evidence exists): - Paid Play listing and localized price/region configuration are reviewed and published/available to the intended testers.
- [ ] **I42-R02** (original line 14, Acceptance — keep open until release evidence exists): - Tester confirms initial install and access, reinstall/replacement-device access with the purchasing Play account, and the applicable full-refund/support path in the actual store flow.
- [ ] **I42-R03** (original line 15, Acceptance — keep open until release evidence exists): - No recurring Halo charge; third-party provider costs are disclosed separately.
- [ ] **I42-R04** (original line 16, Acceptance — keep open until release evidence exists): - Privacy, legal, and customer-support copy reviewed.
- [ ] **I42-R05** (original line 26, Implementation discipline): - Start from current main, inspect existing components and BrandBook/DevKit, reuse runtime and Compose; no global rewrites.
- [ ] **I42-R06** (original line 27, Implementation discipline): - Keep internal traces/experimental model settings in Advanced → Development.
- [ ] **I42-R07** (original line 28, Implementation discipline): - Code-only batches can commit to main; do not run builds/tests/CI/Actions during coding. Physical/store validation is owner/alpha-tester work; never claim it from source review.
- [ ] **I42-R08** (original line 29, Implementation discipline): - EN/RU/LV/PL copy subject to actual language support; accessibility and privacy are requirements.
