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
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.compile;

/**
 * Kills conflicting AEM processes, i.e. AEM processes with either the HTTP or debug port
 * configured for the mojo.
 *
 * @author Olaf Otto
 */
@Mojo(name = "kill", threadSafe = true, requiresProject = false)
public class Kill extends AemMojo {
    private static final Pattern AEM_PID = compile("([0-9]+) ((cq.?|aem.?)-.*\\.jar .*)");
    private final ExecutorService executorService = newCachedThreadPool();

    /**
     * After terminating an AEM process, wait for this amount of seconds to make sure that process
     * resources - such as files or network ports - are freed.
     */
    @Parameter(defaultValue = "5", property = "kill.gracePeriod")
    private int killGracePeriod;

    @Override
    public void runMojo() throws MojoExecutionException, MojoFailureException {
        try {
            killConflictingAemInstances();
            if (aemProcessTerminated().within(5, SECONDS)) {
                getLog().info("All conflicting AEM processes were terminated");
            } else {
                throw new MojoExecutionException("Unable to terminate conflicting AEM instances:" +
                        " The process(es) " + getPidsOfConflictingAemInstances() + " are still running.");
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    boolean killConflictingAemInstances() throws MojoExecutionException {
        getLog().info("Looking for running AEM processes to end...");
        List<Integer> pids = getPidsOfConflictingAemInstances();

        if (pids.isEmpty()) {
            getLog().info("No running AEM processes found - the processes may have already terminated.");
            // Process already gone
            return true;
        }

        for (int pid : pids) {
            getLog().info("Ending AEM process with PID " + pid + "...");
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
                throw new MojoExecutionException("Unable to end AEM process with PID " + pid + ".", e);
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
        List<Integer> pids = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .command(getJpsExecutable(),
                            "-mlv"
                            // long format: Include executed file names and arguments to both the java programm and the JVM (debug port)
                    );
            logCommands(processBuilder);
            Process jps = processBuilder.start();
            StringBuilder commandOutput = new StringBuilder(2048);

            // Log all stdout and stderr output of the quickstart execution. This is crucial to understand startup issues.
            this.executorService.execute(followProcessErrorStream(jps, getLog(), line -> getLog().error(line)));
            Future<?> readProcessOutput = this.executorService.submit(followProcessInputStream(jps, getLog(), l -> commandOutput.append(l).append('\n')));

            ExecutionResult jpsExecutionResult = awaitable(jps).awaitTermination(10, SECONDS);

            if (jpsExecutionResult.isTerminated()) {
                readProcessOutput.get(5, SECONDS);
                String processes = commandOutput.toString();
                Matcher matcher = AEM_PID.matcher(processes);

                while (matcher.find()) {
                    String processAndArguments = matcher.group(2);
                    if (processAndArguments.contains(Integer.toString(getHttpPort()))
                            || processAndArguments.contains(Integer.toString(getDebugPort()))) {
                        pids.add(parseInt(matcher.group(1)));
                    }
                }
            } else {
                getLog().warn("JPS process didn't complete within timeout. AEM instances could still be running.");
            }
        } catch (Exception e) {
            getLog().warn("Unable to determine the AEM processes PID using JPS.", e);
        }
        return pids;
    }

    @NotNull
    private String getJpsExecutable() {
        File jreHome = new File(getJavaHome());
        File jdkRoot = jreHome.getParentFile();
        return jdkRoot.getPath() + separator + "bin" + separator + "jps";
    }

    @NotNull
    private Expectation aemProcessTerminated() {
        return new Expectation() {
            @Override
            protected Outcome fulfill() {
                return getPidsOfConflictingAemInstances().isEmpty() ? FULFILLED : RETRY;
            }
        };
    }
}
