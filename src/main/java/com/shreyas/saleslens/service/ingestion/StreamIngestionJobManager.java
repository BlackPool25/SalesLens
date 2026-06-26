package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamIngestionJobManager {

    private final IngestionJobRepository ingestionJobRepository;
    private final DataSourceRepository dataSourceRepository;

    private final ConcurrentHashMap<UUID, WindowState> activeWindows = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    @Getter
    @Setter
    static class WindowState {
        private final IngestionJob currentJob;
        private final Instant windowOpenedAt;
        private volatile boolean closed = false;

        WindowState(IngestionJob job) {
            this.currentJob = job;
            this.windowOpenedAt = Instant.now();
        }
    }

    /**
     * Returns the active ingestion job for the given data source, creating a new
     * pending job if none exists or the current window is closed.
     * <p>
     * Multiple consumers can call this concurrently — the read lock allows
     * concurrent access while protecting against concurrent window rotation.
     */
    public IngestionJob getOrCreateWindowJob(DataSource source) {
        rwLock.readLock().lock();
        try {
            WindowState state = activeWindows.get(source.getId());
            if (state != null && !state.isClosed()) {
                return state.getCurrentJob();
            }
            return activeWindows.computeIfAbsent(source.getId(),
                    id -> new WindowState(createPendingJob(source))).getCurrentJob();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Rotates (closes) the active window for the given source, removing it from
     * the active map and returning the closed job for downstream processing.
     * <p>
     * Requires exclusive write lock — blocks all concurrent consumers during
     * rotation.
     *
     * @return the closed job, or empty if no active window existed
     */
    public Optional<IngestionJob> rotateWindow(UUID sourceId) {
        rwLock.writeLock().lock();
        try {
            WindowState state = activeWindows.get(sourceId);
            if (state == null || state.isClosed()) {
                return Optional.empty();
            }
            activeWindows.remove(sourceId);
            state.setClosed(true);
            log.info("Rotated window for source {} — job {} closed", sourceId, state.getCurrentJob().getId());
            return Optional.of(state.getCurrentJob());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Checks whether the given source has an active (non-closed) ingestion window.
     */
    public boolean hasActiveWindow(UUID sourceId) {
        WindowState state = activeWindows.get(sourceId);
        return state != null && !state.isClosed();
    }

    /**
     * Returns the current window state for the given source, if one exists.
     */
    public Optional<WindowState> getWindowState(UUID sourceId) {
        return Optional.ofNullable(activeWindows.get(sourceId));
    }

    private IngestionJob createPendingJob(DataSource source) {
        IngestionJob job = new IngestionJob();
        job.setSource(source);
        job.setStatus(JobStatus.PENDING);
        job.setTotalRead(0);
        job.setTotalTransformed(0);
        job.setTotalQualityPass(0);
        job.setTotalQualityFail(0);
        job.setTotalLoaded(0);
        job.setTotalConflicted(0);
        IngestionJob saved = ingestionJobRepository.save(job);
        log.debug("Created pending ingestion job {} for source {}", saved.getId(), source.getId());
        return saved;
    }

}
