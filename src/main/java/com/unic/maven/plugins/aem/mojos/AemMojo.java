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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import unirest.Unirest;

import java.io.File;

import static java.io.File.separator;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Olaf Otto
 */
public abstract class AemMojo extends AbstractMojo {
    public enum AemType {
        author,
        publish
    }

    /**
     * The schema + hostname of the AEM instance, e.g. "http://localhost".
     */
    @Parameter(defaultValue = "http://localhost", property = "base.url")
    protected String baseUrl;

    /**
     * The context path of the AEM instance.
     */
    @Parameter(property = "context.path")
    protected String contextPath = "";

    /**
     * The password of the "admin" user.
     */
    @Parameter(property = "password", defaultValue = "admin")
    protected String adminPassword = "admin";

    /**
     * The instance type, e.g. "author" or "publish".
     */
    @Parameter(defaultValue = "author", property = "aem.type")
    protected AemType aemType;

    /**
     * The AEM HTTP port. Defaults to 4502 for an author and 4503 for a publish {@link #aemType AEM type}.
     */
    @Parameter(defaultValue = "-1", property = "http.port")
    protected int httpPort;

    /**
     * Use this debug port for remote debugging on the instance.
     */
    @Parameter(defaultValue = "30303", property = "debug.port")
    protected int debugPort;

    /**
     * Whether remote debugging shall be available for the started instance via the configured {@link #debugPort port}.
     */
    @Parameter(defaultValue = "true", property = "debug.enabled")
    protected boolean debugEnabled = true;

    /**
     * Use this JRE to start the AEM instance.
     */
    @Parameter(defaultValue = "${java.home}", property = "java.home")
    protected String javaHome;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, property = "target.directory")
    protected File targetDirectory;

    /**
     * If true the AEM Control Port feature is enabled and used for stopping the instance.
     * If false the system console HTTP API is used to stop the AEM instance.
     */
    @Parameter(defaultValue = "false", property = "use.controlport")
    protected boolean useControlPort;

    /**
     * @return the AEM directory, i.e. target/aem/[aemType], e.g. target/aem/author
     * @throws MojoFailureException if the target directory does not exist.
     */
    @NotNull
    File getAemDirectory() throws MojoFailureException {
        File aemDirectory = new File(getTargetDirectory(), separator + "aem" + separator + getAemType());
        if (!aemDirectory.exists()) {
            throw new MojoFailureException("The AEM working directory " + aemDirectory + " does not exist - " +
                    "an AEM quickstart jar must be placed in this directory " +
                    "before this mojo can execute.");
        }
        return aemDirectory;
    }

    boolean isUseControlPort() {
        return this.useControlPort;
    }

    boolean isAemInstalled() {
        try {
            return new File(getAemDirectory(), "crx-quickstart" + separator + "bin").exists();
        } catch (MojoFailureException e) {
            return false;
        }
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Unirest.config().connectTimeout((int) SECONDS.toMillis(10)).socketTimeout((int) SECONDS.toMillis(10));
            runMojo();
        } finally {
            Unirest.shutDown();
        }
    }

    abstract void runMojo() throws MojoExecutionException, MojoFailureException;

    boolean isWindows() {
        return getProperty("os.name").toLowerCase().contains("windows");
    }

    int getHttpPort() {
        return httpPort == -1 ? getStandardPortForAemType() : httpPort;
    }

    private int getStandardPortForAemType() {
        return AemType.author.equals(aemType) ? 4502 : 4503;
    }

    int getDebugPort() {
        return debugPort;
    }

    boolean isDebugEnabled() {
        return debugEnabled;
    }

    @NotNull
    String getJavaHome() {
        return javaHome;
    }

    @NotNull
    private File getTargetDirectory() {
        return targetDirectory;
    }

    @NotNull
    String getContextPath() {
        return contextPath;
    }

    @NotNull
    AemType getAemType() {
        return aemType;
    }

    @NotNull
    String getAdminPassword() {
        return adminPassword;
    }

    /**
     * @return The full path to the java executable.
     */
    @NotNull
    String getJavaExecutable() {
        // Use javaw under windows to start a console-independent process.
        // Processed launched from a windows console using "java" may terminate
        // when the console terminates or sends a signal (such as ctrl+c).
        return javaHome + separator + "bin" + separator + (isWindows() ? "javaw" : "java");
    }

    /**
     * @return the scheme, host, port and context path, e.g. http://localhost:4502/contextpath, never null.
     */
    @NotNull
    String getAemBaseUrl() {
        return baseUrl + ':' + getHttpPort() + getContextPath();
    }

    /**
     * Logs the {@link ProcessBuilder#command()} list.
     */
    void logCommands(@NotNull ProcessBuilder processBuilder) {
        if (getLog().isDebugEnabled()) {
            StringBuilder stringBuilder = new StringBuilder();
            for (String s : processBuilder.command()) {
                stringBuilder.append(s).append(" ");
            }
            getLog().debug("Working dir: " + processBuilder.directory());
            getLog().debug("Command: " + stringBuilder);
        }
    }
}
