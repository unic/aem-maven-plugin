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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.util.function.Consumer;

import static java.nio.ByteBuffer.allocate;
import static java.nio.channels.Channels.newChannel;
import static java.nio.charset.Charset.defaultCharset;

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
        try (ReadableByteChannel readableByteChannel = newChannel(in)) {
            final ByteBuffer buffer = allocate(1024);
            final CharsetDecoder charsetDecoder = defaultCharset().newDecoder();

            StringBuilder lineBuilder = new StringBuilder(1024);
            int read;
            while ((read = readableByteChannel.read(buffer)) != -1) {
                if (read == 0 && process.isAlive()) {
                    Thread.sleep(500);
                    continue;
                }
                buffer.flip();
                CharBuffer charBuffer = charsetDecoder.decode(buffer);
                while (charBuffer.hasRemaining()) {
                    char c = charBuffer.get();

                    if (c == '\r') {
                        // Skip over windows \r
                        continue;
                    }
                    if (c == '\n') {
                        // Send the line to the consumer, omit the newline character
                        consumer.accept(lineBuilder.toString());
                        lineBuilder = new StringBuilder(1024);
                        continue;
                    }
                    lineBuilder.append(c);
                }
                buffer.clear();
                if (!process.isAlive()) {
                    break;
                }
            }
            if (lineBuilder.length() != 0) {
                consumer.accept(lineBuilder.toString());
            }
        } catch (IOException e) {
            log.error("Unable to read from the process stream", e);
        } catch (InterruptedException e) {
            log.debug("Interrupted while waiting for the stream, stopping.", e);
        } finally {
            log.debug("Stopped reading from " + this.process);
        }
    }
}
