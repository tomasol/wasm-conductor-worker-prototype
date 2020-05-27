package prototype.wasmworker;

import org.springframework.stereotype.Component;

@Component
public class ConductorProperties {

    private String conductorRootUri = System.getProperty("conductor.url", "http://localhost:8080/api/");

    private int threadsPerWorker = Integer.getInteger("threadsPerWorker", 5);

    private int maxWaitMillis = Integer.getInteger("WasmWorker.maxWaitMillis", 2000);

    public String getConductorRootUri() {
        return conductorRootUri;
    }

    public void setConductorRootUri(String conductorRootUri) {
        this.conductorRootUri = conductorRootUri;
    }

    public int getThreadsPerWorker() {
        return threadsPerWorker;
    }

    public void setThreadsPerWorker(int threadsPerWorker) {
        this.threadsPerWorker = threadsPerWorker;
    }

    public int getMaxWaitMillis() {
        return maxWaitMillis;
    }

    public void setMaxWaitMillis(int maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }
}
