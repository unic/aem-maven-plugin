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
import org.jetbrains.annotations.NotNull;

import static com.mashape.unirest.http.Unirest.post;

/**
 * Resumes the JCR installer by deleting the "do not install signal" content (AEM 6+)
 */
public class ResumeJcrInstallerAction extends RetryableHttpAction<String, String> {

    public ResumeJcrInstallerAction(Configuration configuration) {
        super(configuration);
    }

    @NotNull
    @Override
    protected  String startMessage() {
        return "Resuming the JCR installer...";
    }

    @NotNull
    @Override
    protected String successMessage(@NotNull HttpResponse<String> response) {
        return "Successfully resumed the JCR installer.";
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull String cause) {
        return "Unable to resume the JCR installer: " + cause;
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull HttpResponse<String> response) {
        return "Unable to resume the JCR installer. AEM responded " + response.getStatusText() + ".";
    }

    @NotNull
    @Override
    protected HttpResponse<String> perform() throws UnirestException {
        return post(getConfiguration().getServerUri() + "/system/sling/installer")
                .field(":operation", "delete")
                .basicAuth("admin", getConfiguration().getPassword()).asString();
    }

    /**
     * Deleting the installer signal causes a synchronous deletion event immediately starting the JCR installer. Resulting,
     * even a successful deletion is likely to receive a HTTP 500 response, as the system state changes before the deletion response
     * is created. This task thus needs to wait generously for the installed changes to be applied in order to make sure that the JCR
     * installer pause
     * signal was indeed successfully removed.
     */
    @Override
    protected int getBasicBackoffTimeInSeconds() {
        return 20;
    }
}
