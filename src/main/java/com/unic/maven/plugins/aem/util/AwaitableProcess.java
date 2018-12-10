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

import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static java.lang.System.nanoTime;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * @author Olaf Otto
 */
public class AwaitableProcess {
    private final Process process;

    public static AwaitableProcess awaitable(Process process) {
        return new AwaitableProcess(process);
    }

    private AwaitableProcess(Process process) {
        this.process = process;
    }

    public ExecutionResult awaitTermination(long timeout, TimeUnit amount) throws InterruptedException {
        long startedAt = nanoTime();
        long remainingTime = amount.toNanos(timeout);

        do {
            try {
                return new ExecutionResult(true, process.exitValue());
            } catch (IllegalThreadStateException ex) {
                if (remainingTime > 0) {
                    sleep(min(NANOSECONDS.toMillis(remainingTime) + 5, 200));
                }
            }
            remainingTime = amount.toNanos(timeout) - (nanoTime() - startedAt);
        } while (remainingTime > 0);

        return new ExecutionResult(false);
    }

    /**
     * The result of a process execution.
     */
    public static class ExecutionResult {
        private int exitCode = -1;
        private final boolean terminated;

        ExecutionResult(boolean terminated) {
            this.terminated = terminated;
        }

        ExecutionResult(boolean terminated, int exitCode) {
            this.terminated = terminated;
            this.exitCode = exitCode;
        }

        /**
         * @return the process exit code, or <code>-1</code> if the process did not {@link #isTerminated() terminate}.
         */
        public int getExitCode() {
            return exitCode;
        }

        public boolean isTerminated() {
            return terminated;
        }
    }
}
