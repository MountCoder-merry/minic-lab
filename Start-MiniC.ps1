$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskJar = Join-Path $taskRoot 'target\minic-lab.jar'
if (-not (Test-Path -LiteralPath $taskJar)) {
    Push-Location -LiteralPath $taskRoot
    try {
        & mvn -B test package
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    } finally { Pop-Location }
}
$taskJava = (Get-Command javaw -ErrorAction Stop).Source
Start-Process -FilePath $taskJava -ArgumentList @('-jar', ('"' + $taskJar + '"')) -WorkingDirectory $taskRoot -WindowStyle Hidden
