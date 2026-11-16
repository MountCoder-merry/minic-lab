$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    & mvn -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Tests or build failed.' }
    $taskDestination = Join-Path $PSScriptRoot 'dist\MiniC-Lab'
    New-Item -ItemType Directory -Force -Path (Join-Path $taskDestination 'target') | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'target\minic-lab.jar') -Destination (Join-Path $taskDestination 'target\minic-lab.jar') -Force
    foreach ($taskItem in @('src','docs','examples','pom.xml','README.md','README.zh-CN.md','Start-MiniC.cmd','Start-MiniC.ps1','Build-Delivery.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $taskItem) -Destination $taskDestination -Recurse -Force
    }
    Compress-Archive -Path (Join-Path $taskDestination '*') -DestinationPath (Join-Path $PSScriptRoot 'dist\MiniC-Lab-1.0.0.zip') -Force
    Write-Output ('Delivery: ' + (Join-Path $PSScriptRoot 'dist\MiniC-Lab-1.0.0.zip'))
} finally { Pop-Location }
