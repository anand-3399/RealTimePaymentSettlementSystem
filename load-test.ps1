# =====================================================================
# EDIT THESE VARIABLES DIRECTLY BEFORE RUNNING
# =====================================================================
$Token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmFuZCIsImlhdCI6MTc4NDMwMTg2MCwiZXhwIjoxNzg0MzAzNjYwfQ.up8LVQ4OpRkZ8ZLPuTGu08_q-f2_bFc_-WVghee11rU"
$ConcurrentCount = 100000
$SequentialCount = 100000
$BankAccount = "1234567890"
# =====================================================================

$Token = $Token.Trim()
$url = "http://localhost:4200/api/v1/orders"

$script:amt = 1
$script:increment = $true

function Get-NextAmount {
    $current = $script:amt
    if ($script:increment) {
        $script:amt++
        if ($script:amt -ge 1000) { $script:increment = $false }
    } else {
        $script:amt--
        if ($script:amt -le 1) { $script:increment = $true }
    }
    return $current
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "         RTPS Load Testing Tool          " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Target URL: $url"
Write-Host "Concurrent Requests to queue: $ConcurrentCount"
Write-Host "Sequential Requests to blast: $SequentialCount"
Write-Host "=========================================`n" -ForegroundColor Cyan

# -------------------------------------------------------------------------------------
# 1. CONCURRENT TEST
# -------------------------------------------------------------------------------------
if ($ConcurrentCount -gt 0) {
    Write-Host "Preparing $ConcurrentCount concurrent requests..." -ForegroundColor Yellow
    
    # Use RunspacePool for high performance parallel execution
    $runspacePool = [runspacefactory]::CreateRunspacePool(1, $ConcurrentCount)
    $runspacePool.Open()
    
    $jobs = @()
    
    for ($i = 1; $i -le $ConcurrentCount; $i++) {
        $currentAmount = Get-NextAmount
        $corrId = [guid]::NewGuid().ToString()
        $idemKey = [guid]::NewGuid().ToString()
        
        $body = @{
            amount = $currentAmount
            currency = "INR"
            recipientBankAccount = $BankAccount
            description = "Concurrent Load Test #$i"
        } | ConvertTo-Json -Compress
        
        $scriptBlock = {
            param($url, $Token, $body, $corrId, $idemKey)
            $reqHeaders = @{
                "Authorization" = "Bearer $Token"
                "Content-Type" = "application/json"
                "X-Correlation-ID" = $corrId
                "Idempotency-Key" = $idemKey
            }
            try {
                $res = Invoke-RestMethod -Uri $url -Method Post -Headers $reqHeaders -Body $body -ErrorAction Stop
                return "Success"
            } catch {
                return "Error: $($_.Exception.Message)"
            }
        }
        
        $pipeline = [powershell]::Create().AddScript($scriptBlock).AddArgument($url).AddArgument($Token).AddArgument($body).AddArgument($corrId).AddArgument($idemKey)
        $pipeline.RunspacePool = $runspacePool
        
        $jobs += [PSCustomObject]@{
            Pipe = $pipeline
        }
    }
    
    Read-Host "-> $ConcurrentCount requests built! Press [ENTER] to unleash them onto the server simultaneously..."
    
    Write-Host "Blasting requests..." -ForegroundColor Green
    $startTime = Get-Date
    
    # Launch all runspaces as fast as the CPU allows
    foreach ($job in $jobs) {
        $job | Add-Member -MemberType NoteProperty -Name Result -Value $job.Pipe.BeginInvoke()
    }
    
    # Wait for completion
    while ($jobs.Result.IsCompleted -contains $false) { 
        Start-Sleep -Milliseconds 100 
    }
    
    $endTime = Get-Date
    
    $success = 0
    $fail = 0
    foreach ($job in $jobs) {
        $res = $job.Pipe.EndInvoke($job.Result)
        if ($res -eq "Success") { 
            $success++ 
        } else { 
            $fail++ 
            Write-Host $res -ForegroundColor Red 
        }
        $job.Pipe.Dispose()
    }
    
    $runspacePool.Close()
    $runspacePool.Dispose()
    
    $duration = ($endTime - $startTime).TotalSeconds
    Write-Host "Concurrent Test Complete in $duration seconds!" -ForegroundColor Cyan
    $color = if ($fail -eq 0) { 'Green' } else { 'Red' }
    Write-Host "Success: $success | Failed: $fail`n" -ForegroundColor $color
}

# -------------------------------------------------------------------------------------
# 2. SEQUENTIAL TEST
# -------------------------------------------------------------------------------------
if ($SequentialCount -gt 0) {
    Write-Host "Starting $SequentialCount sequential requests (hitting rapidly one-by-one)..." -ForegroundColor Yellow
    $success = 0
    $fail = 0
    
    $startTime = Get-Date
    for ($i = 1; $i -le $SequentialCount; $i++) {
        $currentAmount = Get-NextAmount
        $corrId = [guid]::NewGuid().ToString()
        $idemKey = [guid]::NewGuid().ToString()
        
        $body = @{
            amount = $currentAmount
            currency = "INR"
            recipientBankAccount = $BankAccount
            description = "Sequential Load Test #$i"
        } | ConvertTo-Json -Compress
        
        $reqHeaders = @{
            "Authorization" = "Bearer $Token"
            "Content-Type" = "application/json"
            "X-Correlation-ID" = $corrId
            "Idempotency-Key" = $idemKey
        }
        
        try {
            $null = Invoke-RestMethod -Uri $url -Method Post -Headers $reqHeaders -Body $body
            $success++
            Write-Host "." -NoNewline -ForegroundColor Green
        } catch {
            $fail++
            Write-Host "X ($($_.Exception.Message)) " -NoNewline -ForegroundColor Red
        }
    }
    $endTime = Get-Date
    $duration = ($endTime - $startTime).TotalSeconds
    
    Write-Host "`nSequential Test Complete in $duration seconds!" -ForegroundColor Cyan
    $color = if ($fail -eq 0) { 'Green' } else { 'Red' }
    Write-Host "Success: $success | Failed: $fail`n" -ForegroundColor $color
}
