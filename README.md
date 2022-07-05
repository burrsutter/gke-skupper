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

Using a single cluster and 3 namespaces of `one`, `two` and `three` to cut down on hosting costs while experimenting.

**one** holds frontend and backend

**two** and **three** backend only

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


### One: Install Skupper into namespace

```
kubectl apply -f https://raw.githubusercontent.com/skupperproject/skupper/1.0.0/cmd/site-controller/deploy-watch-current-ns.yaml
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
default-token-6p4v7                   kubernetes.io/service-account-token   3      110s
skupper-site-controller-token-dndvc   kubernetes.io/service-account-token   3      86s
```

```
kubectl get services
No resources found in one namespace.
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
default-token-6p4v7                      kubernetes.io/service-account-token   3      4m31s
skupper-claims-server                    kubernetes.io/tls                     3      11s
skupper-console-certs                    kubernetes.io/tls                     3      11s
skupper-console-users                    Opaque                                1      104s
skupper-local-ca                         kubernetes.io/tls                     2      105s
skupper-local-client                     kubernetes.io/tls                     4      104s
skupper-local-server                     kubernetes.io/tls                     3      105s
skupper-router-token-8tl8s               kubernetes.io/service-account-token   3      105s
skupper-service-ca                       kubernetes.io/tls                     2      105s
skupper-service-client                   kubernetes.io/tls                     3      104s
skupper-service-controller-token-cntl8   kubernetes.io/service-account-token   3      53s
skupper-site-ca                          kubernetes.io/tls                     2      105s
skupper-site-controller-token-qqlkk      kubernetes.io/service-account-token   3      4m22s
skupper-site-server                      kubernetes.io/tls                     3      53s
```

```
kubectl get services
NAME                     TYPE           CLUSTER-IP      EXTERNAL-IP    PORT(S)                           AGE
skupper                  LoadBalancer   10.112.11.8     34.95.24.191   8080:31303/TCP,8081:31503/TCP     103s
skupper-router           LoadBalancer   10.112.1.37     34.95.51.203   55671:30188/TCP,45671:32064/TCP   2m33s
skupper-router-console   ClusterIP      10.112.6.101    <none>         8080/TCP                          2m34s
skupper-router-local     ClusterIP      10.112.11.133   <none>         5671/TCP                          2m34s
```

## Two

### Two: Create Namespace 

```
kubectl create namespace two
kubectl config set-context --current --namespace=two
```


### Two: Install Skupper into namespace

```
kubectl apply -f https://raw.githubusercontent.com/skupperproject/skupper/1.0.0/cmd/site-controller/deploy-watch-current-ns.yaml
```


### Two: Create Site 

```
kubectl apply -f via-yaml/two.yml
```

```
skupper status
Skupper is enabled for namespace "two" in interior mode. It is not connected to any other sites. It has no exposed services.
The site console url is:  https://34.95.54.210:8080
The credentials for internal console-auth mode are held in secret: 'skupper-console-users'
```

### Generate Token in One

Create the token request secret in One

```
kubectl config set-context --current --namespace=one
```

```
skupper status
Skupper is enabled for namespace "one" in interior mode. It is not connected to any other sites. It has no exposed services.
The site console url is:  https://34.95.24.191:8080
The credentials for internal console-auth mode are held in secret: 'skupper-console-users'
```

```
kubectl -n one apply -f via-yaml/request-token.yml
```

```
kubectl get secrets -l skupper.io/type=connection-token
NAME          TYPE     DATA   AGE
link-to-one   Opaque   3      38s
```

```
kubectl describe secret link-to-one
Name:         link-to-one
Namespace:    one
Labels:       skupper.io/type=connection-token
Annotations:  edge-host: 34.95.51.203
              edge-port: 45671
              inter-router-host: 34.95.51.203
              inter-router-port: 55671
              skupper.io/generated-by: 2aa764f1-5394-4ccb-a4fd-8965d341c608
              skupper.io/site-version: 1.0.0

Type:  Opaque

Data
====
ca.crt:   1119 bytes
tls.crt:  1135 bytes
tls.key:  1679 bytes
```

```
skupper network status
Sites:
╰─ [local] 2aa764f - one
   URL: 34.95.51.203
   name: one
   namespace: one
   version: 1.0.0
```

### Copy One's Secret/Token to Two

#### From One

```
kubectl config set-context --current --namespace=one
kubectl get secret link-to-one -o yaml > link-to-one.yaml
```

#### Modify the secret to strip out Namespace & cruft

```
brew install yq
```

```
cat link-to-one.yaml| yq 'del(.metadata.namespace)' > link-to-one-no-namespace.yaml
cat link-to-one-no-namespace.yaml| yq 'del(.metadata.resourceVersion)' > link-to-one-no-resourceVersion.yaml
cat link-to-one-no-resourceVersion.yaml| yq 'del(.metadata.uid)' > link-to-one-no-uid.yaml
```

#### To Two

```
kubectl config set-context --current --namespace=two
kubectl apply -f link-to-one-no-uid.yaml
```


```
skupper status
Skupper is enabled for namespace "two" in interior mode. It is connected to 1 other site. It has no exposed services.
The site console url is:  https://34.95.54.210:8080
The credentials for internal console-auth mode are held in secret: 'skupper-console-users'
```

```
skupper network status
Sites:
├─ [local] 2b352a8 - two
│  URL: 34.152.9.114
│  name: two
│  namespace: two
│  sites linked to: 2aa764f-one
│  version: 1.0.0
╰─ [remote] 2aa764f - one
   URL: 34.95.51.203
   name: one
   namespace: one
   version: 1.0.0
```

### One: Frontend and Backend

```
kubectl config set-context --current --namespace=one
```

```
kubectl apply -f backend.yml
kubectl apply -f frontend.yml
```

```
kubectl set env deployment/backapi WORKER_CLOUD_ID="one"
```

```
kubectl get pods
NAME                                         READY   STATUS    RESTARTS   AGE
backapi-f6d67f599-94wjx                      1/1     Running   0          16s
hybrid-cloud-frontend-6d88f9cd4b-5q829       1/1     Running   0          30s
skupper-router-784995cc5-jh7wr               2/2     Running   0          58m
skupper-service-controller-9695fdfc6-dq4sx   1/1     Running   0          56m
skupper-site-controller-56d886649c-44r58     1/1     Running   0          61m
```

```
kubectl get services
NAME                     TYPE           CLUSTER-IP      EXTERNAL-IP     PORT(S)                           AGE
backapi                  ClusterIP      10.112.8.50     <none>          8080/TCP                          56s
hybrid-cloud-frontend    LoadBalancer   10.112.2.32     35.234.253.91   8080:31755/TCP                    51s
skupper                  LoadBalancer   10.112.11.8     34.95.24.191    8080:31303/TCP,8081:31503/TCP     57m
skupper-router           LoadBalancer   10.112.1.37     34.95.51.203    55671:30188/TCP,45671:32064/TCP   58m
skupper-router-console   ClusterIP      10.112.6.101    <none>          8080/TCP                          58m
skupper-router-local     ClusterIP      10.112.11.133   <none>          5671/TCP                          58m
```


```
FRONTENDIP=$(kubectl get service hybrid-cloud-frontend -o jsonpath="{.status.loadBalancer.ingress[0].ip}"):8080

open http://$FRONTENDIP
```

![frontend yaml one](images/yaml-way-frontend-one.png)

```
curl $FRONTENDIP/api/cloud
one:0
```

```
kubectl annotate service backapi skupper.io/proxy=tcp
```

### Two: Backend

```
kubectl config set-context --current --namespace=two
```

```
kubectl apply -f backend.yml
kubectl set env deployment/backapi WORKER_CLOUD_ID="two"
```

How to expose via yaml?

```
kubectl annotate service backapi skupper.io/proxy=tcp
```

```
Skupper is enabled for namespace "two" in interior mode. It is connected to 1 other site. It has 1 exposed service.
The site console url is:  https://34.95.54.210:8080
The credentials for internal console-auth mode are held in secret: 'skupper-console-users'
```

After exposing Two's backapi to One then turn off One

### Fail-over from One to Two

```
kubectl -n one scale --replicas=0 deployment/backapi
```

```
curl $FRONTENDIP/api/cloud
two:0
```

### Three

```
kubectl create namespace three
kubectl config set-context --current --namespace=three
```

```
kubectl apply -f https://raw.githubusercontent.com/skupperproject/skupper/1.0.0/cmd/site-controller/deploy-watch-current-ns.yaml
```

```
kubectl apply -f via-yaml/three.yml
```

```
kubectl apply -f backend.yml
```

```
kubectl set env deployment/backapi WORKER_CLOUD_ID="three"
```

```
kubectl apply -f link-to-one-no-uid.yaml
```

```
kubectl annotate service backapi skupper.io/proxy=tcp
```

```
skupper service status
Services exposed through Skupper:
╰─ backapi (tcp port 8080)
```

```
skupper network status
Sites:
├─ [local] 575bb4a - three
│  URL: 34.152.48.197
│  name: three
│  namespace: three
│  sites linked to: 2aa764f-one
│  version: 1.0.0
│  ╰─ Services:
│     ╰─ name: backapi
│        address: backapi: 8080
│        protocol: tcp
├─ [remote] 2aa764f - one
│  URL: 34.95.51.203
│  name: one
│  namespace: one
│  version: 1.0.0
│  ╰─ Services:
│     ╰─ name: backapi
│        address: backapi: 8080
│        protocol: tcp
╰─ [remote] 2b352a8 - two
   URL: 34.152.9.114
   name: two
   namespace: two
   sites linked to: 2aa764f-one
   version: 1.0.0
   ╰─ Services:
      ╰─ name: backapi
         address: backapi: 8080
         protocol: tcp
```

```
kubectl -n one scale --replicas=0 deployment/backapi
kubectl -n two scale --replicas=0 deployment/backapi
```

```
curl $FRONTENDIP/api/cloud
```

### Costs

```
kubectl -n one annotate service backapi skupper.io/cost="5"
```

```
kubectl -n two annotate service backapi skupper.io/cost="10"
```

```
kubectl -n three annotate service backapi skupper.io/cost="15"
```

OR

```
kubectl -n three annotate service backapi skupper.io/cost="1" --overwrite
kubectl -n one annotate service backapi skupper.io/cost="2" --overwrite
kubectl -n two annotate service backapi skupper.io/cost="3" --overwrite
```

See the current costs

```
kubectl exec deploy/skupper-router -c router -- skmanage query --type node | jq -r '.[] | "Name: \(.name) - Cost: \(if .cost |.==null then 0 else .cost end)"'
```

```
echo "one"
kubectl -n one get service backapi -o jsonpath='{.metadata.annotations.skupper\.io/cost}'
echo ""
echo "two"
kubectl -n two get service backapi -o jsonpath='{.metadata.annotations.skupper\.io/cost}'
echo ""
echo "three"
kubectl -n three get service backapi -o jsonpath='{.metadata.annotations.skupper\.io/cost}'
```

### Console

Display the password

```
skupper status
Skupper is enabled for namespace "one" in interior mode. It is connected to 2 other sites. It has 1 exposed service.
The site console url is:  https://34.95.24.191:8080
The credentials for internal console-auth mode are held in secret: 'skupper-console-users'
```

```
kubectl get secret skupper-console-users -o jsonpath='{.data.admin}' | base64 -d
```

```
open https://34.95.24.191:8080
```

and `admin` + `mypassword`




### Clean Up

```
gcloud container clusters delete montreal --zone northamerica-northeast1
```
