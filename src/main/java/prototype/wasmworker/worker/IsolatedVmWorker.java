package prototype.wasmworker.worker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import prototype.wasmworker.proc.ProcessManager;

public class IsolatedVmWorker extends AbstractWorker {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final String isolatedVmPath;

    @Override
    protected Logger getLogger() {
        return logger;
    }

    IsolatedVmWorker(long maxWaitMillis, ObjectMapper objectMapper, ProcessManager manager, String isolatedVmPath) {
        super(maxWaitMillis, objectMapper, manager);
        this.isolatedVmPath = isolatedVmPath;
    }

    protected List<String> parseArgs(Object maybeArgs) throws JsonProcessingException {
        List<String> result = new LinkedList<>();
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

    @Override
    protected TaskResult executeInner(Task task) throws IOException {
        Logger logger = getLogger();
        TaskResult taskResult = new TaskResult(task);
        List<String> args;
        try {
            args = parseArgs(task.getInputData().get("args"));
        } catch (JsonProcessingException e) {
            logger.debug("Cannot serialize args", e);
            taskResult.getOutputData().put("error.reason", "Bad request");
            taskResult.setStatus(Status.FAILED_WITH_TERMINAL_ERROR);
            return taskResult;
        }

        String script = (String) checkNotNull(task.getInputData().get("script"), "Cannot find script");

        List<String> cmd = Lists.newArrayList(isolatedVmPath, script);
        cmd.addAll(args);
        logger.debug("Executing {}", cmd);

        boolean outputIsJson = Boolean.parseBoolean((String) task.getInputData().get("outputIsJson"));
        return execute(cmd, null, outputIsJson, taskResult);
    }

    @Override
    public String getTaskDefName() {
        return "test";
    }

    @Override
    public boolean preAck(Task task) {
        return false;
    }

    @Override
    public void onErrorUpdate(Task task) {

    }

    @Override
    public boolean paused() {
        return false;
    }

    @Override
    public String getIdentity() {
        return "none";
    }

    @Override
    public int getPollCount() {
        return 110;
    }

    @Override
    public int getPollingInterval() {
        return 110;
    }

    @Override
    public int getLongPollTimeoutInMS() {
        return 110;
    }
}
