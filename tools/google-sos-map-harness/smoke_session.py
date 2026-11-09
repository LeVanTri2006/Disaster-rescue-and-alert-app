"""Smoke the actual launch script, then interact only with its verified foreground UI."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
import json,time,threading,xml.etree.ElementTree as E
OUT=ROOT/'docs/demo/gmaps02/evidence/startup-smoke-final'
def main():
 assert not OUT.exists()
 serial,adb=phone()
 cmd=[sys.executable,str(HERE/'demo_session.py'),'--install','--keep-demo','--seconds','50','--evidence-dir',str(OUT)]
 proc=subprocess.Popen(cmd,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 output=[]
 def pump():
  for line in proc.stdout:output.append(redact(line).replace(serial,'REDACTED'))
 thread=threading.Thread(target=pump);thread.start()
 def own_nodes():
  import uuid
  path='/data/local/tmp/gmaps02-'+str(uuid.uuid4())+'.xml'
  assert adb('shell','test','-e',path).returncode==1
  try:
   dumped=adb('shell','uiautomator','dump',path);assert dumped.returncode==0
   result=adb('shell','cat',path);assert result.returncode==0;text=result.stdout
  finally:adb('shell','rm',path) # Only the unique temporary file created by this call.
  start=text.find('<?xml');end=text.rfind('</hierarchy>')
  if start<0 or end<0:return []
  root=E.fromstring(text[start:end+12]);nodes=list(root.iter('node'));packages={n.get('package') for n in nodes if n.get('package')}
  if PKG not in packages or packages-{PKG,'com.android.systemui'}:return []
  return nodes
 def click(text):
  end=time.monotonic()+20
  while time.monotonic()<end:
   for n in own_nodes():
    if n.get('text')==text:
     import re
     v=list(map(int,re.findall(r'\d+',n.get('bounds'))));assert len(v)==4
     assert adb('shell','input','tap',str((v[0]+v[2])//2),str((v[1]+v[3])//2)).returncode==0;return
   time.sleep(.25)
  raise AssertionError('Owned UI control not found: '+text)
 try:
  end=time.monotonic()+25
  while not any('READY_LOGIN' in x for x in output):assert proc.poll() is None and time.monotonic()<end;time.sleep(.2)
  click('Đội cứu hộ demo')
  end=time.monotonic()+30
  while True:
   nodes=own_nodes()
   if any(n.get('text')=='SDK: LOADED_CALLBACK' for n in nodes):break
   assert time.monotonic()<end and proc.poll() is None;time.sleep(.3)
  click('Refresh SOS')
  assert own_nodes(),'Own foreground required before screenshot'
  shot=subprocess.run([ADB,'-s',serial,'exec-out','screencap','-p'],capture_output=True);assert shot.returncode==0 and shot.stdout.startswith(b'\x89PNG');(OUT/'startup-tiles.png').write_bytes(shot.stdout)
  proc.wait(timeout=60);thread.join(10);(OUT/'launcher-output.txt').write_text(''.join(output));result=json.loads((OUT/'session.json').read_text());assert result['status']=='PASS' and result['start_exit']==0
  assert any(e['path']=='/auth/login' and e.get('synthetic_account_role')=='rescuer' for e in result['fixture_events'])
  assert any(e['path']=='/sos/active' and e.get('response')==[] for e in result['fixture_events'])
  assert 'M02_MAP' in (OUT/'own-pid-logcat.txt').read_text() and 'onMapLoaded' in (OUT/'own-pid-logcat.txt').read_text()
  (OUT/'smoke.json').write_text(json.dumps({'status':'PASS','command':cmd,'exit':proc.returncode,'actual_launch':True,'actual_own_ui_login':True,'actual_empty_get':True,'actual_map_loaded':True,'retained_own_demo':True,'visual_review_required':'startup-tiles.png'},indent=2));print('Actual M02 startup smoke PASS')
 finally:
  if proc.poll() is None:proc.send_signal(__import__('signal').SIGINT);proc.wait(20);thread.join(10)
  if OUT.exists():(OUT/'launcher-output.txt').write_text(''.join(output))
if __name__=='__main__':main()
