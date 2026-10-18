# Teste de concorrência da Carteira Digital
# Com a aplicação rodando, execute na raiz do projeto:
#   powershell -ExecutionPolicy Bypass -File .\scripts\teste-concorrencia.ps1

$base = "http://localhost:8080"
$json = "application/json"

function New-TestUser($name) {
    $suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
    $body = @{ name = $name; email = "$name-$suffix@teste.com"; password = "senha1234" } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$base/users" -ContentType $json -Body $body
}

function Add-Money($walletId, $amount) {
    $body = @{ amount = $amount } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$base/wallets/$walletId/deposit" -ContentType $json -Body $body | Out-Null
}

function Get-Balance($walletId) {
    (Invoke-RestMethod -Uri "$base/wallets/$walletId").balance
}

# Dispara todas as requisições POST ao mesmo tempo: cada job espera até o mesmo horário para começar.
function Invoke-ParallelPost($requests) {
    $startTicks = [DateTime]::UtcNow.AddSeconds(12).Ticks
    $jobs = foreach ($r in $requests) {
        Start-Job -ScriptBlock {
            param($url, $body, $startTicks)
            while ([DateTime]::UtcNow.Ticks -lt $startTicks) { Start-Sleep -Milliseconds 5 }
            try {
                Invoke-RestMethod -Method Post -Uri $url -ContentType "application/json" -Body $body | Out-Null
                201
            } catch {
                if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            }
        } -ArgumentList $r.Url, $r.Body, $startTicks
    }
    $results = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job
    $results
}

function Show-Summary($results) {
    $results | Group-Object | Sort-Object Name | ForEach-Object {
        Write-Host ("  HTTP {0}: {1} requisição(ões)" -f $_.Name, $_.Count)
    }
}

Write-Host "Criando dois usuários de teste..." -ForegroundColor Yellow
$ana = New-TestUser "Ana"
$bruno = New-TestUser "Bruno"

# ---------------------------------------------------------------
Write-Host "`n=== Teste 1: 10 saques simultâneos de 20 numa carteira com saldo 100 ===" -ForegroundColor Cyan
Write-Host "Esperado: 5 x HTTP 201, 5 x HTTP 422 e saldo final 0 (nunca negativo)"
Add-Money $ana.walletId 100

$requests = 1..10 | ForEach-Object {
    @{ Url = "$base/wallets/$($ana.walletId)/withdraw"; Body = '{"amount":20}' }
}
Write-Host "Aguardando os jobs ficarem prontos (cerca de 12 segundos)..."
Show-Summary (Invoke-ParallelPost $requests)
Write-Host "Saldo final da Ana: $(Get-Balance $ana.walletId)"

# ---------------------------------------------------------------
Write-Host "`n=== Teste 2: 20 transferências cruzadas simultâneas entre Ana e Bruno ===" -ForegroundColor Cyan
Write-Host "Esperado: 20 x HTTP 201, sem travar, e os dois saldos somando 200"
Add-Money $ana.walletId 100
Add-Money $bruno.walletId 100

$requests = @()
1..10 | ForEach-Object {
    $requests += @{
        Url  = "$base/wallets/$($ana.walletId)/transfer"
        Body = (@{ targetWalletId = $bruno.walletId; amount = 5 } | ConvertTo-Json)
    }
    $requests += @{
        Url  = "$base/wallets/$($bruno.walletId)/transfer"
        Body = (@{ targetWalletId = $ana.walletId; amount = 5 } | ConvertTo-Json)
    }
}
Write-Host "Aguardando os jobs ficarem prontos (cerca de 12 segundos)..."
Show-Summary (Invoke-ParallelPost $requests)

$saldoAna = Get-Balance $ana.walletId
$saldoBruno = Get-Balance $bruno.walletId
Write-Host "Saldo da Ana: $saldoAna | Saldo do Bruno: $saldoBruno | Total: $($saldoAna + $saldoBruno)"
