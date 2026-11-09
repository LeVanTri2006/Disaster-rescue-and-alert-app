"""Show current M01 status without reading or printing key values."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 out=ROOT/'docs/demo/gmaps01/evidence/runtime'
 gate=json.loads((out/'debug-gate.json').read_text());apk=ROOT/'docs/demo/gmaps01/artifacts/gmaps01-debug.apk';assert hashlib.sha256(apk.read_bytes()).hexdigest()==gate['artifacts'][apk.name]
 runtime=json.loads((out/'physical-final-namespace/results.json').read_text()) if (out/'physical-final-namespace/results.json').exists() else {}
 visual=json.loads((out/'visual-review.json').read_text()) if (out/'visual-review.json').exists() else {}
 ready=runtime.get('status')=='PASS' and visual.get('status')=='PASS'
 print('GOOGLE_MAPS_DEMO_READY = '+('PASS' if ready else 'PENDING_VERIFICATION'))
 print('Package:',gate['package']);print('Certificate SHA-1:',gate['signing_sha1'])
 print('Key metadata: present / REDACTED; no key value read by status command.')
 print('Physical JUnit cases:',len(runtime.get('cases',[])))
 return 0 if ready else 2
if __name__=='__main__':raise SystemExit(main())
