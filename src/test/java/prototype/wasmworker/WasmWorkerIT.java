package prototype.wasmworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.File;
import java.util.Map;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import prototype.wasmworker.proc.NativeProcessManager;
import prototype.wasmworker.worker.WasmWorker;

public class WasmWorkerIT {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    WasmWorker tested;

    @BeforeEach
    public void beforeEach() {
        tested = new WasmWorker(2000, objectMapper, new NativeProcessManager());
    }

    private TaskResult execute(String classPathToWasm, Map<String, Object> inputData) {
        Task task = new Task();
        task.setStatus(Task.Status.SCHEDULED);
        String fileName = getClass().getResource(classPathToWasm).getFile();
        assertTrue(new File(fileName).canRead());
        inputData.put("wasmFileName", fileName);
        task.setInputData(inputData);
        return tested.execute(task);
    }

    @Test
    public void testWasi() throws JsonProcessingException {
        String classPathToWasm = "/wasi/wasi.wasm";
        Map<String, Object> inputData = Maps.newHashMap("args", "{\"name\":\"foo\"}");
        TaskResult result = execute(classPathToWasm, inputData);
        assertEquals(Status.COMPLETED, result.getStatus());
        Map expectedResult = objectMapper.readValue("{\"name\":\"foo\",\"name_length\":3}", Map.class);
        String actualResultString = (String)result.getOutputData().get("result");
        Map actualResult = objectMapper.readValue(actualResultString, Map.class);
        assertEquals(expectedResult, actualResult);
        assertEquals(2, result.getLogs().size(),
                "Unexpected:" + result.getLogs().stream().map(TaskExecLog::getLog).reduce(";", String::concat));
        assertTrue(result.getLogs().get(0).getLog().startsWith("Executing"));
        assertEquals("logging to stderr example", result.getLogs().get(1).getLog());
    }

    @Test
    public void testWasmInterfaceTypes() {
        String classPathToWasm = "/fib/fib.wasm";
        Map<String, Object> inputData = Maps.newHashMap("args", "5");
        inputData.put("function", "fib");
        TaskResult result = execute(classPathToWasm, inputData);
        assertEquals(Status.COMPLETED, result.getStatus());
        String expectedResult = "5";
        assertEquals(expectedResult, result.getOutputData().get("result"));
    }

    @Test
    public void testJsEngine() {
        String classPathToWasm = "/boa-wasi/boa-wasi.wasm";
        int expectedValue = 4;
        Map<String, Object> inputData = Maps.newHashMap("args", "{\"foo\": " + expectedValue + "}");
        inputData.put("stdIn", "console.error('logging');\nconsole.log(JSON.parse(process.argv[1]).foo);");
        TaskResult result = execute(classPathToWasm, inputData);
        assertEquals(Status.COMPLETED, result.getStatus());
        assertEquals(String.valueOf(expectedValue), result.getOutputData().get("result"));
    }
}
