# Phase 10 token sweep — replaces common literals in *Screen*.kt and *Section*.kt
param([string[]]$Paths)

$files = if ($Paths) { $Paths } else {
    Get-ChildItem -Path "app-mobile\src\main\java", "app-tv\src\main\java" -Recurse -Include "*Screen*.kt", "*Section*.kt" | ForEach-Object { $_.FullName }
}

$dpMap = [ordered]@{
    '72.dp' = 'LumenTokens.Space.xxxxxxl'
    '56.dp' = 'LumenTokens.Space.xxxxxl'
    '40.dp' = 'LumenTokens.Space.xxxxl'
    '32.dp' = 'LumenTokens.Space.xxxl'
    '24.dp' = 'LumenTokens.Space.xxl'
    '20.dp' = 'LumenTokens.Space.xl'
    '16.dp' = 'LumenTokens.Space.lg'
    '12.dp' = 'LumenTokens.Space.md'
    '8.dp'  = 'LumenTokens.Space.sm2'
    '6.dp'  = 'LumenTokens.Space.sm'
    '4.dp'  = 'LumenTokens.Space.xs'
    '2.dp'  = 'LumenTokens.Space.xxs'
}

$shapeMap = [ordered]@{
    'RoundedCornerShape(28.dp)' = 'LumenTokens.Shape.xl'
    'RoundedCornerShape(20.dp)' = 'LumenTokens.Shape.lg'
    'RoundedCornerShape(16.dp)' = 'LumenTokens.Shape.lg'
    'RoundedCornerShape(14.dp)' = 'LumenTokens.Shape.md'
    'RoundedCornerShape(12.dp)' = 'LumenTokens.Shape.md'
    'RoundedCornerShape(10.dp)' = 'LumenTokens.Shape.sm'
    'RoundedCornerShape(8.dp)'  = 'LumenTokens.Shape.sm'
    'RoundedCornerShape(6.dp)'  = 'LumenTokens.Shape.xs'
    'RoundedCornerShape(50)'    = 'LumenTokens.Shape.pill'
    'RoundedCornerShape(percent = 50)' = 'LumenTokens.Shape.pill'
}

$colorMap = [ordered]@{
    'Color(0xFF3D6BFF)' = 'LumenTokens.Color.brand'
    'Color(0xFF5C86FF)' = 'LumenTokens.Color.brandHi'
    'Color(0xFF06070B)' = 'LumenTokens.Color.bg'
    'Color(0xFF0E1117)' = 'LumenTokens.Color.surface'
    'Color(0xFF151A22)' = 'LumenTokens.Color.surfaceHi'
    'Color(0xFF1F2630)' = 'LumenTokens.Color.border'
    'Color(0xFFF5F7FA)' = 'LumenTokens.Color.textPrimary'
    'Color(0xFFA6ADBB)' = 'LumenTokens.Color.textSecondary'
    'Color(0xFF6B7280)' = 'LumenTokens.Color.textMuted'
    'Color(0xFFFF4D5E)' = 'LumenTokens.Color.danger'
    'Color(0xFF34D399)' = 'LumenTokens.Color.success'
    'Color(0xFF0B0B10)' = 'LumenTokens.Color.bg'
    'Color(0xFFFAFAFA)' = 'LumenTokens.Color.textPrimary'
    'Color(0xFF000000)' = 'LumenTokens.Color.bg'
    'Color.White'       = 'LumenTokens.Color.textPrimary'
    'Color.Black'       = 'LumenTokens.Color.bg'
}

$schemeMap = [ordered]@{
    'MaterialTheme.colorScheme.primary'         = 'LumenTokens.Color.brand'
    'MaterialTheme.colorScheme.onPrimary'       = 'LumenTokens.Color.textPrimary'
    'MaterialTheme.colorScheme.background'      = 'LumenTokens.Color.bg'
    'MaterialTheme.colorScheme.onBackground'    = 'LumenTokens.Color.textPrimary'
    'MaterialTheme.colorScheme.surface'         = 'LumenTokens.Color.surface'
    'MaterialTheme.colorScheme.onSurface'       = 'LumenTokens.Color.textPrimary'
    'MaterialTheme.colorScheme.surfaceVariant'  = 'LumenTokens.Color.surfaceHi'
    'MaterialTheme.colorScheme.onSurfaceVariant'= 'LumenTokens.Color.textSecondary'
    'MaterialTheme.colorScheme.error'           = 'LumenTokens.Color.danger'
    'MaterialTheme.colorScheme.outline'         = 'LumenTokens.Color.border'
    'MaterialTheme.colorScheme.secondary'       = 'LumenTokens.Color.surfaceHi'
}

foreach ($file in $files) {
    $text = Get-Content $file -Raw
    $orig = $text

    foreach ($k in $shapeMap.Keys) { $text = $text.Replace($k, $shapeMap[$k]) }
    foreach ($k in $colorMap.Keys) { $text = $text.Replace($k, $colorMap[$k]) }
    foreach ($k in $schemeMap.Keys) { $text = $text.Replace($k, $schemeMap[$k]) }
    foreach ($k in $dpMap.Keys) { $text = $text.Replace($k, $dpMap[$k]) }

    if ($text -ne $orig) {
        if ($text -notmatch 'import com\.example\.calmsource\.core\.ui\.theme\.LumenTokens') {
            $text = $text -replace '(package [^\r\n]+\r?\n)', "`$1`nimport com.example.calmsource.core.ui.theme.LumenTokens`n"
        }
        Set-Content -Path $file -Value $text -NoNewline
        Write-Host "Updated: $file"
    }
}

Write-Host "Done. Files touched: $($files.Count)"
