
FRONTENDIP=$(kubectl get service hybrid-cloud-frontend -o jsonpath="{.status.loadBalancer.ingress[0].ip}"):8080

while true
do curl $FRONTENDIP/api/cloud
echo ""
sleep .3
done