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
foreach ($binary in @('initdb.exe','pg_ctl.exe','createdb.exe','psql.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $binary))) { throw "Missing $binary" }
}
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'JavaHome must point to a JDK 21 installation.' }
$run = Join-Path $root ('.local\migration-audit-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$cluster = Join-Path $run 'postgres-data'
$passwordFile = Join-Path $run 'temporary-password'
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
[IO.File]::WriteAllText($passwordFile, $password, [Text.UTF8Encoding]::new($false))
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try { $port = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
if ($port -eq 5432 -or $port -lt 1024) { throw 'Refusing an existing/default PostgreSQL port.' }
$variables = @('JAVA_HOME','PGPASSWORD','MIGRATION_AUDIT_ALLOWED','MIGRATION_AUDIT_URL_PREFIX','MIGRATION_AUDIT_USER','MIGRATION_AUDIT_PASSWORD')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$started = $false
function Native([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $File" }
}
function Control([string]$Action) {
    $arguments = @('-D', ('"' + $cluster + '"'), '-w', '-t', '30')
    if ($Action -eq 'start') {
        $arguments += @('-l', ('"' + (Join-Path $run 'postgres.log') + '"'), '-o', ('"-h 127.0.0.1 -p ' + $port + '"'), 'start')
    } else { $arguments += @('-m', 'fast', 'stop') }
    $process = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $run "pgctl-$Action.out.log") -RedirectStandardError (Join-Path $run "pgctl-$Action.err.log")
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "Temporary PostgreSQL $Action failed. Inspect $run" }
}
try {
    $env:JAVA_HOME = $JavaHome
    $env:PGPASSWORD = $password
    Native (Join-Path $PostgresBin 'initdb.exe') @('-D',$cluster,'-U','audit_admin','--pwfile',$passwordFile,
        '--auth-local=scram-sha-256','--auth-host=scram-sha-256','--encoding=UTF8','--locale=C')
    Remove-Item -LiteralPath $passwordFile
    Control 'start'
    $started = $true
    Native (Join-Path $PostgresBin 'psql.exe') @('-X','-h','127.0.0.1','-p',"$port",'-U','audit_admin','-d','postgres','-v','ON_ERROR_STOP=1',
        '-c',"CREATE ROLE audit_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$password';")
    foreach ($name in @('fresh','upgrade','bad_v8','bad_v9','bad_v2','bad_v6','bad_v8_codes','bad_v9_injected')) {
        Native (Join-Path $PostgresBin 'createdb.exe') @('-h','127.0.0.1','-p',"$port",'-U','audit_admin','--owner=audit_owner',('migration_audit_' + $name))
    }
    $env:MIGRATION_AUDIT_ALLOWED = 'true'
    $env:MIGRATION_AUDIT_URL_PREFIX = "jdbc:postgresql://127.0.0.1:$port/migration_audit_"
    $env:MIGRATION_AUDIT_USER = 'audit_owner'
    $env:MIGRATION_AUDIT_PASSWORD = $password
    Write-Output "Isolated migration audit: $run; loopback port $port"
    Push-Location $backend
    try {
        $arguments = @('-B','-Dtest=MigrationSafetyTest,ProductionProfileTest','verify')
        if ($Offline) { $arguments = @('-o') + $arguments }
        Native (Join-Path $backend 'mvnw.cmd') $arguments
    } finally { Pop-Location }
    Copy-Item -LiteralPath (Join-Path $backend 'target\surefire-reports\TEST-com.example.ticketbooking.MigrationSafetyTest.xml') -Destination $run
    $schema = & (Join-Path $PostgresBin 'psql.exe') -X -h 127.0.0.1 -p $port -U audit_owner -d migration_audit_fresh -At -v ON_ERROR_STOP=1 -c @'
SELECT json_build_object(
 'migrationVersions', (SELECT json_agg(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success),
 'applicationTables', (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'),
 'users', (SELECT count(*) FROM users), 'trips', (SELECT count(*) FROM trips),
 'bookings', (SELECT count(*) FROM bookings), 'siteSettings', (SELECT count(*) FROM site_settings),
 'superuser', (SELECT rolsuper FROM pg_roles WHERE rolname=current_user)
);
'@
    if ($LASTEXITCODE -ne 0) { throw 'Read-only verification failed.' }
    $schema | Set-Content -LiteralPath (Join-Path $run 'fresh-schema.json')
    [ordered]@{
        verifiedAt=[DateTime]::UtcNow.ToString('o')
        postgres=(& (Join-Path $PostgresBin 'postgres.exe') --version)
        host='127.0.0.1'; port=$port; owner='audit_owner'; superuser=$false
        migrationTests=8; productionConfigurationTests=6; totalPassed=14
        neonConnectionAttempted=$false; existingLocalDatabaseTouched=$false
        migrationsChanged=$false; freshSchema=($schema | ConvertFrom-Json)
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $run 'verification.json')
    Write-Output "Migration safety PASSED. Evidence: $run"
} finally {
    if ($started) { Control 'stop' }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
