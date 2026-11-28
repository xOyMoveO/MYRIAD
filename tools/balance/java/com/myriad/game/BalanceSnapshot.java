package com.myriad.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.myriad.character.rework.FirstGroupSkills;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class BalanceSnapshot {
    private static final int FORMAT_VERSION = 1;
    private static final String LANGUAGE_RESOURCE = "assets/myriad/lang/zh_cn.json";
    private static final Pattern LEGACY_RANK_GROWTH = Pattern.compile("\\+([0-9]+(?:\\.[0-9]+)?)\\(r-1\\)\\s*\\+\\s*[0-9.]+S");

    private BalanceSnapshot() {}

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        if (args.length != 4) throw new IllegalArgumentException("Expected source root, output directory, commit and dirty flag");
        Path root = Path.of(args[0]);
        Path output = Path.of(args[1]);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var roles = RosterSnapshot.roles();
        var rules = new JsonObject();
        rules.add("roles", roles);
        rules.add("skillRules", RosterSnapshot.commonSkillRules());
        rules.add("battlefield", BattlefieldSnapshot.export(root));

        var snapshot = new JsonObject();
        snapshot.addProperty("formatVersion", FORMAT_VERSION);
        snapshot.addProperty("sourceSha", args[2]);
        snapshot.addProperty("sourceDirty", Boolean.parseBoolean(args[3]));
        snapshot.addProperty("rulesSha256", sha256(SnapshotJson.canonical(rules)));
        snapshot.add("rules", rules);
        String serialized = SnapshotJson.pretty(snapshot);

        var report = coverage(rules);
        report.addProperty("formatVersion", FORMAT_VERSION);
        report.addProperty("sourceSha", args[2]);
        report.addProperty("sourceDirty", Boolean.parseBoolean(args[3]));
        report.addProperty("rulesSha256", snapshot.get("rulesSha256").getAsString());
        report.add("presentationReminders", checkKehengDescriptions());

        Files.createDirectories(output);
        Files.writeString(output.resolve("balance-snapshot.json"), serialized);
        Files.writeString(output.resolve("balance-coverage.json"), SnapshotJson.pretty(report));
        Files.writeString(output.resolve("balance-coverage.md"), markdown(report));
        Files.writeString(output.resolve("myriad-balance-snapshot.sha256"), sha256(serialized) + "\n");

        var counts = report.getAsJsonObject("roster");
        System.out.printf("Balance snapshot: %d roles, %d candidates, %s%n", counts.get("roles").getAsInt(),
                counts.get("uniqueCandidates").getAsInt(), snapshot.get("rulesSha256").getAsString());
        for (var entry : report.getAsJsonArray("presentationReminders")) {
            System.out.println("Balance reminder: " + entry.getAsJsonObject().get("message").getAsString());
        }
    }

    private static String sha256(String text) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonObject coverage(JsonObject rules) {
        var roster = new JsonObject();
        var ids = new HashSet<String>();
        var resources = new TreeSet<String>();
        int candidates = 0;
        int combinations = 0;
        int damage = 0;
        int heal = 0;
        int shield = 0;
        int control = 0;
        var perRole = new JsonArray();
        for (var rawRole : rules.getAsJsonArray("roles")) {
            var role = rawRole.getAsJsonObject();
            var skills = role.getAsJsonArray("skills");
            var roleRow = new JsonObject();
            roleRow.addProperty("id", role.get("id").getAsString());
            roleRow.addProperty("candidates", skills.size());
            int roleCombinations = role.getAsJsonObject("legalBranches").getAsJsonArray("terminalCombinations").size();
            roleRow.addProperty("terminalCombinations", roleCombinations);
            combinations += roleCombinations;
            perRole.add(roleRow);
            for (var rawSkill : skills) {
                var skill = rawSkill.getAsJsonObject();
                var id = skill.get("id").getAsString();
                candidates++;
                ids.add(id);
                resources.add(FirstGroupSkills.definitionResource(id));
                if (hasDefinedEffect(skill, "damage")) damage++;
                if (hasDefinedEffect(skill, "heal")) heal++;
                if (hasDefinedEffect(skill, "shield")) shield++;
                if (hasDefinedEffect(skill, "control_duration")) control++;
            }
        }
        roster.addProperty("roles", perRole.size());
        roster.addProperty("candidates", candidates);
        roster.addProperty("uniqueCandidates", ids.size());
        roster.addProperty("terminalCombinations", combinations);
        roster.add("roleResources", SnapshotJson.json(resources));
        roster.add("perRole", perRole);
        var covered = new JsonObject();
        covered.addProperty("damage", damage);
        covered.addProperty("heal", heal);
        covered.addProperty("shield", shield);
        covered.addProperty("controlDuration", control);
        roster.add("candidatesWithDefinedEffect", covered);
        var counts = new LinkedHashMap<String, Integer>();
        var gaps = new ArrayList<Map<String, String>>();
        countFields(rules, "rules", counts, gaps);
        var report = new JsonObject();
        report.add("roster", roster);
        report.add("fieldCounts", SnapshotJson.json(counts));
        report.add("missingInputs", SnapshotJson.json(gaps));
        return report;
    }

    private static boolean hasDefinedEffect(JsonObject skill, String type) {
        return skill.getAsJsonArray("effects").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(effect -> effect.get("type").getAsString().equals(type))
                .map(effect -> effect.getAsJsonObject("rule").get("status").getAsString())
                .anyMatch(status -> status.equals("defined") || status.equals("zero"));
    }

    private static void countFields(JsonElement node, String path, Map<String, Integer> counts, List<Map<String, String>> gaps) {
        if (node instanceof JsonObject object) {
            if (object.has("status") && object.has("unit")) {
                String status = object.get("status").getAsString();
                counts.merge(status, 1, Integer::sum);
                if (status.equals("missing_input")) {
                    gaps.add(Map.of("path", path, "source", object.get("source").getAsString(),
                            "reason", object.get("reason").getAsString()));
                }
            }
            object.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    countFields(entry.getValue(), path + "." + entry.getKey(), counts, gaps));
        } else if (node instanceof JsonArray array) {
            for (int index = 0; index < array.size(); index++) {
                JsonElement item = array.get(index);
                String id = item instanceof JsonObject object && object.has("id")
                        ? object.get("id").getAsString() : Integer.toString(index);
                countFields(item, path + "[" + id + "]", counts, gaps);
            }
        }
    }

    private static JsonArray checkKehengDescriptions() {
        var reminders = new JsonArray();
        try (var stream = BalanceSnapshot.class.getClassLoader().getResourceAsStream(LANGUAGE_RESOURCE)) {
            if (stream == null) {
                languageUnavailable(reminders);
                return reminders;
            }
            var language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : List.of("keheng_kehen", "keheng_chijie")) {
                var skill = FirstGroupSkills.byId(id);
                if (skill == null || skill.damageFormulas().isEmpty()) {
                    reminders.add(SnapshotJson.json(Map.of("level", 3, "skill", id, "language", "zh_cn",
                            "message", id + " 暂无对应的注册伤害段，无法比较长说明")));
                    continue;
                }
                String key = skill.nameKey() + ".description";
                float expected = skill.damageFormulas().getFirst().rankGrowth();
                var text = language.get(key);
                var match = LEGACY_RANK_GROWTH.matcher(text != null && text.isJsonPrimitive() ? text.getAsString() : "");
                if (match.find()) {
                    float displayed = Float.parseFloat(match.group(1));
                    if (Float.compare(expected, displayed) == 0) continue;
                    reminders.add(SnapshotJson.json(Map.of(
                            "level", 1, "skill", id, "language", "zh_cn", "field", "damage.rankGrowth",
                            "expected", expected, "displayed", displayed,
                            "source", "src/main/resources/" + LANGUAGE_RESOURCE + "#" + key,
                            "message", id + " zh_cn damage.rankGrowth，结算 " + expected + "，图鉴长说明 " + displayed)));
                } else {
                    reminders.add(SnapshotJson.json(Map.of(
                            "level", 3, "skill", id, "language", "zh_cn", "field", "damage.rankGrowth",
                            "source", "src/main/resources/" + LANGUAGE_RESOURCE + "#" + key,
                            "message", id + " zh_cn 长说明的旧式阶数公式未找到，需确认新的字段绑定")));
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            languageUnavailable(reminders);
        }
        return reminders;
    }

    private static void languageUnavailable(JsonArray reminders) {
        reminders.add(SnapshotJson.json(Map.of("level", 3, "language", "zh_cn",
                "source", "src/main/resources/" + LANGUAGE_RESOURCE, "message", "中文语言资源缺失或无法读取，图鉴长说明尚未核对")));
    }

    private static String markdown(JsonObject report) {
        var roster = report.getAsJsonObject("roster");
        var output = new StringBuilder("# 平衡快照覆盖\n\n")
                .append("- 源码提交 `").append(report.get("sourceSha").getAsString()).append("`\n")
                .append("- 工作区含未提交文件 ").append(report.get("sourceDirty").getAsBoolean() ? "是" : "否").append("\n")
                .append("- 规则摘要 `").append(report.get("rulesSha256").getAsString()).append("`\n")
                .append("- 已登记 ").append(roster.get("roles").getAsInt()).append(" 个角色、")
                .append(roster.get("uniqueCandidates").getAsInt()).append(" 个唯一候选和 ")
                .append(roster.get("terminalCombinations").getAsInt()).append(" 套合法终局分支组合\n\n")
                .append("| 已导出字段状态 | 数量 |\n| --- | ---: |\n");
        report.getAsJsonObject("fieldCounts").entrySet().forEach(entry ->
                output.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().getAsInt()).append(" |\n"));
        output.append("\n| 具备对应字段的候选 | 数量 |\n| --- | ---: |\n");
        roster.getAsJsonObject("candidatesWithDefinedEffect").entrySet().forEach(entry ->
                output.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().getAsInt()).append(" |\n"));
        output.append("\n逐字段原因、来源与受影响路径见同目录的 `balance-coverage.json`。公式是定义读数，实战覆盖需另跑对应场景。\n")
                .append("\n## 文案提醒\n\n");
        var reminders = report.getAsJsonArray("presentationReminders");
        if (reminders.isEmpty()) output.append("本批检查的两条图鉴长说明未发现差异。\n");
        for (var entry : reminders) output.append("- ").append(entry.getAsJsonObject().get("message").getAsString()).append("\n");
        return output.toString();
    }
}
