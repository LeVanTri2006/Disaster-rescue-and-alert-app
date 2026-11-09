from pathlib import Path
import subprocess,os,json,shutil,hashlib,xml.etree.ElementTree as E
r=Path(__file__).resolve().parents[2];e=r/'docs/demo/evidence/jvm-build';e.mkdir()
env=dict(os.environ,JAVA_HOME='/usr/lib/jvm/java-25-openjdk-amd64');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];results={}
for name,tail in [('sos-targeted',[':app:testDebugUnitTest','--tests','com.example.bai1.sos.*']),('routing',[':app:testDebugUnitTest','--rerun-tasks','--tests','com.example.bai1.rescue.routing.*']),('full',[':app:testDebugUnitTest']),('apk',[':app:assembleDebug',':app:processReleaseMainManifest'])]:
 assert shutil.disk_usage('/').free>=2_000_000_000
 cmd=['./gradlew','--offline','--no-daemon']+tail
 with (e/(name+'.log')).open('w') as log:code=subprocess.run(cmd,cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
 row={'command':cmd,'exit_code':code}
 if name!='apk':
  dest=e/(name+'-xml');dest.mkdir()
  for p in (r/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):shutil.copy2(p,dest/p.name)
  suites=[E.parse(p).getroot() for p in dest.glob('*.xml')];row.update(suites=len(suites),**{k:sum(int(t.get(k,0)) for t in suites) for k in ['tests','failures','errors','skipped']})
 else:
  p=r/'app/build/outputs/apk/debug/app-debug.apk'
  if code==0:row.update(sha256=hashlib.sha256(p.read_bytes()).hexdigest(),size=p.stat().st_size)
 results[name]=row;(e/'results.json').write_text(json.dumps(results,indent=2));print(name,row,flush=True)
 if code:break
