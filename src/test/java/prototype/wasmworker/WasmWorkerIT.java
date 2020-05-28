package prototype.wasmworker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.File;
import java.util.Map;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import prototype.wasmworker.proc.NativeProcessManager;
import prototype.wasmworker.proc.ProcessManager;

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
        Assertions.assertTrue(new File(fileName).canRead());
        inputData.put("wasmFileName", fileName);
        task.setInputData(inputData);
        return tested.execute(task);
    }

    @Test
    public void testWasi() {
        String classPathToWasm = "/wasi/wasi.wasm";
        Map<String, Object> inputData = Maps.newHashMap("args", "{\"name\":\"foo\"}");
        TaskResult result = execute(classPathToWasm, inputData);
        assertEquals(Status.COMPLETED, result.getStatus());
        String expectedResult = "{\"name\":\"foo\",\"name_length\":3}";
        assertEquals(expectedResult, result.getOutputData().get("result"));
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
}
