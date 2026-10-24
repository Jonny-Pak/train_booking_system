#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$java = Join-Path $JavaHome 'bin\java.exe'
foreach ($binary in @($java, (Join-Path $PostgresBin 'initdb.exe'),
        (Join-Path $PostgresBin 'pg_ctl.exe'), (Join-Path $PostgresBin 'createdb.exe'),
        (Join-Path $PostgresBin 'psql.exe'))) {
    if (-not (Test-Path -LiteralPath $binary)) { throw "Required executable missing: $binary" }
}

# Every run owns a new cluster, databases and application process. Existing services are untouched.
$run = Join-Path $root ('.local\p1-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$socketDirectory = Join-Path $root '.local\sockets'
New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
$cluster = Join-Path $run 'postgres-data'
$passwordFile = Join-Path $run 'initdb-password'
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
[IO.File]::WriteAllText($passwordFile, $password, [Text.UTF8Encoding]::new($false))

function Get-FreePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}

function Invoke-Native([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code ${LASTEXITCODE}: $File" }
}

function Invoke-PgControl([string]$Action) {
    $arguments = @('-D', ('"' + $cluster + '"'), '-w', '-t', '30')
    if ($Action -eq 'start') {
        $arguments += @('-l', ('"' + (Join-Path $run 'postgres.log') + '"'),
            '-o', ('"-h 127.0.0.1 -p ' + $dbPort + '"'), 'start')
    } else { $arguments += @('-m', 'fast', 'stop') }
    $process = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') `
        -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $run "pgctl-$Action.out.log") `
        -RedirectStandardError (Join-Path $run "pgctl-$Action.err.log")
    # Start-Process -Wait waits for the entire process tree, including the server.
    # WaitForExit only waits for pg_ctl itself, which exits when startup is ready.
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "PostgreSQL $Action failed. Inspect logs in $run" }
}

function Start-Application([int]$Attempt) {
    $jar = Join-Path $backend 'target\ticket-booking-0.0.1-SNAPSHOT.jar'
    # Use a workspace-owned socket directory; TEMP can contain paths unusable by AF_UNIX on Windows.
    $arguments = @(('-Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '"'),
        '-jar', ('"' + $jar + '"'), '--spring.profiles.active=dev')
    return Start-Process -FilePath $java -WindowStyle Hidden -PassThru `
        -ArgumentList $arguments `
        -RedirectStandardOutput (Join-Path $run "application-$Attempt.out.log") `
        -RedirectStandardError (Join-Path $run "application-$Attempt.err.log")
}

function Wait-Healthy($Process) {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) { throw "Application exited early. Inspect logs in $run" }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$httpPort/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Health never became UP. Inspect logs in $run"
}

$variables = @('JAVA_HOME','DB_URL','DB_USERNAME','DB_PASSWORD','TEST_DB_URL',
    'TEST_DB_USERNAME','TEST_DB_PASSWORD','PGPASSWORD','SERVER_PORT','SPRING_PROFILES_ACTIVE')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$dbPort = Get-FreePort
$httpPort = Get-FreePort
while ($httpPort -eq $dbPort) { $httpPort = Get-FreePort }
$databaseStarted = $false
$application = $null

try {
    $env:JAVA_HOME = $JavaHome
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    $env:PGPASSWORD = $password
    Write-Output "P1 isolated verification: $run"
    Invoke-Native (Join-Path $PostgresBin 'initdb.exe') @('-D', $cluster, '-U', 'p1_runner',
        '--pwfile', $passwordFile, '--auth-local=scram-sha-256', '--auth-host=scram-sha-256',
        '--encoding=UTF8', '--locale=C')
    Remove-Item -LiteralPath $passwordFile
    Invoke-PgControl 'start'
    $databaseStarted = $true
    foreach ($database in @('p1_dev','p1_test')) {
        Invoke-Native (Join-Path $PostgresBin 'createdb.exe') @('-h', '127.0.0.1', '-p', "$dbPort",
            '-U', 'p1_runner', $database)
    }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$dbPort/p1_dev"
    $env:DB_USERNAME = 'p1_runner'
    $env:DB_PASSWORD = $password
    $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$dbPort/p1_test"
    $env:TEST_DB_USERNAME = 'p1_runner'
    $env:TEST_DB_PASSWORD = $password
    $env:SERVER_PORT = "$httpPort"
    Push-Location $backend
    try {
        $mavenArguments = @('-B', 'clean', 'verify')
        if ($Offline) { $mavenArguments = @('-o') + $mavenArguments }
        Invoke-Native (Join-Path $backend 'mvnw.cmd') $mavenArguments
    } finally { Pop-Location }

    # Two real launches verify migration on an empty DB and no schema recreation on restart.
    Push-Location $backend
    try {
        foreach ($attempt in @(1,2)) {
            $application = Start-Application $attempt
            Wait-Healthy $application
            $count = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $dbPort -U p1_runner `
                -d p1_dev -At -v ON_ERROR_STOP=1 -c 'SELECT count(*) FROM flyway_schema_history WHERE success;'
            if ($LASTEXITCODE -ne 0 -or "$count".Trim() -ne '1') { throw 'Unexpected Flyway history' }
            Write-Output "Application launch ${attempt}: health UP; one successful migration."
            Stop-Process -Id $application.Id -ErrorAction Stop
            $application.WaitForExit()
            $application = $null
        }
    } finally { Pop-Location }
    $result = [ordered]@{
        verifiedAt = [DateTime]::UtcNow.ToString('o')
        postgres = (& (Join-Path $PostgresBin 'postgres.exe') --version)
        build = 'clean verify passed'
        firstLaunch = 'UP; V1 applied'
        secondLaunch = 'UP; V1 unchanged'
        socketDirectory = $socketDirectory
        scope = 'P1 only; no JWT, booking API or payment implementation'
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'verification.json') -Encoding utf8
    Write-Output 'P1 verification PASSED. Temporary application and database will now stop.'
} finally {
    if ($application -and -not $application.HasExited) { Stop-Process -Id $application.Id }
    if ($databaseStarted) { Invoke-PgControl 'stop' }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
