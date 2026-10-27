"""RCON client that reads commands from a UTF-8 file (one command per line).

Usage: python rcon_file.py <host> <port> <password> <cmdfile>

Same wire protocol as rcon_probe.py, but commands come from a file so that
quotes / SNBT braces never have to survive a shell argument round-trip.
Lines starting with '#' and blank lines are skipped.
"""
import socket
import struct
import sys
import time

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2


def recv_exact(sock, size):
    buf = b""
    while len(buf) < size:
        chunk = sock.recv(size - len(buf))
        if not chunk:
            raise ConnectionError("connection closed by server")
        buf += chunk
    return buf


def read_packet(sock):
    (length,) = struct.unpack("<i", recv_exact(sock, 4))
    payload = recv_exact(sock, length)
    req_id, ptype = struct.unpack("<ii", payload[:8])
    body = payload[8:-2]
    return req_id, ptype, body


def send_packet(sock, req_id, ptype, body):
    data = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)


def main():
    host, port, password = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    path = sys.argv[4]

    with open(path, "r", encoding="utf-8") as fh:
        commands = [
            line.rstrip("\r\n")
            for line in fh
            if line.strip() and not line.lstrip().startswith("#")
        ]

    sock = socket.create_connection((host, port), timeout=20)
    send_packet(sock, 1, SERVERDATA_AUTH, password)
    req_id, ptype, _ = read_packet(sock)
    if ptype != SERVERDATA_AUTH_RESPONSE or req_id == -1:
        print("AUTH FAILED type=%s id=%s" % (ptype, req_id))
        return 1
    print("AUTH OK (%d commands)" % len(commands))

    for index, command in enumerate(commands):
        send_packet(sock, 100 + index, SERVERDATA_EXECCOMMAND, command)
        chunks = []
        sock.settimeout(4.0)
        while True:
            try:
                req_id, ptype, body = read_packet(sock)
            except socket.timeout:
                break
            chunks.append(body.decode("utf-8", "replace"))
            if len(chunks) > 200:
                break
        sock.settimeout(20)
        text = "".join(chunks)
        print("===== CMD[%d]: %s =====" % (index, command))
        print(text)
        time.sleep(0.5)

    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
