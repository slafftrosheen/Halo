# Experimental input diagnostics — not a qualifying regression suite

These latest diagnostic source texts (LF-normalized) are archived as text, outside compiled Android tests. They use isolated memory storage and no user Graph, Send or voice actions. They are not shipped as passing acceptance tests.

The first 8-case run stopped at a merged-placeholder setup assertion; correcting it yielded 6 passes/2 rapid-shell failures on main 15. A fixed 2-second frame drain on main 16 yielded 5 passes/3 failures. Real-clock controls yielded 1 pass/3 failures: local HaloComposer completed 260 characters, shared stopped 213; native/bare controls failed focus setup. Individual real-clock repeats then failed BOTH local and shared. This does not isolate a shared-store regression or justify a speculative production feedback change. Ordinary whole-string IME commits and paced entry had passing observations; rapid shell injection remains UNQUALIFIED.

The verified catalogue patch is separate. Canonical unit/native regressions and real cloud/device evidence are recorded in the dated ledger. Original raw logs, exact experimental APK and private source copies remain locally ignored; this archive retains the latest source for audit. Earlier assertion/drain variants are described by their run records, not claimed byte-identical to these final sources.
