# 本地可观测基础设施

本目录提供本地开发使用的 OpenTelemetry Collector、Tempo、Prometheus 和 Grafana 编排，用于验证 Data Agent 的链路追踪与指标暴露。

## 启动

在项目根目录执行：

```powershell
docker compose -f docker/observability/docker-compose.yml up -d
```

服务启动后，应用可通过以下环境变量上报链路：

```powershell
$env:OBSERVABILITY_OTLP_TRACES_ENDPOINT='http://localhost:4318/v1/traces'
```

## 关闭

```powershell
docker compose -f docker/observability/docker-compose.yml down
```

如需同时清理本地可观测数据卷：

```powershell
docker compose -f docker/observability/docker-compose.yml down -v
```

## 端口

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| OpenTelemetry Collector | `localhost:4317` | OTLP gRPC 接收端口 |
| OpenTelemetry Collector | `localhost:4318` | OTLP HTTP 接收端口 |
| OpenTelemetry Collector | `localhost:13133` | Collector 健康检查 |
| Tempo | `localhost:3200` | Tempo API 与 `/ready` |
| Prometheus | `localhost:9090` | Prometheus 控制台 |
| Grafana | `localhost:3000` | Grafana 控制台 |

## Prometheus 抓取地址

Prometheus 默认抓取应用指标：

```text
host.docker.internal:18080/actuator/prometheus
```

同时会抓取 Prometheus 自身的 `prometheus:9090`，便于确认 Prometheus 运行状态。

## Grafana

默认账号：

```text
用户名：admin
密码：admin
```

安全提示：`admin/admin` 仅限本地开发使用，不要将 Grafana 暴露到公网。必要时请在 `docker-compose.yml` 中通过 `GF_SECURITY_ADMIN_USER` 和 `GF_SECURITY_ADMIN_PASSWORD` 修改默认账号密码。

Grafana 会自动配置 Prometheus 和 Tempo 数据源，并加载 `Data Agent Overview` 仪表盘。仪表盘包含 JVM 堆内存、HTTP 请求量和 HTTP P95 面板。HTTP P95 面板依赖 `http_server_requests_seconds_bucket` 指标；如果应用未启用 HTTP server request histogram，该面板会没有数据。

## 冒烟检查

启动容器后执行：

```powershell
powershell -ExecutionPolicy Bypass -File docker/observability/smoke-test.ps1
```

脚本会检查以下端点，任一端点非 2xx 或请求异常都会退出 1：

- `http://localhost:13133/`
- `http://localhost:3200/ready`
- `http://localhost:9090/-/ready`
- `http://localhost:3000/api/health`

全部通过时输出：

```text
可观测基础设施冒烟检查通过
```

## 常见问题

1. 端口已被占用：请确认本机 `3000`、`9090`、`3200`、`4317`、`4318`、`13133` 未被其他进程占用。
2. Docker Engine 不可用：如果看到 `无法连接 dockerDesktopLinuxEngine`、`Cannot connect to the Docker daemon` 或类似错误，先执行 `docker version` 和 `docker info` 检查 Docker Engine 是否可用。Windows/macOS 请启动 Docker Desktop 并等待 Engine 就绪；Linux 请确认 Docker 服务已启动，例如执行 `sudo systemctl status docker`，必要时执行 `sudo systemctl start docker`。
3. Prometheus 看不到应用指标：请确认应用已在本机 `18080` 暴露 `/actuator/prometheus`，并且 Docker 可以访问 `host.docker.internal`。Windows/macOS Docker Desktop 通常内置支持 `host.docker.internal`；Linux 环境可能需要 Docker 支持 `host-gateway`，或在 compose 中额外配置 `extra_hosts`。
4. Grafana 无数据：请先确认 Prometheus Targets 页面中 `data-agent` 目标可用，再刷新仪表盘时间范围。HTTP P95 面板还要求应用暴露 `http_server_requests_seconds_bucket`，未启用 HTTP server request histogram 时该面板为空是预期现象。
5. Collector 无法写入 Tempo：请先检查 Tempo `/ready` 是否返回 2xx，再查看 Collector 容器日志。
