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
kubectl create namespace todo-dev 2>$null | Out-Null
kubectl create secret docker-registry ghcr-cred `
  --docker-server=ghcr.io `
  --docker-username=$USER `
  --docker-password=$TOKEN `
  -n todo-dev
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

# 既定で overlays/dev を参照（検証環境）
kubectl apply -k deploy/k8s
kubectl -n todo-dev get pods,svc

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
# 検証環境
a) Ingressを使う場合（hostsに dev.todo.example.com → 127.0.0.1 を追加）
# → http://dev.todo.example.com
b) 手軽に確認（port-forward）
kubectl -n todo-dev port-forward svc/web 8080:80
# → http://localhost:8080

# 本番環境
a) Ingress（hostsに todo.example.com → 127.0.0.1 を追加）
# → http://todo.example.com
b) port-forward（必要時）
kubectl -n todo-prod port-forward svc/web 8080:80
# → http://localhost:8080
```
- Ingressを使う場合（Ingress Controllerインストール後）
  - `kubectl get ingress -n todo` でADDRESSを確認
  - ブラウザで http://localhost にアクセス（80ポート）
  - または、`/etc/hosts`（Windows: `C:\Windows\System32\drivers\etc\hosts`）で `todo.example.com` を 127.0.0.1 に向ける


## Kubernetes ダッシュボードの導入（管理画面）
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

## クリーンアップ（ローカル）
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

## Ubuntu サーバ（Minikube）+ Argo CD で自動デプロイ

以下は Ubuntu サーバ上で Docker ドライバの Minikube を起動し、Argo CD で本リポジトリを自動デプロイする手順です。

### 1) Docker Engine インストール
```bash
# Docker の GPG key とリポジトリ追加
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo tee /etc/apt/keyrings/docker.asc > /dev/null
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Docker デーモンが未起動なら起動・自動起動化
sudo systemctl enable --now docker

docker --version
```

### 2) kubectl インストール
```bash
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl gnupg

curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.32/deb/Release.key | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
sudo chmod 644 /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.32/deb/ /' | sudo tee /etc/apt/sources.list.d/kubernetes.list
sudo chmod 644 /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update
sudo apt-get install -y kubectl

kubectl version --client=true
```

### 2.5) Git インストール（未導入なら）
```bash
sudo apt-get update && sudo apt-get install -y git
```

### 3) Minikube インストール
```bash
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube

minikube version
```

### 4)（任意）アプリ用ユーザ作成
既存ユーザを利用する場合は本手順は不要です。以下は例として `k8s` ユーザを作成します。
```bash
sudo useradd -m k8s || true
sudo passwd k8s
sudo chsh -s /bin/bash k8s
```

### 5) k8s ユーザに権限付与（重要）
`k8s` ユーザが Docker を操作できるように `docker` グループを付与します（必要なら `sudo` も）。この操作は管理者ユーザまたは root で実行してください。
```bash
# 管理者ユーザまたは root で実行
sudo usermod -aG docker k8s
# 任意: 管理用途で sudo 権限を付ける場合
sudo usermod -aG sudo k8s

# 反映（新しいセッションで）
su - k8s
newgrp docker

# 確認（k8s ユーザで）
id
ls -l /var/run/docker.sock   # → root:docker かつ 660 が望ましい
docker --version
# 動作確認（任意）
docker run --rm hello-world
```

### 6) Minikube 起動（Docker ドライバ）
```bash
# k8s ユーザで実行
minikube start --driver=docker
# （Docker が使えない場合のみ root で）
# sudo minikube start --driver=none
```

### 7) アドオン（Ingress）
```bash
# MinikubeではアドオンでIngress Controllerを有効化（クラウド用のNGINXマニフェストは不要）
minikube addons enable ingress
minikube addons enable ingress-dns  # 任意（名前解決の検証に便利）
```

### 8) クラスタ確認
```bash
kubectl get nodes
kubectl get pods -A
```

### 9) Argo CD インストールと初期設定
```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd rollout status deploy/argocd-server -w

# 初期パスワード（ユーザ: admin）
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo

# アクセス（別端末でポートフォワード）
kubectl -n argocd port-forward svc/argocd-server 8444:443
# → ブラウザ: https://<サーバIP>:8444 （admin / 上記パスワード）
```

### リポジトリをクローンして prod をデプロイ（サーバ）
```bash
# リポジトリをクローン（自分のフォーク推奨）
# 例: https://github.com/<OWNER>/<REPO>.git
git clone https://github.com/yamayui1107/k8s_practice.git
cd k8s_practice

# [Argo CD 経由] アプリを Argo CD に登録（推奨）
# 必要に応じて deploy/k8s/tools/argocd/application-prod.yaml の
#   - spec.source.repoURL（あなたのリポジトリ URL）
#   - spec.source.targetRevision（例: main）
# を編集してから適用
kubectl apply -f deploy/k8s/tools/argocd/application-prod.yaml
kubectl -n argocd get applications.argoproj.io todo-prod
```

> 補足: `k8s` ユーザが sudoers に無い環境では、権限付与やサービス起動は管理者ユーザで実施してください。`k8s` で `sudo` 不可のままにする運用も可能です。

### GHCR がプライベートの場合（prod）
```bash
kubectl -n todo-prod create secret docker-registry ghcr-cred \
  --docker-server=ghcr.io \
  --docker-username=<GitHubユーザ名> \
  --docker-password=<GHCR_PAT>

kubectl -n todo-prod patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"ghcr-cred"}]}'
```

### Minikube で prod を直接適用（Argo CD を使わない場合）
必要に応じて `deploy/k8s/overlays/prod/kustomization.yaml` の `images` をあなたの GHCR に置き換えてください。
```bash
# リポジトリをクローン（未済なら）
# 例: https://github.com/<OWNER>/<REPO>.git
if [ ! -d k8s_practice ]; then
  git clone https://github.com/yamayui1107/k8s_practice.git
fi
cd k8s_practice

# Secret（必須）
kubectl create namespace todo-prod 2>/dev/null || true

# rootのDBパスワードを任意に設定（appは root/DB_PASSWORD を使用）
export DBPASS='change-me-strong'

kubectl -n todo-prod create secret generic db-secret \
  --from-literal=MYSQL_ROOT_PASSWORD="$DBPASS" \
  --from-literal=DB_PASSWORD="$DBPASS" \
  --dry-run=client -o yaml | kubectl apply -f -

# 適用（prod オーバーレイを直接適用）
kubectl apply -k deploy/k8s/overlays/prod
kubectl -n todo-prod get pods,svc
```

### 初回 DB 初期化（prod / Ready にならない場合）
prod では `spring.sql.init.mode=never` のため、自動でテーブルは作成されません。初回のみ、以下で `todos` テーブルを作成してください。
```bash
kubectl -n todo-prod exec -it mysql-0 -- bash -lc 'cat <<SQL | mysql -uroot -p"$MYSQL_ROOT_PASSWORD" todo_db
CREATE TABLE IF NOT EXISTS todos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(20) NOT NULL,
  due_date DATE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
SQL'
# 数十秒後に Ready を確認
kubectl -n todo-prod get deploy
```

### VPS での Argo CD / アプリへのアクセス（安全な確認方法）
公開せずに確認する場合は、VPS 上でポートフォワードし、手元PCから SSH トンネルでアクセスします。
```bash
# [VPS 側] Argo CD UI
kubectl -n argocd port-forward svc/argocd-server 8444:443

# [VPS 側] アプリ（web）
kubectl -n todo-prod port-forward svc/web 8080:80
```
```bash
# [手元PC] VPS への SSH トンネル（別端末）
ssh -N -L 8444:localhost:8444 <user>@<VPS_IP>
ssh -N -L 8080:localhost:8080 <user>@<VPS_IP>
```
- ブラウザ: `https://localhost:8444`（Argo CD UI）、`http://localhost:8080`（アプリ）
- セキュリティ上、`--address 0.0.0.0` での直接公開は非推奨です。

### 80番ポートでポートフォワードする場合の権限（Linux）
Linux では 1024 未満のポートは特権が必要です。以下のいずれかを利用してください。

- 方法1: `sudo` で実行（簡単）
```bash
sudo kubectl --kubeconfig=/home/k8s/.kube/config -n todo-prod port-forward --address 127.0.0.1 svc/web 80:80
```
  - `--kubeconfig` は環境に合わせて調整。

- 方法2: `setcap` で `kubectl` に 80 番バインド権限付与（非 root 実行）
```bash
sudo apt-get update && sudo apt-get install -y libcap2-bin
sudo setcap 'cap_net_bind_service=+ep' "$(readlink -f "$(which kubectl)")"

# 以後は非 root で OK
kubectl -n todo-prod port-forward --address 127.0.0.1 svc/web 80:80

# 解除したい場合
sudo setcap -r "$(readlink -f "$(which kubectl)")"
```

- 代替（簡易・安全）: VPS は 8080、手元だけ 80 にトンネル
```bash
# [VPS 側]
kubectl -n todo-prod port-forward --address 127.0.0.1 svc/web 8080:80
# [手元PC]
ssh -N -L 80:localhost:8080 <user>@<VPS_IP>
# → ブラウザ: http://localhost
```
> 注意: `--address 0.0.0.0` は外部公開になるため基本は使用しないでください。公開用途は Cloudflare Tunnel もしくはホスト nginx を推奨します。

### 80番ポートで直接公開したい場合（Cloudflare を使わない構成）
- Cloudflare Tunnel を使う場合は、VPS の 80 番ポートを公開する必要はありません（Tunnel 経由で到達）。
- 直接 80 番で公開したい場合は、以下のいずれかが必要です。
  - VPS のホストにリバースプロキシ（例: nginx）を設定し、`http://<MINIKUBE_IP>:<IngressのHTTP NodePort>` にプロキシ
  - もしくは OS レベルのポート転送（iptables 等）で `80 -> <IngressのHTTP NodePort>` にリダイレクト
- 参考（NodePort の取得）:
```bash
MINIKUBE_IP=$(minikube ip)
HTTP_NODEPORT=$(kubectl -n ingress-nginx get svc ingress-nginx-controller -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')
echo $MINIKUBE_IP $HTTP_NODEPORT
```
- ドメインを使う場合は、`deploy/k8s/overlays/prod/kustomization.yaml` の Ingress `host` をドメイン名に変更し、DNS を VPS の IP に向けます。

### VPS 上で Cloudflare Tunnel を使って公開（prod）

前提:
- `todo-example.xvps.jp` のようなサブドメインを Cloudflare で管理していること（DNS ゾーンを Cloudflare に移管済み）
- prod の Ingress ホスト名をサブドメインに合わせる（恒久設定）

1) Ingress のホスト名をサブドメインへ変更（恒久設定）
- `deploy/k8s/overlays/prod/kustomization.yaml` の Ingress 置換を編集:
```diff
-       - op: replace
-         path: /spec/rules/0/host
-         value: todo.example.com
+       - op: replace
+         path: /spec/rules/0/host
+         value: todo-example.xvps.jp
```
- 反映:
```bash
kubectl apply -k deploy/k8s/overlays/prod
kubectl -n todo-prod get ingress web -o wide
```

2) Minikube の Ingress を有効化（未実施なら）
```bash
minikube addons enable ingress
kubectl -n ingress-nginx get pods
```

3) Cloudflare Tunnel（ホスト上に cloudflared を導入する簡易手順）
```bash
# インストール & 認証
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cf.deb
sudo apt install -y ./cf.deb
cloudflared tunnel login

# トンネルを作成
cloudflared tunnel create todo
# トンネルIDを控える
cloudflared tunnel list

# サブドメインにDNSルートを作成
cloudflared tunnel route dns todo todo-example.xvps.jp

# Ingress(NGINX) の NodePort を取得
MINIKUBE_IP=$(minikube ip)
HTTP_NODEPORT=$(kubectl -n ingress-nginx get svc ingress-nginx-controller -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')
echo $MINIKUBE_IP $HTTP_NODEPORT

# 設定ファイルを作成（~/.cloudflared/config.yml）
mkdir -p ~/.cloudflared
cat > ~/.cloudflared/config.yml <<EOF
tunnel: $(cloudflared tunnel list | awk '/todo/{print $1; exit}')
credentials-file: $HOME/.cloudflared/$(cloudflared tunnel list | awk '/todo/{print $1; exit}').json
ingress:
  - hostname: todo-example.xvps.jp
    service: http://$MINIKUBE_IP:$HTTP_NODEPORT
  - service: http_status:404
EOF

# 起動（ターミナルで前面実行。常駐化は systemd 等を利用）
cloudflared tunnel run
```
- 確認:
```bash
curl -I http://todo-example.xvps.jp/
# 302 → /todos リダイレクトが返ればOK
```

4) （運用向け）Cloudflare Tunnel をクラスタ内にデプロイする方法
- PC で `cloudflared tunnel login` → `cloudflared tunnel create todo` 実行し、<トンネルID>.json（認証ファイル）を取得
- K8s に Secret/ConfigMap/Deployment を作成
```bash
TUNNEL_ID=<作成したトンネルID>
# 認証ファイルを Secret として投入
kubectl -n todo-prod create secret generic cloudflared-credentials \
  --from-file=credentials.json=$TUNNEL_ID.json

# ConfigMap（cloudflared 設定）
cat <<'YAML' | kubectl -n todo-prod apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: cloudflared-config
data:
  config.yaml: |
    tunnel: TUNNEL_ID_REPLACE
    credentials-file: /etc/cloudflared/credentials.json
    ingress:
      - hostname: todo-example.xvps.jp
        service: http://ingress-nginx-controller.ingress-nginx.svc.cluster.local:80
      - service: http_status:404
YAML
kubectl -n todo-prod get cm cloudflared-config -o yaml | sed -n '1,160p'

# Deployment（cloudflared）
cat <<'YAML' | kubectl -n todo-prod apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cloudflared
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cloudflared
  template:
    metadata:
      labels:
        app: cloudflared
    spec:
      containers:
        - name: cloudflared
          image: cloudflare/cloudflared:latest
          args: ["tunnel","--config","/etc/cloudflared/config.yaml","run"]
          volumeMounts:
            - name: config
              mountPath: /etc/cloudflared
            - name: creds
              mountPath: /etc/cloudflared/credentials.json
              subPath: credentials.json
      volumes:
        - name: config
          configMap:
            name: cloudflared-config
            items:
              - key: config.yaml
                path: config.yaml
        - name: creds
          secret:
            secretName: cloudflared-credentials
YAML

# TUNNEL_ID を ConfigMap に反映（置換）
kubectl -n todo-prod get cm cloudflared-config -o go-template='{{index .data "config.yaml"}}' \
 | sed "s/TUNNEL_ID_REPLACE/$TUNNEL_ID/g" \
 | kubectl -n todo-prod create configmap cloudflared-config --from-file=config.yaml=/dev/stdin -o yaml --dry-run=client | kubectl apply -f -

# デプロイ確認
kubectl -n todo-prod rollout status deploy/cloudflared
```
- これで `todo-example.xvps.jp` → Cloudflare → Tunnel → `ingress-nginx-controller:80` → Ingress → `web` へ到達します。NodePort やホストNginxは不要です。

注意:
- Ingress の `host` は `todo-example.xvps.jp` に合わせてください。
- Cloudflare ダッシュボードで HTTPS を有効化できます（Flexible / Full は用途に応じて選択）。

### クリーンアップ（Ubuntu / prod）
```bash
# Argo CD 管理のアプリ削除
kubectl delete -f deploy/k8s/tools/argocd/application-prod.yaml

# 任意: Argo CD 自体を削除
kubectl delete namespace argocd

# 任意: アプリのネームスペース（prod）を削除
kubectl delete namespace todo-prod
```
