package prototype.wasmworker.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import prototype.wasmworker.lifecycle.ConductorProperties;
import prototype.wasmworker.proc.ProcessManager;
import prototype.wasmworker.proc.ProcessManager.ExecutionResult;
import prototype.wasmworker.proc.ProcessManager.TimeoutException;

@Component
public class PythonExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ObjectMapper objectMapper;
    private final ProcessManager manager;
    private final long maxWaitMillis;
    private final String pythonBinPath, pythonLibPath, wasmerPath;

    @Autowired
    public PythonExecutor(ObjectMapper objectMapper, ProcessManager manager,
                          ConductorProperties props) {
        this(objectMapper, manager,
                props.getMaxWaitMillis(), props.getPythonBinPath(), props.getPythonLibPath(), props.getWasmerPath());
    }

    public PythonExecutor(ObjectMapper objectMapper, ProcessManager manager,
                          long maxWaitMillis,
                          String pythonBinPath, String pythonLibPath, String wasmerPath) {
        this.maxWaitMillis = maxWaitMillis;
        this.objectMapper = objectMapper;
        this.manager = manager;
        this.pythonBinPath = pythonBinPath;
        this.pythonLibPath = pythonLibPath;
        this.wasmerPath = wasmerPath;
    }

    public ExecutionResult execute(String script, List<String> args) throws
            InterruptedException, TimeoutException, IOException {

        // add args to the script
        String preamble = String.format("argv = %s;", objectMapper.writeValueAsString(args));
        script = preamble + script;

        List<String> cmd = Lists.newArrayList(wasmerPath, "run",
                pythonBinPath, "--mapdir=lib:" + pythonLibPath);
        logger.debug("Executing {} with script '{}'", cmd, script);
        return manager.execute(cmd, script, maxWaitMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isHealthy() {
        try {
            return execute("print('OK')", Collections.singletonList("")).isSuccess();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Unhealthy", e);
        } catch (Exception e) {
            logger.warn("Unhealthy", e);
        }
        return false;
    }
}
