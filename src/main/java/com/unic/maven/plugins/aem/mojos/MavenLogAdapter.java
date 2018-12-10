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
package com.unic.maven.plugins.aem.mojos;

import org.apache.commons.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author adrian.gygax
 */
public class MavenLogAdapter implements Log {

    private final org.apache.maven.plugin.logging.Log log;

    MavenLogAdapter(@NotNull org.apache.maven.plugin.logging.Log log) {
        this.log = Objects.requireNonNull(log);
    }

    @Override public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    @Override public boolean isFatalEnabled() {
        return log.isErrorEnabled();
    }

    @Override public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override public boolean isTraceEnabled() {
        return log.isDebugEnabled();
    }

    @Override public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    @Override public void trace(Object message) {
        log.debug(message.toString());
    }

    @Override public void trace(Object message, Throwable t) {
        log.debug(message.toString(), t);
    }

    @Override public void debug(Object message) {
        log.debug(message.toString());
    }

    @Override public void debug(Object message, Throwable t) {
        log.debug(message.toString(), t);
    }

    @Override public void info(Object message) {
        log.info(message.toString());
    }

    @Override public void info(Object message, Throwable t) {
        log.info(message.toString(), t);
    }

    @Override public void warn(Object message) {
        log.warn(message.toString());
    }

    @Override public void warn(Object message, Throwable t) {
        log.warn(message.toString(), t);
    }

    @Override public void error(Object message) {
        log.error(message.toString());
    }

    @Override public void error(Object message, Throwable t) {
        log.error(message.toString(), t);
    }

    @Override public void fatal(Object message) {
        log.error(message.toString());
    }

    @Override public void fatal(Object message, Throwable t) {
        log.error(message.toString(), t);
    }
}
