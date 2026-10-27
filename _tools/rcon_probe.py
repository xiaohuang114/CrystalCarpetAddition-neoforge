"""Minimal Source RCON client: send a command, print the response.

Usage: python rcon_probe.py <host> <port> <password> <command> [command ...]
ASCII only (no non-ASCII literals) so Windows console codepages never break it.
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
    commands = sys.argv[4:]

    sock = socket.create_connection((host, port), timeout=15)
    send_packet(sock, 1, SERVERDATA_AUTH, password)
    req_id, ptype, _ = read_packet(sock)
    if ptype != SERVERDATA_AUTH_RESPONSE or req_id == -1:
        print("AUTH FAILED type=%s id=%s" % (ptype, req_id))
        return 1
    print("AUTH OK")

    for index, command in enumerate(commands):
        send_packet(sock, 100 + index, SERVERDATA_EXECCOMMAND, command)
        chunks = []
        sock.settimeout(3.0)
        while True:
            try:
                req_id, ptype, body = read_packet(sock)
            except socket.timeout:
                break
            chunks.append(body.decode("utf-8", "replace"))
            if len(chunks) > 200:
                break
        sock.settimeout(15)
        text = "".join(chunks)
        print("===== CMD: %s =====" % command)
        print(text)
        time.sleep(0.5)

    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
