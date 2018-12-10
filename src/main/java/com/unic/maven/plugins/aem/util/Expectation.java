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

import java.util.concurrent.TimeUnit;

import static com.unic.maven.plugins.aem.util.Expectation.Outcome.FULFILLED;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.RETRY;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.UNSATISFIABLE;
import static java.lang.Thread.sleep;

/**
 * @author Olaf Otto
 */
public abstract class Expectation {
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
     * @param other must not be <code>null</code>.
     * @return never <code>null</code>.
     */
    public Expectation and(@NotNull final Expectation other) {
        final Expectation self = this;
        return new Expectation() {
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
            failed();
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

    /**
     * Subtypes may want to log when the expectation fails.
     */
    protected void failed() {
    }
}
