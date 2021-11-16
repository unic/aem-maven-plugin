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
package com.unic.maven.plugins.aem.core.httpactions;

import com.unic.maven.plugins.aem.util.Expectation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.SimpleLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import unirest.HttpResponse;
import unirest.Unirest;
import unirest.UnirestException;

import java.net.URI;

import static com.unic.maven.plugins.aem.util.ExceptionUtil.getRootCause;
import static java.lang.Math.pow;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class RetryableHttpAction<ResponseType, ResultType> {
    private static final int DEFAULT_DEPLOY_RETRIES = 3;

    private final Configuration configuration;

    private int retries = 0;

    RetryableHttpAction(Configuration configuration) {
        this.configuration = configuration;
    }

    public ResultType run() {
        log(startMessage());

        while (true) {
            ++retries;

            HttpResponse<ResponseType> response;
            try {
                response = perform();
            } catch (UnirestException | InterruptedException e) {
                // Protocol / format level error or Thread error
                handleFailure(getRootCause(e).getMessage());
                continue;
            }

            // Internal AEM installation error, recover (backoff)
            if (hasRecoverableError(response)) {
                handleFailure(response.getStatusText());
                continue;
            }

            // Semantic error, for instance invalid package.
            // This cannot be fixed with a re-try -> fail.
            if (hasUnrecoverableError(response)) {
                fail(failureMessage(response));
            }

            log(successMessage(response));

            return result(response);
        }
    }

    protected void log(String message) {
        if (message != null) {
            configuration.getLog().info(message);
        }
    }

    protected boolean hasRecoverableError(HttpResponse<ResponseType> response) {
        return response.getStatus() < 200 || response.getStatus() >= 300;
    }

    protected boolean hasUnrecoverableError(@NotNull HttpResponse<ResponseType> response) {
        return response.getStatus() != 200;
    }

    private void handleFailure(@NotNull String cause) {
        if (retries > configuration.getRetries()) {
            fail(cause);
        }
        configuration.getLog().info(failureMessage(cause) + ", re-trying (" + retries + " / " + configuration.getRetries() + ") ...");
        backoff(retries);
    }

    private void fail(@NotNull String cause) {
        throw new HttpActionFailureException(failureMessage(cause));
    }

    /**
     * Exponential backoff for package installation re-tries: Package installation
     * may cause framework restarts, during which the package manager API is unavailable. A restart
     * may take minutes.
     *
     * @param retries how many retries have already been executed.
     */
    private void backoff(int retries) {
        try {
            sleep(SECONDS.toMillis((long) (getBasicBackoffTimeInSeconds() * pow(2, retries - 1))));
        } catch (InterruptedException e) {
            // continue
        }
    }

    protected int getBasicBackoffTimeInSeconds() {
        return 10;
    }

    int getTotalBackoffTimeInSeconds() {
        return (int) (getBasicBackoffTimeInSeconds() * pow(2, configuration.getRetries()) - 1);
    }

    @Nullable
    protected ResultType result(@NotNull HttpResponse<ResponseType> response) {
        return null;
    }

    @Nullable
    protected abstract String startMessage();

    @Nullable
    protected abstract String successMessage(@NotNull HttpResponse<ResponseType> response);

    @NotNull
    protected abstract String failureMessage(@NotNull String cause);

    @NotNull
    protected abstract String failureMessage(@NotNull HttpResponse<ResponseType> response);

    @NotNull
    protected abstract HttpResponse<ResponseType> perform() throws UnirestException, InterruptedException;

    static Expectation<?> packageManagerApiIsAvailable(final Configuration configuration) {
        return new Expectation<Object>() {
            @Override
            protected Outcome fulfill() {
                try {
                    return Unirest.get(configuration.getServerUri().toString() + "/crx/packmgr/service")
                            .basicAuth("admin", configuration.getPassword()).asString().getStatus() == 405 ?
                            Outcome.FULFILLED :
                            Outcome.RETRY;
                } catch (UnirestException e) {
                    // During deployment, the HTTP implementation itself may restart, resulting in network-level errors, such
                    // as connection refused. As this is expected, continue waiting for API recovery.
                    return Outcome.RETRY;
                }
            }

            @Override
            protected void firstFailure() {
                configuration.getLog().info("Waiting for the package manager API to become available again...");
            }
        };
    }

    public final Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Parameter object for configuring {@link RetryableHttpAction}s.
     *
     * @author adrian.gygax
     */
    public static class Configuration {
        private final Log log;
        private final URI serverUri;
        private final String password;
        private final int retries;

        @SuppressWarnings("unused")
        public Configuration(URI serverUri, String password) {
            this(serverUri, password, DEFAULT_DEPLOY_RETRIES, new SimpleLog("simple"));
        }

        public Configuration(URI serverUri, String password, int retries, Log log) {
            this.log = log;
            this.serverUri = serverUri;
            this.password = password;
            this.retries = retries;
        }

        public Log getLog() {
            return log;
        }

        URI getServerUri() {
            return serverUri;
        }

        String getPassword() {
            return password;
        }

        int getRetries() {
            return retries;
        }

        @Override
        public String toString() {
            return "Configuration{" +
                    "log=" + log +
                    ", serverUri=" + serverUri +
                    ", password='" + password + '\'' +
                    ", retries=" + retries +
                    '}';
        }
    }
}
