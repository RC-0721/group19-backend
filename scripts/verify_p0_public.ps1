param(
    [string]$BaseUrl = "http://47.108.76.210"
)

$ErrorActionPreference = "Stop"
$Root = $BaseUrl.TrimEnd("/")

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

function Invoke-Api([string]$Method, [string]$Path, [string]$Token = "", $Body = $null) {
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["token"] = $Token
    }

    $args = @{
        Method = $Method
        Uri = "$Root$Path"
        Headers = $headers
    }

    if ($null -ne $Body) {
        $args["ContentType"] = "application/json"
        $args["Body"] = ($Body | ConvertTo-Json -Compress)
    }

    Invoke-RestMethod @args
}

function Assert-Code([string]$Name, $Response, [string]$ExpectedCode) {
    if ($null -eq $Response -or [string]$Response.code -ne $ExpectedCode) {
        $actual = if ($null -eq $Response) { "<null>" } else { [string]$Response.code }
        Fail "$Name expected code=$ExpectedCode, actual=$actual"
    }
    Write-Host "[ok] $Name code=$ExpectedCode"
}

function Login([string]$Account, [string]$Role) {
    $response = Invoke-Api "Post" "/api/auth/login" "" @{
        account = $Account
        password = "123456"
        role = $Role
    }
    Assert-Code "login $Account" $response "0"

    $token = [string]$response.data.token
    if ([string]::IsNullOrWhiteSpace($token)) {
        Fail "login $Account returned empty token"
    }
    $token
}

try {
    Assert-Code "health" (Invoke-Api "Get" "/api/health") "0"

    $studentToken = Login "student001" "STUDENT"
    $teacherToken = Login "teacher001" "TEACHER"
    $adminToken = Login "admin001" "EDU_ADMIN"
    if ([string]::IsNullOrWhiteSpace($adminToken)) {
        Fail "admin token is empty"
    }

    Assert-Code "student dashboard" (Invoke-Api "Get" "/api/student/dashboard" $studentToken) "0"
    Assert-Code "course detail" (Invoke-Api "Get" "/api/courses/course-java-001?course_class_id=class-java-001" $studentToken) "0"

    $questions = Invoke-Api "Get" "/api/questions?page_no=1&page_size=5" $studentToken
    Assert-Code "questions" $questions "0"
    Write-Host "[info] questions total=$($questions.data.total)"

    Assert-Code "wrong book" (Invoke-Api "Get" "/api/wrong-book?page_no=1&page_size=5" $studentToken) "0"
    Assert-Code "knowledge chunks" (Invoke-Api "Get" "/api/knowledge/chunks?course_id=course-java-001&chapter_id=chapter-java-001&page_no=1&page_size=5" $teacherToken) "0"
    Assert-Code "jobs" (Invoke-Api "Get" "/api/jobs?page_no=1&page_size=5" $studentToken) "0"
    Assert-Code "profile" (Invoke-Api "Get" "/api/profiles/student001?job_id=job-java-backend" $studentToken) "0"

    Assert-Code "student operation logs forbidden" (Invoke-Api "Get" "/api/logs/operations?page_no=1&page_size=5" $studentToken) "40301"
    Assert-Code "course detail missing course_class_id" (Invoke-Api "Get" "/api/courses/course-java-001" $studentToken) "40001"
    Assert-Code "questions invalid page_no" (Invoke-Api "Get" "/api/questions?page_no=abc&page_size=5" $studentToken) "40001"
    Assert-Code "questions without token" (Invoke-Api "Get" "/api/questions?page_no=1&page_size=5") "40101"

    Write-Host "[ok] P0 public regression passed: $Root"
} catch {
    Fail $_.Exception.Message
}
