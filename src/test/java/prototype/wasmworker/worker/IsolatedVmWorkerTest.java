package prototype.wasmworker.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

public class IsolatedVmWorkerTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    IsolatedVmWorker tested;

    static Function<Long, IsolatedVmWorker> factory = (Long timemoutMillis) ->
            new IsolatedVmWorker(timemoutMillis, objectMapper, new NativeProcessManager(),
                    ConductorProperties.DEFAULT_ISOLATEDVM_PATH);

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
        String script = "(input) => { return 'Hello ' + input; }";
        TaskResult taskResult = execute(script, "Test", false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "Hello Test\n";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }

    @Test
    public void testExecutionWithArgs() {
        // use JSON.stringify to show [ and quotes, qjs outputs just content separated by ','
        String script = "(arg1, arg2) => { return arg1 + arg2; }";
        TaskResult taskResult = execute(script, new String[]{"test1", "test2"}, false);
        assertEquals(Status.COMPLETED, taskResult.getStatus());
        String expectedResult = "test1test2\n";
        String actualResultString = (String) taskResult.getOutputData().get("result");
        assertEquals(expectedResult, actualResultString);
    }
}
