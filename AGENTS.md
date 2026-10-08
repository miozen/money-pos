# MoneyPOS Agent Working Rules

## Source of truth and recovery

Repository state and accepted/frozen decisions are authoritative; conversation memory is only supplemental.
At the start of every new session, machine handoff, or context recovery, read in order:

1. this file;
2. `docs/MoneyPOS-Current-Architecture-and-Business-Scenarios.md`;
3. the current phase plan named by `docs/MoneyPOS-Repository-Handoff.md`;
4. `docs/MoneyPOS-Repository-Handoff.md`;
5. the accepted/frozen decision documents linked by that plan;
6. `git status --short`, branch, `git log --oneline -12`, and `git diff --check`.

Cross-check the handoff against Git, source, migrations, and actual test output. If they disagree, use verifiable
repository facts and correct the plan/handoff before continuing. Do not ask the user to repeat a decision already
recorded as accepted or frozen.

## Execution

- An accepted current phase starts in execution mode. Infer ordinary implementation details from the code, tests,
  and accepted plan; do not regress into discussion for routine choices.
- Pause for user direction only for a business-rule change, a frozen-architecture change, two materially different
  high-impact designs, or a destructive/irreversible operation.
- Preserve public routes, response shapes, migrations, transaction ownership, and existing formulas unless the
  accepted current plan explicitly changes them.
- New cross-feature cooperation uses `money-app-api` Entity-free contracts. Do not introduce cross-feature Entity,
  Mapper, or implementation-class dependencies.
- Treat a phase as complete only when its code, tests, required validation, current-plan update, handoff update,
  commit, Codex-initiated push, and branch-sync evidence are all present. Never report unrun tests as passing.
- After each completed phase slice, Codex commits, pushes `dev` to `origin`, fetches, and verifies
  `dev...origin/dev` has neither ahead nor behind. Ask the user to take over only after an actual authentication,
  network, or remote-state failure that Codex cannot resolve safely.

## Session handoff

At a reliable stopping point: inspect `git diff`, run `git diff --check`, inspect `git status --short`, update the
current phase plan if phase progress changed, and update `docs/MoneyPOS-Repository-Handoff.md` with only factual
state: dirty/clean, uncommitted work, validation actually run, failures/blockers, and one exact next action.

Git is the code-transfer fact. A dirty handoff is local only and must state that another computer cannot reconstruct
or continue it safely until it is committed and pushed. Follow `docs/MoneyPOS-双电脑接力协议.md` for synchronization.
