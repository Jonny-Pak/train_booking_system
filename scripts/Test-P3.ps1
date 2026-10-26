#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline
)

& (Join-Path $PSScriptRoot 'Test-P1.ps1') -PostgresBin $PostgresBin -JavaHome $JavaHome -Offline:$Offline -CatalogSmoke
