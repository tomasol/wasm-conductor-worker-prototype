package prototype.wasmworker.worker;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import prototype.wasmworker.proc.ProcessManager;
import prototype.wasmworker.proc.ProcessManager.ExecutionResult;
import prototype.wasmworker.proc.ProcessManager.TimeoutException;

public abstract class AbstractWorker implements Worker {
    private final long maxWaitMillis;
    protected final ObjectMapper objectMapper;
    private final ProcessManager manager;

    public AbstractWorker(long maxWaitMillis, ObjectMapper objectMapper, ProcessManager manager) {
        this.maxWaitMillis = maxWaitMillis;
        this.objectMapper = objectMapper;
        this.manager = manager;
    }

    abstract protected Logger getLogger();

    @Override
    public TaskResult execute(Task task) {
        Logger logger = getLogger();
        logger.debug("Executing started {}", task.getTaskId());
        logger.trace("Executing started {}", task);
        TaskResult taskResult;
        try {
            taskResult = executeInner(task);
        } catch (Exception e) {
            logger.error("Unhandled exception in task {}", task.getTaskId(), e);
            taskResult = new TaskResult(task);
            taskResult.setReasonForIncompletion("unexpected");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
            return taskResult;
        }
        logger.debug("Executing finished {} : {}", task.getTaskId(), taskResult);
        logger.trace("Executing finished {} : {}", task, taskResult);
        return taskResult;
    }

    abstract protected TaskResult executeInner(Task task) throws IOException;

    protected List<String> parseArgs(String scriptName, Object maybeArgs) throws JsonProcessingException {
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

    protected TaskResult execute(List<String> cmd, String stdIn, boolean outputIsJson, TaskResult taskResult) {
        Logger logger = getLogger();
        try {
            ExecutionResult executionResult = manager.execute(cmd, stdIn,
                    maxWaitMillis, TimeUnit.MILLISECONDS);
            // add stderr to logs
            executionResult.getStdErr().lines().forEach(taskResult::log);

            if (outputIsJson) {
                try {
                    Map result = objectMapper.readValue(executionResult.getStdOut(), Map.class);
                    taskResult.getOutputData().put("result", result);
                } catch (JsonProcessingException e) {
                    taskResult.log("Warning: output is not JSON");
                    taskResult.getOutputData().put("result", executionResult.getStdOut());
                }
            } else {
                taskResult.getOutputData().put("result", executionResult.getStdOut());
            }

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
