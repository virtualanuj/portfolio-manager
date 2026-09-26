# Source this file: `source scripts/env.sh`
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  . "$NVM_DIR/nvm.sh"
  _repo_root="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
  (cd "$_repo_root" && nvm use >/dev/null) && nvm use --silent "$(cat "$_repo_root/.nvmrc")"
  unset _repo_root
fi
