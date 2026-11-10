"""Read-only verification of final device evidence and source preservation."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
import json,hashlib,os,subprocess,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/demo/p5e10a/evidence';FINAL=OUT/'physical-complete'
def save(name,data):(OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2))
def verify():
 r=json.loads((FINAL/'results.json').read_text());assert r['status']=='PASS' and len(r['cases'])==15
 x=E.parse(FINAL/'TEST-demo.xml').getroot();assert x.get('tests')=='15' and all(x.get(k)=='0' for k in ['failures','errors','skipped'])
 h=E.parse(OUT/'TEST-fixture-host.xml').getroot();assert h.get('tests')=='4' and h.get('failures')==h.get('errors')=='0'
 proof=[]
 for c in r['cases']:
  assert c['adb_exit']==0 and all(c[k]==0 for k in ['failures','errors','skipped'])
  events=c['fixture_events'];store=json.loads((FINAL/c['name']/'shared-store.json').read_text());o=json.loads(c['actual_outputs'][0])
  assert all(e['path'] in ['/demo/info','/auth/login','/sos','/sos/active'] for e in events)
  posts=[e for e in events if e['path']=='/sos' and 'response_event_id' in e]
  assert {e['response_event_id'] for e in posts}==set(store)
  for e in posts:assert e['role']=='citizen' and e['response']==store[e['response_event_id']]
  for e in events:
   if e['path']=='/sos/active' and 'response' in e:
    for row in e['response']:assert row==store[row['eventId']]
  for row in o['markers']:
   for f in ['adults','children','elderly','condition','description','latitude','longitude','createdAt','status','transportMode','urgency']:assert row[f]==store[row['eventId']][f]
  if c['name']=='citizenPostThenIndependentRescuerGetRendersClickableMarker':
   assert len(posts)==len(o['markers'])==1 and o['selected']['eventId']==posts[0]['response_event_id']
   assert posts[0]['request']['adults']==2 and posts[0]['request']['latitude']==16.051 and posts[0]['request']['longitude']==108.203
   assert any(e['path']=='/sos/active' and e['role']=='rescuer' and e['response_ids']==[posts[0]['response_event_id']] for e in events)
  if c['name']=='multipleSosHaveSeparateResponseMarkersAndRefreshNeverPosts':assert len(posts)==len(store)==2 and len({e['actor'] for e in posts})==2 and sum(e['path']=='/sos/active' for e in events)==4
  if c['name'].startswith('post') or c['name']=='logoutDuringPendingPostCannotRestoreSuccess':assert not store
  if c['name']=='postOfflineDoesNotReachFixtureOrShowSuccess':assert not any(e['path']=='/sos' for e in events)
  proof.append({'case':c['name'],'server_rows':len(store),'committed_posts':len(posts),'markers':len(o['markers']),'status':'PASS'})
 save('cross-role-proof.json',proof)
 patterns=['FATAL EXCEPTION','ANR in ','Unable to start activity','FirebaseApp initialization','FirebaseInitProvider','e10a-synthetic-citizen-not-a-jwt','e10a-synthetic-rescuer-not-a-jwt','fixture-only-password'];bad=[]
 for p in FINAL.glob('*/own-pid-logcat.txt'):
  for v in patterns:
   if v in p.read_text():bad.append({'file':str(p.relative_to(ROOT)),'pattern':v})
 assert not bad;save('log-review.json',{'status':'PASS','scope':'final own-PID/time-window logs','files':15,'findings':bad})
 prune={'.git','.gradle','build','dist','node_modules','.idea','.kotlin','__pycache__'};preservation={}
 for label,base in [('android',ROOT),('backend',Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app'))]:
  before=json.loads((OUT/(label+'-baseline.json')).read_text());now={}
  for d,dirs,files in os.walk(base):
   dirs[:]=[v for v in dirs if v not in prune]
   for n in files:
    p=Path(d)/n
    if OUT in p.parents:continue
    now[str(p.relative_to(base))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'mtime_ns':str(p.stat().st_mtime_ns)}
  changed=[n for n,v in before.items() if now.get(n)!=v];new=sorted(set(now)-set(before));assert not changed
  if label=='backend':assert not new
  assert subprocess.run(['git','diff','--check'],cwd=base,capture_output=True).returncode==0
  preservation[label]={'status':'PASS','existing_files':len(before),'changed_or_missing':changed,'new_files':new,'git_diff_check':0}
 save('preservation.json',preservation)
 index={str(p.relative_to(ROOT)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in (ROOT/'docs/demo/p5e10a').rglob('*') if p.is_file() and p.name!='evidence-index.json'};save('evidence-index.json',index)
 print('Evidence verified: physical15/15, host4/4, cross-role/server/marker equality, source preservation PASS')
if __name__=='__main__':verify()
