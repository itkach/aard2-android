#!/usr/bin/env bash
# Front-door launcher: `./d <command> [args]` runs a function from scripts/dev.sh
# in a subshell (your interactive shell stays clean). `./d` with no args lists
# the available commands.
here=$(cd "$(dirname "$0")" && pwd)
source "$here/scripts/dev.sh"

if [ $# -eq 0 ]; then
    echo "usage: ./d <command> [args]"
    echo
    grep -hE '^[a-z0-9_-]+\(\)[[:space:]]*\{[[:space:]]*##' "$here/scripts/dev.sh" \
        | sed -E 's/\(\)[[:space:]]*\{[[:space:]]*## /\t/' \
        | sort \
        | while IFS=$'\t' read -r name help; do printf '  %-20s %s\n' "$name" "$help"; done
    exit 0
fi

"$@"
