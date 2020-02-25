package com.unic.maven.plugins.aem.mojos;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Ensures that AEM is stable, i.e. experiences no bundle state changes or service changes
 * during a configurable amount of time.
 */
@Mojo(name = "ensureStable", threadSafe = true, requiresProject = false)
public class EnsureStableFor extends AwaitInitialization {
    /**
     * Expect AEM to remain stable for this amount of seconds, i.e. no service configuration and bundle state changes
     * should occur for this amount of time.
     */
    @Parameter(defaultValue = "16", property = "stable.expectedDuration")
    private int expectedStableTimeInSeconds;

    @Override
    public void runMojo() throws MojoExecutionException, MojoFailureException {
        long waitUntilInMillis = currentTimeMillis() + MINUTES.toMillis(getInitializationWaitTime());

        super.runMojo();

        // At this point we now that AEM is initialized. Otherwise, the MOJO would have failed already.
        long stableSince = currentTimeMillis();

        while (true) {
            long remainingTimeInMillis = waitUntilInMillis - currentTimeMillis();

            if (remainingTimeInMillis < SECONDS.toMillis(2)) {
                throw new MojoFailureException("Exceeded the initialization wait time of " + getInitializationWaitTime() + " minutes when waiting for AEM to be stable for " + expectedStableTimeInSeconds + " seconds.");
            }

            if (currentTimeMillis() - stableSince >= SECONDS.toMillis(expectedStableTimeInSeconds)) {
                getLog().info("AEM has been stable for " + expectedStableTimeInSeconds + " seconds, continuing.");
                break;
            }

            if (aemIsInitialized().within(2, SECONDS)) {
                if (stableSince == 0) {
                    stableSince = currentTimeMillis();
                }
                try {
                    sleep(SECONDS.toMillis(2));
                } catch (InterruptedException e) {
                    getLog().info("Interrupted while sleeping between checks for initialization, aborting.", e);
                }
            } else {
                stableSince = 0;
            }
        }
    }
}
