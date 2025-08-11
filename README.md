# TODO App (Spring Boot + MyBatis + MySQL)

シンプルなTODOアプリ。MyBatis + MySQL。Docker Compose でのローカル実行と、Kubernetes へのデプロイ（GHCRイメージ利用）に対応。

## 目次
- 前提
- ディレクトリ構成
- ローカル起動（Docker Compose）
- イメージのビルドと GHCR へのプッシュ
- Kubernetes へデプロイ（ローカルクラスタ想定）
- アクセス方法
- トラブルシュート

## 前提
- Docker Desktop（Windows）
  - Kubernetes を有効化（Settings > Kubernetes > Enable）
- kubectl, kustomize（kubectl 内蔵の kustomize でOK）
- GitHub アカウントと GHCR 用 PAT（write:packages）

## ディレクトリ構成
- `docker/`
  - `app/Dockerfile`: Spring Boot JAR のビルド/実行
  - `web/Dockerfile`, `web/nginx.conf`: nginx リバースプロキシ
  - `db/Dockerfile`, `db/initdb.d/001_schema.sql`: MySQL 初期化
- `deploy/k8s/`
  - `base/`: 環境共通のマニフェスト（config/app/web/db/ingress）
  - `overlays/dev|prod/`: 環境差分（replicas, イメージタグ等）
  - `kustomization.yaml`: 既定で `overlays/dev` を参照

## ローカル起動（Docker Compose）
```powershell
# ビルド&起動
docker compose build
docker compose up -d

# 停止
docker compose down
```
- Web: http://localhost:8080
- API: `GET /api/todos`
- DB: `localhost:3306`（user: root / pass: pass）

## イメージのビルドと GHCR へのプッシュ
1) GHCR にログイン（GitHub ユーザ名/ PAT）
```powershell
$USER="your-github-username"
$TOKEN="ghp_xxxxx..."  # write:packages 権限

$TOKEN | docker login ghcr.io -u $USER --password-stdin
```
2) ビルド
```powershell
# app（プロジェクト直下をコンテキスト）
docker build -t ghcr.io/$USER/todo-app:latest -f docker/app/Dockerfile .

# web（docker/web をコンテキスト）
docker build -t ghcr.io/$USER/todo-web:latest -f docker/web/Dockerfile docker/web
```
3) プッシュ
```powershell
docker push ghcr.io/$USER/todo-app:latest
docker push ghcr.io/$USER/todo-web:latest
```

## Kubernetes へデプロイ（ローカルクラスタ想定）
1) Ingress Controllerをインストール（Ingressを使用する場合）
```powershell
# NGINX Ingress Controllerをインストール
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml

# インストール完了まで待機
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s

# インストール確認
kubectl get pods -n ingress-nginx
kubectl get ingressclass
```

2) イメージ参照を GHCR に切替（overlays/dev）
`deploy/k8s/overlays/dev/kustomization.yaml` の `images` を編集
```yaml
images:
  - name: your-registry/todo-app
    newName: ghcr.io/your-github-username/todo-app
    newTag: latest
  - name: your-registry/todo-web
    newName: ghcr.io/your-github-username/todo-web
    newTag: latest
```
3) （GHCRがプライベートの場合のみ）Pull Secret を作成し、Deployment に紐づけ
```powershell
kubectl create namespace todo 2>$null | Out-Null
kubectl create secret docker-registry ghcr-cred `
  --docker-server=ghcr.io `
  --docker-username=$USER `
  --docker-password=$TOKEN `
  -n todo
```
`deploy/k8s/base/app/deployment.yaml` と `base/web/deployment.yaml` に以下を追記（spec.template.spec 配下）
```yaml
imagePullSecrets:
  - name: ghcr-cred
```
4) デプロイ
```powershell
# Secret（必須）を先に作成（Git管理外）
# - サンプル: deploy/k8s/secrets/db-secret.yaml.example をコピーして編集
kubectl create namespace todo 2>$null | Out-Null
kubectl apply -f deploy/k8s/secrets/db-secret.yaml

# 既定で overlays/dev を参照
kubectl apply -k deploy/k8s
kubectl -n todo get pods,svc
```

### Argo CD を使った自動デプロイ（任意）
```powershell
# 1) Argo CD のインストール（ローカル）
.\deploy\k8s\tools\argocd\install-argocd.ps1

# 2) Argo CD Application を作成（mainブランチの overlays/dev を監視）
# repoURL と path はあなたのリポジトリに合わせて編集してから適用
kubectl apply -f deploy/k8s/tools/argocd/application-dev.yaml

# 3) UI へアクセス
# https://localhost:8444 にアクセスして admin / 初期パスワード でログイン
# 右上の "SYNC" を有効化すると自動同期（detect & deploy）されます
```

## アクセス方法（ローカル）
- 手軽に確認（port-forward）
```powershell
kubectl -n todo port-forward svc/web 8080:80
# → http://localhost:8080
```
- Ingressを使う場合（Ingress Controllerインストール後）
  - `kubectl get ingress -n todo` でADDRESSを確認
  - ブラウザで http://localhost にアクセス（80ポート）
  - または、`/etc/hosts`（Windows: `C:\Windows\System32\drivers\etc\hosts`）で `todo.example.com` を 127.0.0.1 に向ける

## クラスタの削除
```powershell
# アプリケーションの削除
kubectl delete -k deploy/k8s

# Ingress Controllerの削除（必要に応じて）
kubectl delete -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml

# 確認
kubectl get namespaces
kubectl get pods --all-namespaces
```

## Kubernetes ダッシュボード（管理画面）
Docker Desktop のローカルクラスタでダッシュボードを利用する手順です（学習/検証用途）。本番では限定RBACを推奨。

```powershell
# 1) Dashboard をインストール（公式推奨マニフェスト）
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml

# 2) 管理者アカウントを作成（ローカル用途）
kubectl apply -f deploy/k8s/tools/dashboard/admin-user.yaml

# 3) ログイン用トークンを取得（Kubernetes 1.24+）
kubectl -n kubernetes-dashboard create token admin-user

# 4-A) proxy経由でアクセス（推奨）
kubectl proxy
# ブラウザで以下にアクセス
# http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/

# 4-B) 直接ポートフォワード（自己署名証明書の警告は許可）
kubectl -n kubernetes-dashboard port-forward svc/kubernetes-dashboard 8443:443
# ブラウザ: https://localhost:8443/
```
- 備考: 生成したトークンを「Sign in with token」で貼り付けます。
- セキュリティ: `admin-user` は強い権限です。検証後は削除を推奨します。

```powershell
# 管理者アカウントの削除（検証終了後）
kubectl delete -f deploy/k8s/tools/dashboard/admin-user.yaml

# ダッシュボード本体の削除（導入済みの場合）
kubectl delete -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml
```

## Ubuntu サーバ（Minikube）+ Argo CD で自動デプロイ
以下はUbuntuサーバ上での最小手順（bash）。既にCIでGHCRへイメージが発行される前提です。

```bash
# 1) Minikube インストール
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# 2) Minikube 起動（Dockerドライバ推奨。無ければ --driver=none でroot実行）
# Dockerドライバ例
minikube start --driver=docker
# もしくは root で
# sudo minikube start --driver=none

# 3) アドオン（Ingress）
minikube addons enable ingress

# 4) クラスタ確認
kubectl get nodes
kubectl get pods -A

# 5) Argo CD インストール
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server -w

# 6) 初期パスワード取得（ユーザ: admin）
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo

# 7) アクセス（別端末でポートフォワード）
kubectl -n argocd port-forward svc/argocd-server 8444:443
# → https://<サーバIP>:8444 （admin / 上記パスワード）

# 8) Application適用（repoURLを自分のリポジトリに編集してから）
kubectl apply -f deploy/k8s/tools/argocd/application-dev.yaml
kubectl -n argocd get applications.argoproj.io todo-dev
```

### GHCRがプライベートの場合
```bash
kubectl -n todo create secret docker-registry ghcr-cred \
  --docker-server=ghcr.io \
  --docker-username=<GitHubユーザ名> \
  --docker-password=<GHCR_PAT>

kubectl -n todo patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"ghcr-cred"}]}'
```

## Argo CD の初期パスワード
- ユーザ名: admin
- 取得コマンド
  - Linux/macOS:
    ```bash
    kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
    ```
  - Windows (PowerShell):
    ```powershell
    kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | % { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_)) }
    ```
- 初回ログイン後、UIからパスワード変更を推奨

## クリーンアップ（ローカル/Ubuntu）
```powershell
# 1) アプリケーション削除（Argo CD管理対象）
kubectl delete -f deploy/k8s/tools/argocd/application-dev.yaml

# 2) Argo CD削除
kubectl delete namespace argocd

# 3) アプリ本体の削除（任意）
kubectl delete -k deploy/k8s
kubectl delete namespace todo

# 4) NGINX Ingress Controllerを入れていた場合（任意）
kubectl delete -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml
```
- Ubuntuでk3s自体を削除（任意）
```bash
sudo /usr/local/bin/k3s-uninstall.sh
```

## トラブルシュート
- ImagePullBackOff（イメージ取得失敗）
  - GHCR がプライベート → Pull Secret（`ghcr-cred`）を作成し `imagePullSecrets` を設定
  - `images` の `newName/newTag` を確認
- PVC が Pending
  - クラスタにデフォルト StorageClass が無い → 追加または明示指定
- Ingress が効かない
  - Ingress Controller 未導入 → 上記の「Ingress Controllerをインストール」手順を実行
  - または、`port-forward` を利用するか、Service を NodePort に変更
- 既存の 3306/8080 が使用中（Docker Compose）
  - `docker-compose.yml` のポートを変更

---
補足: Spring Boot は `schema.sql` により初回起動時にテーブルを自動作成します（DB 接続に成功している必要があります）。 