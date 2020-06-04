package prototype.wasmworker.lifecycle;

import org.springframework.stereotype.Component;

@Component
public class ConductorProperties {
    public static final String DEFAULT_QUICKJS_WASM = "./src/main/resources/qjs.wasm";
    private final String conductorRootUri;
    private final int threadsPerWorker;
    private final int maxWaitMillis;
    private final String quickJsPath;

    public ConductorProperties() {
         String conductorRootUri = System.getProperty("conductor.url", "http://localhost:8080/api/");
         if (!conductorRootUri.endsWith("/")) {
             conductorRootUri += "/";
         }
         this.conductorRootUri = conductorRootUri;
         threadsPerWorker = Integer.getInteger("threadsPerWorker", 5);
         maxWaitMillis = Integer.getInteger("WasmWorker.maxWaitMillis", 2000);
         quickJsPath = System.getProperty("quickjs.wasm", DEFAULT_QUICKJS_WASM);
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

    public String getQuickJsPath() {
        return quickJsPath;
    }
}
