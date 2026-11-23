param([string]$BaseUrl = 'http://127.0.0.1:18080')

$ErrorActionPreference = 'Stop'
if (-not $env:AUTH_TEST_USERNAME -or -not $env:AUTH_TEST_PASSWORD) {
    throw 'Set AUTH_TEST_USERNAME and AUTH_TEST_PASSWORD for a test administrator.'
}

$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$login = @{
    Method = 'Post'
    WebSession = $session
    Headers = $headers
    ContentType = 'application/x-www-form-urlencoded'
    Body = @{ username = $env:AUTH_TEST_USERNAME; password = $env:AUTH_TEST_PASSWORD }
}
Invoke-RestMethod "$BaseUrl/api/auth/login" @login | Out-Null
$csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session
$headers = @{}
$headers[$csrf.headerName] = $csrf.token

$url = "$BaseUrl/api/system/menus"
$menu = $null
try {
    $all = Invoke-RestMethod $url -WebSession $session
    $builtIn = $all | Where-Object permissionCode -eq 'system:user:create'
    if ($builtIn.name -ne '新增用户' -or [string]::IsNullOrEmpty($builtIn.description)) {
        throw 'The seeded permission did not receive its display metadata.'
    }
    $parent = $all | Where-Object permissionCode -eq 'system:menu:list'
    if (-not $parent.id) { throw 'Missing the seeded menu management page.' }

    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $payload = @{
        parentId = $parent.id
        type = 'BUTTON'
        name = '导出测试清单'
        description = '下载当前数据范围的测试记录。'
        permissionCode = "test:menu:export-$suffix"
        sortOrder = 0
        visible = $true
        status = 'ENABLED'
    }
    $write = @{ WebSession = $session; Headers = $headers; ContentType = 'application/json; charset=utf-8' }
    $menu = Invoke-RestMethod $url -Method Post @write -Body ($payload | ConvertTo-Json -Compress)
    if ($menu.name -ne $payload.name -or $menu.description -ne $payload.description) {
        throw 'The create response did not preserve the Chinese name and description.'
    }
    $listed = (Invoke-RestMethod "$BaseUrl/api/auth/menus" -WebSession $session) | Where-Object id -eq $menu.id
    if ($listed.description -ne $payload.description) { throw 'The granted menu API did not return the stored description.' }

    $payload.name = '导出业务测试清单'
    $payload.description = '生成业务测试记录的下载清单。'
    $payload.version = $menu.version
    $menu = Invoke-RestMethod "$url/$($menu.id)" -Method Put @write -Body ($payload | ConvertTo-Json -Compress)
    if ($menu.name -ne $payload.name -or $menu.description -ne $payload.description) {
        throw 'The update response did not preserve the updated display metadata.'
    }

    $expectedDescription = $payload.description
    $payload.Remove('description')
    $payload.version = $menu.version
    $menu = Invoke-RestMethod "$url/$($menu.id)" -Method Put @write -Body ($payload | ConvertTo-Json -Compress)
    if ($menu.description -ne $expectedDescription) { throw 'An older client silently removed the description.' }

    $payload.description = ''
    $payload.version = $menu.version
    $menu = Invoke-RestMethod "$url/$($menu.id)" -Method Put @write -Body ($payload | ConvertTo-Json -Compress)
    if ($null -ne $menu.description) { throw 'An explicitly blank description was not cleared.' }
    Write-Output 'PASS seeded metadata, create, read, update, omitted field, and clear'
} finally {
    if ($menu.id) {
        Invoke-RestMethod "$url/$($menu.id)?version=$($menu.version)" -Method Delete -WebSession $session -Headers $headers | Out-Null
    }
    Invoke-RestMethod "$BaseUrl/api/auth/logout" -Method Post -WebSession $session -Headers $headers | Out-Null
}
