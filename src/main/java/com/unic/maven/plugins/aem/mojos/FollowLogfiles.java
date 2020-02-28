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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import static com.unic.maven.plugins.aem.util.FileReader.follow;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * Follows one or more logfiles and spools their content to the console running the maven process.
 *
 * @author Olaf Otto
 */
@Mojo(name = "followLogs", threadSafe = true, requiresProject = false)
public class FollowLogfiles extends AwaitInitialization {
    private final ExecutorService executorService = newCachedThreadPool();

    /**
     * The logfiles to follow, relative to the AEM installation direectory, e.g. <code>logs/error.log</code>
     */
    @Parameter(property = "follow.logfiles", defaultValue = "logs/error.log")
    private String[] logfileNames = new String[]{};

    /**
     * Whether to keep following the logs beyond the execution of the mojo.
     * If true, adds a shutdown hook to the JVM that will stop
     * monitoring when the JVM exits.
     */
    @Parameter(defaultValue = "true", property = "follow.keepFollowing")
    private boolean keepFollowing = true;

    @Override
    public void runMojo() throws MojoFailureException {
        if (this.keepFollowing) {
            getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));
        }
        try {
            doExecute();
        } finally {
            if (!this.keepFollowing) {
                executorService.shutdownNow();
            }
        }
    }

    private void doExecute() throws MojoFailureException {
        getLog().info("Following " + Arrays.toString(this.logfileNames) + "...");
        for (String logfileName : this.logfileNames) {
            File file = new File(getCrxQuickstartDirectory(), logfileName);
            if (!file.exists()) {
                getLog().error("Unable to follow the log file " + file.getPath() + ", the file does not exist.");
                continue;
            }
            if (!file.canRead()) {
                getLog().error("Unable to follow the log file " + file.getPath() + ", the file cannot be read by this process.");
                continue;
            }

            this.executorService.execute(follow(file, getLog(), line -> getLog().info("<" + file.getName() + "> " + line)));
        }
    }

    @NotNull
    private File getCrxQuickstartDirectory() throws MojoFailureException {
        return new File(getAemDirectory(), "crx-quickstart");
    }
}
