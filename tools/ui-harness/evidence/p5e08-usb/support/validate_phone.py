from pathlib import Path
import subprocess,json
r=Path(__file__).resolve().parents[5];e=r/'tools/ui-harness/evidence/p5e08-usb';results={}
for name,extra in [('focused',['--tests','authLoginCancellationCannotRestoreSessionAfterClose,authNavigationUsesActualScreensInSubsetHost,warningFloodSelectedTemplateAndBackUseActualCallbacks']),('physical-final',[])]:
 cmd=['python3','tools/ui-harness/run_ui_tests.py','--physical-usb','--evidence-dir',str(e/name)]+extra
 with (e/(name+'-driver.log')).open('w') as log:code=subprocess.run(cmd,cwd=r,stdout=log,stderr=subprocess.STDOUT).returncode
 results[name]={'command':cmd,'exit_code':code};(e/'physical-driver-exits.json').write_text(json.dumps(results,indent=2));print(name,'exit',code,flush=True)
 if code:break
