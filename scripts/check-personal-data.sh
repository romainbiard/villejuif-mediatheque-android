#!/usr/bin/env bash
# Médiathèque
# SPDX-License-Identifier: MIT
# See LICENSE in the project root for the full license text and copyright notice.
#
# Refuse tout ce qui ressemble à une donnée personnelle dans l'index git :
# e-mails, téléphones, numéros de carte, cookies de session, chemins de dossier personnel,
# fichiers de signature ou de configuration locale.
#
# Seules les valeurs fictives (abonnée « Camille MARTIN », carte 0012345, domaines exemple.fr /
# example.*) et les coordonnées publiques des médiathèques passent : voir .personal-data-allowlist.
#
# Usage : scripts/check-personal-data.sh   (hook pre-commit et CI ; analyse l'index entier)

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

# Correspondances d'une règle, une par ligne (« fichier:ligne:valeur »), moins la liste blanche.
# $2 : options supplémentaires de git grep (`-w` : la valeur doit être un mot entier).
matches() {
    local found
    found=$(git grep --cached -I -n -o ${2:-} -E "$1" -- . "${excluded[@]}" || true)
    [[ -z "$found" ]] && return 0
    if [[ -f "$allowlist" ]]; then
        # Chaque ligne non vide et non commentée de la liste blanche est une expression régulière
        # qui doit couvrir la valeur trouvée (la partie après « fichier:ligne: »).
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

# 1. Fichiers qui n'ont rien à faire dans le dépôt.
forbidden=$(git ls-files --cached | grep -E '(^|/)(local\.properties|keystore\.properties|google-services\.json)$|\.(jks|keystore|p12|pepk)$' || true)
if [[ -n "$forbidden" ]]; then report "Fichier local ou de signature" <<<"$forbidden"; fi

# 2. Adresses e-mail (seuls les domaines fictifs et institutionnels sont admis).
check "Adresse e-mail" '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}'

# 3. Numéros de téléphone français (fixes et mobiles, avec ou sans espaces).
# `-w` : pas de faux positif au milieu d'un horodatage ou d'un identifiant plus long.
check "Numéro de téléphone" '(\+33 ?|0)[1-9]([ .-]?[0-9]{2}){4}' -w

# 4. Numéros de carte d'abonné derrière un identifiant connu du portail.
check "Numéro de carte" '(login|username|lost_username|cardNumber|card_number|iduser|id_abonne)["'\'']?[[:space:]]*[:=][[:space:]]*["'\'']?[0-9]{5,}'

# 5. Cookies et jetons de session ou de SSO.
check "Cookie ou jeton de session" '(PHPSESSID|bunkerweb[A-Za-z_]*)=[A-Za-z0-9%,-]{10,}|[?&](hash|token|ticket|sso)=[A-Za-z0-9]{16,}'

# 6. Chemins de dossier personnel (nom d'utilisateur de la machine).
check "Chemin de dossier personnel" '/(Users|home)/[A-Za-z0-9._-]+/'

if [[ $status -ne 0 ]]; then
    echo >&2
    echo "Données personnelles possibles : anonymisez-les (voir docs/PLAN-PORTAGE.md § 7)," >&2
    echo "ou, si c'est une valeur publique ou fictive, ajoutez-la à $allowlist." >&2
fi
exit $status
