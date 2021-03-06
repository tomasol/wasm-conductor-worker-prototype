package prototype.wasmworker.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.lang.annotation.Native;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import prototype.wasmworker.lifecycle.ConductorProperties;
import prototype.wasmworker.proc.NativeProcessManager;

public class PythonWorkerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    PythonWorker tested;

    @BeforeEach
    public void beforeEach() {
        tested = new PythonWorker(objectMapper, new PythonExecutor(objectMapper, new NativeProcessManager(),
                new ConductorProperties()));
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
        String script = "print(1)";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "1\n";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }

    @Test
    public void testExecutionWithArgs() {
        String script = "print(argv[0]);print(argv[1]);";
        TaskResult taskResult = execute(script, new String[]{"arg1"}, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "script.py\narg1\n";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }

    @Test
    public void testJsonOutput() {
        String script = "import json\n" +
                "print(json.dumps({'key':'value'}))";
        TaskResult taskResult = execute(script, new String[]{"arg1"}, true);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        Map<String, String> expectedResult = Maps.newHashMap("key", "value");
        Object actualResult = taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResult);
    }

    // @TODO Known issue - script with syntax errors ends with exit code 0.
    @Test()
    @Disabled
    public void testSyntaxError() {
        String script = "x";
        TaskResult taskResult = execute(script, null, false);
        assertEquals(Status.FAILED_WITH_TERMINAL_ERROR, taskResult.getStatus());
        assertEquals("exitStatus:1", taskResult.getReasonForIncompletion());
    }
}
