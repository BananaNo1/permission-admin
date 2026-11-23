param([string]$BaseUrl = 'http://127.0.0.1:18080')
$ErrorActionPreference = 'Stop'
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$started = Get-Date
function Assert-Status($response, [int]$expected, [string]$scenario) {
    if ([int]$response.StatusCode -ne $expected) {
        throw "$scenario expected=$expected actual=$($response.StatusCode)"
    }
    Write-Output "PASS $scenario ($expected)"
}
try {
    $csrfResponse = Invoke-WebRequest "$BaseUrl/api/auth/csrf" -WebSession $session -SkipHttpErrorCheck -TimeoutSec 10
    Assert-Status $csrfResponse 200 'anonymous CSRF endpoint'
    $csrf = $csrfResponse.Content | ConvertFrom-Json
    if (-not $csrf.token -or -not $csrf.headerName) { throw 'CSRF response lacks token or headerName' }
    $response = Invoke-WebRequest "$BaseUrl/api/auth/me" -WebSession $session -SkipHttpErrorCheck -TimeoutSec 10
    Assert-Status $response 401 'anonymous identity request'
    $response = Invoke-WebRequest "$BaseUrl/api/auth/logout" -Method Post -WebSession $session -SkipHttpErrorCheck -TimeoutSec 10
    Assert-Status $response 403 'write without CSRF token'
    if ($env:AUTH_TEST_USERNAME -and $env:AUTH_TEST_PASSWORD) {
        $headers = @{}
        $headers[$csrf.headerName] = $csrf.token
        $response = Invoke-WebRequest "$BaseUrl/api/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/x-www-form-urlencoded' -Body @{username=$env:AUTH_TEST_USERNAME;password=$env:AUTH_TEST_PASSWORD} -SkipHttpErrorCheck -TimeoutSec 10
        Assert-Status $response 200 'test account login'
        $response = Invoke-WebRequest "$BaseUrl/api/auth/me" -WebSession $session -SkipHttpErrorCheck -TimeoutSec 10
        Assert-Status $response 200 'session remains valid after login'
        $me = $response.Content | ConvertFrom-Json
        if ($me.username -ne $env:AUTH_TEST_USERNAME) { throw 'unexpected authenticated account' }
        $csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session -TimeoutSec 10
        $headers[$csrf.headerName] = $csrf.token
        $response = Invoke-WebRequest "$BaseUrl/api/auth/logout" -Method Post -WebSession $session -Headers $headers -SkipHttpErrorCheck -TimeoutSec 10
        Assert-Status $response 200 'authenticated logout'
        $response = Invoke-WebRequest "$BaseUrl/api/auth/me" -WebSession $session -SkipHttpErrorCheck -TimeoutSec 10
        Assert-Status $response 401 'session invalid after logout'
    } else { Write-Output 'SKIP authenticated scenarios: set AUTH_TEST_USERNAME and AUTH_TEST_PASSWORD for a test account' }
    Write-Output "Elapsed $(((Get-Date)-$started).TotalSeconds) seconds"
    exit 0
} catch {
    Write-Output "FAIL $($_.Exception.Message)"
    Write-Output "Elapsed $(((Get-Date)-$started).TotalSeconds) seconds"
    exit 1
}