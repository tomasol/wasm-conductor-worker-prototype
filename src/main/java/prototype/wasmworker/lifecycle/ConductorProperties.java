package prototype.wasmworker.lifecycle;

import org.springframework.stereotype.Component;

@Component
public class ConductorProperties {
    public static final String DEFAULT_QUICKJS_PATH = "./src/main/resources/quickjs/quickjs.wasm";
    public static final String DEFAULT_PYTHON_BIN_PATH = "./src/main/resources/python/bin/python.wasm";
    public static final String DEFAULT_PYTHON_LIB_PATH = "./src/main/resources/python/lib";

    private final String conductorRootUri;
    private final int threadsPerWorker;
    private final int maxWaitMillis;
    private final String quickJsPath;
    private final String pythonBinPath, pythonLibPath;

    public ConductorProperties() {
        String conductorRootUri = System.getProperty("conductor.url", "http://localhost:8080/api/");
        if (!conductorRootUri.endsWith("/")) {
            conductorRootUri += "/";
        }
        this.conductorRootUri = conductorRootUri;
        threadsPerWorker = Integer.getInteger("threadsPerWorker", 5);
        maxWaitMillis = Integer.getInteger("WasmWorker.maxWaitMillis", 2000);
        quickJsPath = System.getProperty("path.quickjs", DEFAULT_QUICKJS_PATH);
        pythonBinPath = System.getProperty("path.python.bin", DEFAULT_PYTHON_BIN_PATH);
        pythonLibPath = System.getProperty("path.python.lib", DEFAULT_PYTHON_LIB_PATH);

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

    public String getPythonBinPath() {
        return pythonBinPath;
    }

    public String getPythonLibPath() {
        return pythonLibPath;
    }
}
