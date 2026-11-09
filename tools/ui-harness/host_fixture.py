"""Owned loopback fixture; synthetic contract responses only, no forwarding/storage/cloud."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import gzip,hashlib,json,time,socket,re,threading

GRAPH = json.dumps({'contractVersion':'2.1.0','graphVersion':'ui_fixture_v1','vehicleProfile':'RESCUE_TRUCK_4X4',
    'nodes':[{'id':'a','lat':0,'lon':0},{'id':'b','lat':0,'lon':0.001}],
    'edges':[{'id':'ab','fromNodeId':'a','toNodeId':'b','lengthMeters':111,'geometry':[{'lat':0,'lon':0},{'lat':0,'lon':0.001}], 'compatibilityStatus':'UNVERIFIED'}], 'turnRestrictions':[]},separators=(',',':')).encode()
GZIP=gzip.compress(GRAPH,mtime=0)
INVALID_GRAPH=b"not-json"
INVALID_GZIP=gzip.compress(INVALID_GRAPH,mtime=0)

def status(port, host="10.0.2.2", invalid=False):
    return {'manifest':{'manifestVersion':'2.1.0','generatedAt':'2000-01-01T00:00:00Z','graphVersion':'ui_fixture_v1',
        'osmSnapshotTimestamp':'2000-01-01T00:00:00Z','importerVersion':'isolated-ui','vehicleProfile':'RESCUE_TRUCK_4X4','boundingBoxType':'FIXTURE',
        'bundle':{'fileName':'BaseRoadGraph.json.gz','compressedSizeBytes':len(INVALID_GZIP if invalid else GZIP),'uncompressedSizeBytes':len(INVALID_GRAPH if invalid else GRAPH),'sha256':hashlib.sha256(INVALID_GZIP if invalid else GZIP).hexdigest()},
        'hazardSnapshotStatus':'NOT_INCLUDED','hazardAssessmentPolicy':'HAZARD_DATA_REQUIRED_FOR_LIVE_CONDITIONS','isRestrictionStrictRoutingEligible':False,
        'restrictionStrictRoutingReadiness':'NOT_READY_NO_VERIFIED_EDGES','unsupportedRestrictionsSummary':{'totalCount':0},'canAttemptStrictRouting':False,
        'overallStrictRoutingReadiness':'NOT_READY_NO_VERIFIED_EDGES','edgeSafetySummary':{'totalEdges':1,'verifiedCompatibleEdges':0,'unverifiedEdges':1,'blockedEdges':0,'conflictedEdges':0,'strictSafeEdgesPercent':0,'blockedWays':0}},
        'download':{'url':f'http://{host}:{port}/graph.gz','expiresIn':900},'downloadAvailable':True,'strictRoutingReady':False,'liveHazardReady':False,'canAttemptStrictRouting':False,'overallStrictRoutingReadiness':'NOT_READY_NO_VERIFIED_EDGES'}

class Fixture(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def barrier(self,name):
        assert re.fullmatch(r'[a-z-]{1,40}',name)
        with self.server.barrier_lock:
            return self.server.barriers.setdefault(name,{key:threading.Event() for key in ['entered','release','done']})
    def await_barrier(self):
        tag=self.headers.get('X-Fixture-Barrier')
        self.active_barrier=None
        if tag:
            self.active_barrier=self.barrier(tag);self.active_barrier['entered'].set()
            if not self.active_barrier['release'].wait(10):
                self.reply(504,{'message':'Owned barrier deadline'});return False
        return True
    def reply(self,code,data,mime='application/json'):
        if not isinstance(data,bytes):data=json.dumps(data).encode()
        self.send_response(code);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(data)));self.send_header('Connection','close');self.end_headers()
        self.close_connection=True
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError):pass
        if getattr(self,'active_barrier',None):self.active_barrier['done'].set()
    def record(self):
        self.server.events.append({'method':self.command,'path':self.path,'destination_host':self.headers.get('Host'),'bearer_present':bool(self.headers.get('Authorization')),'mode':self.headers.get('X-Fixture-Mode','ok'),'barrier':self.headers.get('X-Fixture-Barrier')})
    def do_POST(self):
        self.record()
        length=int(self.headers.get('Content-Length','0'))
        if length<0 or length>4096:return self.reply(413,{})
        body=json.loads(self.rfile.read(length) or b'{}')
        if not self.await_barrier():return
        if self.path=='/auth/firebase-token':
            if self.headers.get('Authorization')!='Bearer p5e02-access-synthetic-only':return self.reply(401,{})
            return self.reply(200,{'success':True,'data':{'customToken':'p5e04-custom-synthetic-only'}})
        if self.path=='/auth/refresh':
            if body.get('refreshToken')!='p5e02-refresh-synthetic-only':return self.reply(401,{})
            if self.headers.get('X-Fixture-Mode')=='refreshReject':return self.reply(401,{})
            return self.reply(200,{'success':True,'data':{'accessToken':'p5e02-access-synthetic-only','refreshToken':'p5e02-refresh-synthetic-only'}})
        if self.path not in ['/auth/register','/auth/login']:return self.reply(404,{})
        if (body.get('email')!='fixture@example.invalid' and not (not body.get('email') and body.get('phone')=='0000000000')) or body.get('password')!='fixture-only-password':return self.reply(400,{'message':'Synthetic fixture fields only'})
        mode=self.headers.get('X-Fixture-Mode','ok')
        if mode in ['401','403','500']:return self.reply(int(mode),{'message':'Owned synthetic fault'})
        if mode=='reset':
            self.connection.shutdown(socket.SHUT_RDWR);self.connection.close();return
        if mode=='timeout':time.sleep(3)
        if mode=='slow':self.server.loading_gate.wait(10)
        self.reply(201,{'success':True,'data':{'accessToken':'p5e02-access-synthetic-only','refreshToken':'p5e02-refresh-synthetic-only','isNewUser':True}})
    def do_PATCH(self):
        self.record()
        if self.path!='/users/me/role':return self.reply(404,{})
        if self.headers.get('Authorization')!='Bearer p5e02-access-synthetic-only':return self.reply(401,{})
        length=int(self.headers.get('Content-Length','0'))
        if length<0 or length>4096:return self.reply(413,{})
        body=json.loads(self.rfile.read(length) or b'{}')
        if not self.await_barrier():return
        fault=self.headers.get('X-Fixture-Role-Fault','ok')
        if fault in ['401','403','500']:return self.reply(int(fault),{})
        if fault=='timeout':time.sleep(3)
        # Displaying the rescuer option is not approval. This fixture never grants it or ADMIN.
        if body.get('role')!='USER':return self.reply(403,{})
        return self.reply(200,{'success':True,'data':{'id':'fixture-user','fullName':'Fixture Person','email':'fixture@example.invalid','phone':'0000000000','role':'USER','status':'ACTIVE','verificationStatus':'PENDING'}})

    def do_GET(self):
        self.record()
        control=re.fullmatch(r'/fixture/barrier/([a-z-]{1,40})/(entered|release|done)',self.path)
        if control:
            gate=self.barrier(control[1]);action=control[2]
            if action=='release':gate['release'].set();return self.reply(200,{})
            return self.reply(200 if gate[action].is_set() else 409,{})
        if not self.await_barrier():return
        if self.path=='/fixture/release-loading':
            self.server.loading_gate.set();return self.reply(200,b'RELEASED','text/plain')
        if self.path=='/auth/me':
            if self.headers.get('X-Fixture-Expire-Access')=='true':return self.reply(401,{})
            if self.headers.get('Authorization')!='Bearer p5e02-access-synthetic-only':return self.reply(401,{})
            if self.headers.get('X-Fixture-Profile-Shape')=='empty':return self.reply(200,{'success':True,'data':{'id':'fixture-user','fullName':None,'email':None,'phone':None,'role':'USER','status':'ACTIVE'}})
            return self.reply(200,{'success':True,'data':{'id':'fixture-user','fullName':'Fixture Person','email':'fixture@example.invalid','role':'USER','status':'ACTIVE'}})
        if self.path=='/health':return self.reply(200,b'P5E02_LOCAL_ONLY','text/plain')
        if self.path=='/routing/current':
            if self.headers.get('Authorization')!='Bearer p5e02-access-synthetic-only':return self.reply(401,{})
            return self.reply(200,status(self.server.server_port,getattr(self.server,'fixture_host','10.0.2.2'),self.headers.get('X-Fixture-Mode')=='invalid'))
        if self.path=='/graph.gz':
            if self.headers.get('Authorization') or self.headers.get('Cookie'):return self.reply(400,{})
            return self.reply(200,INVALID_GZIP if self.headers.get('X-Fixture-Mode')=='invalid' else GZIP,'application/gzip')
        return self.reply(404,{})
