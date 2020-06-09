package prototype.wasmworker.worker;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import prototype.wasmworker.lifecycle.ConductorProperties;
import prototype.wasmworker.proc.NativeProcessManager;

public class QuickJsWorkerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    QuickJsWorker tested;

    static Function<Long, QuickJsWorker> factory = (Long timemoutMillis) ->
            new QuickJsWorker(objectMapper,
                    new QuickJsExecutor(objectMapper,
                            new NativeProcessManager(),
                            new ConductorProperties()));

    @BeforeEach
    public void beforeEach() {
        tested = factory.apply(2000L);
    }

    private TaskResult execute(String script, Object args, boolean outputIsJson) {
        Task task = new Task();
        task.setTaskId("testing-id");
        task.setTaskType("SIMPLE");
        task.setStatus(Task.Status.SCHEDULED);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("script", script);
        inputData.put("outputIsJson", String.valueOf(outputIsJson));
        inputData.put("args", args);
        task.setInputData(inputData);
        return tested.execute(task);
    }

    @Test
    public void testExecution() {
        String script = "console.log(1)";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "1\n";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }

    @Test
    public void testExecutionWithArgs() {
        // use JSON.stringify to show [ and quotes, qjs outputs just content separated by ','
        String script = "console.log(JSON.stringify(process.argv));";
        TaskResult taskResult = execute(script, new String[]{"arg1"}, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "[\"script.js\",\"arg1\"]\n";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }

    @Test
    public void testSyntaxError() {
        String script = "console.log(x)";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.FAILED_WITH_TERMINAL_ERROR, taskResult.getStatus());
        assertEquals("exitStatus:1", taskResult.getReasonForIncompletion());
    }

    @Disabled
    @Test
    public void testOOM() {
        tested = factory.apply(20000l);
        String script = "Array(1e9).fill(0)";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.FAILED_WITH_TERMINAL_ERROR, taskResult.getStatus());
        assertEquals("exitStatus:1", taskResult.getReasonForIncompletion());
    }

    @Test
    public void testJson() {
        String script = "console.log(JSON.stringify({key:1}));";
        TaskResult taskResult = execute(script, null, true);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        Map<String, Integer> expectedResult = Maps.newHashMap("key", 1);
        Object actualResult = taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testStdErr() {
        String script = "console.error('std', 'err');console.log('std', 'out')";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        Object actualResult = taskResult.getOutputData().get("result");
        assertEquals("std out\n", actualResult);

        assertEquals(1, taskResult.getLogs().size(), "Expected one entry in " +
                taskResult.getLogs().stream().map(TaskExecLog::getLog).collect(Collectors.toList()));
        assertEquals("std err", taskResult.getLogs().get(0).getLog());
    }

    @Test
    public void throwingExceptionShouldMarkTaskAsFailed() {
        String script = "throw 'e'";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.FAILED_WITH_TERMINAL_ERROR, taskResult.getStatus());
    }
}
