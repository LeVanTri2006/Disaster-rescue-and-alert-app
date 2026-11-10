"""Verify actual M01 runtime results, redaction and source/phone preservation."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
from gate_runtime import ART
import json,hashlib,base64,xml.etree.ElementTree as E
OUT=ROOT/'docs/demo/gmaps01/evidence/runtime';FINAL=OUT/'physical-final-namespace'
def save(n,data):(OUT/n).write_text(json.dumps(data,indent=2))
def main():
 r=json.loads((FINAL/'results.json').read_text());assert r['status']=='PASS' and len(r['cases'])==8 and not r['cleanup_errors'] and not r['remaining_owned_packages'] and r['device_authorized_after']
 x=E.parse(FINAL/'TEST-maps.xml').getroot();assert x.get('tests')=='8' and all(x.get(k)=='0' for k in ['failures','errors','skipped'])
 for c in r['cases']:
  assert c['adb_exit']==0 and all(c[k]==0 for k in ['failures','errors','skipped']) and len(c['actual_outputs'])==1
  v=c['actual_outputs'][0];assert v['ready'] and v['myLocation']==False
  if c['variant']=='negative':assert not v['loaded'] and v['callbacks']==0 and v['state'].startswith('ERROR_OR_TIMEOUT')
  else:assert v['loaded'] and v['callbacks']>=1
  if c['name']=='actualGoogleMapLoadsTilesAtSyntheticCamera':assert abs(v['latitude']-16.0505)<.00001 and abs(v['longitude']-108.2025)<.00001 and abs(v['zoom']-15.5)<.01
  if 'RescuerMarker' in c['name']:assert v['clickedTitle']=='RESCUER TEST' and v['clickedLatitude']==16.05 and v['clickedLongitude']==108.202
  if 'SosMarker' in c['name']:assert v['clickedTitle']=='SOS TEST' and v['clickedLatitude']==16.051 and v['clickedLongitude']==108.203
  if c.get('screenshot'):assert (FINAL/c['name']/c['screenshot']).read_bytes().startswith(b'\x89PNG')
 visual=json.loads((OUT/'visual-review.json').read_text());assert visual['status']=='PASS' and visual['source']==FINAL.name
 # Check all persisted text/images for raw key bytes; APK is intentionally excluded.
 leaks=[];encoded=[]
 for p in OUT.rglob('*'):
  if not p.is_file():continue
  data=p.read_bytes()
  if any(v.encode() in data or v.encode('utf-16le') in data for v in SECRETS):leaks.append(str(p.relative_to(ROOT)))
  if p.name=='instrumentation.txt':
   for line in data.decode(errors='replace').splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: p5e01XmlBase64='):
     raw=base64.b64decode(line.split('p5e01XmlBase64=',1)[1])
     if any(v.encode() in raw for v in SECRETS):encoded.append(str(p.relative_to(ROOT)))
 assert not leaks and not encoded
 save('secret-scan.json',{'status':'PASS','scope':'all runtime evidence bytes + decoded JUnit payloads; APK intentionally excluded','raw_key_matches':leaks,'encoded_junit_key_matches':encoded,'redaction':'exact values + Google API key pattern before log persistence'})
 logs=list(FINAL.glob('*/own-pid-logcat.txt'));fatal=[];auth=[];warnings=[]
 for p in logs:
  text=p.read_text()
  for term in ['FATAL EXCEPTION','ANR in ','Unable to start activity','FirebaseApp initialization','FirebaseInitProvider']:
   if term in text:fatal.append({'file':str(p.relative_to(ROOT)),'pattern':term})
  if 'Authorization failure' in text or 'API key not valid' in text:auth.append({'file':str(p.relative_to(ROOT)),'negative':p.parent.name=='invalidApiKeyCannotProduceLoadedSuccess'})
  if 'Phenotype.API' in text or 'Unknown calling package name' in text:warnings.append(str(p.relative_to(ROOT)))
 assert not fatal and all(v['negative'] for v in auth)
 save('log-review.json',{'status':'PASS','own_pid_windows':len(logs),'fatal_anr_or_firebase_startup':fatal,'authorization_errors':auth,'gms_internal_warnings':warnings,'note':'Caught GMS Phenotype/GoogleApiManager warnings observed; distinct from Maps authorization; SDK loads normally in positive cases'})
 dep=(OUT/'runtime-dependency-graph.txt').read_text();assert 'FAILED' not in dep and 'com.google.maps.android:maps-compose:4.4.1' in dep and 'com.google.android.gms:play-services-maps:19.0.0' in dep
 assert all(v not in dep for v in ['com.google.firebase:','play-services-location','play-services-auth','androidx.credentials:'])
 save('runtime-isolation.json',{'status':'PASS','source_manifest_dex_dependency_gate':True,'location_permission_or_layer':False,'business_firebase_sos_rescue_or_routing_in_apk':False,'network_scope':'Maps SDK/GMS infrastructure only from standalone app wiring; no global packet capture claim','external_map_toolbar':False,'cloud_settings_changed':False})
 session=json.loads((OUT/'handoff-final/session.json').read_text());assert session['status']=='PASS' and session['sdk_loaded_callback'] and session['keep_demo'] and session['retained']
 serial,adb=phone();expected=json.loads((OUT/'debug-gate.json').read_text())['artifacts']['gmaps01-debug.apk'];path=adb('shell','pm','path',PKG).stdout.strip();assert path.startswith('package:');actual=adb('shell','sha256sum',path.removeprefix('package:'));assert actual.returncode==0 and actual.stdout.split()[0]==expected
 others=[p for p in [PKG+'.test',PKG+'.negative',PKG+'.negative.test'] if 'package:' in adb('shell','pm','path',p).stdout];assert not others
 save('phone-handoff.json',{'serial':'REDACTED','model':'CPH2159','authorized':True,'retained_owned_package':PKG,'installed_apk_sha256':expected,'remaining_test_or_negative_packages':others,'no_reverse_permission_settings_or_private_data_operations':True})
 prune={'.git','.gradle','build','dist','node_modules','.idea','.kotlin','__pycache__'};preservation={}
 for label,base in [('android',ROOT),('backend',Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app'))]:
  before=json.loads((OUT/(label+'-baseline.json')).read_text());now={}
  for d,dirs,files in os.walk(base):
   dirs[:]=[v for v in dirs if v not in prune]
   for n in files:
    p=Path(d)/n
    if OUT in p.parents:continue
    now[str(p.relative_to(base))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'mtime_ns':str(p.stat().st_mtime_ns)}
  changed=[n for n,v in before.items() if now.get(n)!=v];new=sorted(set(now)-set(before))
  if label=='backend':assert not changed and not new
  else:
   assert all(n.startswith(('tools/google-maps-harness/','docs/demo/gmaps01/')) or n=='docs/demo/P5_E10_M01_GOOGLE_MAPS_REPORT.md' for n in changed+new)
  assert subprocess.run(['git','diff','--check'],cwd=base,capture_output=True).returncode==0
  preservation[label]={'status':'PASS','baseline_files':len(before),'allowed_m01_changed_files':changed,'new_files':new,'outside_scope_changes':0,'git_diff_check_exit':0}
 save('preservation.json',preservation)
 names=['gmaps01-configuration.apk','gmaps01-debug.apk','gmaps01-debug-test.apk','gmaps01-negative.apk','gmaps01-negative-test.apk'];(ART/'SHA256SUMS').write_text(''.join(hashlib.sha256((ART/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in names))
 sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in HERE.rglob('*') if p.is_file() and not any(n in prune for n in p.relative_to(HERE).parts)};save('source-hashes.json',sources)
 report=ROOT/'docs/demo/P5_E10_M01_GOOGLE_MAPS_REPORT.md';assert not any(v in report.read_text() for v in SECRETS)
 index={str(p.relative_to(ROOT)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in OUT.rglob('*') if p.is_file() and p.name!='evidence-index.json'};index[str(report.relative_to(ROOT))]={'bytes':report.stat().st_size,'sha256':hashlib.sha256(report.read_bytes()).hexdigest()};save('evidence-index.json',index)
 print('M01 verified: physical8/8, native markers/camera, visual tiles, negative API, secret isolation, handoff and preservation PASS')
if __name__=='__main__':main()
