#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][uri]$PublicBaseUrl)
$ErrorActionPreference = 'Stop'
if ($PublicBaseUrl.Scheme -ne 'https' -or $PublicBaseUrl.UserInfo -or $PublicBaseUrl.Query -or
    $PublicBaseUrl.Fragment -or $PublicBaseUrl.AbsolutePath -ne '/' -or $PublicBaseUrl.IsLoopback) {
    throw 'Use a public HTTPS origin without a path or credentials.'
}
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(20)
$origin = $PublicBaseUrl.GetLeftPart([UriPartial]::Authority)
try {
    $ipn = $client.GetAsync("$origin/api/v1/payments/vnpay/ipn").GetAwaiter().GetResult()
    $body = $ipn.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    if ([int]$ipn.StatusCode -ne 200 -or $body.RspCode -ne '97') {
        throw 'Unsigned IPN must reach the backend and return HTTP 200 / RspCode 97.'
    }
    $return = $client.GetAsync("$origin/payment/return?vnp_TxnRef=reachability-check&vnp_ResponseCode=00").GetAwaiter().GetResult()
    if ([int]$return.StatusCode -ne 302 -or $return.Headers.Location.AbsolutePath -ne '/payment/return' -or
        $return.Headers.Location.Query -ne '?vnp_TxnRef=reachability-check') {
        throw 'Return must redirect to the configured frontend with only the transaction reference.'
    }
    $blocked = $client.GetAsync("$origin/api/v1/admin/statistics").GetAwaiter().GetResult()
    if ([int]$blocked.StatusCode -ne 404) { throw 'Admin API must not be published by the callback relay.' }
    Write-Output 'HTTPS callback reachability PASSED: IPN rejects unsigned calls; Return only redirects; Admin is not exposed.'
    Write-Output 'This does not verify merchant credentials, portal registration or a real VNPAY transaction.'
} finally { $client.Dispose(); $handler.Dispose() }
