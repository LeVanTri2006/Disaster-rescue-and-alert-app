"""Pre-install gate for actual Maps APKs. Key-bearing manifest is redacted before export."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
import json,hashlib,shutil,zipfile,xml.etree.ElementTree as E
A='{http://schemas.android.com/apk/res/android}'
OUT=ROOT/'docs/demo/gmaps01/evidence/runtime';ART=ROOT/'docs/demo/gmaps01/artifacts'
def gate(variant):
 assert 'namespace = "com.example.bai1.p5e10maps.demo"' in (HERE/"build.gradle.kts").read_text()
 pkg=PKG+('.negative' if variant=='negative' else '');v=variant[0].upper()+variant[1:]
 apk=next((HERE/f'build/outputs/apk/{variant}').glob('*.apk'));test=next((HERE/f'build/outputs/apk/androidTest/{variant}').glob('*.apk'))
 path=HERE/f'build/intermediates/merged_manifests/{variant}/process{v}Manifest/AndroidManifest.xml';m=E.parse(path).getroot();app=m.find('application')
 assert m.get('package')==pkg and m.get(A+'sharedUserId') is None
 assert app.get(A+'name') is None and app.get(A+'allowBackup')=='false' and app.get(A+'usesCleartextTraffic')=='false'
 assert {x.get(A+'name') for x in m.findall('uses-permission')}=={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE',pkg+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
 assert all(app.find(n) is None for n in ['service','receiver','provider'])
 assert {x.get(A+'name'):x.get(A+'exported') for x in app.findall('activity')}=={'com.example.bai1.gmaps01.MapsDemoActivity':'true','com.google.android.gms.common.api.GoogleApiActivity':'false'}
 meta={x.get(A+'name'):x for x in app.findall('meta-data')};assert set(meta)=={'com.google.android.geo.API_KEY','com.google.android.gms.version'}
 actual=meta['com.google.android.geo.API_KEY'].get(A+'value')
 if variant=='debug':assert actual and actual in SECRETS and actual==(os.environ.get('GMAPS01_API_KEY') or SECRETS[-1])
 else:assert actual=='M01_INVALID_SYNTHETIC_KEY'
 meta['com.google.android.geo.API_KEY'].set(A+'value','[REDACTED]');E.ElementTree(m).write(OUT/(variant+'-manifest-redacted.xml'),encoding='utf-8',xml_declaration=True)
 tm=HERE/f'build/intermediates/packaged_manifests/{variant}AndroidTest/process{v}AndroidTestManifest/AndroidManifest.xml';t=E.parse(tm).getroot();assert t.get('package')==pkg+'.test' and t.find('instrumentation').get(A+'targetPackage')==pkg
 ta=t.find('application');assert ta.get(A+'name') is None and all(ta.find(n) is None for n in ['provider','receiver','service','meta-data'])
 assert {x.get(A+'name') for x in ta.findall('activity')}=={'androidx.test.core.app.InstrumentationActivityInvoker$BootstrapActivity','androidx.test.core.app.InstrumentationActivityInvoker$EmptyActivity','androidx.test.core.app.InstrumentationActivityInvoker$EmptyFloatingActivity'}
 (OUT/(variant+'-test-manifest.xml')).write_text(redact(tm.read_text()))
 def dex(p):
  with zipfile.ZipFile(p) as z:return b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
 d=dex(apk);td=dex(test)
 for name in ['Lcom/google/android/gms/maps/MapView;','Lcom/google/maps/android/compose/GoogleMapKt;','Lcom/example/bai1/gmaps01/MapsDemoActivity;']:assert name.encode() in d
 forbidden=['Lcom/google/android/gms/location/FusedLocationProviderClient;','Landroidx/credentials/','Lcom/example/bai1/auth/','Lcom/example/bai1/sos/','Lcom/example/bai1/rescue/','Lcom/example/bai1/DisasterApplication;','Lcom/example/bai1/MainActivity;']
 for value in forbidden:assert value.encode() not in d and value.encode() not in td
 compat=['Lcom/google/firebase/FirebaseApiNotAvailableException;','Lcom/google/firebase/FirebaseException;','Lcom/google/firebase/FirebaseExceptionMapper;']
 assert sorted(set(x.decode() for x in re.findall(rb'Lcom/google/firebase/[\w/$]+;',d)))==compat
 assert not re.search(rb'Lcom/google/firebase/[\w/$]+;',td)
 sign=subprocess.run([SIGN,'verify','--verbose','--print-certs',str(apk)],capture_output=True,text=True,env=ENV);assert sign.returncode==0
 sha=re.search(r'certificate SHA-1 digest: ([a-f0-9]+)',sign.stdout).group(1).upper();sha=':'.join(sha[i:i+2] for i in range(0,len(sha),2));assert sha=='E4:EA:13:1A:81:21:D3:34:86:B8:3B:EA:64:F1:A4:1C:68:F0:80:07';(OUT/(variant+'-certificate.txt')).write_text(sign.stdout)
 policy=subprocess.check_output([AAPT,'dump','xmltree',str(apk),'--file','res/xml/maps_network_security.xml'],text=True);assert 'cleartextTrafficPermitted=true' not in policy;(OUT/(variant+'-policy.txt')).write_text(policy)
 ART.mkdir(exist_ok=True)
 paths=[]
 for source,name in [(apk,f'gmaps01-{variant}.apk'),(test,f'gmaps01-{variant}-test.apk')]:dest=ART/name;shutil.copy2(source,dest);paths.append(dest)
 result={'status':'PASS','variant':variant,'package':pkg,'key_metadata_present':True,'key_matches_authorized_input':variant=='debug','negative_key_is_synthetic':variant=='negative','key_value':'REDACTED','signing_sha1':sha,'permissions':['INTERNET','ACCESS_NETWORK_STATE','own AndroidX signature'],'business_firebase_fused_location_matches':0,'gms_compatibility_exceptions':compat,'artifacts':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
 (OUT/(variant+'-gate.json')).write_text(json.dumps(result,indent=2));return result
if __name__=='__main__':
 rows=[gate(v) for v in ['debug','negative']];(OUT/'runtime-gates.json').write_text(json.dumps(rows,indent=2))
 names=['gmaps01-configuration.apk','gmaps01-debug.apk','gmaps01-debug-test.apk','gmaps01-negative.apk','gmaps01-negative-test.apk']
 (ART/'SHA256SUMS').write_text(''.join(hashlib.sha256((ART/n).read_bytes()).hexdigest()+'  '+n+'\n' for n in names));print('Preinstall gates PASS: real Maps SDK/Compose, no GPS/business startup; key REDACTED')
