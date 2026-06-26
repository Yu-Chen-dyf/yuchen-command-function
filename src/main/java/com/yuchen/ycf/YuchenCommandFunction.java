package com.yuchen.ycf;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class YuchenCommandFunction extends JavaPlugin implements CommandExecutor {

    private Map<String, CommandEntry> commandMap;
    private int autoIdCounter;

    private static class Variable {
        String name;
        String type;

        Variable(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private static class CommandEntry {
        String id;
        String rawTemplate;
        List<Variable> variables;
        List<String> logicInfo;

        CommandEntry(String id, String rawTemplate, List<Variable> variables) {
            this.id = id;
            this.rawTemplate = rawTemplate;
            this.variables = variables;
            this.logicInfo = new ArrayList<>();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCommandsFromConfig();
        Objects.requireNonNull(getCommand("ycf")).setExecutor(this);
        getLogger().info("yuchen的命令函数 已加载！");
    }

    @Override
    public void onDisable() {
        saveCommandsToConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "add":
                return handleAdd(sender, args);
            case "use":
                return handleUse(sender, args);
            case "del":
                return handleDel(sender, args);
            case "list":
            case "title":
                return handleList(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ycf add <命令模板> [id]");
            sender.sendMessage(ChatColor.GRAY + "模板中用 <变量名-类型> 定义变量，例如: <target-user> <amount-int>");
            return true;
        }

        String fullCommand = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String possibleId = null;
        String[] parts = fullCommand.split(" ");
        String lastPart = parts[parts.length - 1];
        if (!lastPart.contains("<") && !lastPart.contains(">") && lastPart.matches("[a-zA-Z0-9_-]+")) {
            possibleId = lastPart;
            fullCommand = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
        }

        List<Variable> variables = new ArrayList<>();
        java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile("<([a-zA-Z0-9_-]+)-([a-zA-Z]+)>");
        java.util.regex.Matcher matcher = varPattern.matcher(fullCommand);
        Set<String> varNames = new HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String type = matcher.group(2).toLowerCase();
            if (!isValidType(type)) {
                sender.sendMessage(ChatColor.RED + "未知的变量类型: " + type);
                return true;
            }
            if (!varNames.add(name)) {
                sender.sendMessage(ChatColor.RED + "变量名重复: " + name);
                return true;
            }
            variables.add(new Variable(name, type));
        }

        String id;
        if (possibleId != null && !possibleId.isEmpty()) {
            id = possibleId;
            if (commandMap.containsKey(id)) {
                sender.sendMessage(ChatColor.RED + "ID 已存在: " + id);
                return true;
            }
        } else {
            id = String.valueOf(++autoIdCounter);
            while (commandMap.containsKey(id)) {
                id = String.valueOf(++autoIdCounter);
            }
        }

        CommandEntry entry = new CommandEntry(id, fullCommand, variables);
        commandMap.put(id, entry);
        saveCommandsToConfig();

        sender.sendMessage(ChatColor.GREEN + "已添加命令，ID: " + ChatColor.YELLOW + id);
        sender.sendMessage(ChatColor.GRAY + "模板: " + fullCommand);
        if (!variables.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "变量: " +
                    variables.stream().map(v -> "<" + v.name + "-" + v.type + ">").collect(java.util.stream.Collectors.joining(" ")));
        }
        return true;
    }

    private boolean handleUse(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ycf use <id> [变量值...]");
            return true;
        }

        String id = args[1];
        CommandEntry entry = commandMap.get(id);
        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "未找到 ID 为 " + id + " 的命令。");
            return true;
        }

        String[] userParams = Arrays.copyOfRange(args, 2, args.length);
        List<Variable> vars = entry.variables;

        int expectedCount = 0;
        for (Variable v : vars) {
            if (v.type.equals("pos")) expectedCount += 3;
            else expectedCount += 1;
        }
        if (userParams.length != expectedCount) {
            sender.sendMessage(ChatColor.RED + "参数数量错误。需要 " + expectedCount + " 个值，但提供了 " + userParams.length + " 个。");
            sender.sendMessage(ChatColor.GRAY + "变量定义: " +
                    vars.stream().map(v -> "<" + v.name + "-" + v.type + ">").collect(java.util.stream.Collectors.joining(" ")));
            return true;
        }

        Map<String, String> replacements = new LinkedHashMap<>();
        int paramIndex = 0;
        try {
            for (Variable v : vars) {
                switch (v.type) {
                    case "user":
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                        break;
                    case "text":
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                        break;
                    case "int":
                        Integer.parseInt(userParams[paramIndex]);
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                        break;
                    case "float":
                        Float.parseFloat(userParams[paramIndex]);
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                        break;
                    case "item":
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                        break;
                    case "world":
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                        break;
                    case "bool":
                        String boolVal = userParams[paramIndex];
                        if (!boolVal.equalsIgnoreCase("true") && !boolVal.equalsIgnoreCase("false")) {
                            throw new IllegalArgumentException("布尔值必须为 true 或 false");
                        }
                        replacements.put(v.name, boolVal);
                        paramIndex++;
                        break;
                    case "pos":
                        String x = userParams[paramIndex++];
                        String y = userParams[paramIndex++];
                        String z = userParams[paramIndex++];
                        try {
                            Double.parseDouble(x); Double.parseDouble(y); Double.parseDouble(z);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("坐标必须为数字");
                        }
                        replacements.put(v.name, x + " " + y + " " + z);
                        break;
                    default:
                        replacements.put(v.name, userParams[paramIndex]);
                        paramIndex++;
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "数字格式错误。");
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }

        String commandToRun = entry.rawTemplate;
        for (Map.Entry<String, String> rep : replacements.entrySet()) {
            commandToRun = commandToRun.replaceAll(
                    "<" + java.util.regex.Pattern.quote(rep.getKey()) + "-[a-zA-Z]+>",
                    rep.getValue()
            );
        }

        if (containsLogicKeywords(entry)) {
            sender.sendMessage(ChatColor.GOLD + "[逻辑词] 当前版本暂不执行逻辑结构，改为发送原始替换后命令：");
        }

        boolean success = Bukkit.dispatchCommand(sender, commandToRun);
        if (!success) {
            sender.sendMessage(ChatColor.RED + "命令执行失败，可能是权限不足或命令语法错误。");
        } else {
            sender.sendMessage(ChatColor.GREEN + "命令已执行。");
        }
        return true;
    }

    private boolean handleDel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /ycf del <id>");
            return true;
        }
        String id = args[1];
        if (commandMap.remove(id) != null) {
            saveCommandsToConfig();
            sender.sendMessage(ChatColor.GREEN + "已删除命令 ID: " + id);
        } else {
            sender.sendMessage(ChatColor.RED + "未找到 ID 为 " + id + " 的命令。");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (commandMap.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "当前没有保存任何命令。");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "=== 已保存的命令列表 ===");
        for (CommandEntry entry : commandMap.values()) {
            sender.sendMessage(ChatColor.YELLOW + "[" + entry.id + "] " + ChatColor.WHITE + entry.rawTemplate);
            if (!entry.variables.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "    变量: " +
                        entry.variables.stream().map(v -> "<" + v.name + "-" + v.type + ">").collect(java.util.stream.Collectors.joining(" ")));
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "--- yuchen的命令函数 帮助 ---");
        sender.sendMessage(ChatColor.WHITE + "/ycf add <命令模板> [id] " + ChatColor.GRAY + "- 添加命令（变量: <name-type>）");
        sender.sendMessage(ChatColor.WHITE + "/ycf use <id> [参数...] " + ChatColor.GRAY + "- 执行命令");
        sender.sendMessage(ChatColor.WHITE + "/ycf del <id> " + ChatColor.GRAY + "- 删除命令");
        sender.sendMessage(ChatColor.WHITE + "/ycf list " + ChatColor.GRAY + "- 列出所有命令");
        sender.sendMessage(ChatColor.GRAY + "变量类型: user, text, int, float, pos, item, world, bool");
    }

    private boolean isValidType(String type) {
        return Arrays.asList("user", "text", "int", "float", "pos", "item", "world", "bool").contains(type);
    }

    private boolean containsLogicKeywords(CommandEntry entry) {
        String t = entry.rawTemplate.toLowerCase();
        return t.contains(" if ") || t.contains(" for ") || t.contains(" loop ") ||
                t.contains(" and ") || t.contains(" or ") || t.contains(" xor ") ||
                t.contains(" to ") || t.contains(" when ") || t.contains(" not ");
    }

    private void loadCommandsFromConfig() {
        commandMap = new LinkedHashMap<>();
        if (!getConfig().contains("commands")) {
            autoIdCounter = 0;
            return;
        }

        autoIdCounter = getConfig().getInt("auto_id_counter", 0);
        List<Map<?, ?>> list = getConfig().getMapList("commands");
        for (Map<?, ?> map : list) {
            String id = map.get("id").toString();
            String raw = map.get("template").toString();
            List<Variable> vars = new ArrayList<>();
            if (map.containsKey("variables")) {
                List<Map<?, ?>> varList = (List<Map<?, ?>>) map.get("variables");
                for (Map<?, ?> vmap : varList) {
                    vars.add(new Variable(vmap.get("name").toString(), vmap.get("type").toString()));
                }
            }
            commandMap.put(id, new CommandEntry(id, raw, vars));
        }
    }

    private void saveCommandsToConfig() {
        getConfig().set("auto_id_counter", autoIdCounter);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CommandEntry entry : commandMap.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", entry.id);
            map.put("template", entry.rawTemplate);
            List<Map<String, String>> varList = new ArrayList<>();
            for (Variable v : entry.variables) {
                Map<String, String> vmap = new HashMap<>();
                vmap.put("name", v.name);
                vmap.put("type", v.type);
                varList.add(vmap);
            }
            map.put("variables", varList);
            list.add(map);
        }
        getConfig().set("commands", list);
        try {
            getConfig().save(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            getLogger().warning("无法保存配置文件: " + e.getMessage());
        }
    }
                                   }
