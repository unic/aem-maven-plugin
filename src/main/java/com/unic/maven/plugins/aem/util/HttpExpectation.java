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
package com.unic.maven.plugins.aem.util;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import unirest.HttpRequest;
import unirest.HttpResponse;
import unirest.Unirest;
import unirest.UnirestException;

import java.net.MalformedURLException;
import java.net.URL;


/**
 * Used to check for responses from HTTP endpoints in a given time.
 *
 * @author Olaf Otto
 */
public class HttpExpectation extends Expectation<Object> {
    private URL url;
    private String user = null, password = null;
    private int expectedStatusCode = 200;
    private HttpMethod method = HttpMethod.GET;
    private String expectedResponseContent;

    @NotNull
    public static HttpExpectation expect(int statusCode) {
        HttpExpectation expectation = new HttpExpectation();
        expectation.expectedStatusCode = statusCode;
        return expectation;
    }

    @NotNull
    public static HttpExpectation expect(@NotNull String responseContent) {
        HttpExpectation expectation = new HttpExpectation();
        expectation.expectedResponseContent = responseContent;
        return expectation;
    }

    @NotNull
    public HttpExpectation from(@NotNull String url) {
        try {
            HttpExpectation copy = copy();
            copy.url = new URL(url);
            return copy;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    @NotNull
    public HttpExpectation withCredentials(@NotNull String user, @NotNull String password) {
        HttpExpectation copy = copy();
        copy.user = user;
        copy.password = password;
        return copy;
    }

    @Override
    protected Outcome fulfill() {
        HttpRequest<?> request = method == HttpMethod.GET ? Unirest.get(this.url.toExternalForm()) : Unirest.post(this.url.toExternalForm());
        try {
            if (StringUtils.isNotBlank(this.user)) {
                request = request.basicAuth(this.user, this.password);
            }
            HttpResponse<String> response = request.asString();
            if (response.getStatus() == this.expectedStatusCode) {
                if (this.expectedResponseContent == null) {
                    return Outcome.FULFILLED;
                }
                if (response.getBody() != null && response.getBody().contains(this.expectedResponseContent)) {
                    return Outcome.FULFILLED;
                }
            }
        } catch (UnirestException e) {
            // ignore
        }
        return Outcome.RETRY;
    }

    @NotNull
    private HttpExpectation copy() {
        HttpExpectation copy = new HttpExpectation();
        copy.password = this.password;
        copy.url = this.url;
        copy.user = this.user;
        copy.expectedStatusCode = this.expectedStatusCode;
        copy.method = this.method;
        copy.expectedResponseContent = this.expectedResponseContent;
        return copy;
    }

    private HttpExpectation() {
    }
}
