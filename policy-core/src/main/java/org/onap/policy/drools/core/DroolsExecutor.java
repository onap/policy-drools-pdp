package org.onap.policy.drools.core;

import java.util.concurrent.Executor;
import org.drools.core.WorkingMemory;

/**
 * An executor that executes its tasks by inserting {@link DroolsRunnable} objects into
 * the working memory of a running session.
 */
public class DroolsExecutor implements Executor {
    private final WorkingMemory workingMemory;

    /**
     * Constructs the object.
     *
     * @param workingMemory where tasks should be injected
     */
    public DroolsExecutor(WorkingMemory workingMemory) {
        this.workingMemory = workingMemory;
    }

    @Override
    public void execute(Runnable command) {
        DroolsRunnable runnable = command::run;
        workingMemory.insert(runnable);
    }
}
