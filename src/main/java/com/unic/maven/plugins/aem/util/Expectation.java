/*
  Copyright 2018 the original author or authors.
  Licensed under the Apache License, Version 2.0 the "License";
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.unic.maven.plugins.aem.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import static com.unic.maven.plugins.aem.util.Expectation.Outcome.*;
import static java.lang.Thread.sleep;

/**
 * @author Olaf Otto
 */
public abstract class Expectation<CauseOfFailureType> {
    private FailureCallback<CauseOfFailureType> callback = null;

    public enum Outcome {
        /**
         * Try to {@link Expectation#fulfill() fulfill} the expectation again
         */
        RETRY,

        /**
         * It is no longer possible for the expectation to become {@link #FULFILLED} - abort.
         */
        UNSATISFIABLE,

        /**
         * The expectation is {@link Expectation#fulfill() fulfilled} - done.
         */
        FULFILLED
    }

    /**
     * Allows connecting multiple expectations via AND.
     *
     * @param other must not be <code>null</code>.
     * @return never <code>null</code>.
     */
    public Expectation<CauseOfFailureType> and(@NotNull final Expectation other) {
        final Expectation<CauseOfFailureType> self = this;
        return new Expectation<CauseOfFailureType>() {
            @Override
            protected Outcome fulfill() {
                Outcome first = self.fulfill(), second = other.fulfill();
                if (first == FULFILLED && second == FULFILLED) {
                    return FULFILLED;
                }
                if (first == UNSATISFIABLE || second == UNSATISFIABLE) {
                    return UNSATISFIABLE;
                }

                return RETRY;
            }
        };
    }

    /**
     * Set the callback that is invoked when the expectation fails. Only one callback can be registered.
     *
     * @param callback must not be <code>null</code>
     * @return this instance, never <code>null</code>.
     */
    public Expectation<CauseOfFailureType> onFailure(@NotNull FailureCallback<CauseOfFailureType> callback) {
        this.callback = callback;
        return this;
    }

    /**
     * @param unit must not be <code>null</code>.
     */
    public boolean within(long amount, @NotNull TimeUnit unit) {
        long waited = 0;
        Outcome outcome = null;

        try {
            while (waited < unit.toMillis(amount) && (outcome = fulfill()) == RETRY) {
                if (waited == 0) {
                    firstFailure();
                }

                sleep(getRetryIntervalInMillis());
                waited += getRetryIntervalInMillis();
            }
        } catch (InterruptedException e) {
            // We are asked to stop.
        }
        boolean succeeded = outcome == FULFILLED;
        if (!succeeded) {
            failed(amount, unit);
        }
        return succeeded;
    }

    private int getRetryIntervalInMillis() {
        return 2000;
    }

    protected abstract Outcome fulfill();

    /**
     * Subtypes may want to log when the expectation fails for the first time
     */
    protected void firstFailure() {
    }

    private void failed(long amount, @NotNull TimeUnit unit) {
        if (this.callback != null) {
            this.callback.callback(amount, unit, failureContext());
        }
    }

    /**
     * @return the (last recorded) cause of a failed expectation that caused a {@link Outcome#RETRY} or
     * {@link Outcome#UNSATISFIABLE}. Can be <code>null</code>. The value is provided
     * to the {@link FailureCallback} (see {@link #onFailure(FailureCallback)}) when an expectation {@link #failed fails}.
     */
    @Nullable
    protected CauseOfFailureType failureContext() {
        return null;
    }

    @FunctionalInterface
    public interface FailureCallback<T> {
        void callback(long amount, @NotNull TimeUnit unit, @Nullable T context);
    }
}
