#!/usr/bin/env python3
"""Build/audit first, then run only the isolated package on the existing emulator.
No production APK installation, external services, device wipe or shared logcat capture.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
import zipfile
from http.server import ThreadingHTTPServer
import sys
sys.dont_write_bytecode = True
from host_fixture import Fixture

HARNESS = Path(__file__).resolve().parent
REPO = HARNESS.parents[1]
ADB = '/home/vantri/Android/Sdk/platform-tools/adb'
AAPT = '/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2'
PACKAGE = 'com.example.bai1.p5e01.isolated'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--variant', choices=['debug', 'releaseLike'], required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--permission-state', choices=['granted', 'denied', 'revoked'], default='granted')
    args = parser.parse_args()
    assert args.variant == 'debug' or args.permission_state == 'granted'
    evidence = args.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, JAVA_HOME='/usr/lib/jvm/java-25-openjdk-amd64', ANDROID_HOME='/home/vantri/Android/Sdk', ANDROID_SERIAL='emulator-5554')
    env['PATH'] = env['JAVA_HOME'] + '/bin:' + env['PATH']
    def adb(*cmd):
        return subprocess.run([ADB, '-s', 'emulator-5554', *cmd], text=True, capture_output=True, check=False)
    assert adb('get-state').stdout.strip() == 'device'
    assert adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() == '1'
    # Refuse to overwrite any existing package/data in either owned namespace.
    for pkg in (PACKAGE, PACKAGE + '.test'):
        assert 'package:' not in adb('shell', 'pm', 'path', pkg).stdout, 'Namespace already installed: refuse overwrite'
    cap = args.variant[0].upper() + args.variant[1:]
    gradle = [str(REPO / 'gradlew'), '-p', str(HARNESS), '--offline', '--no-daemon', '-PpolicyVariant=' + args.variant]
    result = {'variant': args.variant, 'device': 'emulator-5554', 'commands': [], 'permission_state': args.permission_state}
    build = gradle + ['assemble' + cap, 'assemble' + cap + 'AndroidTest']
    with (evidence / 'build.log').open('w') as out:
        code = subprocess.run(build, cwd=REPO, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
    result['commands'].append({'command': build, 'exit_code': code})
    assert code == 0, 'Harness build failed before installation'
    apk = next((HARNESS / 'build/outputs/apk' / args.variant).glob('*.apk'))
    manifest = HARNESS / 'build/intermediates/merged_manifests' / args.variant / ('process' + cap + 'Manifest') / 'AndroidManifest.xml'
    tree = ET.parse(manifest).getroot()
    assert tree.attrib['package'] == PACKAGE
    application = tree.find('application')
    assert application.get(ANDROID + 'name') == 'android.app.Application'
    assert application.get(ANDROID + 'usesCleartextTraffic') == 'false'
    assert application.get(ANDROID + 'allowBackup') == 'false'
    assert application.get(ANDROID + 'networkSecurityConfig') == '@xml/routing_network_security_config'
    for tag in ('provider', 'service', 'receiver', 'meta-data'):
        assert application.find(tag) is None, 'Unexpected initialization component'
    assert [a.get(ANDROID + 'name') for a in application.findall('activity')] == ['com.example.bai1.fixture.FixtureActivity']
    assert {p.get(ANDROID + 'name') for p in tree.findall('uses-permission')} == {'android.permission.INTERNET', 'android.permission.ACCESS_LOCAL_NETWORK'}
    with zipfile.ZipFile(apk) as z:
        dex = b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
        for forbidden in (b'Lcom/google/firebase/', b'Lcom/google/android/gms/', b'Lcom/example/bai1/auth/',
                          b'Lcom/example/bai1/sos/network/OkHttpSosApiClient;', b'Lcom/example/bai1/MainActivity;'):
            assert forbidden not in dex, 'Cloud/production class unexpectedly packaged'
        assert b'Lcom/example/bai1/rescue/routing/network/OkHttpRoutingApiClient;' in dex
        assert b'Lcom/example/bai1/rescue/routing/download/OkHttpGraphDownloader;' in dex
    # Release resource optimization may rename the ZIP entry: resolve the table entry, never guess.
    import re
    resources = subprocess.check_output([AAPT, 'dump', 'resources', str(apk)], text=True)
    match = re.search(r'xml/routing_network_security_config\s+\(\) \(file\) (\S+)', resources)
    assert match is not None, 'Compiled network policy missing'
    result['compiled_policy_entry'] = match.group(1)
    policy = subprocess.check_output([AAPT, 'dump', 'xmltree', str(apk), '--file', match.group(1)], text=True)
    (evidence / 'compiled-policy.txt').write_text(policy)
    if args.variant == 'debug':
        for host in ('10.0.2.2', '127.0.0.1', 'localhost'):
            assert "T: '" + host + "'" in policy
    else:
        assert 'cleartextTrafficPermitted=true' not in policy
        assert 'domain-config' in policy and "T: 'localhost'" in policy
        assert policy.count('cleartextTrafficPermitted=false') == 2
        assert 'includeSubdomains=true' not in policy
        assert application.get(ANDROID + 'debuggable', 'false') == 'false'
    result['preinstall_isolation'] = 'PASS'
    result['apk'] = {'path': str(apk), 'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}
    print('PREINSTALL ISOLATION PASS: ' + args.variant, flush=True)
    fixture = ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
    thread = threading.Thread(target=fixture.serve_forever, daemon=True)
    thread.start()
    cls = 'DebugDeviceRuntimeTest' if args.variant == 'debug' else 'ReleasePolicyDeviceRuntimeTest'
    if args.permission_state != 'granted':
        cls = 'LocalPermissionDeniedDeviceRuntimeTest'
    test_apk = next((HARNESS / 'build/outputs/apk/androidTest' / args.variant).glob('*.apk'))
    command = [ADB, '-s', 'emulator-5554', 'shell', 'am', 'instrument', '-w', '-r',
               '-e', 'class', 'com.example.bai1.fixture.' + cls,
               '-e', 'listener', 'com.example.bai1.fixture.DeviceXmlListener',
               '-e', 'hostFixturePort', str(fixture.server_port),
               PACKAGE + '.test/com.example.bai1.fixture.IsolatedInstrumentationRunner']
    logcat = None
    seen_pid = None
    installed = []
    try:
        for pkg, artifact in ((PACKAGE, apk), (PACKAGE + '.test', test_apk)):
            install = subprocess.run([ADB, '-s', 'emulator-5554', 'install', '-t', str(artifact)], capture_output=True, text=True)
            result['commands'].append({'command': [ADB, '-s', 'emulator-5554', 'install', '-t', str(artifact)], 'exit_code': install.returncode})
            assert install.returncode == 0, 'Owned harness installation failed'
            installed.append(pkg)
        if args.permission_state != 'denied':
            grant = adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.ACCESS_LOCAL_NETWORK')
            result['commands'].append({'command': [ADB, '-s', 'emulator-5554', 'shell', 'pm', 'grant', PACKAGE, 'android.permission.ACCESS_LOCAL_NETWORK'], 'exit_code': grant.returncode})
            assert grant.returncode == 0, 'API37 local-network permission grant failed for isolated package'
        if args.permission_state == 'revoked':
            revoke = adb('shell', 'pm', 'revoke', PACKAGE, 'android.permission.ACCESS_LOCAL_NETWORK')
            result['commands'].append({'command': [ADB, '-s', 'emulator-5554', 'shell', 'pm', 'revoke', PACKAGE, 'android.permission.ACCESS_LOCAL_NETWORK'], 'exit_code': revoke.returncode})
            assert revoke.returncode == 0, 'Harness permission revocation failed'
        with (evidence / 'instrumentation.txt').open('w') as out, (evidence / 'owned-process-logcat.txt').open('w') as logs:
            since = adb('shell', 'date "+%m-%d %H:%M:%S.000"').stdout.strip()
            assert len(since) == 18, 'Cannot establish device log time window'
            result['logcat_since_device_time'] = since
            proc = subprocess.Popen(command, cwd=REPO, env=env, stdout=out, stderr=subprocess.STDOUT)
            while proc.poll() is None:
                if logcat is None:
                    pid = adb('shell', 'pidof', PACKAGE).stdout.strip()
                    if pid.isdigit():
                        seen_pid = pid
                        logcat = subprocess.Popen([ADB, '-s', 'emulator-5554', 'logcat', '--pid=' + pid, '-v', 'threadtime', '-T', since], stdout=logs, stderr=subprocess.STDOUT)
                time.sleep(0.3)
            result['commands'].append({'command': command, 'exit_code': proc.returncode})
    finally:
        if logcat:
            logcat.terminate(); logcat.wait(timeout=10)
        fixture.shutdown(); fixture.server_close(); thread.join(timeout=5)
        for pkg in reversed(installed):
            removed = adb('uninstall', pkg)
            result['commands'].append({'command': [ADB, '-s', 'emulator-5554', 'uninstall', pkg], 'exit_code': removed.returncode})
    import base64
    text = (evidence / 'instrumentation.txt').read_text()
    prefix = 'INSTRUMENTATION_STATUS: p5e01XmlBase64='
    encoded = next((line[len(prefix):] for line in text.splitlines() if line.startswith(prefix)), None)
    result['host_health_requests'] = getattr(fixture, 'health_requests', 0)
    result['logcat_pid'] = seen_pid
    policy_prefix = 'INSTRUMENTATION_STATUS: p5e01PolicySnapshot='
    result['policy_observations'] = [json.loads(line[len(policy_prefix):]) for line in text.splitlines() if line.startswith(policy_prefix)]
    result['tests'] = []
    if encoded:
        data = base64.b64decode(encoded).decode()
        (evidence / 'TEST-device-runtime.xml').write_text(data)
        t = ET.fromstring(data)
        result['tests'].append({**t.attrib, 'cases': [c.get('name') for c in t.findall('testcase')]})
    # Only uninstall namespaces proven absent before this invocation; no other packages/data touched.
    result['owned_packages_remaining'] = [pkg for pkg in (PACKAGE, PACKAGE + '.test') if 'package:' in adb('shell', 'pm', 'path', pkg).stdout]
    result['device_assertions'] = 'PASS' if result['tests'] and all(int(row.get(k, '0')) == 0 for row in result['tests'] for k in ('failures', 'errors', 'skipped')) else 'FAIL'
    (evidence / 'results.json').write_text(json.dumps(result, indent=2))
    print(json.dumps({'variant': args.variant, 'exit_code': proc.returncode, 'suites': len(result['tests']), 'logcat_pid': seen_pid}), flush=True)
    assert encoded is not None, 'Instrumentation did not export JUnit runtime results'
    assert t.get('junitRunCount') == t.get('tests') and int(t.get('tests', '0')) > 0
    assert all(int(t.get(k, '0')) == 0 for k in ('failures', 'errors', 'skipped')), 'Device assertions failed'
    assert not result['owned_packages_remaining']
    raise SystemExit(proc.returncode)

if __name__ == '__main__':
    main()
