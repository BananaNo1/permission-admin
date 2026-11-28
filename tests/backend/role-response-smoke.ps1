param([string]$BaseUrl = 'http://127.0.0.1:18080')

$ErrorActionPreference = 'Stop'
if (-not $env:AUTH_TEST_USERNAME -or -not $env:AUTH_TEST_PASSWORD) {
    throw 'Set AUTH_TEST_USERNAME and AUTH_TEST_PASSWORD for a test administrator.'
}

$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$csrf = (Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session).data
$headers = @{ ($csrf.headerName) = $csrf.token }
Invoke-RestMethod "$BaseUrl/api/auth/login" -Method Post -WebSession $session -Headers $headers `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ username = $env:AUTH_TEST_USERNAME; password = $env:AUTH_TEST_PASSWORD } | Out-Null
$csrf = (Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session).data
$headers = @{ ($csrf.headerName) = $csrf.token }
$url = "$BaseUrl/api/system/roles"
$create = @{ Uri = $url; Method = 'Post'; WebSession = $session; Headers = $headers; ContentType = 'application/json' }

function Assert-Response($response, [int]$expected) {
    $body = $response.Content | ConvertFrom-Json
    if ($response.StatusCode -ne $expected -or $body.code -ne $expected -or -not $body.message `
        -or 'data' -notin $body.PSObject.Properties.Name) {
        throw "Unexpected API response status or fields for expected HTTP $expected"
    }
    return $body
}

$role = $null
try {
    $payload = @{ code = 'testreviewer'; name = 'reviewer'; dataScope = 'ALL' }
    $invalid = Invoke-WebRequest @create -Body ($payload | ConvertTo-Json) -SkipHttpErrorCheck
    $body = Assert-Response $invalid 400
    if (-not $body.data.fieldErrors.code -or $body.data.fieldErrors.name -or $body.data.fieldErrors.dataScope) {
        throw 'Expected a validation error for the role code alone.'
    }

    $payload.code = 'TEST_REVIEWER_' + [Guid]::NewGuid().ToString('N').ToUpperInvariant()
    $valid = Invoke-WebRequest @create -Body ($payload | ConvertTo-Json) -SkipHttpErrorCheck
    $role = (Assert-Response $valid 200).data
    if ($role.code -ne $payload.code -or $role.dataScope -ne 'ALL') { throw 'Role data does not match the request.' }

    $duplicate = Invoke-WebRequest @create -Body ($payload | ConvertTo-Json) -SkipHttpErrorCheck
    $body = Assert-Response $duplicate 409
    if ($null -ne $body.data) { throw 'Expected null data for duplicate role code.' }

    $list = Assert-Response (Invoke-WebRequest $url -WebSession $session) 200
    if ($role.id -notin $list.data.id) { throw 'Created role is missing from the response list.' }

    $delete = Invoke-WebRequest ("$url/{0}?version={1}" -f $role.id, $role.version) -Method Delete -WebSession $session -Headers $headers
    if ($null -ne (Assert-Response $delete 200).data) { throw 'Expected null data for role deletion.' }
    $role = $null
    Write-Output 'PASS role validation, creation, conflict, listing, and deletion response envelopes'
} finally {
    if ($role.id) {
        Invoke-RestMethod ("$url/{0}?version={1}" -f $role.id, $role.version) -Method Delete -WebSession $session -Headers $headers | Out-Null
    }
    Invoke-RestMethod "$BaseUrl/api/auth/logout" -Method Post -WebSession $session -Headers $headers | Out-Null
}
