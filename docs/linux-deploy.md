# Linux 单机部署

本文说明在一台 Linux 上部署本系统的后端和前端。数据库使用已经安装好的 PostgreSQL。不使用容器。

本文使用 `local` 配置。这个配置启用页面账号登录，文件存在本机磁盘，单机协调使用内存。仓库中的 `prod` 配置还要求统一身份认证、Redis 和对象存储，不在本文范围内。在线编辑默认关闭。

## 前置条件

- 一台 Linux，能安装 JDK 17、Node.js 20.19 或更新版本、pnpm 11.7.0 和 Nginx。
- PostgreSQL 已安装，并且可以为本系统新建一个空库。仓库的容器基线使用 PostgreSQL 16。
- 部署使用的数据库必须是空库。启动时 Flyway 会执行 `V1__baseline.sql` 和 `V2__open_application.sql`。已经执行过旧版 `V1` 至 `V45` 迁移的库不能改用这套脚本。
- 构建机需要能读取本仓库源码。前端构建时不要设置 `VITE_OIDC_URL`。设置后，页面会改为统一身份认证登录，不再使用本系统的登录页。

## 准备数据库

由数据库管理员创建空库和专用账号，并授予该账号在这个库中建表的权限。下面的库名、账号和口令换成现场值。

```sql
CREATE USER document_platform WITH PASSWORD '替换为数据库口令';
CREATE DATABASE document_platform OWNER document_platform ENCODING 'UTF8';
```

## 构建后端

在仓库根目录执行：

```bash
./mvnw -pl platform-bootstrap -am -DskipTests package
```

产物为 `platform-bootstrap/target/platform-bootstrap-0.1.0-SNAPSHOT.jar`。

准备运行目录，并由运行账号持有这些目录：

```bash
sudo install -d -o document -g document /opt/document-platform /var/lib/document-platform/files /etc/document-platform
sudo install -o document -g document -m 640 platform-bootstrap/target/platform-bootstrap-0.1.0-SNAPSHOT.jar /opt/document-platform/app.jar
```

`document` 是运行后端的系统账号。没有该账号时先创建，并且不要给它登录 shell 以外的管理权限。

## 配置后端

仓库自带的 `application-local.yml` 可能指向嵌入式数据库。部署时用外部配置覆盖数据源，不要把口令写进仓库。

创建 `/etc/document-platform/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/document_platform
    username: document_platform
    password: 替换为数据库口令
    driver-class-name: org.postgresql.Driver
  h2:
    console:
      enabled: false

platform:
  security:
    local-admin-password: 替换为初始管理员口令
  storage:
    file-root: /var/lib/document-platform/files

management:
  health:
    redis:
      enabled: false

server:
  address: 127.0.0.1
```

限制这个文件只有运行账号可读：

```bash
sudo chown document:document /etc/document-platform/application.yml
sudo chmod 640 /etc/document-platform/application.yml
```

`local-admin-password` 只在内置管理员还没有口令时写入一次。首次启动后，用页面上的「修改密码」更换。

创建 `/etc/systemd/system/document-platform.service`：

```ini
[Unit]
Description=document platform
After=network.target

[Service]
User=document
Group=document
WorkingDirectory=/opt/document-platform
Environment=SPRING_PROFILES_ACTIVE=local
Environment=JAVA_OPTS=-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/document-platform/app.jar --spring.config.additional-location=file:/etc/document-platform/application.yml
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启动并确认健康检查返回 `UP`：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now document-platform
curl -fsS http://127.0.0.1:18089/actuator/health/readiness
```

后端只监听 `127.0.0.1:18089`。不要把 `18089` 对公网开放。日志用 `journalctl -u document-platform -f` 查看。

## 构建前端

在 `platform-web` 目录执行。确认环境中没有 `VITE_OIDC_URL`。

```bash
corepack enable
corepack prepare pnpm@11.7.0 --activate
pnpm install --frozen-lockfile
pnpm build
```

构建结果在 `platform-web/dist`。把它放到 Nginx 能读的目录，例如 `/var/www/document-platform`。

## 配置 Nginx

前端页面和接口走同一个站点。浏览器访问 Nginx，Nginx 把接口转到本机后端。上传上限与后端一致，请求体最大 `60m`。

```nginx
server {
    listen 80;
    server_name _;
    client_max_body_size 60m;

    location /api/ {
        proxy_pass http://127.0.0.1:18089;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;
    }

    location /actuator/health {
        proxy_pass http://127.0.0.1:18089;
        proxy_set_header Host $host;
    }

    location /v3/api-docs {
        proxy_pass http://127.0.0.1:18089;
        proxy_set_header Host $host;
    }

    location /swagger-ui {
        proxy_pass http://127.0.0.1:18089;
        proxy_set_header Host $host;
    }

    location = /swagger-ui.html {
        proxy_pass http://127.0.0.1:18089;
        proxy_set_header Host $host;
    }

    location / {
        root /var/www/document-platform;
        try_files $uri $uri/ /index.html;
    }
}
```

对外提供访问时，在这台 Nginx 或前置负载均衡上启用 HTTPS。启用 HTTPS 后，把上面的 `80` 改为实际监听端口，并保证 `X-Forwarded-Proto` 传到后端。

重载 Nginx 后，浏览器打开站点，应出现本系统登录页。

## 验收

1. 打开登录页。租户编码填 `local-government`，账号填 `admin`，口令填 `local-admin-password` 中设置的初始口令。
2. 登录后进入「智能写作」或「智能审校」，页面能加载。
3. 在「第三方」中创建一个应用并生成密钥。用该应用编码和密钥调用开放接口。未带密钥时应返回 401。
4. 上传不超过 50MB 的 DOCX，请求不被 Nginx 拒绝。

初始口令只用于第一次登录。登录后立即在页面修改。

## 更新

1. 停止服务：`sudo systemctl stop document-platform`。
2. 替换 `/opt/document-platform/app.jar`。
3. 如有前端变更，重新执行 `pnpm build`，替换 `/var/www/document-platform`。
4. 启动服务，并再次检查 `http://127.0.0.1:18089/actuator/health/readiness`。

Flyway 只追加新的迁移脚本。不要修改已经在该库执行过的 `V1__baseline.sql` 或 `V2__open_application.sql`。
