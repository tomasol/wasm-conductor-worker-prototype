package prototype.wasmworker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.task.WorkflowTaskCoordinator;
import com.netflix.conductor.client.task.WorkflowTaskCoordinator.Builder;
import com.netflix.conductor.client.worker.Worker;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ConductorProperties properties;

    @Bean
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    @Bean
    public TaskClient taskClient() {
        logger.info("Using {} as conductor endpoint", properties.getConductorRootUri());
        TaskClient taskClient = new TaskClient();
        taskClient.setRootURI(properties.getConductorRootUri());
        return taskClient;
    }

    @Bean
    public WorkflowTaskCoordinator taskCoordinator(Collection<Worker> workers) {
        return new Builder()
                .withTaskClient(taskClient())
                .withWorkers(workers)
                .withThreadCount(workers.size() * properties.getThreadsPerWorker())
                .build();
    }

}
