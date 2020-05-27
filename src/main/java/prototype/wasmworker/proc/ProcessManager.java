package prototype.wasmworker.proc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ProcessManager {

    String execute(List<String> cmd, long timeout, TimeUnit unit)
            throws
            InterruptedException,
            IOException,
            NonZeroExitStatusException,
            TimeoutException
            ;

    class NonZeroExitStatusException extends Exception {
        final int exitStatus;

        public NonZeroExitStatusException(int exitStatus) {
            this.exitStatus = exitStatus;
        }

        public int getExitStatus() {
            return exitStatus;
        }
    }

    class TimeoutException extends Exception {

    }
}

