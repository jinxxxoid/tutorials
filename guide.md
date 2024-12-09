# Using persistent caching with Apache Ignite/GridGain

This tutorial introduces how to use Apache Ignite or GridGain (GG) to add persistent storage for important data. [Persistent](https://www.gridgain.com/docs/latest/developers-guide/persistence/native-persistence) storage ensures durability, fault tolerance, and data availability after system restarts.

We will demonstrate this using a simple example: logging people entering and exiting a building. This example uses Apache Ignite 2.16 (or you can use GridGain 8), but for GridGain 9 or Apache Ignite 3, persistence is handled with [tables](https://www.gridgain.com/docs/gridgain9/latest/developers-guide/cache).

We will use a JSON file as the input data source, though other formats like relational databases, CSV files, or real-time streams (e.g., Apache Kafka) can also be integrated into Ignite/GridGain.

The complete example implementation is available in the GitHub [repository](https://github.com/jinxxxoid/tutorials).

---

## Prerequisites

To follow this tutorial, you will need:

- A running Apache Ignite or GridGain cluster.
- JDK version 8 or later. For details, check the compatibility guides for GridGain ([GG8](https://www.gridgain.com/docs/latest/developers-guide/setup), [GG9](https://www.gridgain.com/docs/gridgain9/latest/quick-start/getting-started-guide)) or [Apache Ignite](https://ignite.apache.org/docs/latest/quick-start/java#installing-ignite).
- Ignite/GridGain binaries and configuration files.
- The [Gson](https://github.com/google/gson) library for parsing JSON (or similar library).
- The input JSON file (`data.json`) containing sample data.
- Basic knowledge of Java programming and Ignite/GridGain concepts.

---

## Add persistence to Java application

### Overview

In this part of the tutorial, we will:

1. Modify the Ignite configuration to enable persistent storage.
2. Add a persistent cache in the Java code and explain why each modification is necessary.
3. Validate the setup by storing, restarting, and retrieving the data to ensure persistence works as expected.

---

### Modifying Ignite configuration

To enable persistence, you need to modify the Ignite configuration and specify the path to this configuration in your code. The configuration file is usually located in the `config` directory of the Ignite installation or a custom directory specified during setup.

- **Change both config and code**: Ensure the cluster's configuration defines a persistent region and your Java application specifies the configuration path.
- **Run cluster with ****`--config`**** and specify config in code**: This ensures both the cluster and application use the same persistent settings. If the path is omitted in the code, the application defaults to in-memory mode, causing errors like `CacheNotFoundException` when interacting with persistent caches.
- **Environment-specific paths**:
    - In **test environments**, use relative paths or mock configurations.
    - In **production**, manage paths dynamically using environment variables or configuration management tools.

To enable persistence, update your `ignite-config.xml` with the following configuration:

```xml
<dataStorageConfiguration>
    <dataRegionConfigurations>
        <dataRegionConfiguration>
            <name>persistentRegion</name>
            <persistenceEnabled>true</persistenceEnabled>
        </dataRegionConfiguration>
    </dataRegionConfigurations>
</dataStorageConfiguration>
```

- This configuration defines a persistent data region (`persistentRegion`) where all cache data will be stored persistently. Setting `persistenceEnabled` to `true` ensures the data is saved to disk.

Run Ignite with this configuration for Unix systems:
```bash
./ignite.sh --config /path/to/ignite-config.xml
```
or Windows:
```bash
ignite.bat --config C:\path\to\ignite-config.xml
````

Specify a path to config in your Java application code:
```java
Ignite ignite = Ignition.start("/path/to/ignite-config.xml");
```
---

### Adding persistent cache in code


If your cluster has at least one data region in which **persistence** is enabled, the cluster is `INACTIVE` when you start it for the first time. In the inactive state, all operations are prohibited. The cluster must be activated before you can create caches and upload data. Cluster activation sets the current set of server nodes as the [baseline topology](https://www.gridgain.com/docs/latest/developers-guide/baseline-topology).

When you restart the cluster, it is activated automatically as soon as all nodes that are registered in the baseline topology join in. However, if some nodes do not join after a restart, you must to activate the cluster manually.
To enable auto-adjustment for dynamic clusters, add this line:
```java
ignite.cluster().baselineAutoAdjustEnabled(true);
```
**Use baseline topology**:
- If your cluster is dynamic (nodes frequently join or leave), enabling baseline topology ensures data consistency and proper management of persistence.
- For example, in a multi-node setup tracking building entry/exit events, enabling baseline auto-adjustment allows new nodes to join seamlessly, maintaining consistency across the cluster.

In our example, for a single-node Ignite setup, baseline topology **is not required** because the single node handles all data operations, and there are no topology adjustments to manage.


**Why ensure cluster is active?**

- Persistent storage and other cache operations require the cluster to be in an `ACTIVE` state.
- If the cluster is `INACTIVE`, operations like adding data or retrieving cache entries will fail.

**How startup logs should look?**

- **Inactive cluster:**

  ```
  [22:42:25] Ignite node started OK (id=ecfd93ef)
  [22:42:25] >>> Ignite cluster is in INACTIVE state (limited functionality available). Use control.(sh|bat) script or IgniteCluster.state(ClusterState.ACTIVE) to change the state.
  ```

  This log indicates the cluster is inactive. You need to activate it using either the control script or programmatically in code.


- **Cluster with auto-activation:**

  ```
  [22:43:11] Ignite node started OK (id=8b7fc018)
  [22:43:11] Topology snapshot [ver=1, locNode=bba30b0b, servers=1, clients=0, state=INACTIVE, CPUs=4, offheap=3.2GB, heap=2.0GB]
  [22:43:11] ^-- Baseline [id=0, size=1, online=1, offline=0]
  [22:43:11] ^-- All baseline nodes are online, will start auto-activation
  ```

  This log indicates that all baseline nodes are online, and the cluster is preparing for auto-activation.

Example code to check and activate the cluster if needed:

```java
if (ignite.cluster().state() == ClusterState.INACTIVE) {
    ignite.cluster().state(ClusterState.ACTIVE);
}
```

Update your Java code to define and use a cache linked to the persistent region:

```java
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.cluster.ClusterState;

public class IgnitePersistentCacheExample {
    public static void main(String[] args) {
        try (Ignite ignite = Ignition.start("/path/to/ignite-config.xml")) {
            // Activate cluster if inactive
            if (ignite.cluster().state() == ClusterState.INACTIVE) {
                ignite.cluster().state(ClusterState.ACTIVE);
            }

            // Define a cache linked to the persistent region
            CacheConfiguration<String, String> cacheCfg = new CacheConfiguration<>("BuildingAccessEventsCache");
            cacheCfg.setDataRegionName("persistentRegion");
            var cache = ignite.getOrCreateCache(cacheCfg);

            // Load data from JSON file and store in cache
            Map<String, String> events = loadJson("data.json");
            events.forEach(cache::put);

            // Retrieve and print stored data
            System.out.println("Data stored in the cache:");
            cache.forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue()));
        }
    }

    private static Map<String, String> loadJson(String jsonFilePath) {
        // Implement reading data from JSON via Google Gson or similar library 
    }
}
```

- **Cache configuration**: Specifies the cache (`BuildingAccessEventsCache`) and links it to the `persistentRegion` defined in the configuration.
- **Cluster activation**: Ensures the cluster is in `ACTIVE` state, which is required for cache operations.
- **Sample data**: Demonstrates storing and retrieving data from the persistent cache.
---
**Run Java application and check the output**:

Use sample JSON data for testing purposes, for example save this as `data.json` in the root of your Java app project:

```java
[
  {"timestamp": "2024-12-08T09:02:30", "event": "enter", "person_id": 1},
  {"timestamp": "2024-12-08T09:10:10", "event": "enter", "person_id": 2},
  {"timestamp": "2024-12-08T09:05:11", "event": "exit", "person_id": 1},
  {"timestamp": "2024-12-08T09:11:00", "event": "enter", "person_id": 3},
  {"timestamp": "2024-12-08T09:10:53", "event": "exit", "person_id": 2},
  {"timestamp": "2024-12-08T09:12:20", "event": "exit", "person_id": 3}
]
```

When you run the Java application for the first time, it will store data in the cache. You should see the following output in the terminal:
```java
Data stored in the cache:
2024-12-08T09:02:30 -> Person 1.0 enter
2024-12-08T09:05:11 -> Person 1.0 exit
2024-12-08T09:11:00 -> Person 3.0 enter
2024-12-08T09:10:10 -> Person 2.0 enter
2024-12-08T09:12:20 -> Person 3.0 exit
2024-12-08T09:10:53 -> Person 2.0 exit
```


### Validating the setup

**1. Stop Ignite**:

    - Completely shut down the Ignite node. If you are running Ignite in the terminal, use `Ctrl+C` to stop the node gracefully. Alternatively, use system commands or tools to stop any running Ignite processes.

    - This ensures that the cluster restarts from its persistent storage and verifies whether the data has been saved correctly.

**2. Restart Ignite**:

    - Ensure the cluster restarts with the modified configuration (`ignite-config.xml`) to reinitialize the persistent region.

    - Restart the node using the same configuration:

      ```bash
      ./ignite.sh --config /path/to/ignite-config.xml
      ```

**3. Retrieve data**:

After the cluster is running, run the Java application again. This time, modify the program to retrieve data from the cache instead of adding new data.

**Change code to retrieve cached data**:
- In the original code, data was added to the cache using:
```java 
var cache = ignite.getOrCreateCache(cacheCfg); 
Map<String, String> events = loadJson("data.json");
events.forEach(cache::put);
 ``` 
- Replace this with retrieving data from existing cache:
```java 
var cache = ignite.cache("BuildingAccessEventsCache");
System.out.println("Data retrieved from the cache after restart:");
cache.forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue())); 
```

- `ignite.cache("BuildingAccessEventsCache")` refers to the existing cache created earlier. This ensures the data stored before the cluster restart is retrieved correctly.

**4. Verify the output after Ignite restart**: After restarting the cluster and running the updated Java program, you should see the following output:
```
Data retrieved from the cache after restart:
2024-12-08T09:02:30 -> Person 1.0 enter
2024-12-08T09:05:11 -> Person 1.0 exit
2024-12-08T09:11:00 -> Person 3.0 enter
2024-12-08T09:10:10 -> Person 2.0 enter
2024-12-08T09:12:20 -> Person 3.0 exit
2024-12-08T09:10:53 -> Person 2.0 exit
```

- This confirms that the data persisted across the cluster restart and the cache configuration is correctly set up.

## Building upon this tutorial

In this tutorial, we **learned to**:
- Configure Apache Ignite/GridGain for persistent storage.
- Add and validate a cache in a Java application.
- Retrieve persisted data across cluster restarts.

Now that you’ve completed the basics, **consider experimenting with**:
- Multi-node cluster setups to explore Ignite’s distributed capabilities.
- Advanced cache configurations, such as enabling transactions or using different atomicity modes.
- Integrating other data sources like databases or real-time streams.

For further learning, check out:
- [Common troubleshoting tips](https://www.gridgain.com/docs/latest/perf-troubleshooting-guide/troubleshooting)
- [More on configuring cache](https://www.gridgain.com/docs/latest/developers-guide/configuring-caches/configuration-overview)
- [More info on clustering concepts](https://www.gridgain.com/docs/latest/developers-guide/clustering/clustering)

