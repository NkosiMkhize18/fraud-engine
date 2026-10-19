#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  echo ".env already exists; leaving it unchanged."
  exit 0
fi

while IFS= read -r line; do
  case "$line" in
    [A-Z_]*=) printf '%s%s\n' "$line" "$(openssl rand -hex 16)" ;;
    *) printf '%s\n' "$line" ;;
  esac
done < .env.example > .env

echo "Created .env with generated values. Start the stack with: docker compose --profile app up --build -d --wait"
