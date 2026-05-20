#!/usr/bin/env python3
"""
extract_mapbox_token.py
-----------------------
Reads the Mapbox public access token from the RFSAT ENACT sensor_placement.html
and writes it into local.properties so the Android build picks it up automatically.

Usage:
    python3 extract_mapbox_token.py [path/to/sensor_placement.html]

If no path is given, the script looks for sensor_placement.html in the same
directory as the script itself.

NOTE: Close Android Studio (or at least close the project) before running,
as the IDE keeps local.properties locked on Windows.
"""

import sys
import re
import os
import tempfile


def find_token(html: str) -> str | None:
    # Primary: TOKEN variable assignment in sensor_placement.html
    # Matches: const TOKEN = "pk.eyJ1..."  /  var TOKEN = 'pk.eyJ1...'
    m = re.search(r'\bTOKEN\s*=\s*["\']([^"\']+)["\']', html)
    if m:
        return m.group(1)
    # Fallback: bare pk.eyJ1... anywhere in the source
    m = re.search(r'(pk\.eyJ1[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)', html)
    return m.group(1) if m else None


def update_props(props: str, token: str) -> str:
    """Insert or replace the MAPBOX_ACCESS_TOKEN line."""
    if re.search(r'^MAPBOX_ACCESS_TOKEN=', props, flags=re.MULTILINE):
        return re.sub(
            r'^MAPBOX_ACCESS_TOKEN=.*$',
            f'MAPBOX_ACCESS_TOKEN={token}',
            props, flags=re.MULTILINE
        )
    return props.rstrip('\n') + f'\nMAPBOX_ACCESS_TOKEN={token}\n'


def write_file(path: str, content: str) -> None:
    """Write content to path via a sibling temp file to avoid lock issues."""
    dir_ = os.path.dirname(os.path.abspath(path))
    # Write to a named temp file in the SAME directory so os.replace is atomic
    fd, tmp = tempfile.mkstemp(dir=dir_, prefix='.mapbox_tmp_', suffix='.properties')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as f:
            f.write(content)
        os.replace(tmp, path)   # atomic on Windows NTFS; works even if target exists
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def main():
    # ── Locate sensor_placement.html ──────────────────────────────────────────
    if len(sys.argv) > 1:
        html_path = sys.argv[1]
    else:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        candidates = [
            os.path.join(script_dir, 'sensor_placement.html'),
            os.path.join(script_dir, '..', '..', 'HORIZON-ENACT', 'sensor_placement.html'),
        ]
        html_path = next((p for p in candidates if os.path.exists(p)), None)
        if html_path is None:
            print('ERROR: sensor_placement.html not found.')
            print('Usage: python3 extract_mapbox_token.py /path/to/sensor_placement.html')
            sys.exit(1)

    # ── Read and parse ─────────────────────────────────────────────────────────
    with open(html_path, encoding='utf-8', errors='replace') as f:
        html = f.read()

    token = find_token(html)
    if token is None:
        print('ERROR: No Mapbox token found in the HTML file.')
        sys.exit(1)

    print(f'Found token: {token[:30]}...{token[-6:]}')
    print()

    # ── Write local.properties ─────────────────────────────────────────────────
    props_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'local.properties')

    props = ''
    if os.path.exists(props_path):
        with open(props_path, 'r', encoding='utf-8', errors='replace') as f:
            props = f.read()

    updated = update_props(props, token)

    try:
        write_file(props_path, updated)
        print(f'Token written to: {props_path}')
        print("Run './gradlew assembleDebug' to build.")
    except OSError as e:
        # File is locked (Android Studio open) — print manual instructions
        print(f'WARNING: Could not write to local.properties: {e}')
        print()
        print('Close Android Studio and re-run, OR add this line manually:')
        print()
        print(f'  MAPBOX_ACCESS_TOKEN={token}')
        print()
        print(f'File to edit: {props_path}')
        sys.exit(2)


if __name__ == '__main__':
    main()
