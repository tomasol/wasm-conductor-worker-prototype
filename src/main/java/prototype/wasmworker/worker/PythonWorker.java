package prototype.wasmworker.worker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import prototype.wasmworker.lifecycle.ConductorProperties;
import prototype.wasmworker.proc.ProcessManager;
import prototype.wasmworker.proc.ProcessManager.ExecutionResult;
import prototype.wasmworker.proc.ProcessManager.TimeoutException;

// Based on https://wapm.io/package/quickjs
@Component
public class PythonWorker implements Worker {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String SCRIPT_JS = "script.py";
    private final long maxWaitMillis;
    private final ObjectMapper objectMapper;
    private final ProcessManager manager;
    private final String pythonBinPath, pythonLibPath;

    @Autowired
    public PythonWorker(ConductorProperties props, ObjectMapper objectMapper, ProcessManager manager) {
        this(props.getMaxWaitMillis(), objectMapper, manager, props.getPythonBinPath(), props.getPythonLibPath());
    }

    PythonWorker(long maxWaitMillis, ObjectMapper objectMapper, ProcessManager manager,
                 String pythonBinPath, String pythonLibPath) {
        this.maxWaitMillis = maxWaitMillis;
        this.objectMapper = objectMapper;
        this.manager = manager;
        this.pythonBinPath = pythonBinPath;
        this.pythonLibPath = pythonLibPath;
    }

    @Override
    public String getTaskDefName() {
        return "GLOBAL___py";
    }

    @Override
    public TaskResult execute(Task task) {
        logger.debug("Executing started {}", task.getTaskId());
        logger.trace("Executing started {}", task);
        TaskResult taskResult;
        File tempDir = Files.createTempDir();
        logger.trace("Created temp folder {}", tempDir.getAbsolutePath());
        try {
            taskResult = executeInner(task, tempDir);
        } catch (Exception e) {
            logger.error("Unhandled exception in task {}", task.getTaskId(), e);
            taskResult = new TaskResult(task);
            taskResult.setReasonForIncompletion("unexpected");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
                logger.trace("Deleted temp folder {}", tempDir.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("Cannot delete temp folder {}", tempDir.getAbsolutePath(), e);
            }
        }
        logger.debug("Executing finished {} : {}", task.getTaskId(), taskResult);
        logger.trace("Executing finished {} : {}", task, taskResult);
        return taskResult;
    }

    private List<String> parseArgs(String scriptName, Object maybeArgs) throws JsonProcessingException {
        List<String> result = Lists.newArrayList(scriptName);
        if (maybeArgs instanceof Map) {
            // convert to json
            result.add(objectMapper.writeValueAsString(maybeArgs));
        } else if (maybeArgs instanceof String[]) {
            result.addAll(Arrays.asList((String[]) maybeArgs));
        } else if (maybeArgs != null) {
            String arg = String.valueOf(maybeArgs);
            if (!arg.isEmpty()) {
                result.add(arg);
            }
        }
        return result;
    }

    private TaskResult executeInner(Task task, File tempDir) throws IOException {
        // TODO careful handling of user input to prevent injection attacks
        TaskResult taskResult = new TaskResult(task);
        List<String> args;
        try {
            args = parseArgs(SCRIPT_JS, task.getInputData().get("args"));
        } catch (JsonProcessingException e) {
            logger.debug("Cannot serialize args", e);
            taskResult.getOutputData().put("error.reason", "Bad request");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
            return taskResult;
        }

        String script = (String) checkNotNull(task.getInputData().get("script"), "Cannot find script");
        // add args to the script
        String preamble = String.format("argv = %s;", objectMapper.writeValueAsString(args));
        script = preamble + script;


        List<String> cmd = Lists.newArrayList("wasmer", pythonBinPath, "--mapdir=lib:" + pythonLibPath);

        taskResult.log(String.format("Executing '%s' with script '%s'", cmd, script));
        boolean outputIsJson = Boolean.parseBoolean((String) task.getInputData().get("outputIsJson"));
        return execute(cmd, script, outputIsJson, taskResult);
    }

    private TaskResult execute(List<String> cmd, String stdIn, boolean outputIsJson, TaskResult taskResult) {
        try {
            ExecutionResult executionResult = manager.execute(cmd, stdIn, maxWaitMillis, TimeUnit.MILLISECONDS);
            if (outputIsJson) {
                try {
                    Map result = objectMapper.readValue(executionResult.getStdOut(), Map.class);
                    taskResult.getOutputData().put("result", result);
                } catch (JsonParseException e) {
                    taskResult.log("Warning: output is not JSON");
                    taskResult.getOutputData().put("result", executionResult.getStdOut());
                }
            } else {
                taskResult.getOutputData().put("result", executionResult.getStdOut());
            }
            // add logs
            executionResult.getStdErr().lines().forEach(taskResult::log);
            if (executionResult.isSuccess()) {
                taskResult.setStatus(Status.COMPLETED);
            } else {
                taskResult.setReasonForIncompletion("exitStatus:" + executionResult.getExitStatus());
                taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR); // no retries
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            taskResult.setReasonForIncompletion("interrupted");
            taskResult.setStatus(Status.FAILED);
        } catch (IOException e) {
            logger.error("IOException while running {}", cmd, e);
            taskResult.setReasonForIncompletion("fatal");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR); // no retries
        } catch (TimeoutException e) {
            taskResult.setReasonForIncompletion("timeout");
            taskResult.setStatus(Status.FAILED);
        }
        return taskResult;
    }
}
