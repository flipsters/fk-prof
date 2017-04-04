package fk.prof.nfr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by gaurav.ashok on 03/04/17.
 */
public class RandomWorkloadApp {

    public  static final ObjectMapper om = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        if(args.length < 1) {
            System.err.println("too few params");
            return;
        }
        int jsonSerDeThrdCount = Integer.parseInt(args[0]);

        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ExecutorService execSvc = Executors.newCachedThreadPool();

        // json workload
        startJsonSerDeWorkLoad(jsonSerDeThrdCount, execSvc);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Stopping");
                execSvc.shutdownNow();
                execSvc.awaitTermination(5000, TimeUnit.MILLISECONDS);
                System.out.println("Stopped");
            }
            catch (Exception e) {
                System.err.println("Issue in shutdown hook: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }

    private static void startJsonSerDeWorkLoad(int threadCount, ExecutorService execSvc) {
        final JsonGenerator jsonGen = new JsonGenerator(1000);
        AtomicLong counter = new AtomicLong(0);

        for(int i = 0; i < threadCount; ++i) {
            execSvc.submit(() -> {
                while(true) {
                    Map<String, Object> json = jsonGen.genJsonMap(6, 0.35f, 0.15f);
                    try {
                        String str = om.writeValueAsString(json);
                        Map<String, Object> obj = om.readValue(str, Map.class);
                        counter.addAndGet(obj.size());

                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    } catch (Exception e) {
                        System.err.println("Error while ser/de using jackosn ObjectMapper: " + e.getMessage());
                    }
                }
            });
        }
    }
}
