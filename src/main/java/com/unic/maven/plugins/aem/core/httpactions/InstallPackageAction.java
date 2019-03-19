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

import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import unirest.HttpResponse;
import unirest.Unirest;
import unirest.UnirestException;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.compile;

/**
 * Installs a package using the console interface rather than the JSON interface in order to determine
 * the success of the installation via the log data provided in the returned HTML.
 */
public class InstallPackageAction extends RetryableHttpAction<String, String> {
    private final Pattern errorMessagePattern = compile("<b>E<[\\\\]?/b>&nbsp;(?<path>[^ ]+) \\((?<errorMessage>(?s).*?)\\)<[\\\\]?/span>");
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
        return body == null || body.contains("with errors") || !body.contains("Package imported");
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

        failureMessage.append("Package manager response: ")
                .append(response.getStatus()).append(" ").append(response.getStatusText())
                .append(": \n")
                .append("Errors reported in package manager response:\n");

        boolean found = false;
        while (m.find()) {
            failureMessage.append(m.group("path")).append(": ")
                    .append(
                            m.group("errorMessage")
                                    .replaceAll("[\n\r]]", " --- "))
                    .append("\n\n");
            found = true;
        }

        if (!found) {
            failureMessage.append("None - full package manager response:\n\n").append(response.getBody());
        }

        return failureMessage.toString();
    }

    @NotNull
    @Override
    protected HttpResponse<String> perform() throws UnirestException {
        if (!packageManagerApiIsAvailable(getConfiguration()).within(getTotalBackoffTimeInSeconds(), SECONDS)) {
            throw new HttpActionFailureException("Unable to install " + file + " - the package manager API was unavailable for "
                    + getTotalBackoffTimeInSeconds() + " seconds.");
        }

        String url = new URIBuilder(getConfiguration().getServerUri()).setPath("/crx/packmgr/service/console.html" + packagePath)
                .addParameter("cmd", "install").toString();

        return Unirest.post(url)
                .field("autosave", deploySaveThreshold)
                .field("recursive", Boolean.toString(deploySubpackages))
                .basicAuth("admin", getConfiguration().getPassword()).asString();

    }
}
