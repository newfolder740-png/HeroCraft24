$packDir = "C:\Users\Newfo\HeroCraft24\app\src\main\assets\packs\phb2024"
$manifestPath = "$packDir\manifest.json"

function Extract-String($text, $key) {
    $pattern = '"' + [regex]::Escape($key) + '"\s*:\s*"([^"]*)"'
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { return $m.Groups[1].Value } else { return $null }
}

function Extract-NameBlock($text) {
    try {
        $json = $text | ConvertFrom-Json
        $en = ($json.name.en -replace '"', '\"')
        $ru = ($json.name.ru -replace '"', '\"')
        return "{`"en`":`"$en`",`"ru`":`"$ru`"}"
    } catch {
        $m = [regex]::Match($text, '"name"\s*:\s*(\{[^}]*\})')
        if ($m.Success) { return $m.Groups[1].Value } else { return '{"en":"","ru":""}' }
    }
}

function Extract-Int($text, $key) {
    $pattern = '"' + [regex]::Escape($key) + '"\s*:\s*(-?\d+)'
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { return [int]$m.Groups[1].Value } else { return 0 }
}

function Extract-Bool($text, $key) {
    $pattern = '"' + [regex]::Escape($key) + '"\s*:\s*(true|false)'
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { return $m.Groups[1].Value } else { return 'false' }
}

function Extract-Array($text, $key) {
    $pattern = '"' + [regex]::Escape($key) + '"\s*:\s*(\[[^\]]*\])'
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { return $m.Groups[1].Value } else { return '[]' }
}

function Get-CategoryFiles($category) {
    $dir = "$packDir\$category"
    if (-not (Test-Path $dir)) { return @() }
    return Get-ChildItem -Path $dir -Filter '*.json' | Sort-Object Name
}

function Build-SpellEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $level = Extract-Int $text 'level'
    $school = Extract-String $text 'school'
    $ritual = Extract-Bool $text 'ritual'
    $concentration = Extract-Bool $text 'concentration'
    $classes = Extract-Array $text 'classes'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"level`":$level,`"school`":`"$school`",`"ritual`":$ritual,`"concentration`":$concentration,`"classes`":$classes,`"tags`":$tags}"
}

function Build-ItemEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $category = Extract-String $text 'category'
    $rarity = Extract-String $text 'rarity'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"category`":`"$category`",`"rarity`":`"$rarity`",`"tags`":$tags}"
}

function Build-ClassEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $hitDie = Extract-Int $text 'hit_die'
    $primaryAbility = Extract-String $text 'primary_ability'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"hit_die`":$hitDie,`"primary_ability`":`"$primaryAbility`",`"tags`":$tags}"
}

function Build-SpeciesEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $type = Extract-String $text 'creature_type'
    $size = Extract-String $text 'size'
    $speed = Extract-Int $text 'speed'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"type`":`"$type`",`"size`":`"$size`",`"speed`":$speed,`"tags`":$tags}"
}

function Build-BackgroundEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"tags`":$tags}"
}

function Build-FeatEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $category = Extract-String $text 'category'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"category`":`"$category`",`"tags`":$tags}"
}

function Build-ConditionEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"tags`":$tags}"
}

function Build-MonsterEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $size = Extract-String $text 'size'
    $type = Extract-String $text 'creature_type'
    $cr = Extract-String $text 'challenge_rating'
    if ($cr -eq $null) { $cr = '0' }
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"size`":`"$size`",`"type`":`"$type`",`"challenge_rating`":$cr,`"tags`":$tags}"
}

function Build-MechanicEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $category = Extract-String $text 'category'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"category`":`"$category`",`"tags`":$tags}"
}

function Build-FeatureEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $level = Extract-Int $text 'level'
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"level`":$level,`"tags`":$tags}"
}

function Build-SubclassEntry($text) {
    $id = Extract-String $text 'id'
    $name = Extract-NameBlock $text
    $tags = Extract-Array $text 'tags'
    return "{`"id`":`"$id`",`"name`":$name,`"tags`":$tags}"
}

$categories = @(
    @{Name='spells'; Builder='Build-SpellEntry'},
    @{Name='items'; Builder='Build-ItemEntry'},
    @{Name='classes'; Builder='Build-ClassEntry'},
    @{Name='species'; Builder='Build-SpeciesEntry'},
    @{Name='backgrounds'; Builder='Build-BackgroundEntry'},
    @{Name='feats'; Builder='Build-FeatEntry'},
    @{Name='conditions'; Builder='Build-ConditionEntry'},
    @{Name='monsters'; Builder='Build-MonsterEntry'},
    @{Name='mechanics'; Builder='Build-MechanicEntry'},
    @{Name='features'; Builder='Build-FeatureEntry'},
    @{Name='subclasses'; Builder='Build-SubclassEntry'}
)

$total = 0
$objectsParts = @()

foreach ($cat in $categories) {
    $catName = $cat.Name
    $builder = $cat.Builder
    $files = Get-CategoryFiles $catName
    $entries = @()
    foreach ($file in $files) {
        $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $entry = & $builder $text
        $entries += $entry
    }
    $total += $entries.Length
    $joined = $entries -join ",`n"
    $objectsParts += "`n    `"$catName`": [`n      $joined`n    ]"
}

$objectsJson = ($objectsParts -join ",") 

$manifest = @"
{
  "pack_id": "phb2024",
  "name": { "en": "Player's Handbook 2024", "ru": "Книга игрока (2024)" },
  "version": "1.0.0",
  "format_version": 1,
  "rules_version": "dnd2024",
  "authors": ["Wizards of the Coast"],
  "license": "official",
  "description": {
    "en": "Core rules for Dungeons & Dragons 2024",
    "ru": "Основные правила Dungeons & Dragons 2024"
  },
  "dependencies": [],
  "language": "en",
  "locales": ["en", "ru"],
  "total_objects": $total,
  "objects": {$objectsJson
  }
}
"@

$encoding = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($manifestPath, $manifest, $encoding)

Write-Host "Regenerated manifest with $total objects."
