"""Finalize BLOCKED phase-B evidence without changing any phone/Cloud/backend state."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
import os,json,hashlib,subprocess,zipfile,io
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/demo/gmaps01/evidence';PKG='com.example.bai1.p5e10maps.demo'
def save(n,v):(OUT/n).write_text(json.dumps(v,indent=2))
def main():
 gate=json.loads((OUT/'configuration-gate.json').read_text());assert gate['status']=='PASS_CONFIGURATION_ONLY' and not gate['api_key_metadata_present'] and gate['physical_maps_tests_run']==0
 cache=Path('/home/vantri/.gradle/caches/modules-2/files-2.1');p=next((cache/'com.google.android.gms/play-services-basement/18.9.0').rglob('*.aar'))
 with zipfile.ZipFile(p) as z:
  with zipfile.ZipFile(io.BytesIO(z.read('classes.jar'))) as jar:compat=sorted(n for n in jar.namelist() if n.startswith('com/google/firebase/'))
 assert compat==['com/google/firebase/FirebaseApiNotAvailableException.class','com/google/firebase/FirebaseException.class','com/google/firebase/FirebaseExceptionMapper.class']
 save('gms-compatibility-origin.json',{'artifact':'com.google.android.gms:play-services-basement:18.9.0','aar_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'classes':compat,'firebase_sdk_dependencies':False})
 adb='/home/vantri/Android/Sdk/platform-tools/adb';lines=subprocess.check_output([adb,'devices','-l'],text=True).splitlines()[1:];phones=[l.split()[0] for l in lines if 'usb:' in l and not l.startswith('emulator')];assert len(phones)==1;s=phones[0]
 def call(*a):return subprocess.run([adb,'-s',s,*a],capture_output=True,text=True)
 assert call('get-state').stdout.strip()=='device';p=call('shell','pm','path',PKG);assert 'package:' not in p.stdout and not p.stderr.strip();assert call('get-state').stdout.strip()=='device'
 save('phone-final.json',{'serial':'REDACTED','authorized':True,'demo_package_absent':True,'installs':0,'uninstalls':0,'activity_starts':0,'reverse_changes':0,'permission_or_setting_changes':0,'private_data_reads':0,'note':'Read-only preflight/public GMS package metadata only; no Maps runtime test'})
 prune={'.git','.gradle','build','dist','node_modules','.idea','.kotlin','__pycache__'};preserve={}
 for name,base in [('android',ROOT),('backend',Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app'))]:
  before=json.loads((OUT/(name+'-baseline.json')).read_text());now={}
  for folder,dirs,files in os.walk(base):
   dirs[:]=[n for n in dirs if n not in prune]
   for n in files:
    p=Path(folder)/n
    if OUT in p.parents:continue
    now[str(p.relative_to(base))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'mtime_ns':str(p.stat().st_mtime_ns)}
  changed=[n for n,v in before.items() if now.get(n)!=v];new=sorted(set(now)-set(before));assert not changed,(name,changed)
  if name=='backend':assert not new
  d=subprocess.run(['git','diff','--check'],cwd=base,capture_output=True,text=True);assert d.returncode==0
  preserve[name]={'status':'PASS','baseline_files':len(before),'changed_or_missing':changed,'new_files':new,'git_diff_check_exit':0}
 save('preservation.json',preserve)
 source={str(p.relative_to(ROOT)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in (ROOT/'tools/google-maps-harness').rglob('*') if p.is_file() and not any(n in prune for n in p.relative_to(ROOT/'tools/google-maps-harness').parts)};save('new-source-hashes.json',source)
 report=ROOT/'docs/demo/P5_E10_M01_GOOGLE_MAPS_REPORT.md'
 index={str(p.relative_to(ROOT)):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in (ROOT/'docs/demo/gmaps01').rglob('*') if p.is_file() and p.name!='evidence-index.json'};index[str(report.relative_to(ROOT))]={'bytes':report.stat().st_size,'sha256':hashlib.sha256(report.read_bytes()).hexdigest()};save('evidence-index.json',index)
 print('Final: BLOCKED_NO_DEMO_KEY; configuration gate PASS; 0 physical Maps tests; existing files unchanged',[(k,v['baseline_files']) for k,v in preserve.items()])
if __name__=='__main__':main()
