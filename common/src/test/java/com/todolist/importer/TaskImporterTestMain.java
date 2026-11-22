package com.todolist.importer;

import com.todolist.bootstrap.CommandTestSupport;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * TaskImporterTestMain 覆盖任务导入格式 v1 的解析、严格校验、错误聚合与任务转换。
 */
public final class TaskImporterTestMain {

    /**
     * 工具类不需要实例化。
     */
    private TaskImporterTestMain() {
    }

    /**
     * 执行任务导入解析测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) throws Exception {
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldImportFullFeaturedDocument", TaskImporterTestMain::shouldImportFullFeaturedDocument);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldApplyDefaultsForOptionalFields", TaskImporterTestMain::shouldApplyDefaultsForOptionalFields);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldLinkSubtasksByAliasWithSortOrder", TaskImporterTestMain::shouldLinkSubtasksByAliasWithSortOrder);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldAllowForwardParentReference", TaskImporterTestMain::shouldAllowForwardParentReference);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldNormalizeTags", TaskImporterTestMain::shouldNormalizeTags);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldParseFromFile", TaskImporterTestMain::shouldParseFromFile);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectUnknownParentAlias", TaskImporterTestMain::shouldRejectUnknownParentAlias);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectDuplicateAlias", TaskImporterTestMain::shouldRejectDuplicateAlias);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectUnsupportedFormatVersion", TaskImporterTestMain::shouldRejectUnsupportedFormatVersion);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectMissingFormatVersion", TaskImporterTestMain::shouldRejectMissingFormatVersion);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectEmptyTaskList", TaskImporterTestMain::shouldRejectEmptyTaskList);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectTooManyTasks", TaskImporterTestMain::shouldRejectTooManyTasks);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectBlankTitle", TaskImporterTestMain::shouldRejectBlankTitle);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectInvalidPriority", TaskImporterTestMain::shouldRejectInvalidPriority);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectInvalidTrigger", TaskImporterTestMain::shouldRejectInvalidTrigger);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldAggregateAndTruncateErrors", TaskImporterTestMain::shouldAggregateAndTruncateErrors);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectMalformedJson", TaskImporterTestMain::shouldRejectMalformedJson);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectMissingFile", TaskImporterTestMain::shouldRejectMissingFile);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectNullTaskEntry", TaskImporterTestMain::shouldRejectNullTaskEntry);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectSelfParentReference", TaskImporterTestMain::shouldRejectSelfParentReference);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectParentAliasCycle", TaskImporterTestMain::shouldRejectParentAliasCycle);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectThreeNodeParentCycle", TaskImporterTestMain::shouldRejectThreeNodeParentCycle);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldAcceptParentChainWithoutCycle", TaskImporterTestMain::shouldAcceptParentChainWithoutCycle);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectOverLengthTitle", TaskImporterTestMain::shouldRejectOverLengthTitle);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldAcceptTitleAtLengthLimit", TaskImporterTestMain::shouldAcceptTitleAtLengthLimit);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectOverLengthDescription", TaskImporterTestMain::shouldRejectOverLengthDescription);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectOverLengthTag", TaskImporterTestMain::shouldRejectOverLengthTag);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectOverLengthTriggerTarget", TaskImporterTestMain::shouldRejectOverLengthTriggerTarget);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldStripUtf8Bom", TaskImporterTestMain::shouldStripUtf8Bom);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectDeeplyNestedJson", TaskImporterTestMain::shouldRejectDeeplyNestedJson);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectWrongTypeFields", TaskImporterTestMain::shouldRejectWrongTypeFields);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectTopLevelArray", TaskImporterTestMain::shouldRejectTopLevelArray);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldAcceptExactlyMaxTasks", TaskImporterTestMain::shouldAcceptExactlyMaxTasks);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldExcerptLongValuesInErrors", TaskImporterTestMain::shouldExcerptLongValuesInErrors);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectBatchTextBudget", TaskImporterTestMain::shouldRejectBatchTextBudget);
        CommandTestSupport.runTestCase("TaskImporterTestMain.shouldRejectOversizeImportFile", TaskImporterTestMain::shouldRejectOversizeImportFile);
    }

    /**
     * 验证完整字段文档的导入：标题、描述、优先级、标签、截止时间、触发器与别名父子挂接全部生效。
     */
    private static void shouldImportFullFeaturedDocument() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    {
                      "id": "gear",
                      "title": "准备下界装备",
                      "description": "出发前备齐",
                      "priority": "high",
                      "tags": ["探险", "装备"],
                      "dueDate": 1735689600000,
                      "trigger": {
                        "type": "ITEM_COLLECT",
                        "target": "minecraft:iron_ingot",
                        "targetCount": 10
                      }
                    },
                    {
                      "title": "收集黑曜石",
                      "parentId": "gear",
                      "trigger": {
                        "type": "ITEM_COLLECT",
                        "target": "minecraft:obsidian",
                        "targetCount": 14
                      }
                    }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, new TaskImporter.ImportContext("creator-1", "project-9"));

        GuiTestSupport.assertTrue(result.isSuccess(), "完整文档导入应成功");
        GuiTestSupport.assertEquals(2, result.tasks().size(), "应导入两条任务");
        Task parent = result.tasks().get(0);
        GuiTestSupport.assertEquals("准备下界装备", parent.getTitle(), "父任务标题应保留");
        GuiTestSupport.assertEquals("出发前备齐", parent.getDescription(), "描述应保留");
        GuiTestSupport.assertEquals(Task.Priority.HIGH, parent.getPriority(), "优先级大小写不敏感解析");
        GuiTestSupport.assertEquals(2, parent.getTags().size(), "标签应全部导入");
        GuiTestSupport.assertEquals(Long.valueOf(1735689600000L), parent.getDueDate(), "截止时间应保留");
        GuiTestSupport.assertTrue(parent.hasTrigger(), "父任务应带触发器");
        GuiTestSupport.assertEquals(TaskTrigger.Type.ITEM_COLLECT, parent.getTrigger().getType(), "触发类型应保留");
        GuiTestSupport.assertEquals("minecraft:iron_ingot", parent.getTrigger().getTarget(), "触发目标应保留");
        GuiTestSupport.assertEquals(10, parent.getTrigger().getTargetCount(), "触发数量应保留");
        GuiTestSupport.assertEquals(0, parent.getTrigger().getProgress(), "导入触发器进度必须恒为 0");
        GuiTestSupport.assertEquals("creator-1", parent.getCreatorUuid(), "创建者应来自导入上下文");
        GuiTestSupport.assertEquals("project-9", parent.getProjectId(), "项目归属应来自导入上下文");
        GuiTestSupport.assertEquals(Task.Scope.PERSONAL, parent.getScope(), "导入任务必须为个人作用域");
        GuiTestSupport.assertTrue(parent.isTopLevelTask(), "无 parentId 的任务应为父任务");

        Task child = result.tasks().get(1);
        GuiTestSupport.assertTrue(child.isSubtask(), "带 parentId 的任务应为子任务");
        GuiTestSupport.assertEquals(parent.getId(), child.getParentTaskId(), "子任务应挂接到别名对应父任务的真实 ID");
        GuiTestSupport.assertEquals(0L, child.getSubtaskSortOrder(), "首个子任务排序号应为 0");
    }

    /**
     * 验证可省略字段的默认值：优先级 MEDIUM、描述空串、无截止时间、无触发器、上下文缺省不落项目。
     */
    private static void shouldApplyDefaultsForOptionalFields() {
        String json = """
                { "formatVersion": 1, "tasks": [ { "title": "普通任务" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, new TaskImporter.ImportContext("creator-2", null));

        GuiTestSupport.assertTrue(result.isSuccess(), "仅必填字段的文档导入应成功");
        Task task = result.tasks().get(0);
        GuiTestSupport.assertEquals(Task.Priority.MEDIUM, task.getPriority(), "缺省优先级应为 MEDIUM");
        GuiTestSupport.assertEquals("", task.getDescription(), "缺省描述应为空串");
        GuiTestSupport.assertNull(task.getDueDate(), "缺省截止时间应为 null");
        GuiTestSupport.assertFalse(task.hasTrigger(), "缺省触发器应为空");
        GuiTestSupport.assertEquals("creator-2", task.getCreatorUuid(), "创建者应来自导入上下文");
        GuiTestSupport.assertTrue(task.isProjectUnassigned(), "上下文无项目时应落到未分组");
    }

    /**
     * 验证同一父任务下多个子任务按文档出现顺序分配递增排序号。
     */
    private static void shouldLinkSubtasksByAliasWithSortOrder() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "id": "root", "title": "根任务" },
                    { "title": "子任务甲", "parentId": "root" },
                    { "title": "子任务乙", "parentId": "root" },
                    { "title": "独立任务" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertTrue(result.isSuccess(), "多子任务文档导入应成功");
        Task root = result.tasks().get(0);
        Task first = result.tasks().get(1);
        Task second = result.tasks().get(2);
        Task standalone = result.tasks().get(3);
        GuiTestSupport.assertEquals(0L, first.getSubtaskSortOrder(), "第一个子任务排序号应为 0");
        GuiTestSupport.assertEquals(1L, second.getSubtaskSortOrder(), "第二个子任务排序号应为 1");
        GuiTestSupport.assertEquals(root.getId(), first.getParentTaskId(), "子任务甲应挂到根任务");
        GuiTestSupport.assertEquals(root.getId(), second.getParentTaskId(), "子任务乙应挂到根任务");
        GuiTestSupport.assertTrue(standalone.isTopLevelTask(), "无别名引用的任务应为父任务");
    }

    /**
     * 验证 parentId 允许引用文档后文才声明的别名（前向引用）。
     */
    private static void shouldAllowForwardParentReference() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "title": "子任务", "parentId": "parent" },
                    { "id": "parent", "title": "父任务" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertTrue(result.isSuccess(), "前向引用别名的文档导入应成功");
        GuiTestSupport.assertEquals(result.tasks().get(1).getId(),
                result.tasks().get(0).getParentTaskId(), "前向引用应正确挂接到父任务");
    }

    /**
     * 验证标签导入时去空白、去空串并去重且保持出现顺序。
     */
    private static void shouldNormalizeTags() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "title": "任务", "tags": [" 材料 ", "", "材料", "日常"] }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertTrue(result.isSuccess(), "含冗余标签的文档导入应成功");
        List<String> tags = List.copyOf(result.tasks().get(0).getTags());
        GuiTestSupport.assertEquals(2, tags.size(), "标签应去空白去重");
        GuiTestSupport.assertEquals("材料", tags.get(0), "标签顺序应保持首次出现");
        GuiTestSupport.assertEquals("日常", tags.get(1), "标签顺序应保持首次出现");
    }

    /**
     * 验证从文件路径导入与 UTF-8 中文内容的正确读取。
     */
    private static void shouldParseFromFile() throws Exception {
        Path file = Files.createTempFile("todolist-import", ".json");
        Files.writeString(file, "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"中文任务标题\" } ] }",
                java.nio.charset.StandardCharsets.UTF_8);
        try {
            TaskImporter.Result result = TaskImporter.parse(file, null);
            GuiTestSupport.assertTrue(result.isSuccess(), "文件导入应成功");
            GuiTestSupport.assertEquals("中文任务标题", result.tasks().get(0).getTitle(), "UTF-8 中文标题应正确读取");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * 验证 parentId 引用不存在的别名时整批拒绝。
     */
    private static void shouldRejectUnknownParentAlias() {
        String json = """
                { "formatVersion": 1, "tasks": [ { "title": "任务", "parentId": "ghost" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "未知别名引用应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("unknown alias 'ghost'"), "错误应定位到未知别名");
    }

    /**
     * 验证重复别名时整批拒绝并指出冲突位置。
     */
    private static void shouldRejectDuplicateAlias() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "id": "dup", "title": "任务一" },
                    { "id": "dup", "title": "任务二" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "重复别名应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("duplicate alias 'dup'"), "错误应指出重复别名");
    }

    /**
     * 验证不支持的格式版本号被拒绝。
     */
    private static void shouldRejectUnsupportedFormatVersion() {
        String json = """
                { "formatVersion": 2, "tasks": [ { "title": "任务" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "不支持的版本应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, result.documentErrorCode(), "版本错误应归类为格式错误");
    }

    /**
     * 验证缺失格式版本号被拒绝。
     */
    private static void shouldRejectMissingFormatVersion() {
        String json = """
                { "tasks": [ { "title": "任务" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "缺失版本应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, result.documentErrorCode(), "缺失版本应归类为格式错误");
    }

    /**
     * 验证空任务列表被拒绝。
     */
    private static void shouldRejectEmptyTaskList() {
        String json = """
                { "formatVersion": 1, "tasks": [] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "空任务列表应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_EMPTY, result.documentErrorCode(), "空列表应归类为 empty");
    }

    /**
     * 验证超过单次上限的任务数被拒绝。
     */
    private static void shouldRejectTooManyTasks() {
        StringBuilder builder = new StringBuilder("{ \"formatVersion\": 1, \"tasks\": [");
        for (int index = 0; index <= TaskImporter.MAX_TASKS; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{ \"title\": \"任务").append(index).append("\" }");
        }
        builder.append("]}");
        TaskImporter.Result result = TaskImporter.importTasks(builder.toString(), null);

        GuiTestSupport.assertFalse(result.isSuccess(), "超上限任务数应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_TOO_MANY, result.documentErrorCode(), "超上限应归类为 too_many");
    }

    /**
     * 验证空白标题被拒绝。
     */
    private static void shouldRejectBlankTitle() {
        String json = """
                { "formatVersion": 1, "tasks": [ { "title": "   " } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "空白标题应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("title"), "错误应定位到标题字段");
    }

    /**
     * 验证非法优先级被拒绝。
     */
    private static void shouldRejectInvalidPriority() {
        String json = """
                { "formatVersion": 1, "tasks": [ { "title": "任务", "priority": "URGENT" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "非法优先级应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("priority"), "错误应定位到优先级字段");
    }

    /**
     * 验证触发器的类型、目标与数量校验：非法类型、空白目标、越界数量分别报错。
     */
    private static void shouldRejectInvalidTrigger() {
        String badType = """
                { "formatVersion": 1, "tasks": [ { "title": "任务", "trigger": { "type": "MINE_BLOCK", "target": "minecraft:stone", "targetCount": 1 } } ] }
                """;
        TaskImporter.Result typeResult = TaskImporter.importTasks(badType, null);
        GuiTestSupport.assertFalse(typeResult.isSuccess(), "非法触发类型应失败");
        GuiTestSupport.assertTrue(typeResult.errors().get(0).contains("trigger.type"), "错误应定位到触发类型字段");

        String badTarget = """
                { "formatVersion": 1, "tasks": [ { "title": "任务", "trigger": { "type": "KILL_ENTITY", "target": "  ", "targetCount": 1 } } ] }
                """;
        TaskImporter.Result targetResult = TaskImporter.importTasks(badTarget, null);
        GuiTestSupport.assertFalse(targetResult.isSuccess(), "空白触发目标应失败");
        GuiTestSupport.assertTrue(targetResult.errors().get(0).contains("trigger.target"), "错误应定位到触发目标字段");

        String badCount = """
                { "formatVersion": 1, "tasks": [ { "title": "任务", "trigger": { "type": "KILL_ENTITY", "target": "minecraft:zombie", "targetCount": 0 } } ] }
                """;
        TaskImporter.Result countResult = TaskImporter.importTasks(badCount, null);
        GuiTestSupport.assertFalse(countResult.isSuccess(), "越界触发数量应失败");
        GuiTestSupport.assertTrue(countResult.errors().get(0).contains("trigger.targetCount"), "错误应定位到触发数量字段");
    }

    /**
     * 验证多条错误全部收集、超上限截断并附总数提示。
     */
    private static void shouldAggregateAndTruncateErrors() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "title": "" },
                    { "title": "", "priority": "BAD" },
                    { "title": "", "priority": "BAD" },
                    { "title": "", "priority": "BAD" },
                    { "title": "", "priority": "BAD" },
                    { "title": "", "priority": "BAD" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "多错误文档应失败");
        GuiTestSupport.assertNull(result.documentErrorCode(), "字段校验错误不应标记文档级错误码");
        GuiTestSupport.assertEquals(TaskImporter.MAX_ERRORS_REPORTED + 1, result.errors().size(),
                "错误明细应截断为上限条数并附总数提示");
        GuiTestSupport.assertTrue(result.errors().get(result.errors().size() - 1).contains("more"), "末条应为剩余错误数提示");
    }

    /**
     * 验证非法 JSON 文本按格式错误拒绝且不抛异常。
     */
    private static void shouldRejectMalformedJson() {
        TaskImporter.Result result = TaskImporter.importTasks("{ not json", null);

        GuiTestSupport.assertFalse(result.isSuccess(), "非法 JSON 应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, result.documentErrorCode(), "语法错误应归类为格式错误");
    }

    /**
     * 验证文件不存在时按文件错误拒绝且不抛异常。
     */
    private static void shouldRejectMissingFile() {
        TaskImporter.Result result = TaskImporter.parse(Path.of("no-such-import-file.json"), null);

        GuiTestSupport.assertFalse(result.isSuccess(), "缺失文件应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FILE, result.documentErrorCode(), "缺失文件应归类为文件错误");
    }

    /**
     * 验证 tasks 数组中的 null 元素被拒绝而不触发 NPE。
     */
    private static void shouldRejectNullTaskEntry() {
        String json = """
                { "formatVersion": 1, "tasks": [ null, { "title": "合法任务" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "null 任务条目应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("entry must not be null"), "错误应定位到 null 条目");
    }

    /**
     * 验证 parentId 自引用（a→a）被按环拒绝。
     */
    private static void shouldRejectSelfParentReference() {
        String json = """
                { "formatVersion": 1, "tasks": [ { "id": "a", "title": "自引用任务", "parentId": "a" } ] }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "自引用应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("alias cycle detected"), "错误应报告别名环");
    }

    /**
     * 验证两个任务互为父子（a→b→a）时被按环拒绝。
     */
    private static void shouldRejectParentAliasCycle() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "id": "a", "title": "任务甲", "parentId": "b" },
                    { "id": "b", "title": "任务乙", "parentId": "a" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "两节点环应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("alias cycle detected"), "错误应报告别名环");
    }

    /**
     * 验证三节点环（a→b→c→a）被拒绝且只报一次错。
     */
    private static void shouldRejectThreeNodeParentCycle() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "id": "a", "title": "任务甲", "parentId": "b" },
                    { "id": "b", "title": "任务乙", "parentId": "c" },
                    { "id": "c", "title": "任务丙", "parentId": "a" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "三节点环应失败");
        GuiTestSupport.assertEquals(1, result.errors().size(), "同一个环只应报告一次");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("alias cycle detected"), "错误应报告别名环");
    }

    /**
     * 验证无环的多级父子链正常导入（环检测不误伤合法链）。
     */
    private static void shouldAcceptParentChainWithoutCycle() {
        String json = """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "id": "a", "title": "一级任务" },
                    { "id": "b", "title": "二级任务", "parentId": "a" },
                    { "id": "c", "title": "三级任务", "parentId": "b" }
                  ]
                }
                """;
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertTrue(result.isSuccess(), "无环父子链应成功导入");
        GuiTestSupport.assertEquals(result.tasks().get(1).getId(), result.tasks().get(2).getParentTaskId(),
                "三级任务应挂接到二级任务");
    }

    /**
     * 验证标题超长（513 字符）被拒绝。
     */
    private static void shouldRejectOverLengthTitle() {
        String json = "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"" + "长".repeat(513) + "\" } ] }";
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "超长标题应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("exceeds limit 512"), "错误应指出标题列宽上限");
    }

    /**
     * 验证标题恰好 512 字符（H2 列宽边界）可正常导入。
     */
    private static void shouldAcceptTitleAtLengthLimit() {
        String json = "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"" + "界".repeat(512) + "\" } ] }";
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertTrue(result.isSuccess(), "512 字符边界标题应成功导入");
        GuiTestSupport.assertEquals(512, result.tasks().get(0).getTitle().length(), "标题长度应保持 512");
    }

    /**
     * 验证描述超长（超过 H2 description 列宽 16384）被拒绝。
     */
    private static void shouldRejectOverLengthDescription() {
        String json = "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"任务\", \"description\": \""
                + "述".repeat(16385) + "\" } ] }";
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "超长描述应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("description"), "错误应定位到描述字段");
    }

    /**
     * 验证单个标签超长（超过 H2 task_tags.tag 列宽 128）被拒绝。
     */
    private static void shouldRejectOverLengthTag() {
        String json = "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"任务\", \"tags\": [\""
                + "签".repeat(129) + "\"] } ] }";
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "超长标签应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("tags"), "错误应定位到标签字段");
    }

    /**
     * 验证触发器目标超长（超过 H2 trigger_target 列宽 256）被拒绝。
     */
    private static void shouldRejectOverLengthTriggerTarget() {
        String json = """
                { "formatVersion": 1, "tasks": [ { "title": "任务", "trigger": { "type": "ITEM_COLLECT",
                  "target": "%s", "targetCount": 1 } } ] }
                """.formatted("长".repeat(257));
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "超长触发目标应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).contains("trigger.target"), "错误应定位到触发目标字段");
    }

    /**
     * 验证文件/文本开头的 UTF-8 BOM 被剥离后正常解析。
     */
    private static void shouldStripUtf8Bom() {
        String json = "\uFEFF{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"BOM 任务\" } ] }";
        TaskImporter.Result fromString = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertTrue(fromString.isSuccess(), "带 BOM 的文本应正常导入");
    }

    /**
     * 验证深嵌套 JSON（10 万层）按格式错误拒绝，解析栈溢出被捕获、不向命令线程抛 Error。
     */
    private static void shouldRejectDeeplyNestedJson() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 100_000; index++) {
            builder.append('[');
        }
        for (int index = 0; index < 100_000; index++) {
            builder.append(']');
        }
        TaskImporter.Result result = TaskImporter.importTasks(builder.toString(), null);

        GuiTestSupport.assertFalse(result.isSuccess(), "深嵌套 JSON 应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, result.documentErrorCode(), "深嵌套应归类为格式错误");
    }

    /**
     * 验证字段类型错误的容错：类型不匹配按格式错误拒绝、不抛未捕获异常。
     */
    private static void shouldRejectWrongTypeFields() {
        TaskImporter.Result tasksAsObject = TaskImporter.importTasks(
                "{ \"formatVersion\": 1, \"tasks\": {} }", null);
        GuiTestSupport.assertFalse(tasksAsObject.isSuccess(), "tasks 为对象应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, tasksAsObject.documentErrorCode(),
                "类型不匹配应归类为格式错误");

        TaskImporter.Result tagsAsString = TaskImporter.importTasks(
                "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"任务\", \"tags\": \"不是数组\" } ] }", null);
        GuiTestSupport.assertFalse(tagsAsString.isSuccess(), "tags 为字符串应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, tagsAsString.documentErrorCode(),
                "tags 类型不匹配应归类为格式错误");

        TaskImporter.Result dueDateAsText = TaskImporter.importTasks(
                "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"任务\", \"dueDate\": \"不是数字\" } ] }", null);
        GuiTestSupport.assertFalse(dueDateAsText.isSuccess(), "dueDate 非数字应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, dueDateAsText.documentErrorCode(),
                "dueDate 类型不匹配应归类为格式错误");
    }

    /**
     * 验证顶层是数组（而非文档对象）时按格式错误拒绝。
     */
    private static void shouldRejectTopLevelArray() {
        TaskImporter.Result result = TaskImporter.importTasks("[]", null);

        GuiTestSupport.assertFalse(result.isSuccess(), "顶层数组应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_FORMAT, result.documentErrorCode(), "顶层数组应归类为格式错误");
    }

    /**
     * 验证恰好 500 条（含上限边界）可正常导入。
     */
    private static void shouldAcceptExactlyMaxTasks() {
        StringBuilder builder = new StringBuilder("{ \"formatVersion\": 1, \"tasks\": [");
        for (int index = 0; index < TaskImporter.MAX_TASKS; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{ \"title\": \"任务").append(index).append("\" }");
        }
        builder.append("]}");
        TaskImporter.Result result = TaskImporter.importTasks(builder.toString(), null);

        GuiTestSupport.assertTrue(result.isSuccess(), "恰好上限数量的任务应成功导入");
        GuiTestSupport.assertEquals(TaskImporter.MAX_TASKS, result.tasks().size(), "导入任务数应等于上限");
    }

    /**
     * 验证错误消息中的原始值被截断，超长字段值不会整段回显到聊天。
     */
    private static void shouldExcerptLongValuesInErrors() {
        String longPriority = "优".repeat(500);
        String json = "{ \"formatVersion\": 1, \"tasks\": [ { \"title\": \"任务\", \"priority\": \""
                + longPriority + "\" } ] }";
        TaskImporter.Result result = TaskImporter.importTasks(json, null);

        GuiTestSupport.assertFalse(result.isSuccess(), "非法优先级应失败");
        GuiTestSupport.assertTrue(result.errors().get(0).length() < 200,
                "错误消息应被截断防刷屏，实际长度 " + result.errors().get(0).length());
        GuiTestSupport.assertFalse(result.errors().get(0).contains(longPriority), "完整原始值不应出现在错误消息中");
    }

    /**
     * 验证批次文本总量（标题+描述+标签 UTF-8 字节）超出预算被按 too_large 拒绝。
     */
    private static void shouldRejectBatchTextBudget() {
        StringBuilder builder = new StringBuilder("{ \"formatVersion\": 1, \"tasks\": [");
        for (int index = 0; index < 50; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{ \"title\": \"任务").append(index).append("\", \"description\": \"")
                    .append("述".repeat(6000)).append("\" }");
        }
        builder.append("]}");
        TaskImporter.Result result = TaskImporter.importTasks(builder.toString(), null);

        GuiTestSupport.assertFalse(result.isSuccess(), "超出文本总量预算应失败");
        GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_TOO_LARGE, result.documentErrorCode(),
                "超预算应归类为 too_large");
    }

    /**
     * 验证超过文件大小上限（5MB）的导入文件在读取前即被拒绝。
     */
    private static void shouldRejectOversizeImportFile() throws Exception {
        Path file = Files.createTempFile("todolist-import-oversize", ".json");
        byte[] payload = new byte[(int) TaskImporter.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(payload, (byte) 'a');
        Files.write(file, payload);
        try {
            TaskImporter.Result result = TaskImporter.parse(file, null);

            GuiTestSupport.assertFalse(result.isSuccess(), "超限文件应失败");
            GuiTestSupport.assertEquals(TaskImporter.Result.ERROR_TOO_LARGE, result.documentErrorCode(),
                    "超限文件应归类为 too_large");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
