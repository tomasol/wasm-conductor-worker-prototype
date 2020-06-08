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
import java.util.Collections;
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
public class QuickJsWorker extends AbstractWorker {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String SCRIPT_JS = "script.js";
    private final String quickJsPath;

    @Autowired
    public QuickJsWorker(ConductorProperties props, ObjectMapper objectMapper, ProcessManager manager) {
        this(props.getMaxWaitMillis(), objectMapper, manager, props.getQuickJsPath());
    }

    QuickJsWorker(long maxWaitMillis, ObjectMapper objectMapper, ProcessManager manager, String quickJsPath) {
        super(maxWaitMillis, objectMapper, manager);
        this.quickJsPath = quickJsPath;
    }

    @Override
    public String getTaskDefName() {
        return "GLOBAL___js";
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    // FIXME: pass script as stdin, remove temp folder handling
    protected TaskResult executeInner(Task task) throws IOException {
        Logger logger = getLogger();
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
        // add console.error that works same way as console.log - just writes all arguments separated by space
        // and adds newline.
        String preamble = String.format("const process = {argv:%s};\n" +
                "console.error = function(...args) { std.err.puts(args.join(' '));std.err.puts('\\n'); }\n",
                objectMapper.writeValueAsString(args));
        script = preamble + script;

        List<String> cmd = Lists.newArrayList("wasmer", "run",
                quickJsPath, "--", "--std", "-e", script);

        logger.debug("Executing {}", cmd);
        boolean outputIsJson = Boolean.parseBoolean((String) task.getInputData().get("outputIsJson"));
        return execute(cmd, null, outputIsJson, taskResult);
    }
}
