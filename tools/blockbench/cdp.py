"""Tiny Chrome DevTools Protocol client (stdlib only) for driving Blockbench.

Usage as a module:
    from cdp import BB
    bb = BB()
    print(bb.js("Blockbench.version"))
Usage from the shell:
    python3 -I cdp.py "<js expression>"        (prints the JSON result)
    python3 -I cdp.py -f script.js             (runs a file; its last expression is the result)
"""
import base64, http.client, json, os, socket, struct, sys
from urllib.parse import urlparse


class BB:
    def __init__(self, port=9222):
        conn = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
        conn.request("GET", "/json")
        pages = [t for t in json.loads(conn.getresponse().read()) if t["type"] == "page"]
        url = urlparse(pages[0]["webSocketDebuggerUrl"])
        self.sock = socket.create_connection((url.hostname, url.port), timeout=600)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f"GET {url.path} HTTP/1.1\r\nHost: {url.hostname}:{url.port}\r\nUpgrade: websocket\r\n"
                           f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        resp = b""
        while b"\r\n\r\n" not in resp:
            resp += self.sock.recv(4096)
        if b" 101 " not in resp.split(b"\r\n")[0]:
            raise RuntimeError(resp.decode(errors="replace"))
        self.buf = resp.split(b"\r\n\r\n", 1)[1]
        self.next_id = 1

    def _send(self, text):
        data = text.encode()
        head = bytearray([0x81])
        n = len(data)
        if n < 126:
            head.append(0x80 | n)
        elif n < 65536:
            head.append(0x80 | 126); head += struct.pack(">H", n)
        else:
            head.append(0x80 | 127); head += struct.pack(">Q", n)
        mask = os.urandom(4)
        head += mask
        self.sock.sendall(bytes(head) + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def _read(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(1 << 20)
            if not chunk:
                raise ConnectionError("closed")
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def _recv(self):
        message = b""
        while True:
            b0, b1 = self._read(2)
            n = b1 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            payload = self._read(n)
            if (b0 & 0x0F) == 0x9:  # ping
                continue
            message += payload
            if b0 & 0x80:
                return message.decode()

    def call(self, method, **params):
        mid = self.next_id
        self.next_id += 1
        self._send(json.dumps({"id": mid, "method": method, "params": params}))
        while True:
            msg = json.loads(self._recv())
            if msg.get("id") == mid:
                if "error" in msg:
                    raise RuntimeError(msg["error"])
                return msg["result"]

    def js(self, expression, await_promise=True):
        r = self.call("Runtime.evaluate", expression=expression, awaitPromise=await_promise, returnByValue=True)
        if "exceptionDetails" in r:
            d = r["exceptionDetails"]
            raise RuntimeError(d.get("exception", {}).get("description") or d.get("text"))
        return r["result"].get("value")


if __name__ == "__main__":
    bb = BB()
    if sys.argv[1] == "-f":
        expr = open(sys.argv[2]).read()
    else:
        expr = sys.argv[1]
    print(json.dumps(bb.js(expr), indent=1))
