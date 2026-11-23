$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host " Repairing & Applying Phases 21-23 in $root" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

# 0. Disable Gradle Configuration Cache in gradle.properties
$gradlePropsPath = Join-Path $root "gradle.properties"
if (Test-Path $gradlePropsPath) {
    $gp = [System.IO.File]::ReadAllText($gradlePropsPath, [System.Text.Encoding]::UTF8)
    $gp = $gp -replace "org\.gradle\.configuration-cache\s*=\s*true", "org.gradle.configuration-cache=false"
    [System.IO.File]::WriteAllText($gradlePropsPath, $gp, [System.Text.Encoding]::UTF8)
}

# 1. PlannerScreen.kt — Repair filter_row extra closing braces & remove duplicate top header
$plannerPath = Join-Path $root "app\src\main\kotlin\com\appname\notes\feature\calendar\PlannerScreen.kt"
if (Test-Path $plannerPath) {
    $p = [System.IO.File]::ReadAllText($plannerPath, [System.Text.Encoding]::UTF8)
    if (-not $p.Contains("import androidx.compose.foundation.layout.statusBarsPadding")) {
        $p = $p.Replace("import androidx.compose.foundation.layout.padding", "import androidx.compose.foundation.layout.padding`nimport androidx.compose.foundation.layout.statusBarsPadding`nimport androidx.compose.foundation.layout.imePadding")
    }
    $p = $p.Replace("Box(Modifier.fillMaxSize().backdropSource(backdrop))", "Box(Modifier.fillMaxSize().backdropSource(backdrop).statusBarsPadding())")
    $p = [regex]::Replace($p, "(//\s*Floating Liquid Glass Header[^\n]*\r?\n\s*val\s+isDark\s*=\s*LocalDarkTheme\.current\r?\n\s*)GlassSurface\s*\(", "`$1if (false) GlassSurface(")
    $slidingFilterReplacement = @"
item(key = "filter_row") {
            val filters = listOf(TaskFilter.PENDING, TaskFilter.COMPLETED, TaskFilter.ALL)
            com.appname.notes.core.designsystem.component.AnimatedGlassSegmentedControl(
                items = filters,
                selectedItem = state.taskFilter,
                onSelect = onFilterChange,
                labelProvider = { filter ->
                    when (filter) {
                        TaskFilter.PENDING -> stringResource(R.string.todo_filter_pending, state.pendingTaskCount)
                        TaskFilter.COMPLETED -> stringResource(R.string.todo_filter_completed, state.completedTaskCount)
                        TaskFilter.ALL -> stringResource(R.string.todo_filter_all, state.allTasks.size)
                    }
                },
                tagProvider = { filter ->
                    when (filter) {
                        TaskFilter.PENDING -> PlannerTestTags.FILTER_PENDING
                        TaskFilter.COMPLETED -> PlannerTestTags.FILTER_COMPLETED
                        TaskFilter.ALL -> PlannerTestTags.FILTER_ALL
                    }
                },
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(vertical = 4.dp),
            )
        }
"@
    $p = [regex]::Replace($p, "(?s)item\(key\s*=\s*`"filter_row`"\).*?(?=\r?\n\s*if\s*\([^\n]*\.isEmpty\(\)\s*\))", $slidingFilterReplacement)
    $p = $p.Replace("snackbarHost = { SnackbarHost(snackbar) }", "snackbarHost = { com.appname.notes.core.designsystem.component.GlassSnackbarHost(snackbar) }")
    $p = $p.Replace("MaterialTheme.colorScheme.primaryContainer", "(if (LocalDarkTheme.current) Color.White.copy(alpha = 0.16f) else Color(0xFF141518).copy(alpha = 0.10f))")
    $p = $p.Replace("MaterialTheme.colorScheme.onPrimaryContainer", "(if (LocalDarkTheme.current) Color(0xFFF4F5F7) else Color(0xFF111317))")
    [System.IO.File]::WriteAllText($plannerPath, $p, [System.Text.Encoding]::UTF8)
    Write-Host "  [OK] PlannerScreen.kt: Repaired filter_row braces, removed top bar & updated Task popup." -ForegroundColor Green
}

# 2. InkTools.kt — Remove broken ui.tools.brush block, add MutableInteractionSource import, wire ColorDot -> ActiveInkToolColorState
$inkToolsPath = Join-Path $root "app\src\main\kotlin\com\appname\notes\feature\ink\InkTools.kt"
if (Test-Path $inkToolsPath) {
    $it = [System.IO.File]::ReadAllText($inkToolsPath, [System.Text.Encoding]::UTF8)
    if (-not $it.Contains("import androidx.compose.foundation.interaction.MutableInteractionSource")) {
        $it = $it.Replace("package com.appname.notes.feature.ink", "package com.appname.notes.feature.ink`n`nimport androidx.compose.foundation.interaction.MutableInteractionSource")
    }
    $it = [regex]::Replace($it, "(?s)\r?\n\s*ActiveInkToolColorState\.value\s*=\s*when\s*\{.*?else\s*->\s*null\s*\}", "")
    $it = [regex]::Replace($it, "(?s)\r?\n\s*currentActiveToolDotColor\s*=\s*when\s*\{.*?else\s*->\s*null\s*\}", "")
    $it = [regex]::Replace($it, "\r?\nprivate var currentActiveToolDotColor:\s*androidx\.compose\.ui\.graphics\.Color\?\s*=\s*null\r?\n?", "`n")
    if (-not $it.Contains("syncActiveInkToolColor(")) {
        $it = [regex]::Replace(
            $it,
            "(?s)(@Composable\s+private\s+fun\s+ColorDot\s*\((.*?)\)\s*\{)",
            {
                param($m)
                $header = $m.Groups[1].Value
                $paramsRaw = $m.Groups[2].Value
                $parts = $paramsRaw -split ","
                $firstParamName = (($parts[0] -split ":")[0].Trim() -split "\s+")[-1]
                $boolPart = ($parts | Where-Object { $_ -match "Boolean" } | Select-Object -First 1)
                if (-not $boolPart) { $boolPart = "selected: Boolean" }
                $boolParamName = (($boolPart -split ":")[0].Trim() -split "\s+")[-1]
                return "$header`n    syncActiveInkToolColor($firstParamName, $boolParamName)"
            }
        )
    }
    [System.IO.File]::WriteAllText($inkToolsPath, $it, [System.Text.Encoding]::UTF8)
    Write-Host "  [OK] InkTools.kt: Repaired compilation, added MutableInteractionSource & wired dynamic size circle colours." -ForegroundColor Green
}

Write-Host "`n=========================================================" -ForegroundColor Green
Write-Host " ALL REPAIRS APPLIED! Now click Run in Android Studio." -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green
