#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline,
    [switch]$AuthSmoke,
    [switch]$CatalogSmoke
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
$phase = if ($CatalogSmoke) { 'P3' } elseif ($AuthSmoke) { 'P2' } else { 'P1' }
$run = Join-Path $root ('.local\' + $phase.ToLowerInvariant() + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
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

function Invoke-AuthSmoke {
    $base = "http://127.0.0.1:$httpPort/api/v1"
    $email = 'p2-smoke@example.invalid'
    $credential = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
    $body = @{fullName='P2 Smoke'; email=$email; password=$credential} | ConvertTo-Json -Compress
    $registered = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' `
        -Body $body -StatusCodeVariable responseStatus
    if ($responseStatus -ne 201 -or $registered.role -ne 'CUSTOMER') { throw 'Register HTTP smoke failed' }
    $duplicate = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' `
        -Body $body -SkipHttpErrorCheck -StatusCodeVariable responseStatus
    if ($responseStatus -ne 409 -or $duplicate.code -ne 'EMAIL_ALREADY_EXISTS') { throw 'Duplicate HTTP smoke failed' }
    $loginBody = @{email=$email; password=$credential} | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $loginBody
    if ($login.tokenType -ne 'Bearer' -or -not $login.accessToken) { throw 'Login HTTP smoke failed' }
    $headers = @{Authorization='Bearer '+$login.accessToken}
    $profile = Invoke-RestMethod -Uri "$base/auth/me" -Headers $headers
    if ($profile.email -ne $email -or $profile.role -ne 'CUSTOMER') { throw 'Profile HTTP smoke failed' }
    $unauthorized = Invoke-RestMethod -Uri "$base/auth/me" -SkipHttpErrorCheck -StatusCodeVariable responseStatus
    if ($responseStatus -ne 401) { throw 'Missing-token HTTP smoke failed' }
    $denied = Invoke-RestMethod -Uri "$base/admin/bookings" -Headers $headers -SkipHttpErrorCheck -StatusCodeVariable responseStatus
    if ($responseStatus -ne 403) { throw 'Customer admin-denial HTTP smoke failed' }
    # Only this isolated disposable database is promoted, never a real user's database.
    $null = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $dbPort -U p1_runner `
        -d p1_dev -v ON_ERROR_STOP=1 -c "UPDATE users SET role = 'ADMIN' WHERE email = 'p2-smoke@example.invalid';"
    if ($LASTEXITCODE -ne 0) { throw 'Test admin promotion failed' }
    $profile = Invoke-RestMethod -Uri "$base/auth/me" -Headers $headers
    if ($profile.role -ne 'ADMIN') { throw 'Database role refresh HTTP smoke failed' }
    $login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $loginBody
    Write-Host 'P2 HTTP smoke: register, duplicate, login, me, 401, CUSTOMER 403 and ADMIN profile passed.'
    return $login.accessToken
}

function Invoke-CatalogSmoke([string]$Token) {
    $base = "http://127.0.0.1:$httpPort/api/v1"
    $headers = @{Authorization='Bearer '+$Token}
    $route = Invoke-RestMethod -Method Post -Uri "$base/admin/routes" -Headers $headers -ContentType 'application/json' `
        -Body (@{origin='P3 HTTP origin';destination='P3 HTTP destination';active=$true}|ConvertTo-Json)
    $vehicle = Invoke-RestMethod -Method Post -Uri "$base/admin/vehicles" -Headers $headers -ContentType 'application/json' `
        -Body (@{vehicleCode='HTTP-P3-BUS';vehicleType='BUS';displayName='P3 HTTP bus';active=$true}|ConvertTo-Json)
    $seat = Invoke-RestMethod -Method Post -Uri "$base/admin/vehicles/$($vehicle.id)/seats" -Headers $headers -ContentType 'application/json' `
        -Body (@{seatCode='A1';seatClass='STANDARD'}|ConvertTo-Json)
    $departure = [DateTimeOffset]::UtcNow.AddDays(3)
    $tripBody = @{routeId=$route.id;vehicleId=$vehicle.id;departureAt=$departure.ToString('o');arrivalAt=$departure.AddHours(2).ToString('o');basePrice=180000;status='SCHEDULED'} | ConvertTo-Json
    $trip = Invoke-RestMethod -Method Post -Uri "$base/admin/trips" -Headers $headers -ContentType 'application/json' -Body $tripBody
    $public = Invoke-RestMethod -Uri "$base/trips/$($trip.id)"
    $seats = Invoke-RestMethod -Uri "$base/trips/$($trip.id)/seats"
    $results = Invoke-RestMethod -Uri "$base/trips?origin=P3%20HTTP%20origin&size=1"
    if ($public.id -ne $trip.id -or $seats[0].status -ne 'AVAILABLE' -or $results.totalElements -ne 1) { throw 'P3 public HTTP smoke failed' }
    $overlap = Invoke-RestMethod -Method Post -Uri "$base/admin/trips" -Headers $headers -ContentType 'application/json' `
        -Body $tripBody -SkipHttpErrorCheck -StatusCodeVariable responseStatus
    if ($responseStatus -ne 409 -or $overlap.code -ne 'VEHICLE_SCHEDULE_CONFLICT') { throw 'P3 overlap HTTP smoke failed' }
    # Exercise deletes only on the records this smoke function just created.
    $null = Invoke-RestMethod -Method Delete -Uri "$base/admin/trips/$($trip.id)" -Headers $headers
    $null = Invoke-RestMethod -Method Delete -Uri "$base/admin/vehicles/$($vehicle.id)/seats/$($seat.id)" -Headers $headers
    $null = Invoke-RestMethod -Method Delete -Uri "$base/admin/vehicles/$($vehicle.id)" -Headers $headers
    $null = Invoke-RestMethod -Method Delete -Uri "$base/admin/routes/$($route.id)" -Headers $headers
    $seed = Join-Path $root 'database\02_demo_seed.sql'
    foreach ($attempt in @(1,2)) {
        $null = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $dbPort -U p1_runner -d p1_dev -v ON_ERROR_STOP=1 -f $seed
        if ($LASTEXITCODE -ne 0) { throw 'Demo seed failed' }
    }
    $demo = Invoke-RestMethod -Uri "$base/trips?origin=$([uri]::EscapeDataString('Hà Nội'))"
    if ($demo.totalElements -ne 1 -or $demo.content[0].vehicleCode -ne 'DEMO-P3-BUS-01') { throw 'Demo search failed or seed was not idempotent' }
    Write-Output 'P3 HTTP smoke: catalog CRUD, public search/detail/seats, overlap 409 and repeated demo seed passed.'
}

$variables = @('JAVA_HOME','DB_URL','DB_USERNAME','DB_PASSWORD','TEST_DB_URL',
    'TEST_DB_USERNAME','TEST_DB_PASSWORD','PGPASSWORD','SERVER_PORT','SPRING_PROFILES_ACTIVE','JWT_SECRET')
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
    $env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    Write-Output "$phase isolated verification: $run"
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
            if (($AuthSmoke -or $CatalogSmoke) -and $attempt -eq 1) { $smokeToken = Invoke-AuthSmoke }
            if ($CatalogSmoke -and $attempt -eq 1) { Invoke-CatalogSmoke $smokeToken }
            if (($AuthSmoke -or $CatalogSmoke) -and $attempt -eq 2) {
                $profile = Invoke-RestMethod -Uri "http://127.0.0.1:$httpPort/api/v1/auth/me" `
                    -Headers @{Authorization='Bearer '+$smokeToken}
                if ($profile.role -ne 'ADMIN') { throw 'Token failed after application restart' }
                Write-Output 'P2 HTTP smoke: Bearer token remained valid after restart.'
            }
            $count = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $dbPort -U p1_runner `
                -d p1_dev -At -v ON_ERROR_STOP=1 -c 'SELECT count(*) FROM flyway_schema_history WHERE success;'
            $expectedMigrations = @(Get-ChildItem -LiteralPath (Join-Path $backend 'src\main\resources\db\migration') -Filter 'V*__*.sql').Count
            if ($LASTEXITCODE -ne 0 -or "$count".Trim() -ne "$expectedMigrations") { throw 'Unexpected Flyway history' }
            Write-Output "Application launch ${attempt}: health UP; $expectedMigrations successful migrations."
            Stop-Process -Id $application.Id -ErrorAction Stop
            $application.WaitForExit()
            $application = $null
        }
    } finally { Pop-Location }
    $result = [ordered]@{
        verifiedAt = [DateTime]::UtcNow.ToString('o')
        postgres = (& (Join-Path $PostgresBin 'postgres.exe') --version)
        build = 'clean verify passed'
        firstLaunch = "UP; $expectedMigrations migrations applied"
        secondLaunch = "UP; $expectedMigrations migrations unchanged"
        socketDirectory = $socketDirectory
        scope = 'Infrastructure, auth and catalog tests; no booking API or payment implementation'
        authHttpSmoke = [bool]($AuthSmoke -or $CatalogSmoke)
        catalogHttpSmoke = [bool]$CatalogSmoke
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'verification.json') -Encoding utf8
    Write-Output "$phase verification PASSED. Temporary application and database will now stop."
} finally {
    if ($application -and -not $application.HasExited) { Stop-Process -Id $application.Id }
    if ($databaseStarted) { Invoke-PgControl 'stop' }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
