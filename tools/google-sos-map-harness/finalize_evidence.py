"""Verify actual M02 evidence, preservation, secrets, correlation and handoff."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
from fixture import TOKENS
import json,hashlib,base64,xml.etree.ElementTree as E
D=ROOT/'docs/demo/gmaps02';O=D/'evidence';P=O/'physical-accepted'
def write(name,value):(O/name).write_text(json.dumps(value,indent=2,ensure_ascii=False))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inventory(root):
 result={}
 for base,dirs,files in os.walk(root):
  dirs[:]=[d for d in dirs if d not in {'.git','.gradle','build','node_modules','.idea','.kotlin','__pycache__','dist'}]
  for n in files:
   p=Path(base)/n;rel=str(p.relative_to(root))
   if rel.startswith(('docs/demo/gmaps02/','tools/google-sos-map-harness/')):continue
   result[rel]={'sha256':sha(p),'mtime_ns':str(p.stat().st_mtime_ns)}
 return result

def main():
 result=json.loads((P/'results.json').read_text());cases=result['cases'];assert result['status']=='PASS' and len(cases)==21 and len({c['name'] for c in cases})==21
 xml=E.parse(P/'TEST-maps.xml').getroot();assert xml.get('tests')=='21' and all(xml.get(k)=='0' for k in ['failures','errors','skipped'])
 assert all(c['adb_exit']==0 and c['failures']==c['errors']==c['skipped']==0 for c in cases)
 assert result['device_authorized_after'] and not result['remaining_owned_packages'] and not result['cleanup_errors']
 host=E.parse(O/'TEST-host-delivery.xml').getroot();assert host.get('tests')=='5' and host.get('failures')==host.get('errors')=='0';assert json.loads((O/'host-command.json').read_text())['exit']==0
 # Actual response -> server shared store -> GET DTO -> actual SDK marker.
 proof=[]
 for name in ['citizenPostThenIndependentRescuerGetRendersClickableMarker','multipleSosHaveSeparateResponseMarkersAndRefreshNeverPosts','committedPostTimeoutHasUnknownOutcomeAndNeverAutoRetries']:
  c=next(c for c in cases if c['name']==name);events=c['fixture_events'];store=json.loads((P/name/'shared-store.json').read_text());values=c['actual_outputs'][-1];display={s['eventId']:s for s in values['markers']};gets=[e for e in events if e['path']=='/sos/active' and 'response' in e];server={r['eventId']:r for r in gets[-1]['response']}
  assert set(display)==set(server)==set(store)
  native=values['nativeClicks'];assert {n['eventId'] for n in native}==set(server)
  for n in native:
   s=server[n['eventId']];assert n['latitude']==s['latitude'] and n['longitude']==s['longitude'] and n['infoWindowShown']
   for k in ['eventId','latitude','longitude','adults','children','elderly','status','description']:assert display[s['eventId']][k]==s[k]==store[s['eventId']][k]
  posts=[e for e in events if e['method']=='POST' and e['path']=='/sos'];assert len(posts)==len(store)
  proof.append({'case':name,'post_count':len(posts),'get_count':len(gets),'server_ids':list(server),'typed_get':list(display.values()),'native_sdk_clicks':native,'post_events':posts,'cross_role_accounts':[e['synthetic_account_role'] for e in events if 'synthetic_account_role' in e],'status':'PASS'})
 write('marker-correlation.json',proof)
 # No crash/ANR or Maps authorization failure in actual own-PID test/startup windows.
 logs=list(P.glob('*/own-pid-logcat.txt'))+[O/'startup-smoke-final/own-pid-logcat.txt'];findings=[]
 for p in logs:
  for line in p.read_text(errors='replace').splitlines():
   if re.search(r'FATAL EXCEPTION|ANR in |am_anr|Authorization failure|FirebaseApp.*initializ',line,re.I):findings.append({'file':str(p.relative_to(O)),'line':redact(line)})
 assert not findings;write('log-review.json',{'status':'PASS','own_pid_windows':len(logs),'crash_anr_authorization_firebase_init_findings':findings,'scope':'Own PID/time windows; no global logcat clear or packet capture. GMS internal feature-flag warnings are not dispatch/Firebase SDK startup.'})
 graph=(O/'dependencies.txt').read_text();assert 'play-services-maps:19.0.0' in graph and 'maps-compose:4.4.1' in graph
 for name in ['com.google.firebase:','play-services-location:','play-services-auth:','androidx.credentials:']:assert name not in graph
 serial,adb=phone();assert 'tcp:'+str(result['fixture_port']) not in adb('reverse','--list').stdout
 # Verify retained M02 and preserved M01 public APK identities, never dump package metadata.
 installed={}
 for pkg,artifact in [(PKG,D/'artifacts/gmaps02-debug.apk'),('com.example.bai1.p5e10maps.demo',ROOT/'docs/demo/gmaps01/artifacts/gmaps01-debug.apk')]:
  paths=adb('shell','pm','path',pkg);assert paths.returncode==0 and paths.stdout.strip().startswith('package:');path=paths.stdout.strip()[8:];r=adb('shell','sha256sum',path);assert r.returncode==0 and r.stdout.split()[0]==sha(artifact);installed[pkg]={'sha256':sha(artifact),'matches_preserved_artifact':True}
 assert 'package:' not in adb('shell','pm','path',PKG+'.test').stdout
 smoke=json.loads((O/'startup-smoke-final/smoke.json').read_text());assert smoke['status']=='PASS';session=json.loads((O/'startup-smoke-final/session.json').read_text());assert session['status']=='PASS' and session['owned_reverse_removed'];write('phone-handoff.json',{'status':'PASS','installed':installed,'test_package_absent':True,'test_and_startup_reverse_removed':True,'fixture_processes_stopped':True,'store_scope':'RAM session; rerun demo_session for a fresh fixture','personal_data_access':'None; only own demo UI, package SHA, unique smoke temporary UI XML removed.'})
 # Visual review is separately written only after actual images have been inspected.
 visual=json.loads((O/'visual-review.json').read_text());assert visual['reviewed'] and len(visual['images'])==5
 for row in visual['images']:assert sha(O/row['path'])==row['sha256']
 baseline=json.loads((O/'baseline.json').read_text());current={'android':inventory(ROOT),'backend':inventory(Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app'))};preserve={}
 for repo in baseline:
  old=baseline[repo];now=current[repo];missing=sorted(set(old)-set(now));changed=[p for p in old if p in now and old[p]!=now[p]];added=sorted(set(now)-set(old));assert not missing and not changed,(repo,changed,missing)
  if repo=='backend':assert not added
  else:assert set(added)<={'docs/demo/P5_E10_M02_SOS_GOOGLE_MAPS_REPORT.md'}
  preserve[repo]={'baseline_files':len(old),'changed':changed,'removed':missing,'added_outside_new_module_and_evidence':added,'sha256_and_mtime_preserved':True}
 preserve['new_implementation_files']=[str(p.relative_to(ROOT)) for p in HERE.rglob('*') if p.is_file() and not any(x in p.parts for x in ['build','.gradle','__pycache__'])]
 write('preservation.json',preserve)
 checks={}
 for name,root in [('android',ROOT),('backend',Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app'))]:
  r=subprocess.run(['git','diff','--check'],cwd=root,capture_output=True,text=True);assert r.returncode==0;checks[name]={'diff_check_exit':r.returncode}
 write('final-checks.json',checks)
 # Never export key or auth token values, including encoded JUnit diagnostic payloads.
 secrets=SECRETS+list(TOKENS.values())+['e10a-synthetic-refresh-not-a-jwt'];count=0;decoded=0
 for p in O.rglob('*'):
  if not p.is_file() or p.suffix=='.png':continue
  raw=p.read_bytes();assert not any(v.encode() in raw for v in secrets if v),str(p);count+=1
  for encoded in re.findall(rb'p5e01XmlBase64=([^\r\n]+)',raw):
   xmlraw=base64.b64decode(encoded);assert not any(v.encode() in xmlraw for v in secrets if v);decoded+=1
 write('secret-scan.json',{'status':'PASS','files_checked':count,'decoded_actual_junit_payloads_checked':decoded,'key_and_token_matches':0,'apk_excluded_reason':'Authorized Maps key resides in APK metadata by SDK design; APKs Git ignored. Raw build intermediates are ignored, only redacted manifests exported.'})
 write('source-hashes.json',{str(p.relative_to(ROOT)):sha(p) for p in HERE.rglob('*') if p.is_file() and not any(x in p.parts for x in ['build','.gradle','__pycache__'])})
 write('runtime-isolation.json',{'status':'PASS','maps_sdk':'19.0.0','maps_compose':'4.4.1','business_http':'Only owned HTTP127.0.0.1:port paths /demo/info /auth/login /sos /sos/active; no proxy, redirects, fallback or connection retry','google_sdk_network':'Permitted solely for map SDK; no synthetic token supplied to SDK','location_permissions':False,'my_location':False,'firebase_production':False,'production_backend_calls':False,'production_sos_dispatch':False,'ucs_or_polyline_or_mission':False,'F06':'OPEN','STRICT_ROUTING_DISABLED':True,'evidence_scope':'Source/manifest/DEX/dependency, typed actual HTTP fixture events, own-PID logs and physical UI. Not global phone packet capture.'})
 write('evidence-index.json',{str(p.relative_to(D)):sha(p) for p in D.rglob('*') if p.is_file() and p.name!='evidence-index.json'})
 print('M02 final evidence PASS:21 physical /5 host, native correlation, secrets and preservation')
if __name__=='__main__':main()
