#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
& node.exe --test (Join-Path $PSScriptRoot 'vnpay-callback-server.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'VNPAY callback relay tests failed' }
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend')
try {
    & npm.cmd run lint
    if ($LASTEXITCODE -ne 0) { throw 'Frontend lint failed' }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
} finally { Pop-Location }
& (Join-Path $PSScriptRoot 'Test-P1.ps1') -PostgresBin $PostgresBin -JavaHome $JavaHome -Offline:$Offline -CatalogSmoke -BookingSmoke -FrontendSmoke -PaymentSmoke -AdminSmoke
