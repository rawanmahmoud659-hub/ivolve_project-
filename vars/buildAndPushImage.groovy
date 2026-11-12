def call(String imageRepo, String imageTag) {
    String image = "${imageRepo}:${imageTag}"
    dir('app') {
        sh "docker build -t '${image}' ."
        sh "trivy image --exit-code 1 --severity HIGH,CRITICAL '${image}'"
        withCredentials([usernamePassword(credentialsId: 'docker',
                usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
            withEnv(["CI_IMAGE=${image}"]) {
                sh '''
                    set +x
                    export DOCKER_CONFIG="$(mktemp -d)"
                    trap 'rm -rf "$DOCKER_CONFIG"' EXIT
                    printf '%s' "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                    docker push "$CI_IMAGE"
                '''
            }
        }
        sh "docker image rm '${image}' || true"
    }
}
