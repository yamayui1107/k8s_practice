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
# 既定で overlays/dev を参照
tkubectl apply -k deploy/k8s
kubectl -n todo get pods,svc
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