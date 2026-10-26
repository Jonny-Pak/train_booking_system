#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline
)

# Reuse the isolated cluster/build lifecycle, adding P2 HTTP smoke checks.
& (Join-Path $PSScriptRoot 'Test-P1.ps1') -PostgresBin $PostgresBin -JavaHome $JavaHome -Offline:$Offline -AuthSmoke
