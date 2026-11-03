param([int]$Port = 18089)
$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $workspaceRoot 'platform-bootstrap\target\platform-bootstrap-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw '请先执行 mvn -pl platform-bootstrap -am -DskipTests package' }
# 桌面应用可能早于环境变量配置启动；启动后端前读取最新的用户/机器配置。
if (-not $env:DEEPSEEK_API_KEY) {
    $configuredKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')
    if (-not $configuredKey) { $configuredKey = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine') }
    if ($configuredKey) { $env:DEEPSEEK_API_KEY = $configuredKey }
}
$javaExecutable = if (Test-Path -LiteralPath 'D:\devtools\Java\jdk-17\bin\java.exe') {
    'D:\devtools\Java\jdk-17\bin\java.exe'
} else { (Get-Command java).Source }
$logFolder = Join-Path $workspaceRoot 'platform-bootstrap\target'
$backendProcess = Start-Process -FilePath $javaExecutable -ArgumentList @('-jar', $jarPath, "--server.port=$Port") -WorkingDirectory $workspaceRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logFolder 'backend.out.log') -RedirectStandardError (Join-Path $logFolder 'backend.err.log')
Write-Output "Backend PID=$($backendProcess.Id), port=$Port"
