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

所有端口默认仅绑定 `127.0.0.1`，只允许本机访问，避免将 Grafana、Prometheus 和 OTLP 接收端口暴露到局域网或公网。如确需局域网访问，请自行修改 `docker-compose.yml` 的端口绑定，并自行承担认证、访问控制和网络暴露风险。

| 组件 | 地址 | 说明 |
| --- | --- | --- |
| OpenTelemetry Collector | `127.0.0.1:4317` | OTLP gRPC 接收端口 |
| OpenTelemetry Collector | `127.0.0.1:4318` | OTLP HTTP 接收端口 |
| OpenTelemetry Collector | `127.0.0.1:13133` | Collector 健康检查 |
| Tempo | `127.0.0.1:3200` | Tempo API 与 `/ready` |
| Prometheus | `127.0.0.1:9090` | Prometheus 控制台 |
| Grafana | `127.0.0.1:3000` | Grafana 控制台 |

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

Grafana 会自动配置 Prometheus 和 Tempo 数据源，并加载 `Data Agent Overview` 仪表盘。仪表盘包含 JVM 堆内存、HTTP 请求量和 HTTP 平均耗时等默认面板，使用 actuator/prometheus Timer 通常会暴露的 `http_server_requests_seconds_sum` 与 `http_server_requests_seconds_count` 指标。

## 冒烟检查

启动容器后执行：

```powershell
powershell -ExecutionPolicy Bypass -File docker/observability/smoke-test.ps1
```

脚本使用全局约 50 秒总等待上限，会先检查以下基础设施端点，任一端点非 2xx 或请求异常都会退出 1：

- `http://localhost:13133/`
- `http://localhost:3200/ready`
- `http://localhost:9090/-/ready`
- `http://localhost:3000/api/health`

基础设施健康检查通过后，脚本默认会继续查询 Prometheus API，确认 `data-agent` 应用指标链路可用：

- `http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22data-agent%22%7D`

如果只想检查基础设施，可增加 `-SkipApplicationCheck`：

```powershell
powershell -ExecutionPolicy Bypass -File docker/observability/smoke-test.ps1 -SkipApplicationCheck
```

全部通过时输出：

```text
可观测基础设施和应用指标链路冒烟检查通过
```

使用 `-SkipApplicationCheck` 时仍输出 `可观测基础设施冒烟检查通过`。

## 常见问题

1. 端口已被占用：请确认本机 `3000`、`9090`、`3200`、`4317`、`4318`、`13133` 未被其他进程占用。
2. Docker Engine 不可用：如果看到 `无法连接 dockerDesktopLinuxEngine`、`Cannot connect to the Docker daemon` 或类似错误，先执行 `docker version` 和 `docker info` 检查 Docker Engine 是否可用。Windows/macOS 请启动 Docker Desktop 并等待 Engine 就绪；Linux 请确认 Docker 服务已启动，例如执行 `sudo systemctl status docker`，必要时执行 `sudo systemctl start docker`。
3. Prometheus 看不到应用指标或冒烟检查应用指标链路失败：请确认应用已在本机 `18080` 暴露 `/actuator/prometheus`，并且 Docker 可以访问 `host.docker.internal`。Windows/macOS Docker Desktop 通常内置支持 `host.docker.internal`；Linux 环境可能需要 Docker 支持 `host-gateway`，或在 compose 中额外配置 `extra_hosts`。
4. Grafana 无数据：请先确认 Prometheus Targets 页面中 `data-agent` 目标可用，再刷新仪表盘时间范围。默认仪表盘使用 actuator/prometheus Timer 通常可用的 HTTP 请求计数和耗时总和指标。
5. Collector 无法写入 Tempo：请先检查 Tempo `/ready` 是否返回 2xx，再查看 Collector 容器日志。
