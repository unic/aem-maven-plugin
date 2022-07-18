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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import unirest.HttpResponse;
import unirest.JsonNode;
import unirest.Unirest;
import unirest.UnirestException;

import java.util.ArrayList;
import java.util.List;

import static com.unic.maven.plugins.aem.util.Expectation.Outcome.FULFILLED;
import static com.unic.maven.plugins.aem.util.Expectation.Outcome.RETRY;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Tests whether all bundles are initialized (are either active or, if they are fragments, resolved) and determines that the system has finished initializing by ensuring
 * that no service changes (such as restarting or activating services) occurred since the {@link #initializationGracePeriod grace period}.
 *
 * @author Olaf Otto
 */
@Mojo(name = "awaitInitialization", threadSafe = true, requiresProject = false)
public class AwaitInitialization extends AemMojo {
    /**
     * OSGi spec bundle states.
     */
    private static final int
            BUNDLE_ACTIVE = 32,
            BUNDLE_RESOLVED = 4;

    /**
     * Felix event admin topics for services start with this namespace.
     */
    private static final String TOPIC_SERVICE_NAMESPACE = "org/osgi/framework/ServiceEvent/";

    /**
     * Wait up to this number of minutes for AEM to initialize, i.e. for all bundles and components to start.
     */
    @Parameter(defaultValue = "2", property = "init.waitTime")
    protected int initializationWaitTime = 2;

    /**
     * Wait this amount of seconds before beginning state tests on the components.
     */
    @Parameter(defaultValue = "5", property = "init.gracePeriod")
    protected int initializationGracePeriod = 5;

    /**
     * Ignores the defined bundles during initialization check by given regex checks.
     * The regex is checked against the bundle's symbolic name.
     */
    @Parameter(property = "ignore.bundlesRegex")
    protected String[] ignoreBundlesRegex = new String[]{};

    @Override
    public void runMojo() throws MojoExecutionException, MojoFailureException {
        getLog().info("Waiting up to " + initializationWaitTime + " minutes for all bundles and components to finish initialization...");

        if (ignoreBundlesRegex.length > 0) {
            getLog().info("Ignoring bundles with symbolic names matching: ");
            for (String regex : ignoreBundlesRegex) {
                getLog().info("- " + regex);
            }
        }

        if (!expectInitializedWithinConfiguredTime()) {
            failWithPendingInitializationsMessage();
        }
        getLog().info("All bundles and components are initialized.");
    }

    boolean expectInitializedWithinConfiguredTime() {
        return aemIsInitialized().onFailure((time, unit, lastFailure) -> getLog().info(
                "AEM did not initialize within " + time + " " + unit + "." +
                        (lastFailure == null ? "" : " Last issue: " + lastFailure.getMessage() + ".")
        )).within(initializationWaitTime, MINUTES);
    }

    void failWithPendingInitializationsMessage() throws MojoFailureException, MojoExecutionException {
        List<String> pendingBundles, serviceEvents;

        try {
            pendingBundles = getPendingBundlesInfo();
        } catch (UnirestException e) {
            throw new MojoExecutionException("Unable to retrieve the bundle states.", e);
        }

        try {
            serviceEvents = getServiceEventInfoSince(getTimeBeforeGracePeriodInMillis());
        } catch (UnirestException e) {
            throw new MojoExecutionException("Unable to retrieve the recent events.", e);
        }

        StringBuilder message = new StringBuilder();

        if (!pendingBundles.isEmpty()) {
            message.append("The following bundles failed to initialize within ").append(initializationWaitTime).append(" minutes:\n");
            for (String bundleInfo : pendingBundles) {
                message.append(bundleInfo).append("\n");
            }
        }

        if (!serviceEvents.isEmpty()) {
            message.append("The following service changes have been detected in the last ")
                    .append(initializationGracePeriod)
                    .append(" seconds:\n");
            for (String eventInfo : serviceEvents) {
                message.append(eventInfo).append("\n");
            }
        }

        throw new MojoFailureException(message.toString());
    }

    Expectation<Exception> aemIsInitialized() {
        return new Expectation<Exception>() {
            private Exception lastFailure;

            @Override
            protected Expectation.Outcome fulfill() {
                try {
                    if (!getPendingBundlesInfo().isEmpty() || !getServiceEventInfoSince(getTimeBeforeGracePeriodInMillis()).isEmpty()) {
                        return RETRY;
                    }

                    return FULFILLED;
                } catch (UnirestException | JSONException e) {
                    lastFailure = e;
                    return RETRY;
                }
            }

            @Override
            protected Exception failureContext() {
                return this.lastFailure;
            }
        };
    }

    private long getTimeBeforeGracePeriodInMillis() {
        return currentTimeMillis() - SECONDS.toMillis(initializationGracePeriod);
    }

    @NotNull
    private List<String> getPendingBundlesInfo() throws UnirestException {
        JSONArray bundleStates =
                getJson("/system/console/bundles.json")
                        .getBody()
                        .getObject()
                        .getJSONArray("data");

        List<String> pendingBundles = new ArrayList<>();

        for (int i = 0; i < bundleStates.length(); ++i) {
            JSONObject bundleData = bundleStates.getJSONObject(i);
            if (pendingBundle(bundleData)) {
                String bundleInfo = bundleData.getString("symbolicName") + ", state: " + bundleData.getString("state");
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Pending bundle info: " + bundleInfo);
                }
                pendingBundles.add(bundleInfo);
            }
        }
        return pendingBundles;
    }

    private boolean pendingBundle(JSONObject bundleData) {
        final int state = bundleData.getInt("stateRaw");
        final boolean isFragment = bundleData.getBoolean("fragment");
        final String bundleSymbolicName = bundleData.getString("symbolicName");

        return !ignoreBundle(bundleSymbolicName) &&
                (isFragment && state != BUNDLE_RESOLVED ||
                        !isFragment && state != BUNDLE_ACTIVE);

    }

    private boolean ignoreBundle(String bundleSymbolicName) {
        for (String regex : ignoreBundlesRegex) {
            if (bundleSymbolicName.matches(regex)) {
                getLog().debug("Ignoring inactive bundle " + bundleSymbolicName + " for initialization check.");
                return true;
            }
        }
        return false;
    }

    @NotNull
    private List<String> getServiceEventInfoSince(long since) throws UnirestException {
        JSONArray events =
                getJson("/system/console/events.json")
                        .getBody()
                        .getObject()
                        .getJSONArray("data");

        List<String> eventInformation = new ArrayList<>();

        for (int i = 0; i < events.length(); ++i) {
            JSONObject eventData = events.getJSONObject(i);
            String topic = eventData.getString("topic");
            long received = eventData.getLong("received");
            if (received >= since && topic.startsWith(TOPIC_SERVICE_NAMESPACE)) {
                eventInformation.add(eventData.getString("info"));
            }
        }
        return eventInformation;
    }

    private HttpResponse<JsonNode> getJson(String path) throws UnirestException {
        HttpResponse<JsonNode> response = Unirest.get(getAemBaseUrl() + path)
                .basicAuth("admin", getAdminPassword())
                .asJson();
        // Unirest does not throw an exception but returns a null JSON body when the JSON cannot be parsed.
        // We favor an exception.
        if (response.getParsingError().isPresent()) {
            throw new UnirestException(new RuntimeException("Unable to parse JSON response from " + getAemBaseUrl() + path, response.getParsingError().get()));
        }
        return response;
    }

    int getInitializationWaitTime() {
        return initializationWaitTime;
    }
}
