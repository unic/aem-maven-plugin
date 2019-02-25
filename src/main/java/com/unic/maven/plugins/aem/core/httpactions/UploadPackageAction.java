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
import unirest.JsonNode;
import unirest.Unirest;
import unirest.UnirestException;

import java.io.File;

import static java.util.concurrent.TimeUnit.SECONDS;

public class UploadPackageAction extends RetryableHttpAction<JsonNode, String> {
    private final File file;

    public UploadPackageAction(Configuration configuration, File file) {
        super(configuration);
        this.file = file;
    }

    @Override
    @NotNull
    protected String result(@NotNull HttpResponse<JsonNode> response) {
        return response.getBody().getObject().getString("path");
    }

    @NotNull
    @Override
    protected String successMessage(@NotNull HttpResponse<JsonNode> response) {
        return "Successfully uploaded " + file.getAbsolutePath();
    }

    @Override
    protected boolean hasUnrecoverableError(@NotNull HttpResponse<JsonNode> response) {
        return !response.getBody().getObject().optBoolean("success", false);
    }

    @NotNull
    @Override
    protected String startMessage() {
        return "Uploading " + file + "...";
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull String message) {
        return "Failed to upload " + file + ", AEM responded: " + message;
    }

    @NotNull
    @Override
    protected String failureMessage(@NotNull HttpResponse<JsonNode> response) {
        return response.getBody().getObject().getString("msg");
    }

    @NotNull
    @Override
    protected HttpResponse<JsonNode> perform() throws UnirestException {
        if (!packageManagerApiIsAvailable(getConfiguration()).within(getTotalBackoffTimeInSeconds(), SECONDS)) {
            throw new HttpActionFailureException("Unable to upload " + file + " - the package manager API was unavailable for "
                    + getTotalBackoffTimeInSeconds() + " seconds.");
        }

        HttpResponse<JsonNode> response = Unirest.post(getConfiguration().getServerUri() + "/crx/packmgr/service/.json/?cmd=upload")
                .field("force", true)
                .field("package", file)
                .basicAuth("admin", getConfiguration().getPassword()).asJson();

        if (response.getParsingError().isPresent()) {
            // Unirest does not throw an exception but returns a null JSON body when the JSON cannot be parsed.
            // We favor an exception.
            if (response.getParsingError().isPresent()) {
                throw new UnirestException(new RuntimeException("Unable to parse JSON response from " + getConfiguration().getServerUri() + "/crx/packmgr/service/.json/?cmd=upload", response.getParsingError().get()));
            }
        }
        return response;
    }
}
