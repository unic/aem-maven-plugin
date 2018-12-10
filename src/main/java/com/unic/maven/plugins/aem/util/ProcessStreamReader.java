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

import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * Reads the stdout / stderr of a given process in a non-blocking fashion, thus allowing
 * the logging to be cancelled via interruption.
 *
 * @author Olaf Otto
 */
public class ProcessStreamReader implements Runnable {
    private final InputStream in;
    private final Consumer<String> consumer;
    private final Log log;
    private final Process process;

    @NotNull
    public static ProcessStreamReader followProcessErrorStream(@NotNull Process process, @NotNull Log log, @NotNull Consumer<String> consumer) {
        return new ProcessStreamReader(process.getErrorStream(), consumer, log, process);
    }

    @NotNull
    public static ProcessStreamReader followProcessInputStream(@NotNull Process process, @NotNull Log log, @NotNull Consumer<String> consumer) {
        return new ProcessStreamReader(process.getInputStream(), consumer, log, process);
    }

    private ProcessStreamReader(InputStream in, Consumer<String> consumer, Log log, Process process) {
        this.in = in;
        this.consumer = consumer;
        this.log = log;
        this.process = process;
    }

    @Override
    public void run() {
        try {
            final InputStreamReader reader = new InputStreamReader(in);
            StringBuilder lineBuilder = new StringBuilder(1024);
            char[] buffer = new char[1024];
            int available;
            while (process.isAlive() && (available = reader.read(buffer)) != -1) {
                for (int i = 0; i < available; ++i) {
                    if (buffer[i] == '\r') {
                        // Skip over windows \r
                        continue;
                    }
                    if (buffer[i] == '\n') {
                        // Send the line to the consumer, omit the newline character
                        consumer.accept(lineBuilder.toString());
                        lineBuilder = new StringBuilder(1024);
                        continue;
                    }

                    lineBuilder.append(buffer[i]);
                }
            }
            if (lineBuilder.length() != 0) {
                consumer.accept(lineBuilder.toString());
            }
        } catch (IOException e) {
            log.error("Unable to read from the process stream", e);
        } finally {
            log.debug("Stopped reading from " + this.process);
        }
    }
}
