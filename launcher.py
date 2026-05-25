import http.server
import os
import socket
import socketserver
import sys
import threading
import webbrowser
from pathlib import Path

try:
    import webview  # type: ignore
except Exception:
    webview = None


ROOT = Path(__file__).resolve().parent


def resource_path() -> Path:
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS)
    return ROOT


def find_free_port(start: int = 8000) -> int:
    port = start
    while port < 9000:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            try:
                s.bind(("127.0.0.1", port))
                return port
            except OSError:
                port += 1
    raise RuntimeError("사용 가능한 포트를 찾지 못했습니다.")


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        return


def run_server(base_dir: Path, port: int):
    os.chdir(base_dir)
    with socketserver.TCPServer(("127.0.0.1", port), QuietHandler) as httpd:
        httpd.serve_forever()


def main():
    base_dir = resource_path()
    port = find_free_port()
    url = f"http://127.0.0.1:{port}/index.html"

    t = threading.Thread(target=run_server, args=(base_dir, port), daemon=True)
    t.start()

    if webview:
        webview.create_window("4K Rhythm Game", url, width=1000, height=720)
        webview.start()
    else:
        webbrowser.open(url)
        input("브라우저에서 게임을 종료한 뒤 Enter를 누르세요...")


if __name__ == "__main__":
    main()
