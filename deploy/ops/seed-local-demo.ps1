param(
    [string]$BaseUrl = "http://127.0.0.1:18089",
    [string]$TenantId = "local-government",
    [string]$UserId = "local-user"
)

$ErrorActionPreference = "Stop"
$headers = @{
    "X-Tenant-Id" = $TenantId
    "X-User-Id" = $UserId
}

function Invoke-DemoApi {
    param(
        [ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [object]$Body
    )

    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        "$BaseUrl$Path"
    )
    $request.Headers.Add("X-Tenant-Id", $TenantId)
    $request.Headers.Add("X-User-Id", $UserId)
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 30 -Compress
        # Read and write explicit UTF-8 bytes. Windows PowerShell 5.1 otherwise
        # applies the active ANSI code page to JSON without a charset response.
        $content = [System.Net.Http.ByteArrayContent]::new([Text.Encoding]::UTF8.GetBytes($json))
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/json; charset=utf-8")
        $request.Content = $content
    }
    try {
        $httpResponse = $client.SendAsync($request).GetAwaiter().GetResult()
        $responseBytes = $httpResponse.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $responseText = [Text.Encoding]::UTF8.GetString($responseBytes)
        $response = if ([string]::IsNullOrWhiteSpace($responseText)) { $null } else { $responseText | ConvertFrom-Json }
        if (-not $httpResponse.IsSuccessStatusCode) {
            $message = if ($null -ne $response) { $response.message } else { $httpResponse.ReasonPhrase }
            throw "API $Method $Path failed ($([int]$httpResponse.StatusCode)): $message"
        }
        if ($null -ne $response.code -and $response.code -ne 200) {
            throw "API $Method $Path failed: $($response.message)"
        }
        return $response.data
    }
    finally {
        $request.Dispose()
        $client.Dispose()
    }
}

function Convert-DemoJson {
    param([string]$Json)
    return $Json | ConvertFrom-Json
}

function Find-ByProperty {
    param([object[]]$Items, [string]$Property, [string]$Value)
    return $Items | Where-Object { $_.$Property -eq $Value } | Select-Object -First 1
}

function Ensure-Dictionary {
    $code = "DEMO-COMMON-MISTAKES"
    $items = @(Invoke-DemoApi GET "/api/v1/dictionaries" $null)
    $dictionary = Find-ByProperty $items "dictCode" $code
    if ($null -eq $dictionary) {
        $dictionary = Invoke-DemoApi POST "/api/v1/dictionaries" (Convert-DemoJson @'
{
  "dictCode":"DEMO-COMMON-MISTAKES",
  "dictName":"\u6f14\u793a\uff1a\u653f\u52a1\u516c\u6587\u6613\u9519\u8bcd\u5e93",
  "dictType":"\u6613\u9519\u8bcd",
  "sourceDescription":"\u672c\u5730\u6f14\u793a\u6570\u636e\uff0c\u7528\u4e8e\u4f53\u9a8c\u8bcd\u5e93\u7ba1\u7406\u548c\u667a\u80fd\u5ba1\u6821"
}
'@)
    }

    $terms = @(Invoke-DemoApi GET "/api/v1/dictionaries/$($dictionary.id)/terms" $null)
    $termSpecs = @(
        (Convert-DemoJson '{"termText":"\u5e10\u53f7","standardForm":"\u8d26\u53f7","aliases":["\u7528\u6237\u5e10\u53f7"],"categoryTag":"\u4fe1\u606f\u5316","riskLevel":"\u63d0\u793a","sourceReference":"\u653f\u52a1\u516c\u6587\u89c4\u8303\u7528\u8bed","usageNote":"\u5efa\u8bae\u4f7f\u7528\u8d26\u53f7"}'),
        (Convert-DemoJson '{"termText":"\u505a\u51fa\u8d21\u732e","standardForm":"\u4f5c\u51fa\u8d21\u732e","aliases":[],"categoryTag":"\u5e38\u89c1\u8bef\u7528","riskLevel":"\u4e00\u822c","sourceReference":"\u73b0\u4ee3\u6c49\u8bed\u89c4\u8303\u7528\u6cd5","usageNote":"\u52a8\u8bcd\u4f5c\u51fa\u4e0e\u62bd\u8c61\u5bbe\u8bed\u642d\u914d"}'),
        (Convert-DemoJson '{"termText":"\u622a\u6b62\u76ee\u524d\u4e3a\u6b62","standardForm":"\u622a\u81f3\u76ee\u524d","aliases":["\u622a\u6b62\u5230\u76ee\u524d","\u622a\u6b62\u5f53\u524d"],"categoryTag":"\u65f6\u95f4\u8868\u8ff0","riskLevel":"\u4e00\u822c","sourceReference":"\u516c\u6587\u65f6\u95f4\u8868\u8ff0\u89c4\u8303","usageNote":"\u907f\u514d\u622a\u6b62\u548c\u4e3a\u6b62\u8bed\u4e49\u91cd\u590d"}'),
        (Convert-DemoJson '{"termText":"\u5176\u4ed6","standardForm":"\u5176\u4ed6","aliases":["\u5176\u5b83"],"categoryTag":"\u5f02\u5f62\u8bcd","riskLevel":"\u63d0\u793a","sourceReference":"\u73b0\u4ee3\u6c49\u8bed\u89c4\u8303\u8bcd\u5f62","usageNote":"\u516c\u6587\u4e2d\u7edf\u4e00\u4f7f\u7528\u5176\u4ed6"}')
    )
    foreach ($spec in $termSpecs) {
        if ($null -eq (Find-ByProperty $terms "termText" $spec.termText)) {
            $null = Invoke-DemoApi POST "/api/v1/dictionaries/$($dictionary.id)/terms" $spec
        }
    }
    return $dictionary
}

function Ensure-Rules {
    $items = @(Invoke-DemoApi GET "/api/v1/rules" $null)
    $publishedCode = "DEMO-WORD-DEADLINE-001"
    if ($null -eq (Find-ByProperty $items "ruleCode" $publishedCode)) {
        $rule = Invoke-DemoApi POST "/api/v1/rules" (Convert-DemoJson @'
{
  "ruleCode":"DEMO-WORD-DEADLINE-001",
  "ruleName":"\u6f14\u793a\uff1a\u622a\u6b62\u7528\u8bed\u89c4\u8303",
  "category":"\u89c4\u8303",
  "applicableDocumentTypes":["\u8bf7\u793a","\u62a5\u544a","\u5de5\u4f5c\u603b\u7ed3"],
  "riskLevel":"\u4e00\u822c",
  "detectionMethod":"\u8bcd\u9879",
  "ruleExpression":"\u622a\u6b62\u76ee\u524d\u4e3a\u6b62=>\u622a\u81f3\u76ee\u524d",
  "basis":"\u73b0\u4ee3\u6c49\u8bed\u89c4\u8303\u7528\u6cd5",
  "changeDescription":"\u5efa\u7acb\u6f14\u793a\u89c4\u5219\u521d\u7248"
}
'@)
        $test = Convert-DemoJson '{"cases":[{"text":"\u622a\u6b62\u76ee\u524d\u4e3a\u6b62\uff0c\u5de5\u4f5c\u8fdb\u5c55\u987a\u5229\u3002","expectedMatchCount":1},{"text":"\u622a\u81f3\u76ee\u524d\uff0c\u5de5\u4f5c\u8fdb\u5c55\u987a\u5229\u3002","expectedMatchCount":0}]}'
        $null = Invoke-DemoApi POST "/api/v1/rules/$($rule.id)/test" $test
        $null = Invoke-DemoApi POST "/api/v1/rules/$($rule.id)/submit-for-review" $null
        $null = Invoke-DemoApi POST "/api/v1/rules/$($rule.id)/publish" $null
    }

    $draftCode = "DEMO-SECURITY-001"
    if ($null -eq (Find-ByProperty $items "ruleCode" $draftCode)) {
        $null = Invoke-DemoApi POST "/api/v1/rules" (Convert-DemoJson @'
{
  "ruleCode":"DEMO-SECURITY-001",
  "ruleName":"\u6f14\u793a\uff1a\u654f\u611f\u4fe1\u606f\u5916\u53d1\u63d0\u793a",
  "category":"\u5b89\u5168",
  "applicableDocumentTypes":["\u901a\u77e5","\u516c\u544a"],
  "riskLevel":"\u91cd\u8981",
  "detectionMethod":"\u8bcd\u9879",
  "ruleExpression":"\u8eab\u4efd\u8bc1\u53f7\u7801",
  "basis":"\u6f14\u793a\u6570\u636e\u5b89\u5168\u7ba1\u7406\u8981\u6c42",
  "changeDescription":"\u8349\u7a3f\u793a\u4f8b\uff0c\u7b49\u5f85\u6d4b\u8bd5"
}
'@)
    }
}

function Ensure-KnowledgeSource {
    $code = "DEMO-POLICY-KB"
    $items = @(Invoke-DemoApi GET "/api/v1/knowledge/sources" $null)
    $source = Find-ByProperty $items "sourceCode" $code
    if ($null -eq $source) {
        $source = Invoke-DemoApi POST "/api/v1/knowledge/sources" (Convert-DemoJson @'
{
  "sourceCode":"DEMO-POLICY-KB",
  "sourceName":"\u6f14\u793a\uff1a\u6570\u5b57\u653f\u52a1\u5236\u5ea6\u5e93",
  "category":"\u5355\u4f4d\u5236\u5ea6",
  "storageType":"\u5168\u6587\u7d22\u5f15",
  "permissionScope":"\u672c\u5355\u4f4d"
}
'@)
    }

    $documents = @(Invoke-DemoApi GET "/api/v1/knowledge/sources/$($source.id)/documents" $null)
    $title = (Convert-DemoJson '"\u6570\u5b57\u653f\u52a1\u9879\u76ee\u5efa\u8bbe\u7ba1\u7406\u8981\u70b9\uff08\u6f14\u793a\uff09"')
    if ($null -eq (Find-ByProperty $documents "documentTitle" $title)) {
        $token = [Guid]::NewGuid().ToString("N")
        $tempFile = Join-Path $env:TEMP "document-creator-demo-$token.txt"
        $content = (Convert-DemoJson '"\u6570\u5b57\u653f\u52a1\u9879\u76ee\u5efa\u8bbe\u7ba1\u7406\u8981\u70b9\uff08\u6f14\u793a\u6570\u636e\uff09\n\n\u4e00\u3001\u5efa\u8bbe\u76ee\u6807\n\u575a\u6301\u9700\u6c42\u5bfc\u5411\u3001\u7edf\u7b79\u5efa\u8bbe\u548c\u5b89\u5168\u53ef\u63a7\uff0c\u63d0\u5347\u653f\u52a1\u670d\u52a1\u534f\u540c\u80fd\u529b\u3002\n\n\u4e8c\u3001\u8d44\u91d1\u7ba1\u7406\n\u9879\u76ee\u9884\u7b97\u5e94\u4e0e\u5efa\u8bbe\u5185\u5bb9\u3001\u7ee9\u6548\u76ee\u6807\u4fdd\u6301\u4e00\u81f4\uff0c\u4e25\u7981\u65e0\u4f9d\u636e\u6269\u5927\u6295\u8d44\u3002\n\n\u4e09\u3001\u8fdb\u5ea6\u8981\u6c42\n\u622a\u81f3\u76ee\u524d\uff0c\u793a\u8303\u9879\u76ee\u5df2\u5b8c\u6210\u9700\u6c42\u8c03\u7814\u548c\u521d\u6b65\u65b9\u6848\u8bba\u8bc1\u3002\u540e\u7eed\u6309\u6708\u8ddf\u8e2a\u91cc\u7a0b\u7891\u548c\u98ce\u9669\u3002\n\n\u56db\u3001\u8d23\u4efb\u5206\u5de5\n\u4e1a\u52a1\u90e8\u95e8\u8d1f\u8d23\u9700\u6c42\u786e\u8ba4\uff0c\u6280\u672f\u90e8\u95e8\u8d1f\u8d23\u5b9e\u65bd\uff0c\u8d22\u52a1\u90e8\u95e8\u8d1f\u8d23\u8d44\u91d1\u76d1\u7763\u3002"')
        $authorityLevel = (Convert-DemoJson '"\u5355\u4f4d\u5236\u5ea6"')
        $permissionLevel = (Convert-DemoJson '"\u672c\u5355\u4f4d"')
        try {
            [IO.File]::WriteAllText($tempFile, $content, [Text.UTF8Encoding]::new($false))
            Add-Type -AssemblyName System.Net.Http
            $client = [System.Net.Http.HttpClient]::new()
            $client.DefaultRequestHeaders.Add("X-Tenant-Id", $TenantId)
            $client.DefaultRequestHeaders.Add("X-User-Id", $UserId)
            $form = [System.Net.Http.MultipartFormDataContent]::new()
            $filePart = [System.Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes($tempFile))
            $filePart.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/plain; charset=utf-8")
            $form.Add($filePart, "file", "digital-government-policy-demo.txt")
            $form.Add([System.Net.Http.StringContent]::new($title, [Text.Encoding]::UTF8), "title")
            $form.Add([System.Net.Http.StringContent]::new('{"demo":true,"year":2026}', [Text.Encoding]::UTF8), "metadata")
            $form.Add([System.Net.Http.StringContent]::new($authorityLevel, [Text.Encoding]::UTF8), "authorityLevel")
            $form.Add([System.Net.Http.StringContent]::new($permissionLevel, [Text.Encoding]::UTF8), "permissionLevel")
            $httpResponse = $client.PostAsync("$BaseUrl/api/v1/knowledge/sources/$($source.id)/documents/upload", $form).GetAwaiter().GetResult()
            $responseBytes = $httpResponse.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $uploadResponse = [Text.Encoding]::UTF8.GetString($responseBytes) | ConvertFrom-Json
            if (-not $httpResponse.IsSuccessStatusCode) { throw "Knowledge document upload failed: $($uploadResponse.message)" }
            if ($uploadResponse.code -ne 200) { throw "Knowledge upload API failed: $($uploadResponse.message)" }
            $null = Invoke-DemoApi POST "/api/v1/knowledge/sources/$($source.id)/documents/$($uploadResponse.data.id)/parse" $null
        }
        finally {
            if ($null -ne $form) { $form.Dispose() }
            if ($null -ne $client) { $client.Dispose() }
            if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
        }
    }
    return $source
}

function Ensure-PromptTemplates {
    $items = @(Invoke-DemoApi GET "/api/v1/prompt-templates" $null)
    $publishedCode = "DEMO-PARAGRAPH-SUMMARY"
    if ($null -eq (Find-ByProperty $items "templateCode" $publishedCode)) {
        $template = Invoke-DemoApi POST "/api/v1/prompt-templates" (Convert-DemoJson @'
{
  "templateCode":"DEMO-PARAGRAPH-SUMMARY",
  "templateName":"\u6f14\u793a\uff1a\u5de5\u4f5c\u603b\u7ed3\u6bb5\u843d\u751f\u6210",
  "applicableScene":"\u6bb5\u843d\u751f\u6210",
  "documentTypeCode":"\u5de5\u4f5c\u603b\u7ed3",
  "applicableModels":[],
  "systemRole":"\u4f60\u662f\u653f\u52a1\u516c\u6587\u5199\u4f5c\u52a9\u624b\uff0c\u56f4\u7ed5{{topic}}\u51c6\u786e\u64b0\u5199\u3002",
  "documentTypeMethod":"\u603b\u7ed3\u5e94\u5305\u542b\u6210\u6548\u3001\u95ee\u9898\u548c\u4e0b\u4e00\u6b65\u5b89\u6392\u3002",
  "inputFields":{"topic":{"type":"string","required":true}},
  "materialCitationRule":"\u53ea\u5f15\u7528\u5df2\u6388\u6743\u6750\u6599\uff0c\u4fdd\u7559\u4e8b\u5b9e\u53e3\u5f84\u3002",
  "antiFabricationRule":"\u7981\u6b62\u7f16\u9020\u653f\u7b56\u6587\u53f7\u3001\u6570\u636e\u548c\u5ba1\u6279\u7ed3\u8bba\u3002",
  "outputFormat":"\u53ea\u8f93\u51fa\u53ef\u76f4\u63a5\u653e\u5165\u6b63\u6587\u7684\u6bb5\u843d\u3002",
  "riskConstraint":"\u4e0d\u5f97\u8d8a\u6743\u4f5c\u51fa\u51b3\u7b56\u627f\u8bfa\u3002",
  "modelParameters":{"temperature":0.2,"top_p":0.8},
  "testSampleIds":["demo-summary-001"],
  "changeDescription":"\u6f14\u793a\u521d\u59cb\u7248\u672c"
}
'@)
        $null = Invoke-DemoApi POST "/api/v1/prompt-templates/$($template.id)/test" (Convert-DemoJson '{"variables":{"topic":"\u6570\u5b57\u653f\u52a1\u80fd\u529b\u5efa\u8bbe"}}')
        $null = Invoke-DemoApi POST "/api/v1/prompt-templates/$($template.id)/submit-for-review" $null
        $null = Invoke-DemoApi POST "/api/v1/prompt-templates/$($template.id)/publish" $null
    }

    $draftCode = "DEMO-REVIEW-EXPLAIN"
    if ($null -eq (Find-ByProperty $items "templateCode" $draftCode)) {
        $null = Invoke-DemoApi POST "/api/v1/prompt-templates" (Convert-DemoJson @'
{
  "templateCode":"DEMO-REVIEW-EXPLAIN",
  "templateName":"\u6f14\u793a\uff1a\u5ba1\u6821\u95ee\u9898\u89e3\u91ca",
  "applicableScene":"\u5ba1\u6821\u89e3\u91ca",
  "applicableModels":[],
  "systemRole":"\u4f60\u662f\u516c\u6587\u5ba1\u6821\u5458\u3002",
  "inputFields":{},
  "antiFabricationRule":"\u89e3\u91ca\u5fc5\u987b\u4e0e\u89c4\u5219\u4f9d\u636e\u4e00\u81f4\u3002",
  "outputFormat":"\u8f93\u51fa\u95ee\u9898\u3001\u4f9d\u636e\u3001\u5efa\u8bae\u4e09\u90e8\u5206\u3002",
  "modelParameters":{"temperature":0.1},
  "testSampleIds":[],
  "changeDescription":"\u8349\u7a3f\u793a\u4f8b"
}
'@)
    }
}

function Ensure-ModelConfigs {
    $items = @(Invoke-DemoApi GET "/api/v1/model-configs" $null)
    $specs = @(
        (Convert-DemoJson '{"configCode":"DEMO-LOCAL-WRITE","configName":"\u6f14\u793a\uff1a\u672c\u5730\u5199\u4f5c\u6a21\u578b\uff08\u672a\u542f\u7528\uff09","provider":"\u672c\u5730\u79c1\u6709","modelName":"local-writing-model","modelVersion":"demo","scene":"\u6bb5\u843d\u751f\u6210","dataLevel":"\u654f\u611f","maxContextTokens":8192,"timeoutMs":30000,"maxRetries":2,"costLimitDaily":0,"sensitiveWordFilter":true,"outputValidation":true,"active":false,"inputCostPer1k":0,"outputCostPer1k":0,"qpsLimit":5,"dailyTokenLimit":50000}'),
        (Convert-DemoJson '{"configCode":"DEMO-CLOUD-REVIEW","configName":"\u6f14\u793a\uff1a\u4e91\u7aef\u5ba1\u6821\u6a21\u578b\uff08\u672a\u542f\u7528\uff09","provider":"\u5883\u5185\u4e91\u7aef","modelName":"cloud-review-model","modelVersion":"demo","scene":"\u5ba1\u6821\u89e3\u91ca","dataLevel":"\u5185\u90e8","maxContextTokens":16384,"timeoutMs":45000,"maxRetries":2,"costLimitDaily":50,"sensitiveWordFilter":true,"outputValidation":true,"active":false,"inputCostPer1k":0.01,"outputCostPer1k":0.02,"qpsLimit":10,"dailyTokenLimit":100000}')
    )
    foreach ($spec in $specs) {
        if ($null -eq (Find-ByProperty $items "configCode" $spec.configCode)) {
            $null = Invoke-DemoApi POST "/api/v1/model-configs" $spec
        }
    }
}

function Ensure-SingleReview {
    param([string]$WritingTaskId)
    $allReviews = @(Invoke-DemoApi GET "/api/v1/review-tasks" $null)
    $taskReviews = @($allReviews | Where-Object { $_.writingTaskId -eq $WritingTaskId } | Sort-Object createdAt)
    if ($taskReviews.Count -eq 0) {
        return Invoke-DemoApi POST "/api/v1/writing-tasks/$WritingTaskId/initiate-review" $null
    }
    if ($taskReviews.Count -gt 1) {
        foreach ($duplicate in @($taskReviews | Select-Object -Skip 1)) {
            $null = Invoke-DemoApi DELETE "/api/v1/review-tasks/$($duplicate.id)" $null
        }
    }
    return $taskReviews[0]
}

function New-FullWorkflowTask {
    $name = (Convert-DemoJson '"\u6f14\u793a\uff1a2026\u5e74\u6570\u5b57\u653f\u52a1\u80fd\u529b\u5efa\u8bbe\u5de5\u4f5c\u603b\u7ed3"')
    $page = Invoke-DemoApi GET "/api/v1/writing-tasks?size=200" $null
    $existing = Find-ByProperty @($page.items) "taskName" $name
    if ($null -ne $existing) {
        $null = Ensure-SingleReview $existing.id
        return $existing
    }

    $task = Invoke-DemoApi POST "/api/v1/writing-tasks" (Convert-DemoJson @'
{
  "taskName":"\u6f14\u793a\uff1a2026\u5e74\u6570\u5b57\u653f\u52a1\u80fd\u529b\u5efa\u8bbe\u5de5\u4f5c\u603b\u7ed3",
  "documentTypeCode":"\u5de5\u4f5c\u603b\u7ed3",
  "documentCategory":"\u4e8b\u52a1\u6587\u4e66",
  "writingTopic":"\u603b\u7ed32026\u5e74\u6570\u5b57\u653f\u52a1\u80fd\u529b\u5efa\u8bbe\u6210\u6548\u5e76\u90e8\u7f72\u4e0b\u4e00\u6b65\u5de5\u4f5c",
  "writingPurpose":"\u5411\u5355\u4f4d\u5185\u90e8\u6c47\u62a5\u5e74\u5ea6\u8fdb\u5c55\u3001\u95ee\u9898\u4e0e2027\u5e74\u5b89\u6392",
  "direction":"\u5185\u90e8",
  "targetAudience":"\u5355\u4f4d\u9886\u5bfc\u548c\u5404\u4e1a\u52a1\u90e8\u95e8",
  "writingTone":"\u4e25\u8c28\u3001\u52a1\u5b9e\u3001\u6570\u636e\u5bfc\u5411",
  "expectedLengthMin":1800,
  "expectedLengthMax":3000,
  "allowExternalKnowledge":false,
  "securityLevel":"\u5185\u90e8"
}
'@)
    $null = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/materials" (Convert-DemoJson @'
{
  "materialName":"2026\u5e74\u6570\u5b57\u653f\u52a1\u5efa\u8bbe\u8fdb\u5c55\u53f0\u8d26\uff08\u6f14\u793a\uff09",
  "sourceType":"\u4e0a\u4f20",
  "authorityLevel":"\u6b63\u5f0f\u6750\u6599",
  "usageMode":"\u6307\u5b9a\u7247\u6bb5",
  "citationPurpose":"\u4e8b\u5b9e\u4f9d\u636e",
  "permissionStatus":"\u5141\u8bb8",
  "selectedFragment":"\u622a\u6b62\u76ee\u524d\u4e3a\u6b62\uff0c\u5df2\u5b8c\u621028\u9879\u653f\u52a1\u670d\u52a1\u6d41\u7a0b\u4f18\u5316\uff0c\u5e73\u5747\u529e\u7406\u65f6\u957f\u8f83\u4e0a\u5e74\u7f29\u77ed18%\u3002\u4e0a\u8ff0\u6570\u636e\u4ec5\u4e3a\u6f14\u793a\u3002",
  "locked":true
}
'@)
    $idea = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/ideas" $null
    $ideaBody = Convert-DemoJson @'
{
  "mainTheme":"\u56f4\u7ed5\u6570\u5b57\u653f\u52a1\u80fd\u529b\u5efa\u8bbe\uff0c\u5ba2\u89c2\u603b\u7ed3\u6210\u6548\u3001\u5206\u6790\u4e0d\u8db3\u5e76\u660e\u786e\u4e0b\u4e00\u6b65\u4efb\u52a1",
  "coreViewpoints":["\u653f\u52a1\u670d\u52a1\u534f\u540c\u80fd\u529b\u7a33\u6b65\u63d0\u5347","\u9879\u76ee\u7ee9\u6548\u548c\u6570\u636e\u8d28\u91cf\u4ecd\u9700\u52a0\u5f3a","\u4e0b\u4e00\u6b65\u805a\u7126\u6807\u51c6\u3001\u5b89\u5168\u548c\u5e94\u7528\u5b9e\u6548"],
  "writingSequence":["\u603b\u4f53\u60c5\u51b5","\u4e3b\u8981\u6210\u6548","\u95ee\u9898\u4e0d\u8db3","\u4e0b\u4e00\u6b65\u6253\u7b97"],
  "mustExpressFacts":"\u8bf4\u660e\u53f0\u8d26\u6570\u636e\u4e3a\u6f14\u793a\u53e3\u5f84\uff0c\u5b9e\u9645\u53d1\u5e03\u524d\u5fc5\u987b\u590d\u6838",
  "optionalContent":"\u53ef\u8865\u5145\u5178\u578b\u5e94\u7528\u573a\u666f",
  "missingMaterials":"\u7f3a\u5c112027\u5e74\u9879\u76ee\u6e05\u5355\u548c\u8d22\u653f\u9884\u7b97\u6279\u590d",
  "riskWarnings":"\u4e0d\u5f97\u5c06\u6f14\u793a\u6570\u636e\u76f4\u63a5\u5bf9\u5916\u53d1\u5e03",
  "confirmed":true,
  "lockVersion":0
}
'@
    $ideaBody.lockVersion = $idea.lockVersion
    $null = Invoke-DemoApi PUT "/api/v1/writing-tasks/$($task.id)/ideas/$($idea.version)" $ideaBody
    $outline = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/outlines" $null
    $null = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/outlines/$($outline.version)/structure-check" $null
    $outline = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/outlines/$($outline.version)/confirm" $null
    foreach ($node in @($outline.nodes)) {
        $generation = Convert-DemoJson '{"outlineNodeId":"","instruction":"\u7d27\u6263\u5df2\u786e\u8ba4\u601d\u8def\uff0c\u4ec5\u4f7f\u7528\u6388\u6743\u6750\u6599\uff0c\u4fdd\u7559\u6f14\u793a\u6570\u636e\u58f0\u660e"}'
        $generation.outlineNodeId = $node.id
        $null = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/generate/section" $generation
    }
    # Keep one deliberate wording issue so the review workbench is not empty.
    $document = Invoke-DemoApi GET "/api/v1/writing-tasks/$($task.id)/document" $null
    $firstParagraph = @($document.paragraphs)[0]
    $reviewSampleText = (Convert-DemoJson '"\u622a\u6b62\u76ee\u524d\u4e3a\u6b62\uff0c\u5df2\u5b8c\u621028\u9879\u653f\u52a1\u670d\u52a1\u6d41\u7a0b\u4f18\u5316\uff0c\u8be5\u6570\u636e\u4ec5\u7528\u4e8e\u5ba1\u6821\u6f14\u793a\u3002"')
    $paragraphUpdate = [ordered]@{
        plainText = "$reviewSampleText$($firstParagraph.plainText)"
        lockVersion = $firstParagraph.lockVersion
        changeDescription = (Convert-DemoJson '"\u52a0\u5165\u5ba1\u6821\u95ee\u9898\u6f14\u793a\u53e5"')
    }
    $null = Invoke-DemoApi PUT "/api/v1/writing-tasks/$($task.id)/document/paragraphs/$($firstParagraph.id)" $paragraphUpdate
    $null = Ensure-SingleReview $task.id
    return $task
}

function New-PlanningTask {
    $name = (Convert-DemoJson '"\u6f14\u793a\uff1a\u5173\u4e8e\u4e3e\u529e\u653f\u52a1\u5199\u4f5c\u57f9\u8bad\u73ed\u7684\u901a\u77e5"')
    $page = Invoke-DemoApi GET "/api/v1/writing-tasks?size=200" $null
    $existing = Find-ByProperty @($page.items) "taskName" $name
    if ($null -ne $existing) { return $existing }
    $task = Invoke-DemoApi POST "/api/v1/writing-tasks" (Convert-DemoJson @'
{
  "taskName":"\u6f14\u793a\uff1a\u5173\u4e8e\u4e3e\u529e\u653f\u52a1\u5199\u4f5c\u57f9\u8bad\u73ed\u7684\u901a\u77e5",
  "documentTypeCode":"\u901a\u77e5",
  "documentCategory":"\u6cd5\u5b9a\u516c\u6587",
  "writingTopic":"\u7ec4\u7ec7\u5f00\u5c55\u653f\u52a1\u516c\u6587\u5199\u4f5c\u4e0e\u667a\u80fd\u5ba1\u6821\u57f9\u8bad",
  "writingPurpose":"\u660e\u786e\u57f9\u8bad\u65f6\u95f4\u3001\u5730\u70b9\u3001\u53c2\u52a0\u4eba\u5458\u548c\u62a5\u540d\u8981\u6c42",
  "direction":"\u4e0b\u884c",
  "targetAudience":"\u5404\u90e8\u95e8\u516c\u6587\u8d77\u8349\u4eba\u5458",
  "primaryRecipient":"\u5404\u5185\u8bbe\u90e8\u95e8",
  "writingTone":"\u7b80\u660e\u3001\u51c6\u786e",
  "expectedLengthMin":600,
  "expectedLengthMax":1000,
  "allowExternalKnowledge":false,
  "securityLevel":"\u5185\u90e8"
}
'@)
    $null = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/materials" (Convert-DemoJson @'
{
  "materialName":"\u57f9\u8bad\u73ed\u7b79\u5907\u4fe1\u606f\uff08\u6f14\u793a\uff09",
  "sourceType":"\u4eba\u5de5\u5f55\u5165",
  "authorityLevel":"\u5df2\u6838\u5b9a\u4fe1\u606f",
  "usageMode":"\u5168\u6587\u5f15\u7528",
  "citationPurpose":"\u901a\u77e5\u8981\u7d20",
  "permissionStatus":"\u5141\u8bb8",
  "selectedFragment":"\u57f9\u8bad\u62df\u4e8e2026\u5e748\u670818\u65e59:00\u5728\u7b2c\u4e00\u4f1a\u8bae\u5ba4\u4e3e\u884c\uff0c\u5404\u90e8\u95e8\u90092\u540d\u4e1a\u52a1\u9aa8\u5e72\u53c2\u52a0\uff0c8\u670814\u65e5\u524d\u5b8c\u6210\u62a5\u540d\u3002",
  "locked":true
}
'@)
    $null = Invoke-DemoApi POST "/api/v1/writing-tasks/$($task.id)/ideas" $null
    return $task
}

function New-InitialTask {
    $name = (Convert-DemoJson '"\u6f14\u793a\uff1a\u6570\u636e\u8d28\u91cf\u4e13\u9879\u6cbb\u7406\u5de5\u4f5c\u65b9\u6848"')
    $page = Invoke-DemoApi GET "/api/v1/writing-tasks?size=200" $null
    $existing = Find-ByProperty @($page.items) "taskName" $name
    if ($null -ne $existing) { return $existing }
    return Invoke-DemoApi POST "/api/v1/writing-tasks" (Convert-DemoJson @'
{
  "taskName":"\u6f14\u793a\uff1a\u6570\u636e\u8d28\u91cf\u4e13\u9879\u6cbb\u7406\u5de5\u4f5c\u65b9\u6848",
  "documentTypeCode":"\u5de5\u4f5c\u65b9\u6848",
  "documentCategory":"\u4e8b\u52a1\u6587\u4e66",
  "writingTopic":"\u5f00\u5c55\u5b58\u91cf\u4e1a\u52a1\u6570\u636e\u8d28\u91cf\u4e13\u9879\u6cbb\u7406",
  "writingPurpose":"\u660e\u786e\u6cbb\u7406\u76ee\u6807\u3001\u91cd\u70b9\u4efb\u52a1\u3001\u65f6\u95f4\u6b65\u9aa4\u548c\u8d23\u4efb\u5206\u5de5",
  "direction":"\u5185\u90e8",
  "targetAudience":"\u6570\u636e\u7ba1\u7406\u548c\u4e1a\u52a1\u90e8\u95e8",
  "writingTone":"\u5177\u4f53\u3001\u53ef\u6267\u884c",
  "expectedLengthMin":1500,
  "expectedLengthMax":2500,
  "allowExternalKnowledge":false,
  "securityLevel":"\u5185\u90e8"
}
'@)
}

Write-Output "Seeding demo data into tenant '$TenantId' at $BaseUrl ..."
$null = Invoke-DemoApi GET "/api/v1/document-type-configs" $null
$null = Invoke-DemoApi GET "/api/v1/review-plans" $null
$dictionary = Ensure-Dictionary
Ensure-Rules
$knowledgeSource = Ensure-KnowledgeSource
Ensure-PromptTemplates
Ensure-ModelConfigs
$fullTask = New-FullWorkflowTask
$planningTask = New-PlanningTask
$initialTask = New-InitialTask

$taskPage = Invoke-DemoApi GET "/api/v1/writing-tasks?size=200" $null
$rules = @(Invoke-DemoApi GET "/api/v1/rules" $null)
$dictionaries = @(Invoke-DemoApi GET "/api/v1/dictionaries" $null)
$knowledgeSources = @(Invoke-DemoApi GET "/api/v1/knowledge/sources" $null)
$reviewTasks = @(Invoke-DemoApi GET "/api/v1/review-tasks" $null)
$models = @(Invoke-DemoApi GET "/api/v1/model-configs" $null)
$prompts = @(Invoke-DemoApi GET "/api/v1/prompt-templates" $null)
$documentTypes = @(Invoke-DemoApi GET "/api/v1/document-type-configs" $null)
$reviewPlans = @(Invoke-DemoApi GET "/api/v1/review-plans" $null)

Write-Output "Demo data is ready."
Write-Output "writing_tasks=$($taskPage.total)"
Write-Output "review_tasks=$($reviewTasks.Count)"
Write-Output "rules=$($rules.Count)"
Write-Output "dictionaries=$($dictionaries.Count)"
Write-Output "knowledge_sources=$($knowledgeSources.Count)"
Write-Output "model_configs=$($models.Count)"
Write-Output "prompt_templates=$($prompts.Count)"
Write-Output "document_types=$($documentTypes.Count)"
Write-Output "review_plans=$($reviewPlans.Count)"
Write-Output "Open http://localhost:5175 and refresh the page."
