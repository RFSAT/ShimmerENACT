#!/usr/bin/env python3
"""
extract_mapbox_token.py
-----------------------
Reads the Mapbox public access token from the RFSAT ENACT sensor_placement.html
and writes it into local.properties so the Android build picks it up.

The token is stored in sensor_placement.html in a variable named TOKEN.

Usage:
    python3 extract_mapbox_token.py [path/to/sensor_placement.html]

If no path is given, looks for sensor_placement.html in the same directory
as this script. Close Android Studio before running on Windows.
"""

import sys
import re
import os
import tempfile


def find_token(html: str) -> str | None:
    # Primary: TOKEN variable assignment in sensor_placement.html
    m = re.search(r'\bTOKEN\s*=\s*["\']([^"\']+)["\']', html)
    if m:
        return m.group(1)
    # Fallback: bare pk.eyJ1... anywhere in the source
    m = re.search(r'(pk\.eyJ1[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)', html)
    return m.group(1) if m else None


def update_props(props: str, token: str) -> str:
    if re.search(r'^MAPBOX_ACCESS_TOKEN=', props, flags=re.MULTILINE):
        return re.sub(
            r'^MAPBOX_ACCESS_TOKEN=.*$',
            f'MAPBOX_ACCESS_TOKEN={token}',
            props, flags=re.MULTILINE
        )
    return props.rstrip('\n') + f'\nMAPBOX_ACCESS_TOKEN={token}\n'


def write_file(path: str, content: str) -> None:
    """Write via temp-file rename to avoid Windows file-lock issues."""
    dir_ = os.path.dirname(os.path.abspath(path))
    fd, tmp = tempfile.mkstemp(dir=dir_, prefix='.mapbox_tmp_', suffix='.properties')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as f:
            f.write(content)
        os.replace(tmp, path)
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def main():
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

    with open(html_path, encoding='utf-8', errors='replace') as f:
        html = f.read()

    token = find_token(html)
    if token is None:
        print('ERROR: No Mapbox token (TOKEN = "pk.eyJ1...") found in the HTML file.')
        sys.exit(1)

    print(f'Found token: {token[:30]}...{token[-6:]}')

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
        print(f'WARNING: Could not write to local.properties: {e}')
        print()
        print('Close Android Studio and re-run, OR add this line manually:')
        print(f'  MAPBOX_ACCESS_TOKEN={token}')
        print(f'File: {props_path}')
        sys.exit(2)


if __name__ == '__main__':
    main()
