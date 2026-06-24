Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$deadline = (Get-Date).AddSeconds(60)
$healthChecks = @(
    @{ Name = 'Collector health'; Url = 'http://localhost:13133/' },
    @{ Name = 'Tempo ready'; Url = 'http://localhost:3200/ready' },
    @{ Name = 'Prometheus ready'; Url = 'http://localhost:9090/-/ready' },
    @{ Name = 'Grafana health'; Url = 'http://localhost:3000/api/health' }
)

function ConvertFrom-CodePoint {
    param (
        [Parameter(Mandatory = $true)]
        [int[]] $CodePoints
    )

    return -join ($CodePoints | ForEach-Object { [char] $_ })
}

foreach ($healthCheck in $healthChecks) {
    $lastError = $null

    do {
        try {
            # 1. 请求组件健康端点，单次请求最长等待 5 秒。
            $response = Invoke-WebRequest -Uri $healthCheck.Url -UseBasicParsing -TimeoutSec 5

            # 2. 校验 HTTP 状态码，任一非 2xx 状态立即记录并进入短重试。
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                $lastError = $null
                break
            }

            $lastError = "$($healthCheck.Name) status code: $($response.StatusCode)"
        }
        catch {
            # 3. 捕获连接异常，避免组件启动中的瞬时失败直接中断脚本。
            $lastError = "$($healthCheck.Name) request failed: $($_.Exception.Message)"
        }

        if ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    if ($null -ne $lastError) {
        Write-Error $lastError
        exit 1
    }
}

# 4. 所有组件健康端点均通过后输出成功信息。
$successMessage = ConvertFrom-CodePoint @(21487, 35266, 27979, 22522, 30784, 35774, 26045, 20882, 28895, 26816, 26597, 36890, 36807)
Write-Output $successMessage
