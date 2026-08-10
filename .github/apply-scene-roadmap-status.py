from pathlib import Path

path = Path("docs/39-implementation-roadmap.md")
text = path.read_text()
old = '''1. **Local Character Scene and equipment transaction — `IN_PROGRESS`.** Finish the world-backed
   Scene, preview/commit/cancel, input and interruption ownership, authoritative inventory
   projection, configuration upgrade behavior and reconnect/restart acceptance. The current
   uncommitted Scene work belongs only to this feature.
'''
new = '''1. **Local Character Scene and equipment transaction — `AUTOMATED_VERIFIED`.** The world-backed
   Scene, preview/commit/cancel, input/interruption ownership, authoritative equipment projection,
   stale-session rejection and reconnect/restart persistence have automated coverage. Production
   navigation now consumes the canonical Scene policy, Rest-only workflows are context-gated and a
   new Scene cannot snapshot state while an authoritative value mutation is in flight. Real local
   client acceptance, including interruption plus reconnect/restart, remains required before this
   feature becomes `COMPLETE`.
'''
if text.count(old) != 1:
    raise SystemExit("Expected Local Character Scene roadmap status block exactly once")
path.write_text(text.replace(old, new, 1))
