# 4. Jenkins CI with Shared Library and Agent

The earlier labs used a Jenkins agent named `agent1` and Shared Library functions for a Java `demoapp`. This project adapts that pattern to the Node.js iVolve app. The pipeline in this repository has not been run against the live Jenkins instance.

## Configure Jenkins

1. Connect a Linux agent with label `agent1`. Install Node.js/npm, Docker, Trivy, Python 3, and Git on it. Verify the agent can reach Docker Hub and the Git remote.
2. In **Manage Jenkins → System → Global Trusted Pipeline Libraries** (or the equivalent library configuration), add library `ivolve-gitops`, default version `main`, and this repository as the Git SCM source. The library functions live in root-level `vars/`.
3. Add a Jenkins **Username with password** credential named `docker` for Docker Hub.
4. Add a Jenkins **Username with password** credential named `github-https`; its username is the Git account and its password is a token allowed to push to this repository.
5. Create a Pipeline job from SCM using this repository's `Jenkinsfile` on `main`.
6. Configure triggers or path filters so commits that change only `k8s/` do not start another full image build.

The Jenkinsfile uses the Shared Library in `vars/buildAndPushImage.groovy` and `vars/updateGitOpsManifest.groovy`.

## Pipeline stages

1. **Checkout:** Check out the pipeline's Git repository.
2. **Install and Test:** Run `npm ci` and `npm test --if-present` in `app/`. The current app has no test script, so this stage validates installation but does not claim test coverage.
3. **Build and Push Image:** Build `rawanm21/ivolve-app:<BUILD_NUMBER>`, scan for high and critical findings with Trivy, authenticate using the `docker` credential, and push the image.
4. **Update GitOps Manifest:** Change exactly one image line in `k8s/app-deployment.yaml`, commit it, and push to `main` using the `github-https` credential.

The GitOps update is intentionally after the image push. Argo CD sees a manifest only after the referenced image exists. Jenkins does not apply Kubernetes YAML directly.

## Verify

In the Jenkins console, confirm the image build, scan, push, and Git commit succeeded. Then inspect:

```bash
git log -1 --oneline
git show --stat HEAD
grep 'image:' k8s/app-deployment.yaml
```

If the scan fails, the image is not pushed. If the Git push fails, the image may exist in Docker Hub while the deployed manifest still points at the previous tag. Correct Git access and rerun with a new build number.

The sample Jenkinsfile expects the image repository `rawanm21/ivolve-app`; change that value and the matching image pattern in the Shared Library when using another registry or account.
