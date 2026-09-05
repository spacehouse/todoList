package com.todolist.network;

import com.todolist.project.Project;
import com.todolist.task.Task;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 任务/项目网络负载收发链路自检测试。
 *
 * <p>不依赖外部测试框架，直接通过 main 方法执行序列化-反序列化往返验证，
 * 与真实网络包共用同一条编解码路径（writeTaskList/readTaskList、writeProjectList/readProjectList、
 * TaskPacketChunking 分块重组），用于拦截以下历史回归：</p>
 * <ul>
 *   <li>任务/项目字段序列化丢字段（某次新增字段后 toNbt/fromNbt 不对称）；</li>
 *   <li>团队任务合并负载两段列表错位；</li>
 *   <li>大负载分块乱序到达时重组失败或元数据校验失效。</li>
 * </ul>
 *
 * <p>已知边界：跨网络传输的握手与回调分发依赖真实连接，
 * 由 fabric/forge 各自的兼容层冒烟测试与实机验证覆盖。</p>
 */
public final class TaskPacketsRoundTripTestMain {

    /** 失败用例计数，非零时以退出码 1 结束，令 Gradle 任务失败。 */
    private static int failed;

    /** 工具类禁止实例化。 */
    private TaskPacketsRoundTripTestMain() {
    }

    /**
     * 测试入口：依次执行各用例并汇总结果。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldRoundTripSingleTaskWithAllFields",
                TaskPacketsRoundTripTestMain::shouldRoundTripSingleTaskWithAllFields
        );
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldRoundTripEmptyAndMultiTaskList",
                TaskPacketsRoundTripTestMain::shouldRoundTripEmptyAndMultiTaskList
        );
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldRoundTripTeamMergePayload",
                TaskPacketsRoundTripTestMain::shouldRoundTripTeamMergePayload
        );
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldRoundTripProjectList",
                TaskPacketsRoundTripTestMain::shouldRoundTripProjectList
        );
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldSplitAndAssembleChunksInReverseOrder",
                TaskPacketsRoundTripTestMain::shouldSplitAndAssembleChunksInReverseOrder
        );
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldDropInvalidChunkMetadata",
                TaskPacketsRoundTripTestMain::shouldDropInvalidChunkMetadata
        );
        runTestCase(
                "TaskPacketsRoundTripTestMain.shouldDecideChunkingBySizeThreshold",
                TaskPacketsRoundTripTestMain::shouldDecideChunkingBySizeThreshold
        );

        if (failed > 0) {
            System.out.println("[PKT][RESULT][FAIL] " + failed + " case(s) failed");
            System.exit(1);
        }
        System.out.println("[PKT][RESULT][PASS] all cases passed");
    }

    /**
     * 执行单个用例并打印结果，异常计为失败。
     *
     * @param name 用例名称
     * @param caseBody 用例主体
     */
    private static void runTestCase(String name, Runnable caseBody) {
        long start = System.currentTimeMillis();
        try {
            caseBody.run();
            System.out.println("[PKT][CASE ][PASS] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
        } catch (Throwable t) {
            failed++;
            System.out.println("[PKT][CASE ][FAIL] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
            t.printStackTrace(System.out);
        }
    }

    /**
     * 构造覆盖全部可序列化字段的富任务样本。
     *
     * @return 已填充各字段的任务
     */
    private static Task richTask() {
        Task task = new Task("整理仓库", "收盘前完成主干合并");
        task.setId("task-1");
        task.setCompleted(true);
        Task.Priority[] priorities = Task.Priority.values();
        task.setPriority(priorities[priorities.length - 1]);
        task.addTag("alpha");
        task.addTag("工作");
        task.setDueDate(1735689600000L);
        Task.Scope[] scopes = Task.Scope.values();
        task.setScope(scopes[scopes.length - 1]);
        task.setCreatorUuid("11111111-1111-1111-1111-111111111111");
        task.setAssigneeUuid("22222222-2222-2222-2222-222222222222");
        task.setAssigneeName("Steve");
        task.setProjectId("proj-1");
        task.setParentTaskId("task-0");
        task.setSubtaskSortOrder(3L);
        return task;
    }

    /**
     * 校验往返后的任务与原任务逐字段一致。
     *
     * @param label 用例内标签
     * @param expected 原始任务
     * @param actual 反序列化任务
     */
    private static void assertTaskEquals(String label, Task expected, Task actual) {
        assertEquals(label + ".id", expected.getId(), actual.getId());
        assertEquals(label + ".title", expected.getTitle(), actual.getTitle());
        assertEquals(label + ".description", expected.getDescription(), actual.getDescription());
        assertEquals(label + ".completed", expected.isCompleted(), actual.isCompleted());
        assertEquals(label + ".priority", expected.getPriority(), actual.getPriority());
        assertEquals(label + ".tags", expected.getTags(), actual.getTags());
        assertEquals(label + ".createdAt", expected.getCreatedAt(), actual.getCreatedAt());
        assertEquals(label + ".dueDate", expected.getDueDate(), actual.getDueDate());
        assertEquals(label + ".scope", expected.getScope(), actual.getScope());
        assertEquals(label + ".creatorUuid", expected.getCreatorUuid(), actual.getCreatorUuid());
        assertEquals(label + ".assigneeUuid", expected.getAssigneeUuid(), actual.getAssigneeUuid());
        assertEquals(label + ".assigneeName", expected.getAssigneeName(), actual.getAssigneeName());
        assertEquals(label + ".projectId", expected.getProjectId(), actual.getProjectId());
        assertEquals(label + ".parentTaskId", expected.getParentTaskId(), actual.getParentTaskId());
        assertEquals(label + ".subtaskSortOrder", expected.getSubtaskSortOrder(), actual.getSubtaskSortOrder());
    }

    /**
     * 断言两值相等，不相等时抛出带标签的断言错误。
     *
     * @param label 字段标签
     * @param expected 期望值
     * @param actual 实际值
     */
    private static void assertEquals(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=<" + expected + "> actual=<" + actual + ">");
        }
    }

    /**
     * 断言条件为真。
     *
     * @param label 断言标签
     * @param condition 条件表达式
     */
    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
        }
    }

    /**
     * 用例：单任务全字段 writeTask→readTask 往返保真。
     */
    private static void shouldRoundTripSingleTaskWithAllFields() {
        Task original = richTask();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTask(buf, original);
        Task restored = TaskPackets.readTask(buf);
        assertTaskEquals("single", original, restored);
    }

    /**
     * 用例：空列表与多任务列表（含平铺子任务关系）往返保真。
     */
    private static void shouldRoundTripEmptyAndMultiTaskList() {
        FriendlyByteBuf emptyBuf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTaskList(emptyBuf, List.of());
        assertEquals("emptyList.size", 0, TaskPackets.readTaskList(emptyBuf).size());

        Task parent = richTask();
        Task child = new Task("子任务", "平铺子任务条目");
        child.setId("task-2");
        child.setParentTaskId(parent.getId());
        child.setSubtaskSortOrder(1L);
        child.setProjectId(parent.getProjectId());
        Task plain = new Task("普通任务", "");
        plain.setId("task-3");

        List<Task> original = List.of(parent, child, plain);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTaskList(buf, original);
        List<Task> restored = TaskPackets.readTaskList(buf);

        assertEquals("list.size", original.size(), restored.size());
        for (int i = 0; i < original.size(); i++) {
            assertTaskEquals("list[" + i + "]", original.get(i), restored.get(i));
        }
        assertEquals("list[1].parentTaskId", parent.getId(), restored.get(1).getParentTaskId());
    }

    /**
     * 用例：团队合并负载 tasks + baseTasks 两段列表往返保真。
     */
    private static void shouldRoundTripTeamMergePayload() {
        List<Task> tasks = List.of(richTask());
        Task base = new Task("基线任务", "保存发起时的快照");
        base.setId("task-base");
        base.setDueDate(42L);
        List<Task> baseTasks = List.of(base);

        byte[] payload = TaskPacketChunking.serializeMergeTasks(tasks, baseTasks);
        List<Task>[] restored = TaskPacketChunking.deserializeMergeTasks(payload);

        assertEquals("merge.tasks.size", tasks.size(), restored[0].size());
        assertEquals("merge.base.size", baseTasks.size(), restored[1].size());
        assertTaskEquals("merge.tasks[0]", tasks.get(0), restored[0].get(0));
        assertTaskEquals("merge.base[0]", baseTasks.get(0), restored[1].get(0));
    }

    /**
     * 用例：项目列表（个人 + 团队）writeProjectList→readProjectList 往返保真。
     */
    private static void shouldRoundTripProjectList() {
        Project.Scope[] scopes = Project.Scope.values();
        Project personal = new Project("个人默认", scopes[0], "11111111-1111-1111-1111-111111111111");
        Project team = new Project("团队项目", scopes[scopes.length - 1], "22222222-2222-2222-2222-222222222222");

        List<Project> original = List.of(personal, team);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ProjectPackets.writeProjectList(buf, original);
        List<Project> restored = ProjectPackets.readProjectList(buf);

        assertEquals("projects.size", original.size(), restored.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals("projects[" + i + "].id", original.get(i).getId(), restored.get(i).getId());
            assertEquals("projects[" + i + "].name", original.get(i).getName(), restored.get(i).getName());
            assertEquals("projects[" + i + "].scope", original.get(i).getScope(), restored.get(i).getScope());
            assertEquals("projects[" + i + "].ownerUuid", original.get(i).getOwnerUuid(), restored.get(i).getOwnerUuid());
        }
    }

    /**
     * 用例：超过单包上限的负载拆分后，分片乱序到达仍能重组出原始数据。
     */
    private static void shouldSplitAndAssembleChunksInReverseOrder() {
        Random random = new Random(20260905L);
        byte[] payload = new byte[TaskPacketChunking.MAX_CHUNK_BYTES * 2 + 4000];
        random.nextBytes(payload);

        List<byte[]> chunks = TaskPacketChunking.splitPayload(payload);
        assertEquals("chunks.size", 3, chunks.size());

        TaskPacketChunking.ChunkAccumulator accumulator = new TaskPacketChunking.ChunkAccumulator();
        String sessionId = TaskPacketChunking.newSessionId();
        byte[] assembled = null;
        for (int i = chunks.size() - 1; i >= 0; i--) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            TaskPacketChunking.writeChunk(buf, sessionId, chunks.size(), i, chunks.get(i));
            TaskPacketChunking.ChunkData chunk = TaskPacketChunking.readChunk(buf);
            assertEquals("chunk[" + i + "].index", i, chunk.chunkIndex);
            byte[] result = accumulator.accept(chunk);
            if (i == 0) {
                assembled = result;
            } else {
                assertEquals("chunk[" + i + "].pending", null, result);
            }
        }
        assertTrue("assembled.notNull", assembled != null);
        assertTrue("assembled.content", Arrays.equals(payload, assembled));
    }

    /**
     * 用例：非法分片元数据被丢弃（返回 null）且不抛异常。
     */
    private static void shouldDropInvalidChunkMetadata() {
        TaskPacketChunking.ChunkAccumulator accumulator = new TaskPacketChunking.ChunkAccumulator();
        String sessionId = TaskPacketChunking.newSessionId();

        assertEquals("zeroTotal", null, accumulator.accept(
                new TaskPacketChunking.ChunkData(sessionId, 0, 0, new byte[0])));
        assertEquals("overflowTotal", null, accumulator.accept(
                new TaskPacketChunking.ChunkData(sessionId, 999, 0, new byte[0])));
        assertEquals("negativeIndex", null, accumulator.accept(
                new TaskPacketChunking.ChunkData(sessionId, 2, -1, new byte[0])));
        assertEquals("indexOutOfBounds", null, accumulator.accept(
                new TaskPacketChunking.ChunkData(sessionId, 2, 5, new byte[0])));

        byte[] first = {1};
        byte[] mismatched = {2};
        assertEquals("firstChunkPending", null, accumulator.accept(
                new TaskPacketChunking.ChunkData(sessionId, 2, 0, first)));
        assertEquals("mismatchedTotal", null, accumulator.accept(
                new TaskPacketChunking.ChunkData(sessionId, 3, 1, mismatched)));
    }

    /**
     * 用例：分块判定阈值边界（恰好上限不分块，超出 1 字节分块）。
     */
    private static void shouldDecideChunkingBySizeThreshold() {
        FriendlyByteBuf atLimit = new FriendlyByteBuf(Unpooled.buffer());
        atLimit.writeBytes(new byte[TaskPacketChunking.MAX_CHUNK_BYTES]);
        assertEquals("atLimit.needChunking", false, TaskPacketChunking.needsChunking(atLimit));

        FriendlyByteBuf overLimit = new FriendlyByteBuf(Unpooled.buffer());
        overLimit.writeBytes(new byte[TaskPacketChunking.MAX_CHUNK_BYTES + 1]);
        assertEquals("overLimit.needChunking", true, TaskPacketChunking.needsChunking(overLimit));
    }
}
