package prototype.wasmworker.lifecycle;

import com.netflix.conductor.client.task.WorkflowTaskCoordinator;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;
import prototype.wasmworker.proc.NativeProcessManager;
import prototype.wasmworker.proc.ProcessManager.ExecutionResult;
import prototype.wasmworker.worker.PythonExecutor;
import prototype.wasmworker.worker.QuickJsExecutor;

@Configuration
public class ConductorLifecycle implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(NativeProcessManager.class);

    private final WorkflowTaskCoordinator taskCoordinator;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ConductorProperties props;
    private final PythonExecutor pythonExecutor;
    private final QuickJsExecutor quickJsExecutor;

    public ConductorLifecycle(WorkflowTaskCoordinator taskCoordinator, ConductorProperties props,
                              PythonExecutor pythonExecutor,
                              QuickJsExecutor quickJsExecutor) {
        this.taskCoordinator = taskCoordinator;
        this.props = props;
        this.pythonExecutor = pythonExecutor;
        this.quickJsExecutor = quickJsExecutor;
    }

    @Override
    public void start() {
        taskCoordinator.init();
        isRunning.set(true);
        // check that wasmer can be run
        List<String> cmd = Arrays.asList(props.getWasmerPath(), "-V");
        try {
            ExecutionResult wasmerResult = new NativeProcessManager().execute(
                    cmd, null,
                    1, TimeUnit.SECONDS);
            if (!wasmerResult.isSuccess()) {
                throw new IllegalStateException("Wrong exit status:" + wasmerResult.getExitStatus());
            }
        } catch (Exception e) {
            logger.error("Cannot run wasmer: {}", cmd, e);
            throw new IllegalStateException("Cannot run wasmer", e);
        }
        // check python health
        if (!pythonExecutor.isHealthy()) {
            logger.warn("Python executor reports UNHEALTHY");
        } else {
            logger.info("Python executor reports HEALTHY");
        }
        // check qjs health
        if (!quickJsExecutor.isHealthy()) {
            logger.warn("QuickJs executor reports UNHEALTHY");
        } else {
            logger.info("QuickJs executor reports HEALTHY");
        }
    }

    @Override
    public void stop() {
        taskCoordinator.shutdown();
        isRunning.set(false);
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }
}
