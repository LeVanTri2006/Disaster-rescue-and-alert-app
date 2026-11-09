"""Bounded real-USB startup/cleanup smoke for the owned presentation script."""
import sys
sys.dont_write_bytecode=True
import subprocess,signal,time,json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/demo/p5e10a/evidence';ADB='/home/vantri/Android/Sdk/platform-tools/adb';PKG='com.example.bai1.p5e10a.demo'
def main():
 assert json.loads((OUT/'physical-complete/results.json').read_text())['status']=='PASS'
 lines=subprocess.check_output([ADB,'devices','-l'],text=True).splitlines()[1:];phones=[x.split()[0] for x in lines if 'usb:' in x and not x.startswith('emulator')];assert len(phones)==1;s=phones[0]
 def adb(*a):return subprocess.run([ADB,'-s',s,*a],capture_output=True,text=True)
 assert 'package:' not in adb('shell','pm','path',PKG).stdout
 before=adb('reverse','--list').stdout.replace(s,'REDACTED')
 path=OUT/'session-smoke.log';ready=False
 with path.open('w') as log:
  p=subprocess.Popen([sys.executable,str(ROOT/'tools/sos-map-harness/demo_session.py'),'--install'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
  until=time.monotonic()+60
  try:
   while p.poll() is None and time.monotonic()<until:
    if 'DEMO READY' in path.read_text():ready=True;break
    time.sleep(.25)
  finally:
   if p.poll() is None:p.send_signal(signal.SIGINT)
   try:code=p.wait(timeout=30)
   except subprocess.TimeoutExpired:p.terminate();code=p.wait(timeout=10)
 after=adb('reverse','--list').stdout.replace(s,'REDACTED');absent='package:' not in adb('shell','pm','path',PKG).stdout
 r={'status':'PASS' if ready and code==0 and absent and before==after else 'FAIL','actual_am_start_ready':ready,'script_exit':code,'owned_package_absent_after':absent,'reverse_restored':before==after,'serial':'REDACTED','keep_demo':False,'apk_sha256':hashlib.sha256((ROOT/'docs/demo/p5e10a/artifacts/p5e10a-demo.apk').read_bytes()).hexdigest()}
 (OUT/'session-smoke.json').write_text(json.dumps(r,indent=2));assert r['status']=='PASS';print('Owned presentation startup/cleanup PASS')
if __name__=='__main__':main()
