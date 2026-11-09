"""Verify configuration-only APK. Never installs, starts Maps or loads a key."""
import sys
sys.dont_write_bytecode=True
import os,json,re,hashlib,shutil,subprocess,zipfile,xml.etree.ElementTree as E
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent;OUT=ROOT/'docs/demo/gmaps01/evidence';ART=ROOT/'docs/demo/gmaps01/artifacts';PKG='com.example.bai1.p5e10maps.demo';A='{http://schemas.android.com/apk/res/android}'
def save(n,v):(OUT/n).write_text(json.dumps(v,indent=2))
def main():
 assert 'BUILD SUCCESSFUL' in (OUT/'build-final.log').read_text()
 apk=next((HERE/'build/outputs/apk/debug').glob('*.apk'));manifest=HERE/'build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml';m=E.parse(manifest).getroot();app=m.find('application')
 assert m.get('package')==PKG and m.get(A+'sharedUserId') is None
 assert app.get(A+'name') is None and app.get(A+'allowBackup')=='false' and app.get(A+'usesCleartextTraffic')=='false'
 permissions={p.get(A+'name') for p in m.findall('uses-permission')};assert permissions=={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE',PKG+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
 assert all(app.find(n) is None for n in ['provider','service','receiver'])
 acts={a.get(A+'name'):a.get(A+'exported') for a in app.findall('activity')};assert acts=={'com.example.bai1.gmaps01.ConfigurationActivity':'true','com.google.android.gms.common.api.GoogleApiActivity':'false'}
 assert {x.get(A+'name') for x in app.findall('meta-data')}=={'com.google.android.gms.version'}
 shutil.copy2(manifest,OUT/'configuration-manifest.xml')
 dep=(OUT/'dependency-graph.txt').read_text();assert 'FAILED' not in dep
 coordinates=re.findall(r'com.google.android.gms:(play-services-[\w-]+):([\d.]+)(?: -> ([\d.]+))?',dep)
 modules=sorted(set((name,selected or requested) for name,requested,selected in coordinates))
 assert modules==[('play-services-base','18.9.0'),('play-services-basement','18.9.0'),('play-services-maps','19.0.0'),('play-services-tasks','18.4.0')]
 assert {x[0] for x in modules}=={'play-services-maps','play-services-base','play-services-basement','play-services-tasks'}
 assert 'com.google.firebase:' not in dep and 'play-services-location' not in dep and 'play-services-auth' not in dep
 with zipfile.ZipFile(apk) as z:
  entries=[z.read(n) for n in z.namelist() if not n.endswith('/')];dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
 for s in ['Lcom/google/android/gms/maps/MapView;','Lcom/google/maps/android/compose/GoogleMapKt;']:assert s.encode() in dex
 forbidden=['Lcom/google/android/gms/location/FusedLocationProviderClient;','Landroidx/credentials/','Lcom/example/bai1/auth/','Lcom/example/bai1/sos/','Lcom/example/bai1/rescue/','Lcom/example/bai1/DisasterApplication;','Lcom/example/bai1/MainActivity;']
 for s in forbidden:assert s.encode() not in dex
 firebase_refs=sorted(set(x.decode() for x in re.findall(rb'Lcom/google/firebase/[\w/$]+;',dex)))
 allowed_firebase_compat=['Lcom/google/firebase/FirebaseApiNotAvailableException;','Lcom/google/firebase/FirebaseException;','Lcom/google/firebase/FirebaseExceptionMapper;']
 assert firebase_refs==allowed_firebase_compat # Defined by play-services-basement18.9.0 classes.jar; no Firebase SDK/startup
 # Values stay in memory only; never output or copy production keys/configuration.
 keys=[l.split('=',1)[1].strip() for l in (ROOT/'local.properties').read_text().splitlines() if l.startswith('MAPS_API_KEY=')];keys=[v for v in keys if v and v!='PLACEHOLDER']
 assert not any(v.encode(enc) in block for v in keys for enc in ['utf-8','utf-16le'] for block in entries)
 java=Path('/usr/lib/jvm/java-25-openjdk-amd64');env=dict(os.environ,JAVA_HOME=str(java));env['PATH']=str(java/'bin')+':'+env['PATH']
 sign=subprocess.run(['/home/vantri/Android/Sdk/build-tools/36.0.0/apksigner','verify','--verbose','--print-certs',str(apk)],capture_output=True,text=True,env=env);assert sign.returncode==0;(OUT/'signing-certificate.txt').write_text(sign.stdout)
 sha1=re.search(r'Signer #1 certificate SHA-1 digest: ([0-9a-fA-F]+)',sign.stdout).group(1).upper();sha1=':'.join(sha1[i:i+2] for i in range(0,len(sha1),2))
 policy=subprocess.check_output(['/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2','dump','xmltree',str(apk),'--file','res/xml/maps_network_security.xml'],text=True);assert 'cleartextTrafficPermitted=true' not in policy;(OUT/'compiled-policy.txt').write_text(policy)
 ART.mkdir(parents=True,exist_ok=True);dest=ART/'gmaps01-configuration.apk';shutil.copy2(apk,dest);digest=hashlib.sha256(dest.read_bytes()).hexdigest();(ART/'SHA256SUMS').write_text(digest+'  '+dest.name+'\n')
 save('configuration-gate.json',{'status':'PASS_CONFIGURATION_ONLY','package':PKG,'apk_sha256':digest,'signing_certificate_sha1':sha1,'permissions':sorted(permissions),'gms_modules':modules,'maps_sdk_and_compose_dex_present':True,'forbidden_business_sdk_matches':0,'gms_basement_exception_compatibility_classes':firebase_refs,'production_key_values_checked':len(keys),'production_key_matches':0,'api_key_metadata_present':False,'maps_initialized':False,'physical_maps_tests_run':0,'device_install_performed':False})
 save('device-results.json',{'status':'BLOCKED','reason':'No explicitly authorized demo key/package-SHA1 restriction available. User phase-B stop rule.','physical_maps_tests_passed':0,'physical_maps_tests_executed':0,'tests':[{'id':'M01-T'+str(i).zfill(2),'status':'NOT_RUN','reason':'PHASE_B_CONFIGURATION_STOP'} for i in range(1,16)],'instrumentation_xml':'NOT_GENERATED_NO_INSTRUMENTATION_EXECUTED','logcat':'NOT_COLLECTED_NO_APP_TEST_WINDOW','tiles_visual_proof':'NOT_RUN'})
 print('CONFIGURATION GATE PASS; demo key BLOCKED; package',PKG,'certificate SHA-1',sha1)
if __name__=='__main__':main()
