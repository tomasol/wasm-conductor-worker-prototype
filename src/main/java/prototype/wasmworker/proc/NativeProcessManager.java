package prototype.wasmworker.proc;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NativeProcessManager implements ProcessManager {
    private static final Logger logger = LoggerFactory.getLogger(NativeProcessManager.class);

    @Override
    public ExecutionResult execute(List<String> cmd, String stdIn, long timeout, TimeUnit unit) throws
            InterruptedException,
            IOException,
            TimeoutException {
        logger.debug("Executing {}", cmd);
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        Stopwatch stopwatch = Stopwatch.createStarted();
        Process process = processBuilder.start();
        if (stdIn != null && stdIn.isEmpty() == false) {
            IOUtils.write(stdIn, process.getOutputStream(), StandardCharsets.UTF_8);
            process.getOutputStream().close();
        }
        boolean hasExitted = process.waitFor(timeout, unit);
        if (hasExitted) {
            String stdOut = new String(process.getInputStream().readAllBytes());
            String stdErr = new String(process.getErrorStream().readAllBytes());
            logger.trace("Exited with {} in {}ms, stdOut: '{}', stdErr: '{}'", process.exitValue(),
                    stopwatch.elapsed(TimeUnit.MILLISECONDS), stdOut, stdErr);
            return new ExecutionResult(process.exitValue(), stdOut, stdErr);
        } else {
            String stdOut = new String(process.getInputStream().readNBytes(process.getInputStream().available()));
            String stdErr = new String(process.getInputStream().readNBytes(process.getErrorStream().available()));
            logger.debug("Timeout while executing {}, stdOut: '{}', stdErr: '{}'", cmd, stdOut, stdErr);
            process.destroyForcibly();
            throw new TimeoutException();
        }
    }
}
