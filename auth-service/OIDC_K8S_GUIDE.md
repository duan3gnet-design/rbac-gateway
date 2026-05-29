# OIDC Server – Kubernetes Integration Guide

## Tổng quan

`auth-service` giờ đây là một **OIDC Provider** chuẩn RFC 8414 / OpenID Connect 1.0.

| Endpoint                                      | Mô tả                                              |
|-----------------------------------------------|----------------------------------------------------|
| `GET /.well-known/openid-configuration`       | Discovery document (k8s đọc khi khởi động)         |
| `GET /.well-known/oauth-authorization-server` | Alias OAuth2 RFC 8414                              |
| `GET /oauth2/jwks`                            | Public key (RS256) để verify JWT                   |
| `POST /oauth2/token`                          | Token endpoint (password / refresh / client_creds) |
| `GET /oauth2/userinfo`                        | OIDC userinfo endpoint                             |

---

## Cấu hình Kubernetes API Server

### 1. Production: dùng RSA key cố định

Tạo RSA key và lưu vào k8s Secret:

```bash
# Tạo key pair
openssl genrsa -out rsa_private.pem 2048
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
    -in rsa_private.pem -out rsa_private_pkcs8.pem

# Tạo Secret trong k8s
kubectl create secret generic auth-rsa-key \
    --from-file=rsa_private.pem=rsa_private_pkcs8.pem \
    -n your-namespace
```

Mount vào auth-service Deployment:

```yaml
# trong Deployment spec:
volumes:
  - name: rsa-key
    secret:
      secretName: auth-rsa-key

containers:
  - name: auth-service
    env:
      - name: OIDC_ISSUER_URI
        value: "https://auth.your-domain.com"
      - name: OIDC_RSA_KEY_PATH
        value: "/etc/auth/keys/rsa_private.pem"
    volumeMounts:
      - name: rsa-key
        mountPath: /etc/auth/keys
        readOnly: true
```

### 2. Cấu hình kube-apiserver (cho OIDC user authentication)

Thêm flags vào kube-apiserver:

```yaml
# /etc/kubernetes/manifests/kube-apiserver.yaml
spec:
  containers:
  - command:
    - kube-apiserver
    # ...existing flags...
    - --oidc-issuer-url=https://auth.your-domain.com
    - --oidc-client-id=kubernetes          # phải khớp với 'aud' trong JWT
    - --oidc-username-claim=sub
    - --oidc-groups-claim=roles
    - --oidc-username-prefix=oidc:
    - --oidc-groups-prefix=oidc:
```

> **Lưu ý:** k8s sẽ tự fetch `/.well-known/openid-configuration` để tìm `jwks_uri`.

### 3. ClusterRoleBinding cho OIDC user

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: oidc-admin-binding
subjects:
- kind: User
  name: "oidc:admin@example.com"   # prefix oidc: + sub claim
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: ClusterRole
  name: cluster-admin
  apiGroup: rbac.authorization.k8s.io
```

---

## Lấy token cho kubectl

```bash
# Lấy token qua OIDC token endpoint
TOKEN=$(curl -s -X POST https://auth.your-domain.com/oauth2/token \
  -d "grant_type=password&username=admin@example.com&password=yourpassword" \
  | jq -r .access_token)

# Dùng với kubectl
kubectl --token="$TOKEN" get pods
```

Hoặc cấu hình kubeconfig:

```yaml
users:
- name: oidc-user
  user:
    exec:
      apiVersion: client.authentication.k8s.io/v1beta1
      command: kubectl
      args:
      - oidc-login
      - get-token
      - --oidc-issuer-url=https://auth.your-domain.com
      - --oidc-client-id=kubernetes
```

---

## Client Credentials (Machine-to-Machine)

Dùng cho CI/CD pipelines, k8s operators, v.v.:

```bash
# Lấy token với client_credentials grant
TOKEN=$(curl -s -X POST https://auth.your-domain.com/oauth2/token \
  -d "grant_type=client_credentials&client_id=k8s-service-account&client_secret=yourpassword" \
  | jq -r .access_token)
```

Thêm client mới vào bảng `oauth2_clients`:

```sql
INSERT INTO oauth2_clients (client_id, client_secret, scopes, granted_types, description)
VALUES (
    'my-operator',
    '$2a$10$...',   -- BCrypt hash của client secret
    'openid',
    'client_credentials',
    'My k8s operator'
);
```

---

## Dev / Local

Khi không set `OIDC_RSA_KEY_PATH`, service tự generate RSA key mỗi lần restart.
Token vẫn valid trong cùng session nhưng **sẽ bị invalidate khi restart**.

Để test local:

```bash
# Discovery
curl http://localhost:8081/.well-known/openid-configuration | jq

# JWKS
curl http://localhost:8081/oauth2/jwks | jq

# Login
curl -X POST http://localhost:8081/oauth2/token \
  -d "grant_type=password&username=admin&password=admin123" | jq

# UserInfo
curl -H "Authorization: Bearer <token>" http://localhost:8081/oauth2/userinfo | jq
```
