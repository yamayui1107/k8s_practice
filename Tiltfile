# Tiltfile
k8s_yaml(kustomize('deploy/k8s'))

# ローカル確認用のポートフォワード
k8s_resource('web', port_forwards=['8080:80'])