package prototype.wasmworker.worker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import prototype.wasmworker.lifecycle.ConductorProperties;
import prototype.wasmworker.proc.ProcessManager;

// Based on https://wapm.io/package/python
@Component
public class PythonWorker extends AbstractWorker {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String SCRIPT_JS = "script.py";
    private final String pythonBinPath, pythonLibPath;

    @Autowired
    public PythonWorker(ConductorProperties props, ObjectMapper objectMapper, ProcessManager manager) {
        this(props.getMaxWaitMillis(), objectMapper, manager, props.getPythonBinPath(), props.getPythonLibPath());
    }

    PythonWorker(long maxWaitMillis, ObjectMapper objectMapper, ProcessManager manager,
                 String pythonBinPath, String pythonLibPath) {
        super(maxWaitMillis, objectMapper, manager);
        this.pythonBinPath = pythonBinPath;
        this.pythonLibPath = pythonLibPath;
    }

    @Override
    public String getTaskDefName() {
        return "GLOBAL___py";
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected TaskResult executeInner(Task task) throws IOException {
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

        logger.debug("Executing {} with script '{}'", cmd, script);
        boolean outputIsJson = Boolean.parseBoolean((String) task.getInputData().get("outputIsJson"));
        return execute(cmd, script, outputIsJson, taskResult);
    }
}
