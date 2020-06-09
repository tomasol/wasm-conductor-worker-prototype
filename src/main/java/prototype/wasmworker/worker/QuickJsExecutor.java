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
public class QuickJsExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ObjectMapper objectMapper;
    private final ProcessManager manager;
    private final long maxWaitMillis;
    private final String quickJsPath, wasmerPath;

    @Autowired
    public QuickJsExecutor(ObjectMapper objectMapper, ProcessManager manager, ConductorProperties props) {
        this(objectMapper, manager,
                props.getMaxWaitMillis(), props.getQuickJsPath(), props.getWasmerPath());
    }

    public QuickJsExecutor(ObjectMapper objectMapper, ProcessManager manager,
                           long maxWaitMillis, String quickJsPath,
                           String wasmerPath) {
        this.maxWaitMillis = maxWaitMillis;
        this.objectMapper = objectMapper;
        this.manager = manager;
        this.quickJsPath = quickJsPath;
        this.wasmerPath = wasmerPath;
    }

    public ExecutionResult execute(String script, List<String> args) throws
            InterruptedException, TimeoutException, IOException {
        // add args to the script
        // add console.error that works same way as console.log - just writes all arguments separated by space
        // and adds newline.
        String preamble = String.format("const process = {argv:%s};\n" +
                        "console.error = function(...args) { std.err.puts(args.join(' '));std.err.puts('\\n'); }\n",
                objectMapper.writeValueAsString(args));
        script = preamble + script;

        List<String> cmd = Lists.newArrayList(wasmerPath, "run",
                quickJsPath, "--", "--std", "-e", script);

        logger.debug("Executing {}", cmd);
        return manager.execute(cmd, null, maxWaitMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isHealthy() {
        try {
            return execute("console.log('ok')", Collections.singletonList("")).isSuccess();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Unhealthy", e);
        } catch (Exception e) {
            logger.warn("Unhealthy", e);
        }
        return false;
    }
}
