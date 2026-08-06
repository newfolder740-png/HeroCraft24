import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class GenerateContent {

    public static void main(String[] args) throws Exception {
        String projectRoot = "C:\\Users\\Newfo\\AndroidStudioProjects\\HeroCraft24";
        String packDir = projectRoot + "\\app\\src\\main\\assets\\packs\\phb2024";
        String spellsTxt = projectRoot + "\\txt\\zaklinania.txt";
        String backgroundsTxt = projectRoot + "\\txt\\предыстории.md";
        String speciesTxt = projectRoot + "\\txt\\виды.txt";
        String featsTxt = projectRoot + "\\txt\\черты происхождения.txt";
        String featsExtraTxt = projectRoot + "\\txt\\cherty_krome_proiskhozhdenia.txt";
        String itemsTxt = projectRoot + "\\txt\\nemag_predmety.txt";
        String classesMd = projectRoot + "\\txt\\Волшебник [Wizard].md";

        System.out.println("Generating content...");

        // Generate spells from txt
        if (new File(spellsTxt).exists()) {
            generateSpells(spellsTxt, packDir);
        } else {
            System.out.println("Spells txt not found: " + spellsTxt);
        }

        String featsDir = packDir + "\\feats";
        String itemsDir = packDir + "\\items";

        // Generate origin feats from txt
        if (new File(featsTxt).exists()) {
            generateFeatsFromTxt(featsTxt, featsDir);
        } else {
            System.out.println("Feats txt not found: " + featsTxt);
        }

        // Generate extra feats from txt
        if (new File(featsExtraTxt).exists()) {
            generateFeatsFromTxt(featsExtraTxt, featsDir);
        } else {
            System.out.println("Feats extra txt not found: " + featsExtraTxt);
        }

        // Generate items from txt
        if (new File(itemsTxt).exists()) {
            generateItemsFromTxt(itemsTxt, itemsDir);
        } else {
            System.out.println("Items txt not found: " + itemsTxt);
        }

        // Generate backgrounds from txt (must be after items/feats exist)
        if (new File(backgroundsTxt).exists()) {
            generateBackgrounds(backgroundsTxt, packDir);
        } else {
            System.out.println("Backgrounds txt not found: " + backgroundsTxt);
        }

        // Generate classes from markdown
        if (new File(classesMd).exists()) {
            generateClassesFromMd(classesMd, packDir);
        } else {
            System.out.println("Classes md not found: " + classesMd);
        }

        // Regenerate manifest
        regenerateManifest(packDir);

        System.out.println("Done.");
    }

    private static void generateSpells(String inputFile, String packDir) throws Exception {
        String spellsDir = packDir + "\\spells";
        Files.createDirectories(Paths.get(spellsDir));

        List<String> lines = Files.readAllLines(Paths.get(inputFile));
        List<Spell> spells = new ArrayList<>();
        Spell current = null;
        StringBuilder desc = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            Matcher nameMatcher = Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*$").matcher(line);
            if (nameMatcher.matches()) {
                if (current != null) {
                    String full = desc.toString().trim();
                    String[] split = splitHigherLevels(full);
                    current.description = split[0];
                    current.higherLevels = split[1];
                    spells.add(current);
                }
                current = new Spell();
                desc.setLength(0);
                current.nameRu = nameMatcher.group(1).trim();
                current.nameEn = nameMatcher.group(2).trim();
                continue;
            }

            if (current == null) continue;

            if (line.matches("^Заговор,.*")) {
                current.level = 0;
                current.school = mapSchool(line.substring("Заговор,".length()).trim());
            } else if (line.matches("^\\d+ уровень,.*")) {
                current.level = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                current.school = mapSchool(line.substring(line.indexOf(',') + 1).trim());
            } else if (line.startsWith("Время сотворения:")) {
                current.castingTime = line.substring("Время сотворения:".length()).trim();
            } else if (line.startsWith("Дистанция:")) {
                current.range = line.substring("Дистанция:".length()).trim();
            } else if (line.startsWith("Компоненты:")) {
                String compLine = line.substring("Компоненты:".length()).trim();
                parseComponents(current, compLine);
            } else if (line.startsWith("Длительность:")) {
                current.duration = line.substring("Длительность:".length()).trim();
                current.concentration = current.duration.toLowerCase().contains("концентрация");
            } else if (line.startsWith("Классы:")) {
                String clsLine = line.substring("Классы:".length()).trim();
                current.classes.addAll(mapClasses(clsLine));
            } else if (line.startsWith("Подклассы:")) {
                String subLine = line.substring("Подклассы:".length()).trim();
                current.subclasses.addAll(mapSubclasses(subLine));
            } else {
                if (desc.length() > 0) desc.append("\n\n");
                desc.append(line);
            }
        }

        if (current != null) {
            String full = desc.toString().trim();
            String[] split = splitHigherLevels(full);
            current.description = split[0];
            current.higherLevels = split[1];
            spells.add(current);
        }

        System.out.println("Parsed " + spells.size() + " spells.");

        for (Spell spell : spells) {
            String id = toSnakeCase(spell.nameEn);
            String json = spell.toJson(id);
            Files.write(Paths.get(spellsDir, id + ".json"), json.getBytes("UTF-8"));
        }
    }

    private static String mapSchool(String ru) {
        String s = ru.toLowerCase().trim();
        switch (s) {
            case "воплощение": return "evocation";
            case "призыв": return "conjuration";
            case "очарование": return "enchantment";
            case "ограждение": return "abjuration";
            case "преобразование": return "transmutation";
            case "некромантия": return "necromancy";
            case "иллюзия": return "illusion";
            case "прорицание": return "divination";
            default: return "evocation";
        }
    }

    private static void parseComponents(Spell spell, String line) {
        String[] parts = line.split("[,;]");
        for (String part : parts) {
            String p = part.trim();
            if (p.startsWith("В")) spell.components.add("V");
            else if (p.startsWith("C")) spell.components.add("S");
            else if (p.startsWith("М")) {
                spell.components.add("M");
                int idx = p.indexOf('(');
                if (idx > 0) {
                    String mat = p.substring(idx + 1).replace(")", "").trim();
                    spell.material = mat;
                }
            }
        }
    }

    private static List<String> mapClasses(String line) {
        Map<String, String> map = new HashMap<>();
        map.put("артэфактор", "phb2024:artificer");
        map.put("волшебник", "phb2024:wizard");
        map.put("чародей", "phb2024:sorcerer");
        map.put("бард", "phb2024:bard");
        map.put("колдун", "phb2024:warlock");
        map.put("друид", "phb2024:druid");
        map.put("жрец", "phb2024:cleric");
        map.put("паладин", "phb2024:paladin");
        map.put("следопыт", "phb2024:ranger");
        map.put("монах", "phb2024:monk");
        map.put("плут", "phb2024:rogue");
        map.put("воин", "phb2024:fighter");
        map.put("варвар", "phb2024:barbarian");

        List<String> result = new ArrayList<>();
        String[] parts = line.split(",");
        for (String part : parts) {
            String key = part.trim().toLowerCase();
            if (map.containsKey(key)) result.add(map.get(key));
        }
        return result;
    }

    private static List<String> mapSubclasses(String line) {
        // Simplified: just collect unique entries
        List<String> result = new ArrayList<>();
        String[] parts = line.split("\\),");
        for (String part : parts) {
            String clean = part.replace("(", "").replace(")", "").trim();
            if (clean.isEmpty()) continue;
            // Try to extract class from parentheses
            Matcher m = Pattern.compile("(.+?)\\s*\\(([^)]+)\\)").matcher(clean);
            if (m.matches()) {
                String className = m.group(2).trim().toLowerCase();
                String classId = mapClassName(className);
                result.add(classId + ":" + toSnakeCase(m.group(1).trim()));
            }
        }
        return result;
    }

    private static String mapClassName(String ru) {
        switch (ru) {
            case "друид": return "phb2024:druid";
            case "плут": return "phb2024:rogue";
            case "монах": return "phb2024:monk";
            case "волшебник": return "phb2024:wizard";
            case "колдун": return "phb2024:warlock";
            case "жрец": return "phb2024:cleric";
            case "паладин": return "phb2024:paladin";
            case "бард": return "phb2024:bard";
            case "следопыт": return "phb2024:ranger";
            case "воин": return "phb2024:fighter";
            default: return "phb2024:" + ru;
        }
    }

    private static void fixBackgrounds(String packDir) throws Exception {
        File backgroundsDir = new File(packDir, "backgrounds");
        if (!backgroundsDir.exists()) return;
        File[] files = backgroundsDir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
            String updated = content;

            // Reset all increases to 0 inside ability_score_increases
            updated = updated.replaceAll("\"increase\"\\s*:\\s*\\d+", "\"increase\": 0");

            // Add ability_score_choice flag if missing
            if (!updated.contains("\"ability_score_choice\"")) {
                updated = updated.replace("\"ability_score_increases\":", "\"ability_score_choice\": true,\n  \"ability_score_increases\":");
            }

            if (!updated.equals(content)) {
                Files.write(file.toPath(), updated.getBytes("UTF-8"));
            }
        }
    }

    private static String toSnakeCase(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "").replaceAll("^_+", "");
    }

    private static String[] splitHigherLevels(String full) {
        String lower = full.toLowerCase();
        int idx = -1;
        String[] markers = {
            "используя ячейку заклинания большего уровня.",
            "усиление заговора."
        };
        for (String marker : markers) {
            int pos = lower.indexOf(marker);
            if (pos >= 0 && (idx < 0 || pos < idx)) {
                idx = pos;
            }
        }
        if (idx <= 0) return new String[]{full, null};
        // Include the marker line in higher levels
        return new String[]{full.substring(0, idx).trim(), full.substring(idx).trim()};
    }

    private static void generateBackgrounds(String inputFile, String packDir) throws Exception {
        String backgroundsDir = packDir + "\\backgrounds";
        String featsDir = packDir + "\\feats";
        String itemsDir = packDir + "\\items";
        Files.createDirectories(Paths.get(backgroundsDir));
        Files.createDirectories(Paths.get(featsDir));
        Files.createDirectories(Paths.get(itemsDir));

        Map<String, String> featMap = loadFeatMap(featsDir);
        Set<String> existingItems = loadExistingItemNames(itemsDir);
        Map<String, String> itemNameMap = loadItemNameMap(itemsDir);
        List<String> lines = Files.readAllLines(Paths.get(inputFile), StandardCharsets.UTF_8);
        List<List<String>> entries = splitBackgroundEntries(lines);

        int count = 0;
        for (List<String> entryLines : entries) {
            Background bg = parseBackground(entryLines, itemNameMap);
            if (bg.ruName == null || bg.ruName.isEmpty()) continue;
            String id = backgroundId(bg);
            String featId = resolveFeatId(bg.featName, featMap);
            bg.tool_item_ids = filterExistingItems(bg.tool_item_ids, existingItems);
            bg.equipment_items = filterExistingItems(bg.equipment_items, existingItems);
            String abbreviation = abbreviateSource(bg.source);
            String json = buildBackgroundJson(bg, id, featId, abbreviation);
            Files.write(Paths.get(backgroundsDir, id + ".json"), json.getBytes(StandardCharsets.UTF_8));
            count++;
        }
        System.out.println("Generated " + count + " backgrounds.");
    }

    private static Set<String> loadExistingItemNames(String itemsDir) {
        Set<String> set = new HashSet<>();
        File dir = new File(itemsDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return set;
        for (File f : files) {
            set.add(f.getName().replace(".json", ""));
        }
        return set;
    }

    private static Map<String, String> loadItemNameMap(String itemsDir) throws Exception {
        Map<String, String> map = new HashMap<>();
        File dir = new File(itemsDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return map;
        for (File f : files) {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String compact = content.replaceAll("\\s+", " ").trim();
            String id = extractString(compact, "\"id\"");
            String nameBlock = extractNameBlock(compact);
            if (id == null || nameBlock == null) continue;
            String ru = extractString(nameBlock, "\"ru\"");
            String en = extractString(nameBlock, "\"en\"");
            if (ru != null) map.put(ru.toLowerCase(), id);
            if (en != null) map.put(en.toLowerCase(), id);
        }
        return map;
    }

    private static List<String> filterExistingItems(List<String> itemIds, Set<String> existing) {
        List<String> result = new ArrayList<>();
        for (String id : itemIds) {
            if (existing.contains(id)) result.add(id);
        }
        return result;
    }

    private static String defaultItemName(String id) {
        String s = id.replace("_", " ");
        if (s.isEmpty()) return id;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String buildItemStubJson(String id, String enName, String ruName) {
        String cleanEn = cleanMarkdownText(enName);
        String cleanRu = cleanMarkdownText(ruName);
        String cleanDesc = "";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"item\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"").append(escape(cleanEn)).append("\",\"ru\":\"").append(escape(cleanRu)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"\"},");
        sb.append("\"source\":{\"book\":{\"en\":\"\",\"ru\":\"\"},\"abbreviation\":\"\"},");
        sb.append("\"tags\":[\"item\"],");
        sb.append("\"category\":\"adventuring_gear\",");
        sb.append("\"rarity\":\"common\"");
        sb.append("}");
        return sb.toString();
    }

    private static List<List<String>> splitBackgroundEntries(List<String> lines) {
        List<List<String>> entries = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (current.isEmpty()) {
                if (line.trim().isEmpty()) continue;
                current.add(line);
                continue;
            }
            if (line.trim().length() > 0 && !isKnownHeader(line.trim())
                    && i + 1 < lines.size()
                    && lines.get(i + 1).trim().startsWith("Источник:")) {
                entries.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            entries.add(current);
        }
        return entries;
    }

    private static boolean isKnownHeader(String line) {
        return line.startsWith("Источник:") || line.startsWith("Характеристики:")
                || line.startsWith("Черта:") || line.startsWith("Навыки:")
                || line.startsWith("Инструменты:") || line.startsWith("Снаряжение:")
                || line.startsWith("Описание:");
    }

    private static Background parseBackground(List<String> lines, Map<String, String> itemNameMap) {
        Background bg = new Background();
        StringBuilder desc = new StringBuilder();
        boolean nameSet = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (!nameSet) {
                Matcher m = Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*$").matcher(line);
                if (m.matches()) {
                    bg.ruName = m.group(1).trim();
                    bg.enName = m.group(2).trim();
                } else {
                    bg.ruName = line;
                }
                nameSet = true;
                continue;
            }
            if (line.startsWith("Источник:")) {
                bg.source = line.substring("Источник:".length()).trim();
            } else if (line.startsWith("Характеристики:")) {
                String[] parts = line.substring("Характеристики:".length()).split(",");
                for (String p : parts) {
                    String a = mapAbility(p.trim());
                    if (a != null) bg.abilities.add(a);
                }
            } else if (line.startsWith("Черта:")) {
                bg.featName = line.substring("Черта:".length()).trim();
            } else if (line.startsWith("Навыки:")) {
                String rest = line.substring("Навыки:".length()).trim();
                String[] parts = rest.contains(" и ") ? rest.split(" и ") : rest.split(",");
                for (String p : parts) {
                    String s = mapSkill(p.trim());
                    if (s != null) bg.skills.add(s);
                }
            } else if (line.startsWith("Инструменты:")) {
                String[] parts = line.substring("Инструменты:".length()).split(",");
                for (String p : parts) {
                    String t = p.trim();
                    if (t.isEmpty()) continue;
                    bg.tools.add(t);
                    String itemId = itemNameMap.getOrDefault(t.toLowerCase(), toItemId(t));
                    bg.tool_item_ids.add(itemId);
                    bg.itemNames.put(itemId, t);
                }
            } else if (line.startsWith("Снаряжение:")) {
                String eq = line.substring("Снаряжение:".length()).trim();
                bg.equipment = eq;
                bg.equipment_items.addAll(parseEquipmentItems(eq, itemNameMap, bg.itemNames));
            } else if (line.startsWith("Описание:")) {
                if (desc.length() > 0) desc.append('\n');
                desc.append(line.substring("Описание:".length()).trim());
            } else {
                if (desc.length() > 0) desc.append('\n');
                desc.append(line);
            }
        }
        bg.description = desc.toString().trim();
        return bg;
    }

    private static String mapAbility(String ru) {
        switch (ru.toLowerCase()) {
            case "сила": return "strength";
            case "ловкость": return "dexterity";
            case "телосложение": return "constitution";
            case "интеллект": return "intelligence";
            case "мудрость": return "wisdom";
            case "харизма": return "charisma";
            default: return null;
        }
    }

    private static String mapSkill(String ru) {
        switch (ru.toLowerCase()) {
            case "акробатика": return "acrobatics";
            case "обращение с животными": return "animal_handling";
            case "тайная магия": return "arcana";
            case "атлетика": return "athletics";
            case "обман": return "deception";
            case "история": return "history";
            case "проницательность": return "insight";
            case "запугивание": return "intimidation";
            case "расследование": return "investigation";
            case "медицина": return "medicine";
            case "природа": return "nature";
            case "восприятие": return "perception";
            case "выступление": return "performance";
            case "убеждение": return "persuasion";
            case "религия": return "religion";
            case "ловкость рук": return "sleight_of_hand";
            case "скрытность": return "stealth";
            case "выживание": return "survival";
            default: return null;
        }
    }

    private static String toItemId(String s) {
        String cleaned = s.toLowerCase()
            .replaceAll("\\([^)]*\\)", "")
            .replaceAll("[^a-z0-9а-яё ]+", " ")
            .trim();
        if (cleaned.isEmpty()) return "";
        String translit = transliterateCyrillic(cleaned);
        return toSnakeCase(translit);
    }

    private static List<String> parseEquipmentItems(String eq, Map<String, String> itemNameMap, Map<String, String> itemNames) {
        List<String> items = new ArrayList<>();
        String stripped = eq
            .replaceAll("(?iu)выберите\\s*[а-яё]+\\s*или\\s*[а-яё]+\\s*:", "")
            .replaceAll("\\([А-Яa-zA-Z]\\)", "")
            .replaceAll("(?i)\\bили\\b", ",")
            .replaceAll("\\.", "");
        String[] parts = stripped.split("[,;]");
        for (String p : parts) {
            String t = p.trim()
                .replaceAll("(?i)\\d+\\s*зм", "")
                .replaceAll("(?i)\\bзм\\b", "")
                .replaceAll("\\d+", "")
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("(?i)\\bили\\b", "")
                .trim();
            if (t.isEmpty() || t.length() < 3) continue;
            String id = itemNameMap.getOrDefault(t.toLowerCase(), toItemId(t));
            if (id.isEmpty() || items.contains(id)) continue;
            if (id.equals("vyberite") || id.startsWith("vyberite_")) continue;
            if (id.equals("ili") || id.startsWith("ili_")) continue;
            items.add(id);
            itemNames.put(id, t);
        }
        return items;
    }

    private static String backgroundId(Background bg) {
        if (bg.enName != null && !bg.enName.isEmpty()) return toSnakeCase(bg.enName);
        return toSnakeCase(transliterateCyrillic(bg.ruName));
    }

    private static String abbreviateSource(String source) {
        if (source == null) return "";
        String s = source.replace('’', '\'');
        if (s.contains("Player's Handbook 2024") || s.contains("Player’s Handbook 2024")) return "PHB2024";
        if (s.contains("Eberron: Forge of the Artificer")) return "E:FA";
        if (s.contains("Forgotten Realms: Heroes of Faerûn")) return "FRHF";
        if (s.contains("Astarion's Book of Hungers")) return "ABH";
        if (s.contains("Ravenloft: The Horrors Within")) return "RTHW";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c) || Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static Map<String, String> loadFeatMap(String featsDir) throws Exception {
        Map<String, String> map = new HashMap<>();
        File dir = new File(featsDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return map;
        for (File f : files) {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String compact = content.replaceAll("\\s+", " ").trim();
            String id = extractString(compact, "\"id\"");
            String nameBlock = extractNameBlock(compact);
            if (id == null || nameBlock == null) continue;
            String ru = extractString(nameBlock, "\"ru\"");
            if (ru != null) map.put(normalizeFeatName(ru), id);
        }
        return map;
    }

    private static String normalizeFeatName(String s) {
        return s.toLowerCase().trim();
    }

    private static String resolveFeatId(String featName, Map<String, String> featMap) {
        if (featName == null) return null;
        String normalized = normalizeFeatName(featName);
        String id = featMap.get(normalized);
        if (id != null) return id;
        // try base name without parenthetical variants
        if (featName.contains("(")) {
            String base = featName.replaceAll("\\s*\\([^)]*\\)", "").trim();
            id = featMap.get(normalizeFeatName(base));
            if (id != null) return id;
        }
        return null;
    }

    private static String buildFeatStubJson(String id, String enName, String ruName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"feat\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"").append(escape(enName)).append("\",\"ru\":\"").append(escape(ruName)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"\"},");
        sb.append("\"category\":\"origin\",");
        sb.append("\"repeatable\":false");
        sb.append("}");
        return sb.toString();
    }

    private static String transliterateCyrillic(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            String t = translitChar(c);
            if (t != null) {
                sb.append(t);
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static String translitChar(char ch) {
        char c = Character.toLowerCase(ch);
        switch (c) {
            case 'а': return "a";
            case 'б': return "b";
            case 'в': return "v";
            case 'г': return "g";
            case 'д': return "d";
            case 'е': return "e";
            case 'ё': return "e";
            case 'ж': return "zh";
            case 'з': return "z";
            case 'и': return "i";
            case 'й': return "y";
            case 'к': return "k";
            case 'л': return "l";
            case 'м': return "m";
            case 'н': return "n";
            case 'о': return "o";
            case 'п': return "p";
            case 'р': return "r";
            case 'с': return "s";
            case 'т': return "t";
            case 'у': return "u";
            case 'ф': return "f";
            case 'х': return "h";
            case 'ц': return "ts";
            case 'ч': return "ch";
            case 'ш': return "sh";
            case 'щ': return "shch";
            case 'ъ': return "";
            case 'ы': return "y";
            case 'ь': return "";
            case 'э': return "e";
            case 'ю': return "yu";
            case 'я': return "ya";
            default: return null;
        }
    }

    private static String buildBackgroundJson(Background bg, String id, String featId, String abbreviation) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"background\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"").append(escape(bg.enName != null ? bg.enName : "")).append("\",\"ru\":\"").append(escape(bg.ruName)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"").append(escape(bg.description)).append("\"},");
        sb.append("\"source\":{\"book\":{\"en\":\"").append(escape(bg.source != null ? bg.source : "")).append("\"},\"abbreviation\":\"").append(escape(abbreviation)).append("\"},");
        sb.append("\"tags\":[\"background\"],");
        sb.append("\"ability_score_choice\":true,");
        sb.append("\"ability_score_increases\":[");
        for (int i = 0; i < bg.abilities.size(); i++) {
            String a = bg.abilities.get(i);
            sb.append("{\"ability\":\"").append(a).append("\",\"increase\":0,\"optional\":true}");
            if (i < bg.abilities.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"skill_proficiencies\":[");
        for (int i = 0; i < bg.skills.size(); i++) {
            sb.append("\"").append(bg.skills.get(i)).append("\"");
            if (i < bg.skills.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"tool_proficiencies\":[");
        for (int i = 0; i < bg.tools.size(); i++) {
            sb.append("\"").append(escape(bg.tools.get(i))).append("\"");
            if (i < bg.tools.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"tool_item_ids\":[");
        for (int i = 0; i < bg.tool_item_ids.size(); i++) {
            sb.append("\"phb2024:").append(bg.tool_item_ids.get(i)).append("\"");
            if (i < bg.tool_item_ids.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"equipment_items\":[");
        for (int i = 0; i < bg.equipment_items.size(); i++) {
            sb.append("\"phb2024:").append(bg.equipment_items.get(i)).append("\"");
            if (i < bg.equipment_items.size() - 1) sb.append(",");
        }
        sb.append("],");
        if (featId != null) {
            sb.append("\"feat\":\"phb2024:").append(featId).append("\",");
        }
        sb.append("\"feature\":{\"name\":{\"en\":\"\",\"ru\":\"").append(escape(bg.featName != null ? bg.featName : "")).append("\"},\"description\":{\"en\":\"\",\"ru\":\"\"}}");
        sb.append("}");
        return sb.toString();
    }

    private static class Background {
        String ruName;
        String enName;
        String source;
        String featName;
        String equipment;
        List<String> abilities = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        List<String> tool_item_ids = new ArrayList<>();
        List<String> equipment_items = new ArrayList<>();
        Map<String, String> itemNames = new HashMap<>();
        String description = "";
    }

    private static void regenerateManifest(String packDir) throws Exception {
        String manifestPath = packDir + "\\manifest.json";
        StringBuilder manifest = new StringBuilder();
        manifest.append("{\n");
        manifest.append("  \"pack_id\": \"phb2024\",\n");
        manifest.append("  \"name\": { \"en\": \"Player's Handbook 2024\", \"ru\": \"Книга игрока 2024\" },\n");
        manifest.append("  \"version\": \"1.0.0\",\n");
        manifest.append("  \"format_version\": 1,\n");
        manifest.append("  \"rules_version\": \"dnd2024\",\n");
        manifest.append("  \"authors\": [\"Wizards of the Coast\"],\n");
        manifest.append("  \"license\": \"official\",\n");
        manifest.append("  \"description\": {\n");
        manifest.append("    \"en\": \"Core rules for Dungeons & Dragons 2024\",\n");
        manifest.append("    \"ru\": \"Основные правила Dungeons & Dragons 2024\"\n");
        manifest.append("  },\n");
        manifest.append("  \"dependencies\": [],\n");
        manifest.append("  \"language\": \"en\",\n");
        manifest.append("  \"locales\": [\"en\", \"ru\"],\n");

        String[] categories = {"spells", "items", "classes", "species", "backgrounds", "feats", "conditions", "monsters", "mechanics", "features", "subclasses"};
        int total = 0;
        StringBuilder objectsBuilder = new StringBuilder();
        objectsBuilder.append("  \"objects\": {\n");
        for (int i = 0; i < categories.length; i++) {
            String cat = categories[i];
            objectsBuilder.append("    \"").append(cat).append("\": [\n");
            List<String> entries = collectCategory(packDir, cat);
            total += entries.size();
            for (int j = 0; j < entries.size(); j++) {
                objectsBuilder.append(entries.get(j));
                if (j < entries.size() - 1) objectsBuilder.append(",");
                objectsBuilder.append("\n");
            }
            objectsBuilder.append("    ]");
            if (i < categories.length - 1) objectsBuilder.append(",");
            objectsBuilder.append("\n");
        }
        objectsBuilder.append("  }\n");

        manifest.append("  \"total_objects\": ").append(total).append(",\n");
        manifest.append(objectsBuilder);
        manifest.append("}\n");

        Files.write(Paths.get(manifestPath), manifest.toString().getBytes("UTF-8"));
        System.out.println("Regenerated manifest with " + total + " objects.");
    }

    private static List<String> collectCategory(String packDir, String category) throws Exception {
        List<String> result = new ArrayList<>();
        File dir = new File(packDir, category);
        if (!dir.exists()) return result;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return result;

        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
            // Remove whitespace for simple parsing
            String compact = content.replaceAll("\\s+", " ").trim();
            String id = extractString(compact, "\"id\"");
            String nameBlock = extractNameBlock(compact);
            if (id == null || nameBlock == null) continue;

            StringBuilder entry = new StringBuilder();
            entry.append("      {\n");
            entry.append("        \"id\": \"").append(id).append("\",\n");
            entry.append("        \"name\": ").append(nameBlock).append(",\n");

            // Add category-specific summary fields
            switch (category) {
                case "spells":
                    addSpellSummary(entry, compact);
                    break;
                case "items":
                    addItemSummary(entry, compact);
                    break;
                case "classes":
                    addClassSummary(entry, compact);
                    break;
                case "species":
                    addSpeciesSummary(entry, compact);
                    break;
                case "backgrounds":
                    addBackgroundSummary(entry, compact);
                    break;
                case "feats":
                    addFeatSummary(entry, compact);
                    break;
                case "conditions":
                    addConditionSummary(entry, compact);
                    break;
                case "monsters":
                    addMonsterSummary(entry, compact);
                    break;
                case "mechanics":
                    addMechanicSummary(entry, compact);
                    break;
                case "features":
                    addFeatureSummary(entry, compact);
                    break;
                case "subclasses":
                    // no extra summary fields
                    break;
            }

            String tags = extractArray(compact, "\"tags\"");
            entry.append("        \"tags\": ").append(tags != null ? tags : "[]").append("\n");
            entry.append("      }");

            result.add(entry.toString());
        }
        return result;
    }

    private static void addSpellSummary(StringBuilder entry, String compact) {
        entry.append("        \"level\": ").append(extractInt(compact, "\"level\"")).append(",\n");
        entry.append("        \"school\": \"").append(extractString(compact, "\"school\"")).append("\",\n");
        entry.append("        \"ritual\": ").append(extractBool(compact, "\"ritual\"")).append(",\n");
        entry.append("        \"concentration\": ").append(extractBool(compact, "\"concentration\"")).append(",\n");
        String classes = extractArray(compact, "\"classes\"");
        entry.append("        \"classes\": ").append(classes != null ? classes : "[]").append(",\n");
    }

    private static void addItemSummary(StringBuilder entry, String compact) {
        entry.append("        \"category\": \"").append(extractString(compact, "\"category\"")).append("\",\n");
        entry.append("        \"rarity\": \"").append(extractString(compact, "\"rarity\"")).append("\",\n");
    }

    private static void addClassSummary(StringBuilder entry, String compact) {
        entry.append("        \"hit_die\": ").append(extractInt(compact, "\"hit_die\"")).append(",\n");
        entry.append("        \"primary_ability\": \"").append(extractString(compact, "\"primary_ability\"")).append("\",\n");
    }

    private static void addSpeciesSummary(StringBuilder entry, String compact) {
        entry.append("        \"type\": \"").append(extractString(compact, "\"creature_type\"")).append("\",\n");
        entry.append("        \"size\": \"").append(extractString(compact, "\"size\"")).append("\",\n");
        entry.append("        \"speed\": ").append(extractInt(compact, "\"speed\"")).append(",\n");
    }

    private static void addBackgroundSummary(StringBuilder entry, String compact) {
        // no extra fields
    }

    private static void addFeatSummary(StringBuilder entry, String compact) {
        entry.append("        \"category\": \"").append(extractString(compact, "\"category\"")).append("\",\n");
    }

    private static void addConditionSummary(StringBuilder entry, String compact) {
        // no extra fields
    }

    private static void addMonsterSummary(StringBuilder entry, String compact) {
        entry.append("        \"size\": \"").append(extractString(compact, "\"size\"")).append("\",\n");
        entry.append("        \"type\": \"").append(extractString(compact, "\"creature_type\"")).append("\",\n");
        entry.append("        \"challenge_rating\": ").append(extractDouble(compact, "\"challenge_rating\"")).append(",\n");
    }

    private static void addMechanicSummary(StringBuilder entry, String compact) {
        entry.append("        \"category\": \"").append(extractString(compact, "\"category\"")).append("\",\n");
    }

    private static void addFeatureSummary(StringBuilder entry, String compact) {
        entry.append("        \"level\": ").append(extractInt(compact, "\"level\"")).append(",\n");
    }

    private static String extractNameBlock(String compact) {
        int idx = compact.indexOf("\"name\":");
        if (idx < 0) return null;
        int brace = compact.indexOf('{', idx);
        if (brace < 0) return null;
        int end = findMatchingBrace(compact, brace);
        return compact.substring(brace, end + 1);
    }

    private static int findMatchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return s.length() - 1;
    }

    private static String extractString(String compact, String key) {
        String pattern = Pattern.quote(key) + "\\s*:\\s*\"";
        Matcher m = Pattern.compile(pattern).matcher(compact);
        if (!m.find()) return null;
        int start = m.end();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (c == '\\' && i + 1 < compact.length()) {
                sb.append(c);
                sb.append(compact.charAt(i + 1));
                i++;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int extractInt(String compact, String key) {
        String val = extractValue(compact, key);
        if (val == null) return 0;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    private static double extractDouble(String compact, String key) {
        String val = extractValue(compact, key);
        if (val == null) return 0.0;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return 0.0; }
    }

    private static boolean extractBool(String compact, String key) {
        String val = extractValue(compact, key);
        return "true".equalsIgnoreCase(val);
    }

    private static String extractValue(String compact, String key) {
        String pattern = Pattern.quote(key) + "\\s*:\\s*";
        Matcher m = Pattern.compile(pattern).matcher(compact);
        if (!m.find()) return null;
        int start = m.end();
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = start; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (c == '"' && (i == 0 || compact.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && (c == ',' || c == '}' || c == ']')) {
                return sb.toString().trim();
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String extractArray(String compact, String key) {
        String pattern = Pattern.quote(key) + "\\s*:\\s*\\[";
        Matcher m = Pattern.compile(pattern).matcher(compact);
        if (!m.find()) return null;
        int start = m.end() - 1;
        int end = findMatchingBracket(compact, start);
        return compact.substring(start, end + 1);
    }

    private static int findMatchingBracket(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return s.length() - 1;
    }

    static class Spell {
        String nameRu;
        String nameEn;
        int level = 0;
        String school = "evocation";
        String castingTime = "";
        String range = "";
        List<String> components = new ArrayList<>();
        String material = null;
        String duration = "";
        boolean concentration = false;
        boolean ritual = false;
        List<String> classes = new ArrayList<>();
        List<String> subclasses = new ArrayList<>();
        String description = "";
        String higherLevels = null;

        String toJson(String id) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"id\": \"").append(id).append("\",\n");
            sb.append("  \"type\": \"spell\",\n");
            sb.append("  \"format_version\": 1,\n");
            sb.append("  \"name\": {\n");
            sb.append("    \"en\": \"").append(escape(nameEn)).append("\",\n");
            sb.append("    \"ru\": \"").append(escape(nameRu)).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"description\": {\n");
            sb.append("    \"en\": \"\",\n");
            sb.append("    \"ru\": \"").append(escape(description)).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"source\": {\n");
            sb.append("    \"book\": { \"en\": \"Player's Handbook (2024)\", \"ru\": \"Книга игрока (2024)\" },\n");
            sb.append("    \"abbreviation\": \"PHB 2024\"\n");
            sb.append("  },\n");
            sb.append("  \"tags\": [\"spell\"],\n");
            sb.append("  \"level\": ").append(level).append(",\n");
            sb.append("  \"school\": \"").append(school).append("\",\n");
            sb.append("  \"casting_time\": \"").append(escape(castingTime)).append("\",\n");
            sb.append("  \"range\": {\n");
            sb.append("    \"type\": \"range\",\n");
            sb.append("    \"text\": \"").append(escape(range)).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"components\": [");
            for (int i = 0; i < components.size(); i++) {
                sb.append("\"").append(components.get(i)).append("\"");
                if (i < components.size() - 1) sb.append(", ");
            }
            sb.append("],\n");
            if (material != null) {
                sb.append("  \"material\": \"").append(escape(material)).append("\",\n");
            }
            sb.append("  \"duration\": \"").append(escape(duration)).append("\",\n");
            sb.append("  \"concentration\": ").append(concentration).append(",\n");
            sb.append("  \"ritual\": ").append(ritual).append(",\n");
            if (higherLevels != null && !higherLevels.isEmpty()) {
                sb.append("  \"higher_levels\": {\n");
                sb.append("    \"en\": \"\",\n");
                sb.append("    \"ru\": \"").append(escape(higherLevels)).append("\"\n");
                sb.append("  },\n");
            }
            sb.append("  \"classes\": [");
            for (int i = 0; i < classes.size(); i++) {
                sb.append("\"").append(classes.get(i)).append("\"");
                if (i < classes.size() - 1) sb.append(", ");
            }
            sb.append("]\n");
            sb.append("}\n");
            return sb.toString();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String cleanMarkdownText(String s) {
        if (s == null) return "";
        return s.replace("&#x09;", " ")
                .replace("&#x20;", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    private static List<List<String>> splitEntriesByName(List<String> lines, Pattern namePattern) {
        List<List<String>> entries = new ArrayList<>();
        List<String> current = null;
        for (String raw : lines) {
            String trimmed = raw.trim();
            if (namePattern.matcher(trimmed).matches()) {
                if (current != null && !current.isEmpty()) entries.add(current);
                current = new ArrayList<>();
                current.add(raw);
            } else if (current != null) {
                current.add(raw);
            }
        }
        if (current != null && !current.isEmpty()) entries.add(current);
        return entries;
    }

    private static void generateFeatsFromTxt(String inputFile, String featsDir) throws Exception {
        Files.createDirectories(Paths.get(featsDir));
        List<String> lines = Files.readAllLines(Paths.get(inputFile), StandardCharsets.UTF_8);
        Pattern namePattern = Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*$");
        List<List<String>> entries = splitEntriesByName(lines, namePattern);

        int count = 0;
        int skipped = 0;
        for (List<String> entryLines : entries) {
            while (!entryLines.isEmpty() && entryLines.get(0).trim().isEmpty()) entryLines.remove(0);
            while (!entryLines.isEmpty() && entryLines.get(entryLines.size() - 1).trim().isEmpty()) entryLines.remove(entryLines.size() - 1);
            if (entryLines.isEmpty()) continue;

            Matcher m = namePattern.matcher(entryLines.get(0).trim());
            if (!m.matches()) continue;
            String ruName = m.group(1).trim();
            String enName = m.group(2).trim();
            String id = toSnakeCase(!enName.isEmpty() ? enName : transliterateCyrillic(ruName));

            File out = new File(featsDir, id + ".json");
            if (out.exists()) {
                skipped++;
                continue;
            }

            String catLine = entryLines.size() > 1 ? entryLines.get(1).trim() : "";
            String category = "";
            String prerequisite = "";
            if (!catLine.isEmpty()) {
                int paren = catLine.indexOf('(');
                if (paren >= 0) {
                    category = catLine.substring(0, paren).trim();
                    int close = catLine.indexOf(')', paren);
                    if (close > paren) {
                        prerequisite = catLine.substring(paren + 1, close).trim();
                        if (prerequisite.toLowerCase().startsWith("требования:")) {
                            prerequisite = prerequisite.substring("требования:".length()).trim();
                        }
                    }
                } else {
                    category = catLine;
                }
            }

            StringBuilder desc = new StringBuilder();
            for (int i = 2; i < entryLines.size(); i++) {
                if (desc.length() > 0) desc.append('\n');
                desc.append(entryLines.get(i));
            }
            String description = desc.toString().trim();

            String json = buildFeatJson(id, enName, ruName, mapFeatCategory(category), prerequisite, description);
            Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8));
            count++;
        }
        System.out.println("Generated " + count + " feats, skipped " + skipped + " existing.");
    }

    private static String buildFeatJson(String id, String enName, String ruName, String category, String prerequisite, String description) {
        String cleanEn = cleanMarkdownText(enName);
        String cleanRu = cleanMarkdownText(ruName);
        String cleanDesc = cleanMarkdownText(description);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"feat\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"").append(escape(cleanEn)).append("\",\"ru\":\"").append(escape(cleanRu)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"").append(escape(cleanDesc)).append("\"},");
        sb.append("\"source\":{\"book\":{\"en\":\"Player's Handbook 2024\",\"ru\":\"Книга игрока (2024)\"},\"abbreviation\":\"PHB2024\"},");
        sb.append("\"tags\":[\"feat\"],");
        sb.append("\"category\":\"").append(escape(category)).append("\",");
        sb.append("\"prerequisite\":{\"en\":\"\",\"ru\":\"").append(escape(prerequisite)).append("\"},");
        sb.append("\"repeatable\":false,");
        sb.append("\"benefits\":[]");
        sb.append("}");
        return sb.toString();
    }

    private static String mapFeatCategory(String ru) {
        String s = ru.trim().toLowerCase();
        if (s.startsWith("универсальная")) return "universal";
        if (s.startsWith("черта происхождения")) return "origin";
        if (s.startsWith("черта тёмного дара")) return "dark_gift";
        if (s.startsWith("черта эпического дара")) return "epic_boon";
        if (s.startsWith("черта драконьей метки")) return "dragonmark";
        return toSnakeCase(transliterateCyrillic(ru));
    }

    private static void generateItemsFromTxt(String inputFile, String itemsDir) throws Exception {
        Files.createDirectories(Paths.get(itemsDir));
        List<String> lines = Files.readAllLines(Paths.get(inputFile), StandardCharsets.UTF_8);
        Pattern namePattern = Pattern.compile("^(.+?)\\s*\\[(.+?)\\]\\s*$");
        List<List<String>> entries = splitEntriesByName(lines, namePattern);

        int count = 0;
        int skipped = 0;
        for (List<String> entryLines : entries) {
            while (!entryLines.isEmpty() && entryLines.get(0).trim().isEmpty()) entryLines.remove(0);
            while (!entryLines.isEmpty() && entryLines.get(entryLines.size() - 1).trim().isEmpty()) entryLines.remove(entryLines.size() - 1);
            if (entryLines.isEmpty()) continue;

            Matcher m = namePattern.matcher(entryLines.get(0).trim());
            if (!m.matches()) continue;
            String ruName = m.group(1).trim();
            String enName = m.group(2).trim();
            String id = toSnakeCase(!enName.isEmpty() ? enName : transliterateCyrillic(ruName));

            String categoryLine = entryLines.size() > 1 ? entryLines.get(1).trim() : "";

            String costLine = "";
            String weightLine = "";
            String damageLine = "";
            int lastParsed = 1;
            for (int i = 1; i < entryLines.size(); i++) {
                String line = entryLines.get(i).trim();
                if (line.startsWith("Стоимость:")) {
                    costLine = line;
                    lastParsed = i;
                } else if (line.startsWith("Вес:")) {
                    weightLine = line;
                    lastParsed = i;
                } else if (line.startsWith("Урон:")) {
                    damageLine = line;
                    lastParsed = i;
                }
            }
            int descStart = lastParsed + 1;

            StringBuilder desc = new StringBuilder();
            for (int i = descStart; i < entryLines.size(); i++) {
                if (desc.length() > 0) desc.append('\n');
                desc.append(entryLines.get(i));
            }
            String description = desc.toString().trim();

            double costAmount = 0.0;
            String costUnit = "cp";
            Matcher costMatcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(ЗМ|СМ|ММ|зм|см|мм)").matcher(costLine);
            if (costMatcher.find()) {
                costAmount = Double.parseDouble(costMatcher.group(1));
                String unit = costMatcher.group(2).toLowerCase();
                if (unit.equals("зм")) costUnit = "gp";
                else if (unit.equals("см")) costUnit = "sp";
                else if (unit.equals("мм")) costUnit = "cp";
            }

            double weightAmount = 0.0;
            Matcher weightMatcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:фнт|lb)").matcher(weightLine);
            if (weightMatcher.find()) weightAmount = Double.parseDouble(weightMatcher.group(1));

            String damageDice = null;
            String damageType = null;
            if (!damageLine.isEmpty()) {
                Matcher dMatcher = Pattern.compile("([0-9]+)\\s*[кk]\\s*([0-9]+)").matcher(damageLine);
                if (dMatcher.find()) {
                    damageDice = dMatcher.group(1) + "d" + dMatcher.group(2);
                    int comma = damageLine.indexOf(',');
                    if (comma >= 0) damageType = mapDamageType(damageLine.substring(comma + 1).trim());
                }
            }

            String category = mapItemCategory(categoryLine, ruName, enName, description);
            String subcategory = null;
            if (category.equals("weapon")) {
                if (categoryLine.equals("Простое Рукопашное оружие")) subcategory = "simple_melee";
                else if (categoryLine.equals("Простое Дальнобойное оружие")) subcategory = "simple_ranged";
                else if (categoryLine.equals("Воинское Рукопашное оружие")) subcategory = "martial_melee";
                else if (categoryLine.equals("Воинское Дальнобойное оружие")) subcategory = "martial_ranged";
            }

            String armorClassJson = parseArmorClass(description, ruName);
            String propertiesJson = parseItemProperties(description);

            String json = buildItemJson(id, enName, ruName, category, subcategory, costAmount, costUnit, weightAmount, damageDice, damageType, armorClassJson, propertiesJson, description);
            File out = new File(itemsDir, id + ".json");
            Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8));
            count++;
        }
        System.out.println("Generated " + count + " items, skipped " + skipped + " existing.");
    }

    private static String buildItemJson(String id, String enName, String ruName, String category, String subcategory,
                                        double costAmount, String costUnit, double weightAmount, String damageDice, String damageType,
                                        String armorClassJson, String propertiesJson, String description) {
        String cleanEn = cleanMarkdownText(enName);
        String cleanRu = cleanMarkdownText(ruName);
        String cleanDesc = cleanMarkdownText(description);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"item\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"").append(escape(cleanEn)).append("\",\"ru\":\"").append(escape(cleanRu)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"").append(escape(cleanDesc)).append("\"},");
        sb.append("\"source\":{\"book\":{\"en\":\"Player's Handbook 2024\",\"ru\":\"Книга игрока (2024)\"},\"abbreviation\":\"PHB2024\"},");
        sb.append("\"tags\":[\"item\"],");
        sb.append("\"category\":\"").append(escape(category)).append("\",");
        if (subcategory != null && !subcategory.isEmpty()) sb.append("\"subcategory\":\"").append(escape(subcategory)).append("\",");
        sb.append("\"rarity\":\"common\",");
        sb.append("\"cost\":{\"amount\":").append(costAmount).append(",\"unit\":\"").append(costUnit).append("\"},");
        sb.append("\"weight\":{\"amount\":").append(weightAmount).append(",\"unit\":\"lb\"}");
        if (damageDice != null && !damageDice.isEmpty()) {
            sb.append(",\"damage\":{\"damage_dice\":\"").append(damageDice).append("\",\"damage_type\":\"").append(escape(damageType != null ? damageType : "")).append("\"}");
        }
        if (armorClassJson != null && !armorClassJson.isEmpty()) {
            sb.append(",\"armor_class\":").append(armorClassJson);
        }
        sb.append(",\"properties\":").append(propertiesJson).append("");
        sb.append("}");
        return sb.toString();
    }

    private static String parseArmorClass(String description, String ruName) {
        if (ruName.equalsIgnoreCase("Щит") || ruName.equalsIgnoreCase("Shield")) {
            return "{\"base\":2,\"dex_bonus\":false,\"stealth_disadvantage\":false}";
        }
        Matcher m = Pattern.compile("Класс защиты:\\s*(\\d+)(?:\\s*\\+\\s*модификатор (\\p{L}+))?").matcher(description);
        if (!m.find()) return null;
        int base = Integer.parseInt(m.group(1));
        boolean dexBonus = m.group(2) != null;
        Integer maxDex = null;
        Matcher maxM = Pattern.compile("максимум\\s*\\+(\\d+)").matcher(description);
        if (maxM.find()) maxDex = Integer.parseInt(maxM.group(1));
        Integer minStr = null;
        Matcher strM = Pattern.compile("Требуется\\s+Сила\\s*(\\d+)").matcher(description);
        if (strM.find()) minStr = Integer.parseInt(strM.group(1));
        boolean stealthDis = description.contains("Помеха") && (description.contains("Скрытность") || description.contains("скрытность"));
        StringBuilder sb = new StringBuilder();
        sb.append("{\"base\":").append(base).append(",\"dex_bonus\":").append(dexBonus);
        if (maxDex != null) sb.append(",\"max_dex\":").append(maxDex);
        if (minStr != null) sb.append(",\"min_strength\":").append(minStr);
        sb.append(",\"stealth_disadvantage\":").append(stealthDis).append("}");
        return sb.toString();
    }

    private static String parseItemProperties(String description) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Лёгкое", "light");
        map.put("Тяжёлое", "heavy");
        map.put("Универсальное", "versatile");
        map.put("Метательное", "thrown");
        map.put("Фехтовальное", "finesse");
        map.put("Двуручное", "two_handed");
        map.put("Досягаемость", "reach");
        map.put("Боеприпас", "ammunition");
        map.put("Перезарядка", "reload");
        List<String> props = new ArrayList<>();
        for (String line : description.split("\\n")) {
            String l = line.trim();
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (l.startsWith(e.getKey()) && !props.contains(e.getValue())) {
                    props.add(e.getValue());
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < props.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(props.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String mapItemCategory(String categoryLine, String ruName, String enName, String description) {
        String cat = categoryLine.trim();
        if (cat.equals("Простое Рукопашное оружие") || cat.equals("Простое Дальнобойное оружие")
                || cat.equals("Воинское Рукопашное оружие") || cat.equals("Воинское Дальнобойное оружие")) {
            return "weapon";
        }
        String ru = ruName.toLowerCase();
        String en = enName.toLowerCase();
        String desc = description.toLowerCase();
        if (cat.contains("доспех") || en.contains("armor") || ru.equals("щит") || en.equals("shield")) return "armor";
        if (ru.startsWith("набор ") || en.contains("pack") || en.contains("pack")) return "pack";
        if (ru.contains("инструмент") || en.contains("tools") || en.contains("supplies") || en.contains("kit") || en.contains("utensils")) {
            // Instruments also contain "инструмент", differentiate them below
            if (!desc.contains("музыкальн")) return "tool";
        }
        if (desc.contains("вариант музыкального инструмента") || en.contains("bandore") || en.contains("drum") || en.contains("viola")
                || en.contains("bagpipes") || en.contains("playing cards") || en.contains("dulcimer") || en.contains("dragonchess")
                || en.contains("horn") || en.contains("shawm") || en.contains("cittern") || en.contains("pan flute") || en.contains("flute")
                || en.contains("lute") || en.contains("lyre") || en.contains("yarting")) return "instrument";
        if (ru.contains("фокусировк") || en.contains("focus") || ru.contains("символ") || en.contains("symbol")
                || ru.contains("жезл") || en.contains("rod") || ru.contains("посох") || en.contains("staff")
                || ru.contains("сфера") || en.contains("orb") || ru.contains("amulet") || ru.contains("emblem") || ru.contains("reliquary")) {
            return "focus";
        }
        return "adventuring_gear";
    }

    private static String mapDamageType(String ru) {
        String s = ru.toLowerCase().trim();
        if (s.contains("дроб")) return "bludgeoning";
        if (s.contains("колю")) return "piercing";
        if (s.contains("руб")) return "slashing";
        return ru;
    }

    // ─── Classes ─────────────────────────────────────────────────────────

    private static void generateClassesFromMd(String inputFile, String packDir) throws Exception {
        String classesDir = packDir + "\\classes";
        String featuresDir = packDir + "\\features";
        String subclassesDir = packDir + "\\subclasses";
        Files.createDirectories(Paths.get(classesDir));
        Files.createDirectories(Paths.get(featuresDir));
        Files.createDirectories(Paths.get(subclassesDir));

        List<String> lines = Files.readAllLines(Paths.get(inputFile), StandardCharsets.UTF_8);

        String classNameRu = "Волшебник";
        String classNameEn = "Wizard";
        String classId = "wizard";
        String classDescription = "";
        StringBuilder descriptionBuilder = new StringBuilder();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.startsWith("# ")) {
                String raw = line.substring(2).trim().replace("\\[", "[").replace("\\]", "]");
                Matcher titleMatcher = Pattern.compile("^(.*?)\\s*\\[(.*?)\\]\\s*$").matcher(raw);
                if (titleMatcher.matches()) {
                    classNameRu = titleMatcher.group(1).trim();
                    classNameEn = titleMatcher.group(2).trim();
                } else {
                    classNameRu = raw;
                }
                i++;
                while (i < lines.size() && !lines.get(i).trim().startsWith("#### ")) {
                    if (descriptionBuilder.length() > 0) descriptionBuilder.append("\n");
                    descriptionBuilder.append(lines.get(i));
                    i++;
                }
                break;
            }
            i++;
        }
        classDescription = descriptionBuilder.toString().trim();
        if (classDescription.startsWith("**Описание:**")) {
            classDescription = classDescription.substring("**Описание:**".length()).trim();
        }

        List<String> classFeatures = new ArrayList<>();
        List<String> subclassIds = new ArrayList<>();
        Map<String, String> keyAttributes = new LinkedHashMap<>();
        String firstClassText = "";
        String multiclassText = "";
        List<ClassTableRow> tableRows = new ArrayList<>();
        List<ClassTableColumn> tableColumns = new ArrayList<>();

        List<FeatureBlock> classFeatureBlocks = new ArrayList<>();
        List<SubclassBlock> subclassBlocks = new ArrayList<>();

        String currentSection = "";
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("#### Таблица:")) {
                currentSection = "table";
                i = parseClassTable(lines, i + 1, tableColumns, tableRows);
                continue;
            }

            if (trimmed.startsWith("#### Получая первый уровень")) {
                currentSection = "acquisition";
                ParseAcquisitionResult par = parseAcquisition(lines, i + 1);
                firstClassText = par.firstClass;
                multiclassText = par.multiclass;
                i = par.nextIndex;
                continue;
            }

            if (trimmed.startsWith("#### Ключевые атрибуты")) {
                currentSection = "key_attributes";
                i = parseKeyAttributes(lines, i + 1, keyAttributes);
                continue;
            }

            if (trimmed.startsWith("#### Уровень")) {
                currentSection = "feature";
                FeatureBlock fb = new FeatureBlock();
                String afterHeader = trimmed.substring("#### Уровень".length()).trim();
                int colon = afterHeader.indexOf(':');
                fb.level = Integer.parseInt(afterHeader.substring(0, colon).trim());
                fb.name = afterHeader.substring(colon + 1).trim();
                StringBuilder desc = new StringBuilder();
                i++;
                while (i < lines.size()) {
                    String next = lines.get(i);
                    if (next.trim().startsWith("#### ") || next.trim().startsWith("### ")) break;
                    if (desc.length() > 0) desc.append("\n");
                    desc.append(next);
                    i++;
                }
                fb.description = desc.toString().trim();
                classFeatureBlocks.add(fb);
                continue;
            }

            if (trimmed.startsWith("### Подклассы")) {
                i = parseSubclasses(lines, i + 1, subclassBlocks);
                continue;
            }

            i++;
        }

        // Write class features
        for (FeatureBlock fb : classFeatureBlocks) {
            String id = classId + "_l" + fb.level + "_" + toSnakeCase(transliterateCyrillic(fb.name)).replaceAll("[^a-z0-9_]", "");
            String fullId = "phb2024:" + id;
            boolean isPlaceholder = fb.name.toLowerCase().contains("умение подкласса");
            boolean isSubclassChoice = fb.name.toLowerCase().contains("подкласс");
            String json = buildFeatureJson(id, fb.name, fb.description, fb.level, isPlaceholder, isSubclassChoice);
            Files.write(Paths.get(featuresDir, id + ".json"), json.getBytes(StandardCharsets.UTF_8));
            classFeatures.add(fullId);
        }

        // Write subclass features and subclass JSONs
        for (SubclassBlock sb : subclassBlocks) {
            String subId = classId + "_" + toSnakeCase(transliterateCyrillic(sb.name)).replaceAll("[^a-z0-9_]", "");
            String subFullId = "phb2024:" + subId;
            List<String> subFeatureIds = new ArrayList<>();
            for (FeatureBlock fb : sb.features) {
                String id = subId + "_l" + fb.level + "_" + toSnakeCase(transliterateCyrillic(fb.name)).replaceAll("[^a-z0-9_]", "");
                String json = buildFeatureJson(id, fb.name, fb.description, fb.level, false, false);
                Files.write(Paths.get(featuresDir, id + ".json"), json.getBytes(StandardCharsets.UTF_8));
                subFeatureIds.add("phb2024:" + id);
            }
            String subJson = buildSubclassJson(subId, sb.name, sb.description, "phb2024:" + classId, subFeatureIds);
            Files.write(Paths.get(subclassesDir, subId + ".json"), subJson.getBytes(StandardCharsets.UTF_8));
            subclassIds.add(subFullId);
        }

        // Write class JSON
        String classJson = buildWizardClassJson(classId, classNameEn, classNameRu, classDescription,
                keyAttributes, tableColumns, tableRows, classFeatures, subclassIds, firstClassText, multiclassText);
        Files.write(Paths.get(classesDir, classId + ".json"), classJson.getBytes(StandardCharsets.UTF_8));

        System.out.println("Generated class " + classId + " with " + classFeatures.size() + " features and " + subclassIds.size() + " subclasses.");
    }

    private static class FeatureBlock {
        String name = "";
        int level = 0;
        String description = "";
    }

    private static class SubclassBlock {
        String name = "";
        String description = "";
        List<FeatureBlock> features = new ArrayList<>();
    }

    private static int parseClassTable(List<String> lines, int start, List<ClassTableColumn> columns, List<ClassTableRow> rows) {
        // Predefined columns for wizard
        columns.add(new ClassTableColumn("proficiency_bonus", new LocalizedString("БМ", "Prof. Bonus")));
        columns.add(new ClassTableColumn("cantrips", new LocalizedString("Заговоры", "Cantrips")));
        columns.add(new ClassTableColumn("prepared", new LocalizedString("Подготовленные", "Prepared")));
        for (int lvl = 1; lvl <= 9; lvl++) {
            columns.add(new ClassTableColumn("slot" + lvl, new LocalizedString(String.valueOf(lvl), String.valueOf(lvl))));
        }
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || !Character.isDigit(line.charAt(0))) {
                if (line.startsWith("#### ")) break;
                i++;
                continue;
            }
            String[] parts = line.split("\\t+");
            if (parts.length < 13) {
                i++;
                continue;
            }
            try {
                int level = Integer.parseInt(parts[0].trim());
                Map<String, String> values = new LinkedHashMap<>();
                values.put("proficiency_bonus", parts[1].trim());
                values.put("cantrips", parts[2].trim());
                values.put("prepared", parts[3].trim());
                for (int lvl = 1; lvl <= 9; lvl++) {
                    values.put("slot" + lvl, parts[3 + lvl].trim());
                }
                rows.add(new ClassTableRow(level, values));
            } catch (NumberFormatException e) {
                // ignore malformed row
            }
            i++;
        }
        return i;
    }

    private static class ParseAcquisitionResult {
        String firstClass;
        String multiclass;
        int nextIndex;
        ParseAcquisitionResult(String firstClass, String multiclass, int nextIndex) {
            this.firstClass = firstClass;
            this.multiclass = multiclass;
            this.nextIndex = nextIndex;
        }
    }

    private static ParseAcquisitionResult parseAcquisition(List<String> lines, int start) {
        StringBuilder first = new StringBuilder();
        StringBuilder multi = new StringBuilder();
        StringBuilder current = first;
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith("#### ")) break;
            if (trimmed.toLowerCase().startsWith("как персонаж 1-го уровня")) {
                current = first;
                i++;
                continue;
            }
            if (trimmed.toLowerCase().startsWith("как мультиклассовый персонаж")) {
                current = multi;
                i++;
                continue;
            }
            if (trimmed.isEmpty()) {
                i++;
                continue;
            }
            if (current.length() > 0) current.append("\n");
            current.append(line.trim());
            i++;
        }
        return new ParseAcquisitionResult(first.toString().trim(), multi.toString().trim(), i);
    }

    private static int parseKeyAttributes(List<String> lines, int start, Map<String, String> map) {
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.startsWith("#### ") || line.startsWith("### ")) break;
            int dash = line.indexOf('—');
            if (dash < 0) dash = line.indexOf('-');
            if (dash > 0) {
                String label = line.substring(0, dash).trim();
                String value = line.substring(dash + 1).trim();
                map.put(label, value);
            }
            i++;
        }
        return i;
    }

    private static int parseSubclasses(List<String> lines, int start, List<SubclassBlock> subclasses) {
        int i = start;
        SubclassBlock current = null;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith("### ")) break;
            if (trimmed.startsWith("#### **")) {
                current = new SubclassBlock();
                String name = trimmed.replace("#### **", "").replace("**", "").trim();
                current.name = name;
                StringBuilder desc = new StringBuilder();
                i++;
                while (i < lines.size() && !lines.get(i).trim().startsWith("#### ")) {
                    if (desc.length() > 0) desc.append("\n");
                    desc.append(lines.get(i));
                    i++;
                }
                String description = desc.toString().trim();
                if (description.startsWith("**Описание:**")) {
                    description = description.substring("**Описание:**".length()).trim();
                }
                current.description = description;
                subclasses.add(current);
                continue;
            }
            if (trimmed.startsWith("#### Уровень")) {
                if (current == null) {
                    i++;
                    continue;
                }
                String afterHeader = trimmed.substring("#### Уровень".length()).trim();
                int colon = afterHeader.indexOf(':');
                FeatureBlock fb = new FeatureBlock();
                fb.level = Integer.parseInt(afterHeader.substring(0, colon).trim());
                fb.name = afterHeader.substring(colon + 1).trim();
                StringBuilder desc = new StringBuilder();
                i++;
                while (i < lines.size()) {
                    String next = lines.get(i);
                    if (next.trim().startsWith("#### ") || next.trim().startsWith("### ")) break;
                    if (desc.length() > 0) desc.append("\n");
                    desc.append(next);
                    i++;
                }
                fb.description = desc.toString().trim();
                current.features.add(fb);
                continue;
            }
            i++;
        }
        return i;
    }

    private static String buildFeatureJson(String id, String name, String description, int level, boolean isPlaceholder, boolean isSubclassChoice) {
        String desc = isPlaceholder ? "" : cleanMarkdownText(description);
        String cleanName = cleanMarkdownText(name);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"feature\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"\",\"ru\":\"").append(escape(cleanName)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"").append(escape(desc)).append("\"},");
        sb.append("\"level\":").append(level).append(",");
        sb.append("\"is_placeholder\":").append(isPlaceholder).append(",");
        sb.append("\"is_subclass_choice\":").append(isSubclassChoice);
        sb.append("}");
        return sb.toString();
    }

    private static String buildSubclassJson(String id, String name, String description, String classId, List<String> features) {
        String cleanName = cleanMarkdownText(name);
        String cleanDesc = cleanMarkdownText(description);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"subclass\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"\",\"ru\":\"").append(escape(cleanName)).append("\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"").append(escape(cleanDesc)).append("\"},");
        sb.append("\"source\":{\"book\":{\"en\":\"Player's Handbook (2024)\",\"ru\":\"Книга игрока (2024)\"},\"abbreviation\":\"PHB2024\"},");
        sb.append("\"class_id\":\"").append(classId).append("\",");
        sb.append("\"features\":[");
        for (int i = 0; i < features.size(); i++) {
            sb.append("\"").append(features.get(i)).append("\"");
            if (i < features.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildWizardStartingEquipment() {
        StringBuilder sb = new StringBuilder();
        sb.append("[{\"description\":{\"en\":\"Choose A or B\",\"ru\":\"Выберите А или Б:\"},\"count\":1,\"options\":[");
        sb.append("{\"description\":{\"en\":\"A\",\"ru\":\"А\"},\"options\":[");
        sb.append("{\"item_id\":\"phb2024:dagger\",\"quantity\":2},");
        sb.append("{\"item_id\":\"phb2024:quarterstaff\",\"quantity\":1},");
        sb.append("{\"item_id\":\"phb2024:robe\",\"quantity\":1},");
        sb.append("{\"item_id\":\"phb2024:book\",\"quantity\":1},");
        sb.append("{\"item_id\":\"phb2024:scholar_s_pack\",\"quantity\":1},");
        sb.append("{\"description\":{\"en\":\"5 gp\",\"ru\":\"5 ЗМ\"},\"quantity\":1}");
        sb.append("]},");
        sb.append("{\"description\":{\"en\":\"B\",\"ru\":\"Б\"},\"options\":[");
        sb.append("{\"description\":{\"en\":\"55 gp\",\"ru\":\"55 ЗМ\"},\"quantity\":1}");
        sb.append("]}]}]");
        return sb.toString();
    }

    private static String buildWizardClassJson(String id, String enName, String ruName, String description,
                                                Map<String, String> keyAttributes, List<ClassTableColumn> columns, List<ClassTableRow> rows,
                                                List<String> features, List<String> subclasses, String firstClass, String multiclass) {
        String cleanEnName = cleanMarkdownText(enName);
        String cleanRuName = cleanMarkdownText(ruName);
        String cleanDesc = cleanMarkdownText(description);
        String cleanFirst = cleanMarkdownText(firstClass);
        String cleanMulti = cleanMarkdownText(multiclass);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"type\":\"class\",");
        sb.append("\"format_version\":1,");
        sb.append("\"name\":{\"en\":\"").append(escape(cleanEnName)).append("\",\"ru\":\"").append(escape(cleanRuName)).append("\"},");
        sb.append("\"short_description\":{\"en\":\"\",\"ru\":\"\"},");
        sb.append("\"description\":{\"en\":\"\",\"ru\":\"").append(escape(cleanDesc)).append("\"},");
        sb.append("\"source\":{\"book\":{\"en\":\"Player's Handbook (2024)\",\"ru\":\"Книга игрока (2024)\"},\"abbreviation\":\"PHB2024\"},");
        sb.append("\"tags\":[\"arcane\",\"spellcaster\",\"full-caster\",\"intelligence\",\"prepared\"],");
        sb.append("\"hit_die\":6,");
        sb.append("\"primary_ability\":\"intelligence\",");
        sb.append("\"saving_throws\":[\"intelligence\",\"wisdom\"],");
        sb.append("\"skills\":{\"count\":2,\"from\":[\"arcana\",\"history\",\"insight\",\"investigation\",\"medicine\",\"religion\"]},");
        sb.append("\"starting_proficiencies\":{\"armor\":[],\"weapons\":[\"dagger\",\"dart\",\"sling\",\"quarterstaff\",\"light_crossbow\"],\"tools\":[],\"saving_throws\":[\"intelligence\",\"wisdom\"],\"skills\":[]},");
        sb.append("\"starting_equipment\":").append(buildWizardStartingEquipment()).append(",");
        sb.append("\"subclass_title\":{\"en\":\"Arcane Tradition\",\"ru\":\"Магическая традиция\"},");
        sb.append("\"subclass_level\":3,");
        sb.append("\"features\":[");
        for (int i = 0; i < features.size(); i++) {
            sb.append("\"").append(features.get(i)).append("\"");
            if (i < features.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"subclasses\":[");
        for (int i = 0; i < subclasses.size(); i++) {
            sb.append("\"").append(subclasses.get(i)).append("\"");
            if (i < subclasses.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"acquisition\":{\"first_class\":{\"en\":\"\",\"ru\":\"").append(escape(cleanFirst)).append("\"},\"multiclass\":{\"en\":\"\",\"ru\":\"").append(escape(cleanMulti)).append("\"}},");
        sb.append("\"key_attributes\":{");
        int idx = 0;
        for (Map.Entry<String, String> entry : keyAttributes.entrySet()) {
            sb.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
            if (idx < keyAttributes.size() - 1) sb.append(",");
            idx++;
        }
        sb.append("},");
        sb.append("\"class_table\":{");
        sb.append("\"columns\":[");
        for (int i = 0; i < columns.size(); i++) {
            ClassTableColumn col = columns.get(i);
            sb.append("{\"key\":\"").append(col.key).append("\",\"name\":{\"en\":\"").append(escape(col.name.en)).append("\",\"ru\":\"").append(escape(col.name.ru)).append("\"}}");
            if (i < columns.size() - 1) sb.append(",");
        }
        sb.append("],\"rows\":[");
        for (int i = 0; i < rows.size(); i++) {
            ClassTableRow row = rows.get(i);
            sb.append("{\"level\":").append(row.level).append(",\"values\":{");
            int v = 0;
            for (Map.Entry<String, String> e : row.values.entrySet()) {
                sb.append("\"").append(e.getKey()).append("\":\"").append(escape(e.getValue())).append("\"");
                if (v < row.values.size() - 1) sb.append(",");
                v++;
            }
            sb.append("}}");
            if (i < rows.size() - 1) sb.append(",");
        }
        sb.append("]},");
        sb.append("\"spellcasting\":{\"ability\":\"intelligence\",\"type\":\"prepared\"},");
        sb.append("\"multiclass_requirements\":{\"ability_scores\":{\"intelligence\":13}}");
        sb.append("}");
        return sb.toString();
    }

    private static class ClassTableColumn {
        String key;
        LocalizedString name;
        ClassTableColumn(String key, LocalizedString name) {
            this.key = key;
            this.name = name;
        }
    }

    private static class ClassTableRow {
        int level;
        Map<String, String> values;
        ClassTableRow(int level, Map<String, String> values) {
            this.level = level;
            this.values = values;
        }
    }

    private static class LocalizedString {
        String ru;
        String en;
        LocalizedString(String ru, String en) {
            this.ru = ru;
            this.en = en;
        }
    }
}

