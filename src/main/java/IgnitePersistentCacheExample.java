import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IgnitePersistentCacheExample {
    public static void main(String[] args) {
        // Path to your JSON file from project root
        String jsonFilePath = "data.json";

        try (Ignite ignite = Ignition.start("/path_to/config/custom-ignite-config.xml")) {
            if (ignite.cluster().state() == ClusterState.INACTIVE) {
                ignite.cluster().state(ClusterState.ACTIVE); // Activate the cluster
            }

            // Define cache configuration for persistent storage
            CacheConfiguration<String, String> cacheCfg = new CacheConfiguration<>("BuildingAccessEventsCache");
            // Use the persistent region
            cacheCfg.setDataRegionName("persistentRegion");

            // Create or get the existing cache
            var cache = ignite.getOrCreateCache(cacheCfg);

            // To retrieve data from cache after restart, use:
            // var cache = ignite.cache("BuildingAccessEventsCache");

            // Load data from JSON
            Map<String, String> events = loadJson(jsonFilePath);

            // Store data in the cache
            events.forEach(cache::put);

            // Retrieve and print data
            System.out.println("Data stored in the cache");
            cache.forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue()));
        }
    }

    private static Map<String, String> loadJson(String jsonFilePath) {
        try (Reader reader = new FileReader(jsonFilePath)) {
            Gson gson = new Gson();

            Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> events = gson.fromJson(reader, listType);

            Map<String, String> eventMap = new HashMap<>();
            for (Map<String, Object> event : events) {
                String timestamp = (String) event.get("timestamp");
                String eventDetail = "Person " + event.get("person_id") + " " + event.get("event");
                eventMap.put(timestamp, eventDetail);
            }
            return eventMap;
        } catch (IOException e) {
            throw new RuntimeException("Error reading JSON file", e);
        }
    }

}
