#!/usr/bin/env python3
# Médiathèque
# SPDX-License-Identifier: MIT
# See LICENSE in the project root for the full license text and copyright notice.
#
# Adds Material Symbols (Rounded, 24 px, Apache 2.0, github.com/google/material-design-icons) to the design system
# as vector drawables: ic_<name>.xml, and ic_<name>_filled.xml with --filled (selected navigation items).
#
# Usage: scripts/material_symbol.py [--filled] account_circle search …

import argparse
import pathlib
import re
import urllib.request

SOURCE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{name}/materialsymbolsrounded/{name}{variant}_24px.svg"
TARGET = pathlib.Path(__file__).resolve().parent.parent / "core/designsystem/src/main/res/drawable"

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}"{variant_note} (Apache 2.0), added by scripts/material_symbol.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="{mirrored}"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
{paths}    </group>
</vector>
"""

# Symbols that point in a reading direction and must flip in right-to-left languages.
MIRRORED = {"arrow_back", "arrow_forward", "chevron_right", "chevron_left", "open_in_new", "logout", "login"}


def convert(name: str, filled: bool) -> pathlib.Path:
    variant = "_fill1" if filled else ""
    with urllib.request.urlopen(SOURCE.format(name=name, variant=variant), timeout=20) as response:
        svg = response.read().decode()
    paths = "".join(
        f'        <path\n            android:fillColor="#FF000000"\n            android:pathData="{d}" />\n'
        for d in re.findall(r'<path d="([^"]+)"', svg)
    )
    if not paths:
        raise SystemExit(f"No path in {name}{variant}")
    output = TARGET / f"ic_{name}{'_filled' if filled else ''}.xml"
    output.write_text(
        TEMPLATE.format(
            name=name,
            variant_note=", filled" if filled else "",
            mirrored="true" if name in MIRRORED else "false",
            paths=paths,
        )
    )
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--filled", action="store_true", help="also add the filled variant")
    parser.add_argument("names", nargs="+")
    arguments = parser.parse_args()
    TARGET.mkdir(parents=True, exist_ok=True)
    for name in arguments.names:
        print(convert(name, filled=False).name)
        if arguments.filled:
            print(convert(name, filled=True).name)


if __name__ == "__main__":
    main()
