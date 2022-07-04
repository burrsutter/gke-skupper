# GKE + Skupper CLI

I am using the following Google Regions and I am not sure if this requires quota adjustment to allow for the lauching of 3 node GKE clusters

Frankfurt europe-west3 - has the frontend+backend

Sydney australia-southeast1 - backend-only

Montréal northamerica-northeast1 - backend-only



```
export KUBE_EDITOR="code -w"
export PATH=~/devnation/bin:$PATH
```

## Download Skupper CLI

https://skupper.io/install/index.html


```
skupper version
client version                 1.0.0
transport version              not-found
controller version             not-found
config-sync version            not-found
```

The transport, controller and config-sync versions populate after `skupper init`

```
gcloud container clusters list
```

```
# To address the following warning
# WARNING: the gcp auth plugin is deprecated in v1.22+, unavailable in v1.25+; use gcloud instead.
# To learn more, consult https://cloud.google.com/blog/products/containers-kubernetes/kubectl-auth-changes-in-gke

export PATH=/System/Volumes/Data/opt/homebrew/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/:$PATH
export USE_GKE_GCLOUD_AUTH_PLUGIN=True

# AND gcloud container clusters get-credentials as seen below
```

### Frankfurt Cluster Up

```
export KUBECONFIG=/Users/burr/xKS/.kubeconfig/frankfurt-config

gcloud container clusters create frankfurt --zone europe-west3 --num-nodes 1

# note: num-nodes is per zone so `kubectl get nodes` will report 3

gcloud container clusters get-credentials frankfurt --zone europe-west3
```

### Sydney Cluster Up

```
export KUBECONFIG=/Users/burr/xKS/.kubeconfig/sydney-config

gcloud container clusters create sydney --zone australia-southeast1 --num-nodes 1

gcloud container clusters get-credentials sydney --zone australia-southeast1

```

### Franfurt Skupper up

```
kubectl create namespace frankfurt
kubectl config set-context --current --namespace=frankfurt
```

```
skupper init
```

```
skupper status
Skupper is enabled for namespace "frankfurt" in interior mode. It is not connected to any other sites. It has no exposed services.
The site console url is:  https://34.141.109.254:8080
```

### Sydney Skupper up

```
kubectl create namespace sydney
kubectl config set-context --current --namespace=sydney
```

```
skupper init
```

```
skupper status
Skupper is enabled for namespace "sydney" in interior mode. It is not connected to any other sites. It has no exposed services.
The site console url is:  https://34.87.234.63:8080
```

### Frankfurt create token

```
skupper token create token.yaml -t cert
```

### Sydney link token

```
skupper link create token.yaml
```

### Frankfurt

```
skupper status
Skupper is enabled for namespace "frankfurt" in interior mode. It is connected to 1 other site. It has no exposed services.
```

### Sydney

```
Skupper is enabled for namespace "sydney" in interior mode. It is connected to 1 other site. It has no exposed services.
```

### Frankfurt Deploy App

```
kubectl apply -f backend.yml
kubectl apply -f frontend.yml
```

```
kubectl set env deployment/backapi WORKER_CLOUD_ID="frankfurt"
```

```
kubectl get services
NAME                    TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)                           AGE
backapi                 ClusterIP      10.12.8.150    <none>           8080/TCP                          2m9s
hybrid-cloud-frontend   LoadBalancer   10.12.15.201   <pending>        8080:30090/TCP                    21s
skupper                 LoadBalancer   10.12.1.136    34.141.109.254   8080:30374/TCP,8081:31533/TCP     3h44m
skupper-router          LoadBalancer   10.12.9.65     34.89.234.29     55671:32739/TCP,45671:30461/TCP   3h45m
skupper-router-local    ClusterIP      10.12.14.192   <none>           5671/TCP                          3h45m
```

```
skupper expose deployment/backapi --port 8080
```

```
FRONTENDIP=$(kubectl get service hybrid-cloud-frontend -o jsonpath="{.status.loadBalancer.ingress[0].ip}"):8080

open http://$FRONTENDIP
```

![frontend frankfurt](images/frontend-frankfurt.png)


### Sydney Deploy App

```
kubectl apply -f backend.yml
```

```
kubectl get services
NAME                   TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)                           AGE
backapi                ClusterIP      10.24.6.133   <none>          8080/TCP                          40s
skupper                LoadBalancer   10.24.5.23    34.87.234.63    8080:31305/TCP,8081:32629/TCP     21m
skupper-router         LoadBalancer   10.24.9.48    34.116.77.199   55671:31991/TCP,45671:32749/TCP   22m
skupper-router-local   ClusterIP      10.24.4.201   <none>          5671/TCP                          22m
```

Check on pods to verify no crashlooping

```
kubectl get pods
NAME                                          READY   STATUS    RESTARTS   AGE
backapi-6cc79dbb7c-qpvfh                      1/1     Running   0          1m45s
skupper-router-5fddbd5698-zxrjj               2/2     Running   0          24m
skupper-service-controller-69495555cd-mswhn   1/1     Running   0          24m
```

```
kubectl set env deployment/backapi WORKER_CLOUD_ID="sydney"
```

Expose this service

```
skupper expose deployment/backapi --port 8080
```

```
skupper network status
Sites:
├─ [local] 49efdca - sydney
│  URL: 34.116.77.199
│  name: sydney
│  namespace: sydney
│  sites linked to: 2ead203-frankfurt
│  version: 1.0.0
│  ╰─ Services:
│     ╰─ name: backapi
│        address: backapi: 8080
│        protocol: tcp
│        ╰─ Targets:
│           ╰─ name: backapi-8cfd5874-5fww2
╰─ [remote] 2ead203 - frankfurt
   URL: 34.89.234.29
   name: frankfurt
   namespace: frankfurt
   version: 1.0.0
   ╰─ Services:
      ╰─ name: backapi
         address: backapi: 8080
         protocol: tcp
         ╰─ Targets:
            ╰─ name: backapi-557dfbd54c-99s86
```


### Frankfurt backend zero

Start up poller

```
FRONTENDIP=$(kubectl get service hybrid-cloud-frontend -o jsonpath="{.status.loadBalancer.ingress[0].ip}"):8080

while true
do curl $FRONTENDIP/api/cloud
echo ""
sleep .3
done

```

```
kubectl scale --replicas=0 deployment/backapi
```

![Fail-over to Sydney](images/frontend-sydney.png)


## Add 3rd cluster

### Montreal 
```
export KUBECONFIG=/Users/burr/xKS/.kubeconfig/montreal-config

gcloud container clusters create montreal --zone northamerica-northeast1 --num-nodes 1

gcloud container clusters get-credentials montreal --zone northamerica-northeast1
```

```
kubectl create namespace montreal
kubectl config set-context --current --namespace=montreal
```

```
skupper init
```

```
skupper link create token.yaml
```

```
skupper status
Skupper is enabled for namespace "montreal" in interior mode. It is connected to 2 other sites (1 indirectly). It has 1 exposed service.
```

```
kubectl apply -f backend.yml
```

```
kubectl set env deployment/backapi WORKER_CLOUD_ID="montreal"
```

```
skupper expose deployment/backapi --port 8080
```

### Sydney backend zero

```
kubectl scale --replicas=0 deployment/backapi
```

![frontend montreal](images/frontend-montreal.png)


### And have some fun with it

Sydney

```
kubectl scale --replicas=1 deployment/backapi
```

Frankfurt
```
kubectl scale --replicas=1 deployment/backapi
```

To clean up the UI/Frontend just bounce the pod

Frankfurt

```
kubectl delete pod -l app.kubernetes.io/name=hybrid-cloud-frontend
``` 

```
skupper version
client version                 1.0.0
transport version              quay.io/skupper/skupper-router:2.0.1 (sha256:5f08ae90af0a)
controller version             quay.io/skupper/service-controller:1.0.0 (sha256:85f4dab48dcd)
config-sync version            quay.io/skupper/config-sync:1.0.0 (sha256:c16f8b171840)
```

```
skupper network status
Sites:
├─ [remote] 49efdca - sydney
│  URL: 34.116.77.199
│  name: sydney
│  namespace: sydney
│  sites linked to: 2ead203-frankfurt
│  version: 1.0.0
│  ╰─ Services:
│     ╰─ name: backapi
│        address: backapi: 8080
│        protocol: tcp
│        ╰─ Targets:
│           ╰─ name: backapi-8cfd5874-h92bh
├─ [local] 218e851 - montreal
│  URL: 34.136.167.87
│  name: montreal
│  namespace: montreal
│  sites linked to: 2ead203-frankfurt
│  version: 1.0.0
│  ╰─ Services:
│     ╰─ name: backapi
│        address: backapi: 8080
│        protocol: tcp
│        ╰─ Targets:
│           ╰─ name: backapi-6d78c979c8-nnndz
╰─ [remote] 2ead203 - frankfurt
   URL: 34.89.234.29
   name: frankfurt
   namespace: frankfurt
   version: 1.0.0
   ╰─ Services:
      ╰─ name: backapi
         address: backapi: 8080
         protocol: tcp
         ╰─ Targets:
            ╰─ name: backapi-557dfbd54c-k6szv
```


### Clean Up

```
gcloud container clusters delete sydney --zone australia-southeast1
gcloud container clusters delete frankfurt --zone europe-west3
gcloud container clusters delete montreal --zone northamerica-northeast1
```


# YAML Way

Using a single cluster and 3 namespaces of `one`, `two` and `three` to cut down on hosting costs while experimenting

### Set some env vars

```
export KUBE_EDITOR="code -w"
export PATH=~/devnation/bin:/System/Volumes/Data/opt/homebrew/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/:$PATH
export USE_GKE_GCLOUD_AUTH_PLUGIN=True
```

### Create a GKE Cluster

```
export KUBECONFIG=/Users/burr/xKS/.kubeconfig/montreal-config

gcloud container clusters create montreal --zone northamerica-northeast1 --num-nodes 1

gcloud container clusters get-credentials montreal --zone northamerica-northeast1
```

![iterm2 setup](images/iterm2-yaml-1.png)

## One

### One: Create Namespace 

```
kubectl create namespace one
kubectl config set-context --current --namespace=one
```

### One: Install Skupper into namespace

```
kubectl apply -f https://raw.githubusercontent.com/skupperproject/skupper/0.8.6/cmd/site-controller/deploy-watch-current-ns.yaml
```

#### Verify

```
kubectl get pods
NAME                                       READY   STATUS    RESTARTS   AGE
skupper-site-controller-689565b686-5tfdd   1/1     Running   0          54s
```

```
kubectl get secrets
NAME                                  TYPE                                  DATA   AGE
default-token-p9g72                   kubernetes.io/service-account-token   3      110s
skupper-site-controller-token-dndvc   kubernetes.io/service-account-token   3      86s
```

### One: Create Site 

```
kubectl apply -f via-yaml/one.yml
```

#### Verify

```
kubectl get pods
NAME                                       READY   STATUS    RESTARTS   AGE
skupper-router-844b6d45d9-bhdm7            2/2     Running   0          25s
skupper-site-controller-689565b686-5tfdd   1/1     Running   0          8m29s
```

```
kubectl get cm
NAME                  DATA   AGE
kube-root-ca.crt      1      8m49s
skupper-internal      1      19s
skupper-sasl-config   1      21s
skupper-services      0      20s
skupper-site          10     21s
```

```
kubectl get secrets
NAME                                     TYPE                                  DATA   AGE
default-token-p9g72                      kubernetes.io/service-account-token   3      8m52s
skupper-console-users                    Opaque                                1      23s
skupper-local-ca                         kubernetes.io/tls                     2      24s
skupper-local-client                     kubernetes.io/tls                     4      23s
skupper-local-server                     kubernetes.io/tls                     3      23s
skupper-router-token-hg9pk               kubernetes.io/service-account-token   3      24s
skupper-service-controller-token-jd6z5   kubernetes.io/service-account-token   3      22s
skupper-site-controller-token-dndvc      kubernetes.io/service-account-token   3      8m28s
```

```
kubectl get services
NAME                     TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)          AGE
skupper                  LoadBalancer   10.112.6.23   35.203.20.189   8080:30330/TCP   75s
skupper-router-console   ClusterIP      10.112.6.44   <none>          8080/TCP         76s
skupper-router-local     ClusterIP      10.112.9.30   <none>          5671/TCP         76s
```

## Two

### Two: Create Namespace 

```
kubectl create namespace two
kubectl config set-context --current --namespace=two
```

A secret by default

```
kubectl get secrets
NAME                  TYPE                                  DATA   AGE
default-token-6p4v7   kubernetes.io/service-account-token   3      30s
```

A configmap by default

```
kubectl get cm
NAME               DATA   AGE
kube-root-ca.crt   1      22s
```

### Two: Install Skupper into namespace

```
kubectl apply -f https://raw.githubusercontent.com/skupperproject/skupper/0.8.6/cmd/site-controller/deploy-watch-current-ns.yaml
```


### Two: Create Site 

```
kubectl apply -f via-yaml/two.yml
```

### Link One to Two

Create the token request secret

```
kubectl -n one apply -f via-yaml/request-token.yml
```


### Clean Up

```
gcloud container clusters delete montreal --zone northamerica-northeast1
```
