package prototype.wasmworker.proc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NativeProcessManager implements ProcessManager {
    private static final Logger logger = LoggerFactory.getLogger(NativeProcessManager.class);

    @Override
    public ExecutionResult execute(List<String> cmd, long timeout, TimeUnit unit) throws
            InterruptedException,
            IOException,
            TimeoutException {
        logger.debug("Executing {}", cmd);
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        Process process = processBuilder.start();
        boolean hasExitted = process.waitFor(timeout, unit);
        if (hasExitted) {
            String stdOut = new String(process.getInputStream().readAllBytes());
            String stdErr = new String(process.getErrorStream().readAllBytes());
            // wasmtime appends \n, remove it
            if (stdOut.endsWith("\n")) {
                stdOut = stdOut.substring(0, stdOut.length() - 1);
            }
            return new ExecutionResult(process.exitValue(), stdOut, stdErr);
        } else {
            process.destroyForcibly();
            throw new TimeoutException();
        }
    }
}
