package prototype.wasmworker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import prototype.wasmworker.proc.ProcessManager;
import prototype.wasmworker.proc.ProcessManager.NonZeroExitStatusException;
import prototype.wasmworker.proc.ProcessManager.TimeoutException;

@Component
public class WasmWorker implements Worker {
    private static final Logger logger = LoggerFactory.getLogger(WasmWorker.class);
    private final long maxWaitMillis;
    private final ObjectMapper objectMapper;
    private final ProcessManager manager;

    @Autowired
    public WasmWorker(ConductorProperties props, ObjectMapper objectMapper, ProcessManager manager) {
        this(props.getMaxWaitMillis(), objectMapper, manager);
    }

    WasmWorker(long maxWaitMillis, ObjectMapper objectMapper, ProcessManager manager) {
        this.maxWaitMillis = maxWaitMillis;
        this.objectMapper = objectMapper;
        this.manager = manager;
    }

    @Override
    public String getTaskDefName() {
        return "fb-test___wasm"; // TODO
    }

    private void addArgs(Object maybeArgs, List<String> cmd) throws JsonProcessingException {
        if (maybeArgs instanceof Map) {
            // convert to json
            cmd.add(objectMapper.writeValueAsString(maybeArgs));
        } else if (maybeArgs instanceof String[]) {
            cmd.addAll(Arrays.asList((String[]) maybeArgs));
        } else if (maybeArgs != null) {
            String arg = String.valueOf(maybeArgs);
            if (!arg.isEmpty()) {
                cmd.add(arg);
            }
        }
    }

    @Override
    public TaskResult execute(Task task) {
        logger.debug("Executing started {}", task.getTaskId());
        logger.trace("Executing started {}", task);
        TaskResult taskResult;
        try {
            taskResult = executeInner(task);
        } catch (Exception e) {
            logger.error("Unhandled exception in task {}", task.getTaskId(), e);
            taskResult = new TaskResult(task);
            taskResult.getOutputData().put("error.reason", "Unhandled exception");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
        }
        logger.debug("Executing finished {} : {}", task.getTaskId(), taskResult);
        logger.trace("Executing finished {} : {}", task, taskResult);
        return taskResult;
    }

    private TaskResult executeInner(Task task) {
        // TODO careful handling of user input to prevent injection attacks
        String functionName = (String) task.getInputData().get("function");
        String wasmFileName = (String) task.getInputData().get("wasmFileName");
        Object maybeArgs = task.getInputData().get("args");
        boolean dryRun = Boolean.parseBoolean((String) task.getInputData().get("dryRun"));
        boolean outputIsJson = Boolean.parseBoolean((String) task.getInputData().get("outputIsJson"));

        List<String> cmd = Lists.newArrayList("wasmtime", "run");

        if (!Strings.isNullOrEmpty(functionName)) {
            cmd.add("--invoke");
            cmd.add(functionName);
        }

        cmd.add(wasmFileName);

        TaskResult taskResult = new TaskResult(task);
        taskResult.getOutputData().put("cmd", cmd);

        try {
            addArgs(maybeArgs, cmd);
        } catch (Exception e) {
            logger.debug("Cannot serialize args", e);
            taskResult.getOutputData().put("error.reason", "Bad request");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
            return taskResult;
        }

        if (dryRun) {
            taskResult.setStatus(Status.COMPLETED);
            return taskResult;
        }

        try {
            String stdOut = manager.execute(cmd, maxWaitMillis, TimeUnit.MILLISECONDS);
            if (outputIsJson) {
                // TODO test if this is correct
                Map result = objectMapper.readValue(stdOut, Map.class);
                taskResult.getOutputData().put("result", result);
            } else {
                taskResult.getOutputData().put("result", stdOut);
            }
            taskResult.setStatus(Status.COMPLETED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            taskResult.getOutputData().put("error.reason", "interrupted");
            taskResult.setStatus(Status.FAILED);
        } catch (IOException e) {
            logger.error("IOException while running {}", cmd, e);
            taskResult.getOutputData().put("error.reason", "fatal error");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR); // no retries
        } catch (NonZeroExitStatusException e) {
            taskResult.getOutputData().put("error.reason", "crash");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR); // no retries
        } catch (TimeoutException e) {
            taskResult.getOutputData().put("error.reason", "timeout");
            taskResult.setStatus(Status.FAILED);
        }
        return taskResult;
    }
}