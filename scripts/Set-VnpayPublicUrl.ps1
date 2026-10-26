#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PublicBaseUrl,
    [switch]$EnablePayment
)
$ErrorActionPreference = 'Stop'
$uri = $null
if (-not [Uri]::TryCreate($PublicBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
    $uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
    $uri.AbsolutePath -ne '/' -or $uri.IsLoopback) {
    throw 'PublicBaseUrl must be a public HTTPS origin, for example https://example.trycloudflare.com'
}
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $path)) { throw 'Create the root .env first; existing credentials will be preserved.' }
$lines = [Collections.Generic.List[string]]::new()
$lines.AddRange([string[]][IO.File]::ReadAllLines($path))
if ($EnablePayment) {
    foreach ($name in @('VNPAY_TMN_CODE','VNPAY_HASH_SECRET')) {
        $configured = $false
        foreach ($line in $lines) {
            if ($line -match ('^' + $name + '=(.+)$') -and $matches[1].Trim()) { $configured = $true }
        }
        if (-not $configured) { throw "Fill $name in the ignored root .env before enabling payment. Do not send secrets in chat." }
    }
}
$origin = $uri.GetLeftPart([UriPartial]::Authority)
$values = [ordered]@{
    VNPAY_RETURN_URL = "$origin/payment/return"
    VNPAY_IPN_URL = "$origin/api/v1/payments/vnpay/ipn"
}
if ($EnablePayment) { $values['VNPAY_ENABLED'] = 'true' }
foreach ($name in $values.Keys) {
    $found = $false
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match ('^' + $name + '=')) {
            $lines[$index] = $name + '=' + $values[$name]
            $found = $true
        }
    }
    if (-not $found) { $lines.Add($name + '=' + $values[$name]) }
}
[IO.File]::WriteAllLines($path, $lines, [Text.UTF8Encoding]::new($false))
Write-Output ('Return URL: ' + $values['VNPAY_RETURN_URL'])
Write-Output ('IPN URL: ' + $values['VNPAY_IPN_URL'])
Write-Output 'Register the IPN URL with the VNPAY merchant portal/support, then restart the backend. No keys were printed.'
