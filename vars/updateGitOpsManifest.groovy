def call(String imageRepo, String imageTag) {
    String image = "${imageRepo}:${imageTag}"
    withCredentials([usernamePassword(credentialsId: 'github-https',
            usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        withEnv(["CI_IMAGE=${image}"]) {
            sh '''
                set -eu
                python3 - <<'PY'
from pathlib import Path
import os
p = Path("k8s/app-deployment.yaml")
s = p.read_text()
image = os.environ["CI_IMAGE"]
lines = s.splitlines(keepends=True)
count = 0
for index, line in enumerate(lines):
    if line.strip().startswith("image: rawanm21/ivolve-app:"):
        indent = line[:len(line) - len(line.lstrip())]
        lines[index] = f"{indent}image: {image}" + chr(10)
        count += 1
if count != 1:
    raise SystemExit(f"Expected one app image line, found {count}")
p.write_text("".join(lines))
PY
                git add k8s/app-deployment.yaml
                if git diff --cached --quiet; then
                    echo 'Manifest already points to this image.'
                    exit 0
                fi
                git config user.name 'Jenkins GitOps'
                git config user.email 'jenkins@localhost'
                git commit -m "Update iVolve image to $CI_IMAGE"
                askpass="$(mktemp)"
                trap 'rm -f "$askpass"' EXIT
                cat > "$askpass" <<'ASKPASS'
#!/bin/sh
case "$1" in
  *Username*) printf '%s' "$GIT_USER" ;;
  *Password*) printf '%s' "$GIT_TOKEN" ;;
esac
ASKPASS
                chmod 700 "$askpass"
                set +x
                GIT_ASKPASS="$askpass" GIT_TERMINAL_PROMPT=0 git push origin HEAD:main
            '''
        }
    }
}
