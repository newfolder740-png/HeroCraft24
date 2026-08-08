# Cleanup script for Phase 5
# Usage: powershell -File phase5_cleanup.ps1

$ErrorActionPreference = "Stop"

$packRoot = "C:\Users\Newfo\HeroCraft24\app\src\main\assets\packs\phb2024"
$enc = New-Object System.Text.UTF8Encoding($true)
$encNoBom = New-Object System.Text.UTF8Encoding($false)
function Write-JsonNoBom($path, $jsonObj, [switch]$Compress) {
    $text = if ($Compress) { $jsonObj | ConvertTo-Json -Depth 100 -Compress } else { $jsonObj | ConvertTo-Json -Depth 100 }
    [System.IO.File]::WriteAllText($path, $text, $encNoBom)
}

# -------------------- Subclass mapping --------------------
$manifest = Get-Content "$packRoot\manifest.json" -Raw -Encoding UTF8 | ConvertFrom-Json
$subclassMap = @{}
foreach ($sc in $manifest.objects.subclasses) {
    $ru = $sc.name.ru
    $id = "phb2024:" + $sc.id
    if ($ru) {
        $subclassMap[$ru] = $id
    }
}
# Manual case-variant mapping
if ($subclassMap.ContainsKey("Великий древний")) {
    $subclassMap["Великий Древний"] = $subclassMap["Великий древний"]
}

# Build spell subclass value list and apply replacements
$spellRoot = "$packRoot\spells"
$spellFiles = Get-ChildItem $spellRoot -Filter '*.json'
$spellChanged = 0
$spellUnknown = @{}
$spellMappingUsed = @{}

foreach ($file in $spellFiles) {
    $json = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $json.PSObject.Properties['subclasses']) { continue }
    $changed = $false
    $newSubclasses = @()
    foreach ($name in $json.subclasses) {
        if ($subclassMap.ContainsKey($name)) {
            $newSubclasses += $subclassMap[$name]
            $spellMappingUsed[$name] = $subclassMap[$name]
            $changed = $true
        } else {
            $newSubclasses += $name
            if (-not [string]::IsNullOrWhiteSpace($name)) {
                $spellUnknown[$name] = ($spellUnknown[$name] + 1)
            }
        }
    }
    if ($changed) {
        $json.subclasses = $newSubclasses
        Write-JsonNoBom -path $file.FullName -jsonObj $json
        $spellChanged++
    }
}

# -------------------- Damage vulnerabilities --------------------
$damageMap = @{
    "к урону Огонь" = "fire"
    "к урону Дробящий" = "bludgeoning"
    "к урону Холод" = "cold"
    "к урону Психический" = "psychic"
    "к урону Звук" = "thunder"
    "к урону Излучение" = "radiant"
    "к урону Яд" = "poison"
}

$monsterRoot = "$packRoot\monsters"
$monsterFiles = Get-ChildItem $monsterRoot -Filter '*.json'
$monsterChanged = 0
$monsterUnknown = @{}
$monsterStandardChanged = 0

foreach ($file in $monsterFiles) {
    $json = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not ($json.PSObject.Properties['damage_vulnerabilities'])) { continue }
    $dv = $json.damage_vulnerabilities
    if ($dv -eq $null) { continue }

    $fileName = $file.Name
    # Special handling
    if ($fileName -eq "shadow.json") {
        $json.damage_vulnerabilities = @("radiant")
        Write-JsonNoBom -path $file.FullName -jsonObj $json
        $monsterChanged++
        continue
    }
    if ($fileName -eq "rakshasa.json") {
        $json.damage_vulnerabilities = @("piercing")
        # Add trait if not already present
        $traitName = "Уязвимость к Колющему урону"
        $traitDesc = "Ракшас уязвим к Колющему урону от оружия существ под воздействием заклинания Благословение."
        $existing = $false
        if ($json.traits) {
            foreach ($t in $json.traits) {
                if ($t.name.ru -eq $traitName) { $existing = $true; break }
            }
        }
        if (-not $existing) {
            $newTrait = @{
                name = @{ en = ""; ru = $traitName }
                description = @{ en = ""; ru = $traitDesc }
            }
            if ($json.traits) { $json.traits += $newTrait }
            else { $json.traits = @($newTrait) }
        }
        Write-JsonNoBom -path $file.FullName -jsonObj $json
        $monsterChanged++
        continue
    }
    if ($fileName -eq "shoggoth.json") {
        $json.damage_vulnerabilities = @()
        Write-JsonNoBom -path $file.FullName -jsonObj $json
        $monsterChanged++
        continue
    }

    $newDv = @()
    $changed = $false
    foreach ($val in $dv) {
        if ($damageMap.ContainsKey($val)) {
            $newDv += $damageMap[$val]
            $changed = $true
        } else {
            $newDv += $val
            if (-not [string]::IsNullOrWhiteSpace($val)) {
                $monsterUnknown[$val] = ($monsterUnknown[$val] + 1)
            }
        }
    }
    if ($changed) {
        $json.damage_vulnerabilities = $newDv
        $json | ConvertTo-Json -Depth 100 | Out-File -FilePath $file.FullName -Encoding utf8
        $monsterChanged++
        $monsterStandardChanged++
    }
}

# -------------------- Class files --------------------
$classesToFix = @("druid", "monk", "paladin", "ranger", "rogue")
$subclassTitleRu = @{
    "druid" = "Круг друидов"
    "monk" = "Монастическая традиция"
    "paladin" = "Священная клятва"
    "ranger" = "Конклав следопыта"
    "rogue" = "Архетип плута"
}
$classChanged = 0
foreach ($classId in $classesToFix) {
    $path = "$packRoot\classes\$classId.json"
    if (-not (Test-Path $path)) { continue }
    $json = Get-Content $path -Raw -Encoding UTF8 | ConvertFrom-Json
    # subclass_title.ru
    if ($json.subclass_title.ru -ne $subclassTitleRu[$classId]) {
        $json.subclass_title.ru = $subclassTitleRu[$classId]
    }
    # slot columns ru null -> number
    foreach ($col in $json.class_table.columns) {
        if ($col.name.ru -eq $null -and $col.name.en -match '^\d+$') {
            $col.name.ru = $col.name.en
        }
    }
    $json | ConvertTo-Json -Depth 100 -Compress | Out-File -FilePath $path -Encoding utf8
    $classChanged++
}

# -------------------- Validation --------------------
$invalid = @()
foreach ($dir in @($spellRoot, $monsterRoot, "$packRoot\classes")) {
    Get-ChildItem $dir -Filter '*.json' | ForEach-Object {
        try {
            $null = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        } catch {
            $invalid += $_.FullName
        }
    }
}

# -------------------- Summary logs --------------------
$summary = @"
Phase 5 cleanup summary
=======================
Spell files changed: $spellChanged
Monster files changed: $monsterChanged
Class files changed: $classChanged
Invalid JSON files: $($invalid.Count)

Spell subclass unknown values:
$($spellUnknown.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" } | Out-String)

Monster damage_vulnerabilities unknown values:
$($monsterUnknown.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" } | Out-String)

Invalid files:
$($invalid | Out-String)
"@
[System.IO.File]::WriteAllText("C:\Users\Newfo\HeroCraft24\phase5_summary.txt", $summary, $enc)
Write-Host "Done. See phase5_summary.txt"
