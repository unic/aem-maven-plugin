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

import com.unic.maven.plugins.aem.util.AwaitableProcess.ExecutionResult;
import com.unic.maven.plugins.aem.util.Expectation;
import com.unic.maven.plugins.aem.util.FileUtil;
import org.apache.http.annotation.ThreadSafe;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.unic.maven.plugins.aem.util.AwaitableProcess.awaitable;
import static com.unic.maven.plugins.aem.util.HttpExpectation.expect;
import static com.unic.maven.plugins.aem.util.ProcessStreamReader.followProcessErrorStream;
import static com.unic.maven.plugins.aem.util.ProcessStreamReader.followProcessInputStream;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;
import static java.util.Collections.addAll;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.codehaus.plexus.util.StringUtils.isEmpty;
import static org.codehaus.plexus.util.StringUtils.join;

/**
 * Starts a local AEM instance using a quickstart jar. The quickstart jar must have been provided before hand. Supports setting arbitrary VM parameters
 * in addition to run modes, server port and various AEM settings. The startup {@link AwaitInitialization awaits initialization} of the AEM instance,
 * i.e. makes sure that the AEM instance is ready ot use after successful execution of this mojo.
 *
 * @author Olaf Otto
 */
@Mojo(name = "start")
@ThreadSafe
public class Start extends AwaitInitialization {
    private final ExecutorService executorService = newCachedThreadPool();

    /**
     * The run modes for this instance, in addition to {@link #getAemType()}.
     */
    @Parameter
    private String[] runModes = new String[]{};

    /**
     * Use this amount of heap. Example: 2048M.
     */
    @Parameter(defaultValue = "2048M")
    private String heapSize;

    /**
     * Use this for adding custom vmOptions to the AEM startup, e.g. agents for
     * code analysis.
     * Example:
     * <pre>
     * &lt;configuration>
     *   &lt;startupVmOptions>
     *     &lt;vmOption>-Xms512m&lt;/vmOption>
     *     &lt;vmOption>${failsafeArgLine}&lt;/vmOption>
     *   &lt;/aemStartupVmOptions>
     * &lt;/configuration>
     * </pre>
     */
    @Parameter
    private String[] startupVmOptions = new String[]{};

    /**
     * Wait up to this number of minutes for AEM to start
     */
    @Parameter(defaultValue = "2", property = "startup.waitTime")
    private int startupWaitTime = 2;

    @Override
    public void runMojo() throws MojoExecutionException, MojoFailureException {
        try {
            doExecute();
        } finally {
            executorService.shutdownNow();
        }
    }

    private void doExecute() throws MojoFailureException, MojoExecutionException {
        getLog().info("Starting AEM ...");

        final long startTime = currentTimeMillis();
        int maximumDuration = startupWaitTime;

        if (!isAemInstalled()) {
            getLog().info(
                    "AEM is started for the first time, increasing the maximum startup duration from " + maximumDuration + " to "
                            + maximumDuration * 3 + " minutes.");
            maximumDuration = maximumDuration * 3;
        }

        final Process aem = startAem();

        Expectation aemIsStarted = expect("<status code=\"200\">ok</status>")
                .from(getAemBaseUrl() + "/crx/packmgr/service.jsp?cmd=ls")
                .withCredentials("admin", getAdminPassword());

        if (!aemIsStarted.within(maximumDuration, MINUTES)) {
            aem.destroy();
            throw new MojoFailureException("Unable to start AEM - the instance was not started within " +
                    maximumDuration + " minutes. Aborting startup.");
        }

        getLog().info("AEM is running, awaiting complete initialization...");

        if (!expectInitializedWithinConfiguredTime()) {
            failWithPendingInitializationsMessage();
        }

        getLog().info("AEM startup completed after " + MILLISECONDS.toSeconds(currentTimeMillis() - startTime) + " seconds.");
    }

    @NotNull
    private Process startAem() throws MojoFailureException, MojoExecutionException {
        try {
            ProcessBuilder builder = new ProcessBuilder().directory(getAemDirectory()).command(getCommands());
            logCommands(builder);
            Process process = builder.start();

            // Log all stdout and stderr output of the quickstart execution. This is crucial to understand startup issues.
            // log stderr to info as the quickstart jar is using it for info output.
            this.executorService.execute(followProcessErrorStream(process, getLog(), line -> getLog().info(line)));
            this.executorService.execute(followProcessInputStream(process, getLog(), line -> getLog().info(line)));

            // Grace period: If the AEM process does not terminate within the first five seconds
            // after it was started, we assume the startup was successfully initiated and that it is
            // safe to detach from the process.
            ExecutionResult aemExecutionResult = awaitable(process).awaitTermination(5, SECONDS);

            // Sleep to make sure the stdout and stderr readers pick up any failure messages on stdout and stderr
            try {
                sleep(SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                // Ignore
            }

            if (aemExecutionResult.isTerminated()) {
                throw new MojoFailureException("Unable to start AEM - the quickstart process terminated with exit code: " + aemExecutionResult.getExitCode());
            }
            return process;
        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Unable to start AEM.", e);
        }
    }

    private List<String> getCommands() throws MojoFailureException, MojoExecutionException {
        // Default commands for any AEM instance type
        List<String> commands = new ArrayList<>();

        commands.add(getJavaExecutable());

        if (isDebugEnabled()) {
            commands.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + getDebugPort());
        }

        commands.addAll(asList("-server", "-Xmx" + heapSize));

        // Headless: AEM is a server without a UI
        commands.add("-Djava.awt.headless=true");

        // Add additional JVM options configured in the POM
        commands.addAll(asList(startupVmOptions));

        Set<String> runModes = new HashSet<>();
        // Collect custom runmodes configured in the POM
        addAll(runModes, this.runModes);

        runModes.add(getAemType().name());

        // -jar option must come last and must be followed by the AEM quickstart specific parameters
        commands.addAll(asList(
                "-jar", getQuickstartJarName(),
                "-nofork",
                "-nobrowser",
                "-verbose",
                "-r", join(runModes.iterator(), ","),
                "-port", Integer.toString(getHttpPort())));
        if (useControlPort) {
            commands.add("-use-control-port");
        }

        // Servlet context path - by default, AEM runs at the root context.
        if (!isEmpty(getContextPath())) {
            commands.add("-contextpath");
            commands.add(getContextPath());
        }

        return commands;
    }

    @NotNull
    private String getQuickstartJarName() throws MojoFailureException, MojoExecutionException {
        return FileUtil.getJarFileName(getAemDirectory());
    }
}