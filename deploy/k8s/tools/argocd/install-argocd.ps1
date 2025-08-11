# Argo CD install script (for local dev)
Write-Host "Installing Argo CD..." -ForegroundColor Cyan

kubectl create namespace argocd 2>$null | Out-Null
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

Write-Host "Waiting for Argo CD server..." -ForegroundColor Cyan
kubectl -n argocd rollout status deploy/argocd-server -w

Write-Host "Initial admin password:" -ForegroundColor Yellow
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | % { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }

Write-Host "Port-forward to https://localhost:8444" -ForegroundColor Cyan
kubectl -n argocd port-forward svc/argocd-server 8444:443 