package prototype.wasmworker.worker;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import prototype.wasmworker.proc.NativeProcessManager;

public class QuickJsWorkerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    QuickJsWorker tested;

    @BeforeEach
    public void beforeEach() {
        tested = new QuickJsWorker(2000, objectMapper, new NativeProcessManager());
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
        String expectedResult = "1";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }

    @Test
    public void testExecutionWithArgs() {
        String script = "console.log(JSON.stringify(process.argv));";
        TaskResult taskResult = execute(script, new String[]{"arg1"}, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "[\"script.js\",\"arg1\"]";
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
}
