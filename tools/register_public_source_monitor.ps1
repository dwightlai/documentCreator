param(
    [string]$TaskName = 'DocumentCreator-PublicSourceMonitor'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$monitor = (Resolve-Path (Join-Path $PSScriptRoot 'check_public_sources.mjs')).Path
$node = (Get-Command node -ErrorAction Stop).Source
$action = New-ScheduledTaskAction -Execute $node -Argument ('"' + $monitor + '"') -WorkingDirectory $projectRoot
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday -At '09:20'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 15)

if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
    throw "Scheduled task $TaskName already exists; inspect it before changing the schedule."
}

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description 'Checks registered public government pages and writes a human review report.' | Out-Null
Get-ScheduledTask -TaskName $TaskName | Select-Object TaskName,State
