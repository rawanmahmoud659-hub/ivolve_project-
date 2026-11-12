# Full CI/CD with GitOps Project

This repository brings together the iVolve labs into one end-to-end path: run the Node.js application and MySQL with Docker Compose, build a kubeadm cluster, deploy Kubernetes YAML, build and publish images from a Jenkins agent using a Shared Library, and let Argo CD deploy changes from Git.

## Architecture

```text
Developer commit
      |
      v
Jenkins job on agent1 --> npm install/test --> Docker build --> Trivy scan
      |                                          |
      |                                          v
      |                                      Docker Hub
      v
Update k8s/app-deployment.yaml and push to Git
      |
      v
Argo CD watches k8s/ --> Kubernetes Deployment --> Node.js app
                                                 |
                                                 v
                                         MySQL StatefulSet + PV
```

The same Node.js application can first run locally with Compose. In both environments it serves the frontend on port 3000 and checks MySQL through `/health` and `/ready`.

## Repository Layout

```text
app/                    Node.js app, frontend, Dockerfile
compose/                Local app + MySQL stack
k8s/                    Desired Kubernetes state watched by Argo CD
vars/                   Jenkins Shared Library steps
Jenkinsfile             CI pipeline for the Jenkins agent
argocd-application.yaml Argo CD Application bootstrap manifest
docs/                   Detailed setup and lab evidence
```

## 1. Run the App and Database with Docker Compose

The application code is based on the Node.js app on the lab host. Compose starts a MySQL 8 container, creates the `ivolve` database and `ivolveuser`, and stores database files in a named volume. The app uses the Compose service name `db` to connect to MySQL and writes access logs to another volume.

From `compose/`:

```bash
cp .env.example .env
# Edit .env and replace both example passwords.
docker compose config
docker compose up -d --build
docker compose ps
curl http://localhost:3000/
curl http://localhost:3000/health
curl http://localhost:3000/ready
```

The app starts after MySQL passes its health check. The app itself retries database connection on startup. `/health` and `/ready` query MySQL, so a 200 response checks both components. Compose persists MySQL data in `db_data` and access logs in `app_logs`.

See [Docker Compose steps](docs/01-compose.md) for database and volume checks.

## 2. Create the Kubernetes Cluster

The original Kubernetes lab used Ubuntu, containerd, kubeadm, and Flannel with pod CIDR `10.244.0.0/16`. Configure the control plane and worker nodes, initialize the control plane, install Flannel, and join workers using a newly generated `kubeadm join` command.

Follow [kubeadm cluster setup](docs/02-kubeadm.md). The lab host was inspected on 12 November 2026 and showed a Ready control plane plus two Ready workers. This repository does not reset or reinitialize that live cluster.

## 3. Apply the Kubernetes YAML

The `k8s/` directory defines namespace `ivolve`, a ConfigMap, a MySQL headless Service and StatefulSet, a local PersistentVolume, and the app Deployment and Service. The StatefulSet uses a PVC so MySQL data survives pod recreation. The app Deployment has two replicas and HTTP readiness/liveness checks.

Before first sync:

1. Edit `k8s/storage.yaml` for the chosen worker's hostname and local path. The included hostname is from the inspected lab cluster.
2. Create that directory on the selected worker and ensure it is writable by the MySQL container.
3. Create the `ivolve-db` Secret outside Git, as shown in [Kubernetes deployment](docs/03-kubernetes.md).
4. Push the repository and configure the real repository URL in `argocd-application.yaml`.
5. Build and publish the first image, or edit `k8s/app-deployment.yaml` to an image tag that already exists.

Do not apply this repository's manifests to the current lab namespace without checking name and storage conflicts with the existing workloads.

## 4. Configure Jenkins CI

Create a Jenkins Pipeline job from this repository, with agent label `agent1`. Register this same repository as a Global Pipeline Library named `ivolve-gitops` from branch `main`; its `vars/` directory is at the repository root. The agent needs Node.js/npm, Docker, Trivy, Python 3, and Git, plus permission to use Docker.

Create Jenkins credentials:

| ID | Kind | Used for |
| --- | --- | --- |
| `docker` | Username with password | Docker Hub push |
| `github-https` | Username with password | Git username and token for the manifest commit |

The Jenkinsfile checks out source, installs Node dependencies, runs any available tests, builds and scans the image, pushes it to Docker Hub, and calls the Shared Library to update the image tag in `k8s/app-deployment.yaml`. Jenkins does not run `kubectl apply`; Argo CD owns deployment. Configure job triggers so manifest-only commits do not start an endless build loop.

See [Jenkins CI setup](docs/04-jenkins.md).

## 5. Configure Argo CD CD

Install Argo CD on the cluster, update `argocd-application.yaml` with this repository's URL, then apply it. Argo CD watches `k8s/` on `main` and synchronizes that directory to namespace `ivolve`. A Jenkins image update becomes a Git commit; Argo CD detects it and rolls out the new image.

See [Argo CD GitOps setup](docs/05-argocd.md).

## Verify the End-to-End Flow

```bash
# Compose stage
docker compose -f compose/docker-compose.yml --env-file compose/.env ps

# Cluster and workload stage
kubectl get nodes -o wide
kubectl get pods,deploy,svc,pvc -n ivolve
kubectl rollout status deployment/ivolveapp -n ivolve

# GitOps stage
kubectl get application ivolve-full-cicd -n argocd
kubectl get deployment ivolveapp -n ivolve -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl port-forward svc/ivolveapp -n ivolve 3000:3000
# In another terminal: curl http://localhost:3000/health
```

Check Jenkins logs for the image push and manifest commit, then confirm Argo CD reports `Synced` and `Healthy` for the **new** Application. A healthy historical `demoapp` Application on the lab cluster does not verify this new repository.

## Source and Status

- The Node.js application and frontend were copied from `/home/ubuntu/ivolveapp` on the provided EC2 host. The Compose, Kubernetes, Jenkins, and Argo CD files here were assembled into a new, consistent project and have **not** been deployed to that live cluster.
- The earlier Shared Library and agent lab used a separate Java `demoapp`; its historical screenshots are included only as [lab evidence](docs/06-evidence.md).
- The new repository is local until you add a Git remote and push it. The placeholder URL in `argocd-application.yaml` must be replaced.
