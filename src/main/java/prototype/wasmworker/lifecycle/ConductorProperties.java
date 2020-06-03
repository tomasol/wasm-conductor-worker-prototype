package prototype.wasmworker.lifecycle;

import org.springframework.stereotype.Component;

@Component
public class ConductorProperties {
    private final String conductorRootUri;
    private final int threadsPerWorker;
    private final int maxWaitMillis;

    public ConductorProperties() {
         String conductorRootUri = System.getProperty("conductor.url", "http://localhost:8080/api/");
         if (!conductorRootUri.endsWith("/")) {
             conductorRootUri += "/";
         }
         this.conductorRootUri = conductorRootUri;
         threadsPerWorker = Integer.getInteger("threadsPerWorker", 5);
         maxWaitMillis = Integer.getInteger("WasmWorker.maxWaitMillis", 2000);
    }

    public String getConductorRootUri() {
        return conductorRootUri;
    }

    public int getThreadsPerWorker() {
        return threadsPerWorker;
    }

    public int getMaxWaitMillis() {
        return maxWaitMillis;
    }
}
