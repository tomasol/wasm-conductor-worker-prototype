package prototype.wasmworker.worker;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Based on https://wapm.io/package/python
@Component
public class PythonWorker extends AbstractWorker {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String SCRIPT_JS = "script.py";
    private final PythonExecutor pythonExecutor;

    @Autowired
    public PythonWorker(ObjectMapper objectMapper, PythonExecutor pythonExecutor) {
        super(objectMapper);
        this.pythonExecutor = pythonExecutor;
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
    protected TaskResult executeInner(Task task) {
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
        boolean outputIsJson = Boolean.parseBoolean((String) task.getInputData().get("outputIsJson"));
        Executable executable = () -> pythonExecutor.execute(script, args);
        return execute(executable, outputIsJson, taskResult);
    }
}
