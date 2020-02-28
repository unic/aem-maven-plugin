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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;

/**
 * Tails a file in a non-blocking fashion, thus allowing
 * the logging to be cancelled via interruption.
 *
 * @author Olaf Otto
 */
public class FileReader implements Runnable {
    private final Consumer<String> consumer;
    private final Log log;
    private final File file;

    @NotNull
    public static FileReader follow(@NotNull File file, @NotNull Log log, @NotNull Consumer<String> consumer) {
        return new FileReader(file, consumer, log);
    }

    private FileReader(File file, Consumer<String> consumer, Log log) {
        this.consumer = consumer;
        this.log = log;
        this.file = file;
    }

    @Override
    public void run() {
        try (InputStream in = new BufferedInputStream(new FileInputStream(this.file))) {
            long bytesSkipped = in.skip(this.file.length());
            log.debug("Skipped " + bytesSkipped + " until the end of " + this.file + ".");

            final InputStreamReader reader = new InputStreamReader(in);
            StringBuilder lineBuilder = new StringBuilder(1024);
            char[] buffer = new char[1024];
            int available;

            while (true) {
                if (in.available() == 0 && file.exists()) {
                    sleep(500);
                    continue;
                }

                available = reader.read(buffer);

                if (available == -1) {
                    // EOF
                    break;
                }

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
        } catch (InterruptedException e) {
            log.debug("Interrupted while reading from " + this.file);
        } finally {
            log.debug("Stopped reading from " + this.file);
        }
    }
}
