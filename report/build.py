#!/usr/bin/env python3
"""Builds the report assets with headless Chrome.

    python3 report/build.py              # both steps
    python3 report/build.py screenshots  # evidence/*.txt   -> screenshots/*.png
    python3 report/build.py pdf          # report-*.html    -> report-*.pdf

The images are renderings of the real transcripts stored in evidence/ (each one
is the exact command that was run followed by its unedited output). Needs only
Python 3 and Chrome or Chromium; nothing to install.
"""

import html
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
EVIDENCE = HERE / "evidence"
SCREENSHOTS = HERE / "screenshots"

# Title shown in the bar above each transcript.
TITLES = {
    "01-tests": "Tests: ./scripts/run-tests.sh",
    "02-new-task.client": "Client: single requests to the new task and the original tasks",
    "02-new-task.server": "Server: compiled and started locally (default cached pool)",
    "03-concurrent-cached.client": "Client: 8 requests sent together (cached pool)",
    "03-concurrent-cached.server": "Server log: cached pool, one thread per concurrent request",
    "04-concurrent-fixed4.client": "Client: 8 requests sent together (POOL_SIZE=4)",
    "04-concurrent-fixed4.server": "Server log: fixed pool of 4 threads",
    "05-concurrent-fixed1.client": "Client: 8 requests sent together (POOL_SIZE=1)",
    "05-concurrent-fixed1.server": "Server log: fixed pool of 1 thread",
    "06-docker-build": "Docker: building the image",
    "07-docker-run": "Docker: running the container and calling it from the host",
    "08-docker-concurrent.client": "Client on the host: 8 requests sent together to the container",
    "08-docker-concurrent.logs": "Container log: docker logs task-manager",
}

COLUMNS = 100          # long lines wrap here, as they would in a 100-column terminal
CHAR_WIDTH = 7.83      # Menlo at 13px
LINE_HEIGHT = 19
BAR_HEIGHT = 30
PADDING_X, PADDING_Y = 16, 12

# One colour per pool thread so interleaved lines are easy to follow.
THREAD_COLOURS = ["#79c0ff", "#f2cc60", "#ff7b72", "#d2a8ff",
                  "#56d364", "#ffa657", "#39c5cf", "#f778ba"]

LOG_LINE = re.compile(r"^(\d\d:\d\d:\d\d\.\d{3}) \[([^\]]+)\] (.*)$")
POOL_THREAD = re.compile(r"pool-\d+-thread-(\d+)$")

PAGE = """<!doctype html>
<meta charset="utf-8">
<style>
  html, body {{ margin: 0; background: #0d1117; }}
  .bar {{ height: {bar}px; padding: 0 {px}px; background: #21262d; color: #c9d1d9;
          font: 600 12px/{bar}px -apple-system, "Helvetica Neue", Arial, sans-serif; }}
  pre {{ margin: 0; padding: {py}px {px}px; color: #c9d1d9; white-space: pre;
         font: 13px/{lh}px Menlo, "DejaVu Sans Mono", Consolas, monospace; }}
  .prompt {{ color: #7ee787; }}
  .command {{ color: #ffffff; font-weight: bold; }}
  .dim {{ color: #8b949e; }}
  .pass {{ color: #56d364; }}
  .fail {{ color: #ff7b72; }}
</style>
<div class="bar">{title}</div>
<pre>{body}</pre>
"""


def find_chrome():
    candidates = [
        os.environ.get("CHROME"),
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/Applications/Chromium.app/Contents/MacOS/Chromium",
        shutil.which("google-chrome"),
        shutil.which("chromium"),
        shutil.which("chromium-browser"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    sys.exit("Chrome/Chromium not found. Set the CHROME environment variable to its path.")


def run_chrome(chrome, *arguments):
    with tempfile.TemporaryDirectory() as profile:
        subprocess.run(
            [chrome, "--headless=new", "--disable-gpu", "--no-first-run",
             f"--user-data-dir={profile}", *arguments],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)


def wrap(line):
    """Splits a line into terminal rows of at most COLUMNS characters."""
    return [line[i:i + COLUMNS] for i in range(0, len(line), COLUMNS)] or [""]


def render_line(line):
    """Returns the HTML rows for one transcript line."""
    rows = wrap(line)
    escaped = [html.escape(row) for row in rows]

    if line.startswith("$ "):
        first = '<span class="prompt">$</span> <span class="command">%s</span>' % html.escape(rows[0][2:])
        return [first] + ['<span class="command">%s</span>' % row for row in escaped[1:]]

    log = LOG_LINE.match(line)
    if log and len(rows) == 1:
        time, thread, message = log.groups()
        pool_thread = POOL_THREAD.match(thread)
        if pool_thread:
            colour = THREAD_COLOURS[(int(pool_thread.group(1)) - 1) % len(THREAD_COLOURS)]
            tag = '<span style="color:%s">[%s]</span>' % (colour, html.escape(thread))
        else:
            tag = '<span class="dim">[%s]</span>' % html.escape(thread)
        return ['<span class="dim">%s</span> %s %s' % (time, tag, html.escape(message))]

    if line.startswith("PASS "):
        return ['<span class="pass">PASS</span>' + escaped[0][4:]] + escaped[1:]
    if line.startswith("FAIL "):
        return ['<span class="fail">FAIL</span>' + escaped[0][4:]] + escaped[1:]
    return escaped


def build_screenshots(chrome):
    SCREENSHOTS.mkdir(exist_ok=True)
    width = math.ceil(2 * PADDING_X + COLUMNS * CHAR_WIDTH) + 4

    with tempfile.TemporaryDirectory() as work:
        for transcript in sorted(EVIDENCE.glob("*.txt")):
            name = transcript.stem
            rows = []
            for line in transcript.read_text(encoding="utf-8").rstrip("\n").split("\n"):
                rows.extend(render_line(line.expandtabs(8)))

            page = Path(work) / (name + ".html")
            page.write_text(PAGE.format(
                title=html.escape(TITLES.get(name, name)), body="\n".join(rows),
                bar=BAR_HEIGHT, px=PADDING_X, py=PADDING_Y, lh=LINE_HEIGHT), encoding="utf-8")

            height = BAR_HEIGHT + 2 * PADDING_Y + len(rows) * LINE_HEIGHT
            target = SCREENSHOTS / (name + ".png")
            run_chrome(chrome, "--hide-scrollbars", "--force-device-scale-factor=2",
                       f"--window-size={width},{height}", f"--screenshot={target}", page.as_uri())
            print(f"screenshots/{target.name}  ({len(rows)} rows)")


def build_pdfs(chrome):
    for source in sorted(HERE.glob("report-*.html")):
        target = source.with_suffix(".pdf")
        run_chrome(chrome, "--no-pdf-header-footer", f"--print-to-pdf={target}", source.as_uri())
        print(target.name)


def main():
    steps = sys.argv[1:] or ["screenshots", "pdf"]
    chrome = find_chrome()
    if "screenshots" in steps:
        build_screenshots(chrome)
    if "pdf" in steps:
        build_pdfs(chrome)


if __name__ == "__main__":
    main()
