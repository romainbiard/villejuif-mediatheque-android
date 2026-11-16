#!/usr/bin/env python3
# Médiathèque
# SPDX-License-Identifier: MIT
# See LICENSE in the project root for the full license text and copyright notice.
#
# Source code comments must be in US English. Flags comment lines that contain French outside of quotes:
# accented letters or common French words. Quoted text ("…", `…`, '…') is allowed, since comments often
# quote the portal's French pages verbatim.
#
# Not checked: portal captures (test fixtures), docs and the README. In string resources, only the XML comments are
# checked: the string values are the localized UI.
#
# Usage: scripts/check_english_comments.py   (pre-commit hook and CI; checks the whole git index)

import re
import subprocess
import sys

CODE_FILE = re.compile(r"\.(kt|kts|java|xml|sh|py|yml|yaml|toml|pro|properties)$|(^|/)\.(gitignore|gitattributes|editorconfig)$|^\.githooks/")
SKIPPED = re.compile(r"(^|/)testFixtures/resources/|^docs/|README\.md$|^gradlew")

# Start of a comment: `//` (not inside a URL), `/*`, a KDoc continuation `*`, `#` or `<!--`.
COMMENT = re.compile(r"(?<![:\"'\w])//.*|/\*.*|^\s*\*.*|^\s*#.*|<!--.*?(?:-->|$)")
QUOTED = re.compile(r"\"[^\"]*\"|`[^`]*`|'[^'\s][^']*'|«[^»]*»")
ACCENTS = re.compile(r"[àâçéèêëîïôûùüÿœ]", re.IGNORECASE)
FRENCH_WORDS = re.compile(
    r"\b(le|la|les|des|une|du|pour|avec|dans|sont|est|qui|que|sur|aux|pas|cette|ces|depuis|quand|mais|donc|"
    r"ou|où|sans|selon|sinon|alors|chaque|comme|fichier|ligne|seulement|jamais|toujours|lire|voir)\b",
    re.IGNORECASE,
)
# Proper names: the app (license header) and partner services.
ALLOWED = re.compile(r"Médiathèque|Eurêka")


def comment_part(line: str) -> str | None:
    match = COMMENT.search(line)
    return match.group(0) if match else None


def main() -> int:
    files = subprocess.run(["git", "ls-files", "--cached"], capture_output=True, text=True, check=True).stdout.split("\n")
    problems = []
    for path in files:
        if not path or not CODE_FILE.search(path) or SKIPPED.search(path):
            continue
        content = subprocess.run(["git", "show", f":{path}"], capture_output=True, check=True).stdout
        try:
            text = content.decode("utf-8")
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            comment = comment_part(line)
            if not comment:
                continue
            bare = ALLOWED.sub("", QUOTED.sub("", comment))
            if ACCENTS.search(bare) or FRENCH_WORDS.search(bare):
                problems.append(f"    {path}:{number}: {line.strip()}")
    if problems:
        print("✖ French in source code comments (write them in US English; quote site text verbatim):", file=sys.stderr)
        print("\n".join(problems), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
