# 3. Kubernetes YAML: App, Database, Storage

The `k8s/` directory is the desired state for this new project. Argo CD watches this directory after bootstrap.

| File | Purpose |
| --- | --- |
| `namespace.yaml` | Creates namespace `ivolve`. |
| `configmap.yaml` | Provides nonsecret database host, user, and name. |
| `storage.yaml` | Static local PersistentVolume for MySQL on a selected worker. |
| `mysql-service.yaml` | Headless Service for the MySQL StatefulSet. |
| `mysql-statefulset.yaml` | MySQL 8 with a 1 GiB PVC. |
| `app-deployment.yaml` | Two Node.js app replicas, database environment, health probes. |
| `app-service.yaml` | Internal ClusterIP Service on port 3000. |

## Prepare storage

The inspected cluster has a `local-storage` StorageClass with no automatic provisioner. The included `storage.yaml` uses worker `ip-172-31-4-135` and path `/mnt/data/ivolve-gitops-mysql`. On a new cluster, edit the hostname and path first; use a storage class available there. On the selected worker:

```bash
sudo mkdir -p /mnt/data/ivolve-gitops-mysql
# Set ownership/permissions appropriate for the MySQL container and node policy.
```

Local storage is tied to that worker. Its data will not automatically move to another node. The PV uses `Retain`, so deleting the claim does not deliberately delete the database files.

## Create the database Secret

Create the Secret directly on the cluster before Argo CD syncs the workload:

```bash
kubectl create namespace ivolve --dry-run=client -o yaml | kubectl apply -f -
kubectl -n ivolve create secret generic ivolve-db \
  --from-literal=MYSQL_ROOT_PASSWORD='<strong-root-password>' \
  --from-literal=MYSQL_APP_PASSWORD='<strong-app-password>'
```

Replace both placeholders. Keep the passwords out of Git and shell history. For a persistent production setup, use a Secret management system rather than a manually maintained Secret.

## Check the manifests

```bash
kubectl apply --dry-run=client -f k8s/
kubectl get pv ivolve-gitops-mysql-pv
kubectl get pvc,pods,svc,deploy,statefulset -n ivolve
kubectl rollout status statefulset/ivolvedb -n ivolve
kubectl rollout status deployment/ivolveapp -n ivolve
kubectl port-forward svc/ivolveapp -n ivolve 3000:3000
```

In another terminal, use `curl -f http://localhost:3000/health`. The app's health endpoint queries MySQL. For a new environment, the first image tag in `app-deployment.yaml` must already exist in the image registry before the app can start; run the Jenkins pipeline or publish that image first.

The original labs used several different Service and Deployment names and node scheduling rules. These files form a new coherent set, so review existing resources in `ivolve` before applying them to the shared lab cluster.
