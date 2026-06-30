param(
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "teaching_sys",
    [string]$DbUser = "teaching_user",
    [string]$DbPassword = $env:MYSQL_PASSWORD,
    [string]$MySqlPath = "mysql",
    [string]$BaseUrl = "",
    [switch]$CreateDatabase
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$AuthSql = Join-Path $RepoRoot "src\main\resources\db\init-auth.sql"
$BusinessSql = Join-Path $RepoRoot "src\main\resources\db\init-business.sql"

function Fail([string]$Message) {
    [Console]::Error.WriteLine($Message)
    exit 1
}

function Assert-SafeDatabaseName([string]$Name) {
    if ($Name -notmatch "^[A-Za-z0-9_]+$") {
        Fail "DbName only supports letters, numbers, and underscore: $Name"
    }
}

function Invoke-Mysql([string]$Name, [string[]]$ExtraArgs, [string]$InputFile = "") {
    $oldPassword = $env:MYSQL_PWD
    $env:MYSQL_PWD = $DbPassword
    try {
        $args = @(
            "--protocol=TCP",
            "-h", $DbHost,
            "-P", [string]$DbPort,
            "-u", $DbUser,
            "--default-character-set=utf8mb4"
        ) + $ExtraArgs

        if ([string]::IsNullOrWhiteSpace($InputFile)) {
            & $MySqlPath @args
        } else {
            Get-Content -LiteralPath $InputFile -Raw -Encoding UTF8 | & $MySqlPath @args
        }

        if ($LASTEXITCODE -ne 0) {
            Fail "$Name failed with exit code $LASTEXITCODE"
        }
    } finally {
        $env:MYSQL_PWD = $oldPassword
    }
}

function Import-Sql([string]$Name, [string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        Fail "$Name file not found: $Path"
    }
    Invoke-Mysql $Name @($DbName) $Path
    Write-Host "[ok] imported $Name"
}

Assert-SafeDatabaseName $DbName

try {
    if ($CreateDatabase) {
        Invoke-Mysql "create database" @("-e", "CREATE DATABASE IF NOT EXISTS ``$DbName`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        Write-Host "[ok] database exists: $DbName"
    }

    Import-Sql "init-auth.sql" $AuthSql
    Import-Sql "init-business.sql" $BusinessSql

    $checkSql = @"
SELECT CASE WHEN
  (SELECT COUNT(*) FROM sys_user WHERE account IN ('student001','teacher001','admin001')) = 3
  AND EXISTS (SELECT 1 FROM course WHERE course_id = 'course-java-001')
  AND EXISTS (SELECT 1 FROM course_class WHERE course_class_id = 'class-java-001')
  AND EXISTS (SELECT 1 FROM question WHERE question_id = 'jg-q-001')
THEN 'OK' ELSE 'FAIL' END;
"@

    $check = Invoke-Mysql "seed check" @("-N", "-B", $DbName, "-e", $checkSql)
    if (($check | Select-Object -Last 1) -ne "OK") {
        Fail "seed check failed"
    }
    Write-Host "[ok] seed check passed"

    if (-not [string]::IsNullOrWhiteSpace($BaseUrl)) {
        & (Join-Path $PSScriptRoot "verify_p0_public.ps1") -BaseUrl $BaseUrl
        if ($LASTEXITCODE -ne 0) {
            Fail "P0 verification failed"
        }
    }

    Write-Host "[ok] empty database init verification passed"
} catch {
    Fail $_.Exception.Message
}
