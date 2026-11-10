#!/usr/bin/env python3
"""Host-only HTTP fixture for Android 10.0.2.2; never forwards traffic or reads credentials."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys

class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        assert not self.headers.get('Authorization'), 'Unexpected authorization at health fixture'
        if self.path != '/p5e01/health':
            self.send_error(404)
            return
        self.server.health_requests = getattr(self.server, 'health_requests', 0) + 1
        data = b'P5E01_LOCAL_ONLY'
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def log_message(self, *args):
        pass

if __name__ == '__main__':
    with ThreadingHTTPServer(('127.0.0.1', 0), Fixture) as server:
        Path(sys.argv[1]).write_text(str(server.server_port))
        server.serve_forever()
