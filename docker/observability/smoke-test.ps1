param (
    [switch] $SkipApplicationCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 1. 设置全局 50 秒总等待上限，避免服务未启动时脚本无限或超长等待。
$maxWaitSeconds = 50
$deadline = (Get-Date).AddSeconds($maxWaitSeconds)
$healthChecks = @(
    @{ Name = 'Collector 健康检查'; Url = 'http://localhost:13133/' },
    @{ Name = 'Tempo 就绪检查'; Url = 'http://localhost:3200/ready' },
    @{ Name = 'Prometheus 就绪检查'; Url = 'http://localhost:9090/-/ready' },
    @{ Name = 'Grafana 健康检查'; Url = 'http://localhost:3000/api/health' }
)

function Get-RemainingTimeoutSeconds {
    param (
        [Parameter(Mandatory = $true)]
        [datetime] $Deadline,
        [Parameter(Mandatory = $true)]
        [int] $MaxRequestSeconds
    )

    # 1. 计算距离全局截止时间的剩余秒数。
    $remainingSeconds = [int][Math]::Floor(($Deadline - (Get-Date)).TotalSeconds)
    if ($remainingSeconds -le 0) {
        return 0
    }

    # 2. 使用较小值作为单次请求超时，避免突破全局等待上限。
    return [Math]::Min($remainingSeconds, $MaxRequestSeconds)
}

function Test-ApplicationMetrics {
    param (
        [Parameter(Mandatory = $true)]
        [datetime] $Deadline
    )

    $applicationQueryUrl = 'http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22data-agent%22%7D'
    $timeoutSeconds = Get-RemainingTimeoutSeconds -Deadline $Deadline -MaxRequestSeconds 5
    if ($timeoutSeconds -le 0) {
        Write-Output '冒烟检查失败：等待应用指标链路检查时已达到全局等待上限，请确认应用已在本机 18080 暴露 /actuator/prometheus。'
        exit 1
    }

    try {
        # 1. 查询 Prometheus 中 data-agent 任务的 up 指标。
        $queryResult = Invoke-RestMethod -Uri $applicationQueryUrl -TimeoutSec $timeoutSeconds

        # 2. 校验 Prometheus 查询状态和结果集合，避免空数据被误判为通过。
        $metricResults = @($queryResult.data.result)
        if ($queryResult.status -ne 'success' -or $metricResults.Count -lt 1) {
            Write-Output '冒烟检查失败：Prometheus 暂未抓取到 data-agent 应用指标，请确认应用已在本机 18080 暴露 /actuator/prometheus。'
            exit 1
        }

        # 3. 只要存在一个 up 值为 1 的 data-agent 目标，即认为应用指标链路可用。
        foreach ($metricResult in $metricResults) {
            if ($null -ne $metricResult.value -and $metricResult.value.Count -ge 2 -and [string]$metricResult.value[1] -eq '1') {
                return
            }
        }

        Write-Output '冒烟检查失败：Prometheus 已发现 data-agent 目标，但应用指标链路未就绪，请确认应用已在本机 18080 暴露 /actuator/prometheus。'
        exit 1
    }
    catch {
        Write-Output '冒烟检查失败：无法查询 Prometheus 应用指标，请确认 Prometheus 已启动，且应用已在本机 18080 暴露 /actuator/prometheus。'
        exit 1
    }
}

foreach ($healthCheck in $healthChecks) {
    $lastError = $null

    do {
        try {
            $timeoutSeconds = Get-RemainingTimeoutSeconds -Deadline $deadline -MaxRequestSeconds 5
            if ($timeoutSeconds -le 0) {
                $lastError = "$($healthCheck.Name)等待超时"
                break
            }

            # 2. 请求组件健康端点，单次请求不会超过全局剩余等待时间。
            $response = Invoke-WebRequest -Uri $healthCheck.Url -UseBasicParsing -TimeoutSec $timeoutSeconds

            # 3. 校验 HTTP 状态码，任一非 2xx 状态立即记录并进入短重试。
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                $lastError = $null
                break
            }

            $lastError = "$($healthCheck.Name) 返回非 2xx 状态码：$($response.StatusCode)"
        }
        catch {
            # 4. 捕获连接异常，避免组件启动中的瞬时失败直接中断脚本。
            $lastError = "$($healthCheck.Name)不可访问"
        }

        $sleepSeconds = Get-RemainingTimeoutSeconds -Deadline $deadline -MaxRequestSeconds 2
        if ($sleepSeconds -gt 0) {
            Start-Sleep -Seconds $sleepSeconds
        }
    } while ((Get-Date) -lt $deadline)

    if ($null -ne $lastError) {
        Write-Output "冒烟检查失败：$lastError，请确认已执行 docker compose up -d，且 Docker Engine 正在运行。"
        exit 1
    }
}

# 5. 基础设施通过后，默认继续检查应用指标链路。
if (-not $SkipApplicationCheck) {
    Test-ApplicationMetrics -Deadline $deadline
}

# 6. 所有检查通过后输出成功信息。
if ($SkipApplicationCheck) {
    Write-Output '可观测基础设施冒烟检查通过'
}
else {
    Write-Output '可观测基础设施和应用指标链路冒烟检查通过'
}
