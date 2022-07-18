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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.unic.maven.plugins.aem.util.AwaitableProcess.ExecutionResult;
import static com.unic.maven.plugins.aem.util.AwaitableProcess.awaitable;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.FULFILLED;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.RETRY;
import static com.unic.maven.plugins.aem.util.ProcessStreamReader.followProcessErrorStream;
import static com.unic.maven.plugins.aem.util.ProcessStreamReader.followProcessInputStream;
import static java.io.File.separator;
import static java.lang.Integer.parseInt;
import static java.lang.Thread.sleep;
import static java.util.Collections.emptyList;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.compile;

/**
 * Kills conflicting AEM processes, i.e. AEM processes with either the HTTP or debug port
 * configured for the mojo.
 *
 * @author Olaf Otto
 */
@Mojo(name = "kill", threadSafe = true, requiresProject = false)
public class Kill extends AemMojo {
    // A process, it's command line and PID are returned by JPS and PS in this format.
    private static final Pattern JPS_LIKE_AEM_PID = compile("^[\\s]*(?<pid>[0-9]+)[\\s]+.*(?<process>(cq.?|aem.?)-.*\\.jar .*)$", MULTILINE);
    // The WMIC command we are using returns this format.
    private static final Pattern WMIC_AEM_PID = compile("^.*(?<process>(cq.?|aem.?)-.*\\.jar .*),(?<pid>[0-9]+)$", MULTILINE);
    private static final Pattern HTTP_PORT_ARGUMENT = compile("(-quickstart\\.server\\.port|-p|-port)[\\s]+(?<port>[0-9]+)");
    private static final Pattern DEBUG_PORT_ARGUMENT = compile("address[\\s]*=[\\s]*(?<port>[0-9]+)");
    private final ExecutorService executorService = newCachedThreadPool();

    /**
     * After terminating an AEM process, wait for this amount of seconds to make sure that process
     * resources - such as files or network ports - are freed.
     */
    @Parameter(defaultValue = "5", property = "kill.gracePeriod")
    protected int killGracePeriod;

    @Override
    public void runMojo() throws MojoExecutionException, MojoFailureException {
        try {
            	List<Integer> pids = getPidsOfConflictingAemInstances();

            if (killConflictingAemInstances() || aemProcessTerminated().within(5, SECONDS)) {
                getLog().info("AEM processes " + pids + " successfully terminated");
            } else {
                throw new MojoExecutionException("Unable to terminate all AEM instances:" +
                        " The process(es) " + getPidsOfConflictingAemInstances() + " are still running.");
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    boolean killConflictingAemInstances() throws MojoExecutionException {
        getLog().info("Looking for running AEM instances to end...");
        List<Integer> pids = getPidsOfConflictingAemInstances();

        if (pids.isEmpty()) {
            getLog().info("No running AEM instances found - the processes may have already terminated.");
            // Process already gone
            return true;
        }

        for (int pid : pids) {
            getLog().info("Ending AEM instance with PID " + pid + "...");
            ProcessBuilder builder = new ProcessBuilder();
            if (isWindows()) {
                builder.command("taskkill", "/F", "/PID", Integer.toString(pid));
            } else {
                builder.command("kill", "-s", "SIGKILL", Integer.toString(pid));
            }
            try {
                if (builder.start().waitFor() != 0) {
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                throw new MojoExecutionException("Unable to end AEM instance with PID " + pid + ".", e);
            }
        }

        if (!pids.isEmpty()) {
            try {
                sleep(SECONDS.toMillis(killGracePeriod));
            } catch (InterruptedException e) {
                // continue.
            }
        }

        return true;
    }

    /**
     * Obtain the PIDs of all conflicting AEM instance, i.e. instance running on either the same HTTP or debug port
     *
     * @return never null bur rather an empty list.
     */
    @NotNull
    List<Integer> getPidsOfConflictingAemInstances() {
        String jpsExecutablePath = getJpsExecutablePath();
        if (new File(jpsExecutablePath).exists()) {
            // -mlv: Include executed file names and arguments to both the java program and the JVM (debug port)
            String jpsResult = execute(jpsExecutablePath, "-mlv");

            // JPS may not yield anything erroneously, see e.g. https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8193710
            if (!isBlank(jpsResult)) {
                return findPidsOfConflictingAemInstances(jpsResult, JPS_LIKE_AEM_PID);
            }
        }

        // Here, JPS is not available or provides no data. Try and use system-specific tooling.
        if (isWindows()) {
            String wmicResult = execute("wmic", "PROCESS", "WHERE", "\"name like 'java%' and commandline like '%-jar %'\"", "GET", "ProcessID,CommandLine", "/Format:csv");
            return findPidsOfConflictingAemInstances(wmicResult, WMIC_AEM_PID);
        }

        // At this point we may be on any *Nix distro or in a docker container and unable to JPS, so we attempt to find conflicting instances
        // by checking for all processes to make sure we did not miss a process.
        // x: List processes of the current user, include those not running from tty.
        String psResult = execute("ps", "x");
        if (!isBlank(psResult)) {
            return findPidsOfConflictingAemInstances(psResult, JPS_LIKE_AEM_PID);
        }
        return emptyList();
    }

    private String execute(String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder().command(command);
            logCommands(processBuilder);
            Process process = processBuilder.start();
            StringBuilder commandOutput = new StringBuilder(2048);

            this.executorService.execute(followProcessErrorStream(process, getLog(), line -> getLog().error("<stderr> " + line)));
            Future<?> readProcessOutput = this.executorService.submit(followProcessInputStream(process, getLog(), l -> commandOutput.append(l).append('\n')));
            ExecutionResult executionResult = awaitable(process).awaitTermination(10, SECONDS);
            if (executionResult.isTerminated()) {
                readProcessOutput.get(5, SECONDS);
                return commandOutput.toString();
            } else {
                getLog().warn(command[0] + " didn't complete within timeout. AEM instances could still be running.");
            }
        } catch (Exception e) {
            getLog().warn("Unable to execute " + command[0], e);
        }
        return null;
    }

    /**
     * Extract the PIDs of conflicting processes. This matching uses a fuzzy regular expression since the format of the process lists somewhat depend on the underlying
     * platform and tool used.
     */
    @NotNull
    private List<Integer> findPidsOfConflictingAemInstances(String processes, Pattern pidPattern) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Looking for AEM processes with conflicting HTTP port " + getHttpPort() + " or debug port " + getDebugPort() + " in\n\r" + processes);
        }
        List<Integer> pids = new ArrayList<>();
        Matcher aemProcessLine = pidPattern.matcher(processes);
        while (aemProcessLine.find()) {
            String processAndArguments = aemProcessLine.group("process");

            Matcher httpPortArg = HTTP_PORT_ARGUMENT.matcher(processAndArguments);
            if (httpPortArg.find() && httpPortArg.group("port").equals(Integer.toString(getHttpPort()))) {
                pids.add(parseInt(aemProcessLine.group("pid")));
                continue;
            }

            Matcher debugPortArg = DEBUG_PORT_ARGUMENT.matcher(processAndArguments);
            if (debugPortArg.find() && debugPortArg.group("port").equals(Integer.toString(getDebugPort()))) {
                pids.add(parseInt(aemProcessLine.group("pid")));
            }
        }
        return pids;
    }

    @NotNull
    private String getJpsExecutablePath() {
        File jreHome = new File(getJavaHome());
        File jdkRoot = jreHome.getParentFile();
        return jdkRoot.getPath() + separator + "bin" + separator + "jps" + (isWindows() ? ".exe" : "");
    }

    @NotNull
    private Expectation<?> aemProcessTerminated() {
        return new Expectation<Object>() {
            @Override
            protected Outcome fulfill() {
                return getPidsOfConflictingAemInstances().isEmpty() ? FULFILLED : RETRY;
            }
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
