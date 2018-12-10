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

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mashape.unirest.http.Unirest.post;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.compile;

/**
 * Installs a package using the console interface rather than the JSON interface in order to determine
 * the success of the installation via the log data provided in the returned HTML.
 */
public class InstallPackageAction extends RetryableHttpAction<String, String> {
    private final Pattern errorMessagePattern = compile("<b>E</b>&nbsp;([^ ]+) \\((.+)\\)</span>");
    private final String packagePath;
    private final boolean deploySubpackages;
    private final File file;
    private final int deploySaveThreshold;

    public InstallPackageAction(Configuration configuration, File file, String packagePath, boolean deploySubpackages,
                                int deploySaveThreshold) {
        super(configuration);
        this.file = file;
        this.packagePath = packagePath;
        this.deploySubpackages = deploySubpackages;
        this.deploySaveThreshold = deploySaveThreshold;
    }

    @Override
    protected boolean hasUnrecoverableError(@NotNull HttpResponse<String> response) {
        String body = response.getBody();
        return body == null || body.contains("with errors") || !hasInstallationFinished(body);
    }

    @NotNull
    @Override
    protected String startMessage() {
        return "Installing " + file + (deploySubpackages ? " and its subpackages, if any..." : "...");
    }

    @NotNull
    @Override
    protected String successMessage(@NotNull HttpResponse<String> response) {
        return "Successfully installed " + file + ".";
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull String cause) {
        return "Failed to install " + file + ", AEM responded: " + cause;
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull HttpResponse<String> response) {
        Matcher m = errorMessagePattern.matcher(response.getBody());
        StringBuilder failureMessage = new StringBuilder(1024);

        failureMessage.append("HTTP status and body: ")
                .append(response.getStatus()).append("-")
                .append(response.getStatusText())
                .append(": \n")
                .append(response.getBody());

        while (m.find()) {
            failureMessage.append(m.group(1)).append(": ").append(m.group(2)).append('\n');
        }

        return failureMessage.toString();
    }

    @NotNull
    @Override
    protected HttpResponse<String> perform() throws UnirestException, InterruptedException {
        if (!packageManagerApiIsAvailable(getConfiguration()).within(getTotalBackoffTimeInSeconds(), SECONDS)) {
            throw new HttpActionFailureException("Unable to install " + file + " - the package manager API was unavailable for "
                    + getTotalBackoffTimeInSeconds() + " seconds.");
        }

        String url = new URIBuilder(getConfiguration().getServerUri()).setPath("/crx/packmgr/service/console.html" + packagePath)
                .addParameter("cmd", "install").toString();

        HttpResponse<String> response = post(url)
                .field("autosave", deploySaveThreshold)
                .field("recursive", deploySubpackages)
                .basicAuth("admin", getConfiguration().getPassword()).asString();

        if (response.getStatus() == HttpStatus.SC_OK) {
            waitingForInstallationToFinish(response);
        }

        return response;
    }

    private void waitingForInstallationToFinish(HttpResponse<String> response) throws InterruptedException {
        String body = response.getBody();
        int retries = 0;
        int maxRetries = 20;
        while (retries < maxRetries) {
            getConfiguration().getLog().debug("Waiting for installation...");
            sleep(2000);
            if (hasInstallationFinished(body)) {
                return;
            }
            retries++;
        }
    }

    private boolean hasInstallationFinished(String body) {
        return body.contains("Package imported");
    }
}
