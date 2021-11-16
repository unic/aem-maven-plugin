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

import com.unic.maven.plugins.aem.util.Expectation;
import com.unic.maven.plugins.aem.util.FileUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import unirest.HttpResponse;
import unirest.Unirest;
import unirest.UnirestException;

import java.io.File;
import java.io.IOException;

import static com.unic.maven.plugins.aem.util.AwaitableProcess.awaitable;
import static com.unic.maven.plugins.aem.util.ExceptionUtil.getRootCause;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.FULFILLED;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.RETRY;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.*;

/**
 * Stops running AEM instances conflicting with the current AEM instance.
 * Running AEM instances can be shut down gracefully if they were started in the same module
 * the stop task is executed in, provided their {@link #isAemInstalled() installation directory}
 * still exists.<br>
 * Otherwise, conflicting AEM processes - i.e. AEM processes started by the same system user this
 * mojo was started with running on either the same HTTP port or debug port - are terminated (killed).
 * This is since orphaned AEM instances in arbitrary state may have be left over by abnormally terminated previous builds.
 *
 * @author Olaf Otto
 */
@Mojo(name = "stop", threadSafe = true, requiresProject = false)
public class Stop extends Kill {
    /**
     * Wait up to this number of minutes for AEM to stop
     */
    @Parameter(defaultValue = "2", property = "shutdown.waitTime")
    private int shutdownWaitTime = 2;

    /**
     * After terminating an AEM process, wait for this amount of seconds to make sure that process
     * resources - such as files or network ports - are freed.
     */
    @Parameter(defaultValue = "5", property = "shutdown.gracePeriod")
    private int shutdownGracePeriod;

    @Override
    public void runMojo() throws MojoExecutionException, MojoFailureException {
        final long startTime = currentTimeMillis();
        getLog().info("Stopping AEM...");

        if (!isAemInstalled()) {
            getLog().info("AEM is not installed.");
            killConflictingAemInstances();
        } else {
            boolean shutdownComplete = isUseControlPort() ? shutdownAemUsingControlPort() : shutdownAem();
            if (!shutdownComplete) {
                shutdownComplete = killConflictingAemInstances();
            }

            if (shutdownComplete) {
                try {
                    sleep(SECONDS.toMillis(shutdownGracePeriod));
                } catch (InterruptedException e) {
                    // continue.
                }
                getLog().info("AEM shutdown completed after " + MILLISECONDS.toSeconds(currentTimeMillis() - startTime) + " seconds.");
            } else {
                throw new MojoExecutionException("Unable to stop AEM - neither graceful nor forceful shutdown succeeded.");
            }
        }
    }

    private boolean shutdownAem() {
        getLog().info("Checking whether system/console is available...");
        if (!systemConsoleIsAvailable().within(20, SECONDS)) {
            getLog().info("Unable to gracefully shutdown AEM: the system/console is not available.");
            return false;
        }

        getLog().info("Attempting to gracefully shutdown AEM via system/console...");

        try {
            HttpResponse<String> response =
                    Unirest.post("http://localhost:" + getHttpPort() + getContextPath() + "/system/console/vmstat")
                            .basicAuth("admin", getAdminPassword())
                            .field("shutdown_type", "stop").asString();


            if (response.getStatus() != 200) {
                getLog().info("Unable to gracefully shutdown AEM, the AEM server responded: " + response.getStatusText() + ".");
                return false;
            }
        } catch (UnirestException e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            getLog().info("Unable to send graceful shutdown command to AEM: " + getRootCause(e).getMessage());
            return false;
        }

        if (aemProcessTerminated().within(shutdownWaitTime, MINUTES)) {
            return true;
        }

        getLog().info("Unable to gracefully shutdown AEM within " + shutdownWaitTime + " minutes.");
        return false;
    }

    @NotNull
    private Expectation<?> systemConsoleIsAvailable() {
        return new Expectation<Object>() {
            @Override
            protected Outcome fulfill() {
                try {
                    return Unirest.get("http://localhost:" + getHttpPort() + getContextPath() + "/system/console/vmstat")
                            .basicAuth("admin", getAdminPassword())
                            .asString()
                            .getStatus() == 200 ? FULFILLED : RETRY;
                } catch (UnirestException e) {
                    return Outcome.UNSATISFIABLE;
                }
            }

            @Override
            protected void firstFailure() {
                getLog().info("Waiting for system/console to become available again...");
            }
        };
    }

    /**
     * Shuts down AEM using the
     * <a href="https://sling.apache.org/documentation/the-sling-engine/the-sling-launchpad.html#control-port">Sling Launchpad Control Port feature.</a>
     */
    private boolean shutdownAemUsingControlPort() throws MojoFailureException, MojoExecutionException {
        try {
            File quickstartJarFileDirectory = new File(getCrxQuickstartDirectory(), "app");
            String relativePathToQuickstartJar = quickstartJarFileDirectory.getName() + File.separator + FileUtil.getJarFileName(quickstartJarFileDirectory);

            // java -jar app/aem-quickstart-6.1.0-standalone-quickstart.jar stop -c .
            ProcessBuilder builder = new ProcessBuilder()
                    .directory(getCrxQuickstartDirectory())
                    .command(getJavaExecutable(), "-jar", relativePathToQuickstartJar, "stop", "-c", ".");
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            logCommands(builder);
            Process process = builder.start();

            int processResult = awaitable(process).awaitTermination(1, MINUTES).getExitCode();
            if (processResult > 0) {
                getLog().info("Stopping AEM using the quickstart stop command failed with error code " + processResult);
                return false;
            }

            getLog().info("Waiting up to " + shutdownWaitTime + " minutes for AEM to stop...");

            if (aemProcessTerminated().within(shutdownWaitTime, MINUTES)) {
                return true;
            }

            getLog().info("Unable to gracefully shutdown AEM within " + shutdownWaitTime + " minutes.");
            return false;
        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Unable to stop AEM.", e);
        }
    }

    @NotNull
    private File getCrxQuickstartDirectory() throws MojoFailureException {
        return new File(getAemDirectory(), "crx-quickstart");
    }

    @NotNull
    private Expectation aemProcessTerminated() {
        return new Expectation() {
            @Override
            protected Outcome fulfill() {
                return getPidsOfConflictingAemInstances().isEmpty() ? Outcome.FULFILLED : Outcome.RETRY;
            }
        };
    }
}
