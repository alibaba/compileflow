package com.alibaba.compileflow.engine.deploy.detector;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.deploy.event.FlowChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * An implementation of {@link FlowChangeDetector} that monitors a local file
 * system directory for changes to process definition files.
 * <p>
 * This detector uses Java's {@link WatchService} API to efficiently listen for
 * file creation and modification events in a specified directory. It operates
 * on a dedicated background daemon thread to avoid blocking the main application.
 *
 * @author yusu
 */
public class FileSystemChangeDetector implements FlowChangeDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemChangeDetector.class);

    private static final long DEBOUNCE_DELAY_MS = 300;

    private final Path watchPath;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "compileflow-fs-detector");
        t.setDaemon(true);
        return t;
    });
    // Debounce mechanism to avoid recompiling the same file repeatedly in a short time window
    private final ConcurrentHashMap<String, Long> lastChangeTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "compileflow-fs-debounce");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    /**
     * Creates a new detector that monitors the given directory path.
     *
     * @param directoryPath The path to the directory to watch for changes.
     * @throws IllegalArgumentException if the path is not a valid, accessible directory.
     */
    public FileSystemChangeDetector(String directoryPath) {
        this.watchPath = Paths.get(directoryPath);
        if (!Files.isDirectory(this.watchPath)) {
            throw new IllegalArgumentException("Path must be a directory: " + directoryPath);
        }
    }

    @Override
    public void start(Consumer<FlowChangeEvent> onFlowChange) {
        executor.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                watchPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                LOGGER.info("File system detector started: watching for changes in {}", watchPath.toAbsolutePath());

                while (running) {
                    WatchKey key;
                    try {
                        // Block until a key is available
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        // This is the expected path for a clean shutdown.
                        Thread.currentThread().interrupt();
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            LOGGER.warn("WatchService event overflow detected. Some file changes may have been lost.");
                            continue;
                        }

                        Path fileNamePath = (Path) event.context();
                        Path fullPath = watchPath.resolve(fileNamePath);
                        handleFileEvent(fullPath, onFlowChange);
                    }
                    if (!key.reset()) {
                        LOGGER.warn("WatchKey has become invalid. Stopping file system detector for path: {}", watchPath);
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to initialize file system watch service for path: {}", watchPath.toAbsolutePath(), e);
            }
            LOGGER.info("File system detector thread terminated for path: {}", watchPath.toAbsolutePath());
        });
    }

    /**
     * Handles a detected file system event.
     * <p>
     * If the file has a recognized process definition extension (e.g., .bpmn, .bpm),
     * it constructs a {@link FlowChangeEvent} and passes it to the registered callback.
     *
     * @param filePath     The full path of the file that changed.
     * @param onFlowChange The callback to invoke.
     */
    private void handleFileEvent(Path filePath, Consumer<FlowChangeEvent> onFlowChange) {
        String fileName = filePath.getFileName().toString();
        // A simple filter to avoid processing irrelevant files like .tmp or .swp
        if (fileName.endsWith(".bpmn") || fileName.endsWith(".bpmn20") || fileName.endsWith(".bpm")) {
            String code = fileName.substring(0, fileName.lastIndexOf('.'));

            // Debounce logic: if a file changes multiple times within a short window, only process the last one
            long now = System.currentTimeMillis();
            lastChangeTime.put(code, now);

            // Schedule delayed execution; newer changes overwrite the timestamp
            debounceExecutor.schedule(() -> {
                Long recordedTime = lastChangeTime.get(code);
                // Only process if the recorded timestamp equals the current one, implying it's the last change
                if (recordedTime != null && recordedTime.equals(now)) {
                    LOGGER.info("Detected change, processing update for process file: {}", fileName);
                    onFlowChange.accept(FlowChangeEvent.of(ProcessSource.fromFile(code, filePath.toFile())));
                    lastChangeTime.remove(code); // Cleanup processed entry
                } else {
                    LOGGER.debug("Skipping stale file change event for {} (a newer change was detected).", fileName);
                }
            }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        LOGGER.info("Stopping file system detector for path: {}...", watchPath.toAbsolutePath());

        // Graceful shutdown of executors
        executor.shutdown();
        debounceExecutor.shutdown();

        try {
            // Interrupt the blocking watchService.take() call
            if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
            if (!debounceExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                debounceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            debounceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            // Cleanup debounce state
            lastChangeTime.clear();
            LOGGER.info("File system detector stopped for path: {}", watchPath.toAbsolutePath());
        }
    }

}
