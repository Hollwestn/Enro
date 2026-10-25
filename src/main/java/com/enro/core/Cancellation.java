package com.enro.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Cancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
    public void check() { if (isCancelled()) throw new CancellationException("Operation cancelled"); }
}
