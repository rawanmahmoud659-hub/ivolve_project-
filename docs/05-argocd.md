# 5. Argo CD Continuous Delivery

Argo CD implements the CD part of this project. It watches the `k8s/` directory in Git and reconciles that state to the cluster. Jenkins publishes the image and commits a new tag; Argo CD performs the rollout.

## Bootstrap

For a new cluster, install Argo CD using its [official getting-started guide](https://argo-cd.readthedocs.io/en/stable/getting_started/):

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl get pods -n argocd
```

For repeatable environments, pin a tested Argo CD release instead of the moving `stable` reference. The inspected lab cluster already has Argo CD; do not reinstall it there. Then:

1. Push this local project to a new Git repository on branch `main`.
2. Replace `https://github.com/YOUR_ACCOUNT/YOUR_REPOSITORY.git` in `argocd-application.yaml` with that repository's URL.
3. If private, configure Argo CD repository credentials.
4. Prepare local storage and the `ivolve-db` Secret as described in [Kubernetes setup](03-kubernetes.md).
5. Ensure the image tag in `k8s/app-deployment.yaml` exists in the registry.
6. Apply the Application manifest:

```bash
kubectl apply -f argocd-application.yaml
kubectl get application ivolve-full-cicd -n argocd
kubectl get pods -n ivolve
```

The Application uses automatic sync, prune, and self-heal. Review these settings before connecting it to a namespace with existing lab resources: prune can delete tracked resources removed from Git, and overlapping Argo CD Applications can conflict.

## Verify a change

Run the Jenkins job after changing app source. It should publish a new image and commit the tag change. Then check:

```bash
kubectl get application ivolve-full-cicd -n argocd \
  -o jsonpath='{.status.sync.status}{"\n"}{.status.health.status}{"\n"}'
kubectl rollout status deployment/ivolveapp -n ivolve --timeout=180s
kubectl get deployment ivolveapp -n ivolve \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

The desired result is `Synced`, `Healthy`, and the image tag from the latest Jenkins build. If sync fails, inspect Application conditions and verify repository URL, branch, path, Secret, storage, and image access.

The live lab cluster's existing Argo CD Application `ivolveapp` tracks a **different Java demoapp repository** and was observed as `Synced` and `Healthy`. It is historical evidence for the GitOps pattern, not a deployment of this new Node.js repository.
