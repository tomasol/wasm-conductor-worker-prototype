package prototype.wasmworker.proc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ProcessManager {

    ExecutionResult execute(List<String> cmd, String stdIn, long timeout, TimeUnit unit)
            throws
            InterruptedException,
            IOException,
            TimeoutException
            ;


    class ExecutionResult {
        private final int exitStatus;
        private final String stdOut, stdErr;

        public ExecutionResult(int exitStatus, String stdOut, String stdErr) {
            this.exitStatus = exitStatus;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        public int getExitStatus() {
            return exitStatus;
        }

        public String getStdOut() {
            return stdOut;
        }

        public String getStdErr() {
            return stdErr;
        }

        public boolean isSuccess() {
            return exitStatus == 0;
        }
    }

    class TimeoutException extends Exception {

    }
}

