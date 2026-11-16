#!/usr/bin/env bash
# Médiathèque
# SPDX-License-Identifier: MIT
# See LICENSE in the project root for the full license text and copyright notice.
#
# Rejects anything that looks like personal data in the git index:
# emails, phone numbers, card numbers, session cookies, home directory paths,
# signing files or local configuration files.
#
# Only fictitious values (subscriber "Camille MARTIN", card 0012345, domains exemple.fr /
# example.*) and the libraries' public contact details get through: see .personal-data-allowlist.
#
# Usage: scripts/check-personal-data.sh   (pre-commit hook and CI; scans the entire index)

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

allowlist=".personal-data-allowlist"
excluded=(':!scripts/check-personal-data.sh' ":!$allowlist")
status=0

report() {
    echo "✖ $1" >&2
    sed 's/^/    /' >&2
    status=1
}

# Matches of a rule, one per line ("file:line:value"), minus the allowlist.
# $2: extra git grep options (`-w`: the value must be a whole word).
matches() {
    local found
    found=$(git grep --cached -I -n -o ${2:-} -E "$1" -- . "${excluded[@]}" || true)
    [[ -z "$found" ]] && return 0
    if [[ -f "$allowlist" ]]; then
        # Each non-empty, non-comment line of the allowlist is a regular expression
        # that must cover the value found (the part after "file:line:").
        local patterns
        patterns=$(grep -vE '^\s*(#|$)' "$allowlist" || true)
        found=$(while IFS= read -r line; do
            value=${line#*:*:}
            if [[ -z "$patterns" ]] || ! grep -qE -- "$patterns" <<<"$value"; then echo "$line"; fi
        done <<<"$found")
    fi
    [[ -n "$found" ]] && echo "$found"
    return 0
}

check() {
    local name=$1 regex=$2 found
    found=$(matches "$regex" "${3:-}")
    if [[ -n "$found" ]]; then report "$name" <<<"$found"; fi
}

# 1. Files that have no business being in the repo.
forbidden=$(git ls-files --cached | grep -E '(^|/)(local\.properties|keystore\.properties|google-services\.json)$|\.(jks|keystore|p12|pepk)$' || true)
if [[ -n "$forbidden" ]]; then report "Local or signing file" <<<"$forbidden"; fi

# 2. Email addresses (only fictitious and institutional domains are allowed).
check "Email address" '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}'

# 3. French phone numbers (landline and mobile, with or without spaces).
# `-w`: no false positive in the middle of a timestamp or a longer identifier.
check "Phone number" '(\+33 ?|0)[1-9]([ .-]?[0-9]{2}){4}' -w

# 4. Subscriber card numbers behind a known portal identifier.
check "Card number" '(login|username|lost_username|cardNumber|card_number|iduser|id_abonne)["'\'']?[[:space:]]*[:=][[:space:]]*["'\'']?[0-9]{5,}'

# 5. Session or SSO cookies and tokens.
check "Session cookie or token" '(PHPSESSID|bunkerweb[A-Za-z_]*)=[A-Za-z0-9%,-]{10,}|[?&](hash|token|ticket|sso)=[A-Za-z0-9]{16,}'

# 6. Home directory paths (the machine's user name).
check "Home directory path" '/(Users|home)/[A-Za-z0-9._-]+/'

if [[ $status -ne 0 ]]; then
    echo >&2
    echo "Possible personal data: anonymize it (see docs/PLAN-PORTAGE.md § 7)," >&2
    echo "or, if it is a public or fictitious value, add it to $allowlist." >&2
fi
exit $status
