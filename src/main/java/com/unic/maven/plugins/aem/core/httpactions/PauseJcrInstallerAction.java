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

import org.jetbrains.annotations.NotNull;
import unirest.HttpResponse;
import unirest.Unirest;
import unirest.UnirestException;


/**
 * Pauses the JCR installer by creating the "do not install signal" content (AEM 6+)
 */
public class PauseJcrInstallerAction extends RetryableHttpAction<String, String> {

    public PauseJcrInstallerAction(Configuration configuration) {
        super(configuration);
    }

    @NotNull
    @Override
    protected String startMessage() {
        return "Trying to pause the JCR installer...";
    }

    @Override
    protected boolean hasRecoverableError(HttpResponse<String> response) {
        int status = response.getStatus();
        return status != 200 &&
                status != 201 &&
                status != 404;

    }

    @Override
    protected boolean hasUnrecoverableError(@NotNull HttpResponse<String> response) {
        return false;
    }

    @NotNull
    @Override
    protected String successMessage(@NotNull HttpResponse<String> response) {
        return response.getStatus() == 404 ? "Pausing the JCR installer is not supported in this AEM version, continuing."
                : "Successfully paused the JCR installer.";
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull String cause) {
        return "Unable to pause the JCR installer: " + cause;
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull HttpResponse<String> response) {
        return "Unable to pause the JCR installer. AEM responded " + response.getStatusText() + ".";
    }

    @NotNull
    @Override
    protected HttpResponse<String> perform() throws UnirestException {
        return Unirest.post(getConfiguration().getServerUri() + "/system/sling")
            .field(":operation", "import")
            .field(":contentType", "json")
            .field(":content", "{\n" +
                    "  \"installer\": {\n" +
                    "    \"jcr:primaryType\": \"nt:folder\",\n" +
                    "    \"jcr\": {\n" +
                    "      \"jcr:primaryType\": \"nt:folder\",\n" +
                    "      \"pauseInstallation\": {\n" +
                    "        \"jcr:primaryType\": \"nt:folder\",\n" +
                    "        \"paused\": {\n" +
                    "          \"jcr:primaryType\": \"nt:folder\"\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}")
            .basicAuth("admin", getConfiguration().getPassword()).asString();
    }
}
