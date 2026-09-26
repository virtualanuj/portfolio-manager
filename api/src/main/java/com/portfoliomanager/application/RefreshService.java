package com.portfoliomanager.application;

import com.portfoliomanager.domain.RefreshStatus;
import com.portfoliomanager.persistence.RefreshRunEntity;
import com.portfoliomanager.persistence.RefreshRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts and reports on refresh runs. The unique index on RUNNING rows is the single-flight guard:
 * of two simultaneous starts exactly one insert succeeds.
 */
@Service
public class RefreshService {

    /** A RUNNING row this old is assumed to belong to a process that died. */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);

    private static final String ABANDONED_RESULT =
            "[{\"symbol\":\"(refresh)\",\"ok\":false,\"message\":\"The server stopped before this run finished\"}]";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RefreshRunRepository runs;
    private final RefreshWorker worker;
    private final RefreshRunner runner;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RefreshService(
            RefreshRunRepository runs,
            RefreshWorker worker,
            RefreshRunner runner,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.runs = runs;
        this.worker = worker;
        this.runner = runner;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Starts a background refresh and returns its id, or throws if one is already running. */
    public UUID start() {
        Instant now = Instant.now(clock);
        failAbandonedRuns(now);

        UUID runId;
        try {
            runId =
                    transactions.execute(
                            s -> runs.saveAndFlush(RefreshRunEntity.started(now)).getId());
        } catch (DataIntegrityViolationException alreadyRunning) {
            throw new RefreshInProgressException(currentRunId());
        }
        runner.run(() -> worker.run(runId));
        return runId;
    }

    public RefreshRunView find(UUID id) {
        return runs.findById(id)
                .map(RefreshService::view)
                .orElseThrow(
                        () -> new NotFoundException("Refresh run %s does not exist".formatted(id)));
    }

    public Optional<RefreshRunView> latest() {
        return runs.findFirstByOrderByStartedAtDesc().map(RefreshService::view);
    }

    private void failAbandonedRuns(Instant now) {
        transactions.executeWithoutResult(
                s ->
                        runs.findByStatusAndStartedAtBefore(
                                        RefreshStatus.RUNNING, now.minus(ABANDONED_AFTER))
                                .forEach(
                                        run -> {
                                            run.finish(RefreshStatus.FAILED, ABANDONED_RESULT, now);
                                            runs.save(run);
                                        }));
    }

    private UUID currentRunId() {
        return runs.findByStatus(RefreshStatus.RUNNING).stream()
                .findFirst()
                .or(runs::findFirstByOrderByStartedAtDesc)
                .map(RefreshRunEntity::getId)
                .orElseThrow(() -> new ConflictException("A refresh is already running"));
    }

    private static RefreshRunView view(RefreshRunEntity run) {
        List<RefreshResultItem> results =
                MAPPER.readValue(run.getResults(), new TypeReference<List<RefreshResultItem>>() {});
        return new RefreshRunView(
                run.getId(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(), results);
    }
}
