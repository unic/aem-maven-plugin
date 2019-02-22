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
package com.unic.maven.plugins.aem.core;

import com.unic.maven.plugins.aem.core.httpactions.InstallPackageAction;
import com.unic.maven.plugins.aem.core.httpactions.PauseJcrInstallerAction;
import com.unic.maven.plugins.aem.core.httpactions.ResumeJcrInstallerAction;
import com.unic.maven.plugins.aem.core.httpactions.RetryableHttpAction;
import com.unic.maven.plugins.aem.core.httpactions.UploadPackageAction;
import org.apache.commons.logging.Log;
import org.jetbrains.annotations.NotNull;
import unirest.Unirest;

import java.io.File;
import java.net.URI;
import java.util.List;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Command which combines upload and installation of a package with optional JCR Installer pausing.
 *
 * @author Olaf Otto
 */
public class DeployCommand {
    /**
     * The packages to deploy directly from the file system.
     */
    private final List<File> deployFiles;

    /**
     * The number of nodes after which an intermediate save is triggered during deployment.
     */
    private final int deploySaveThreshold;

    /**
     * Whether to extract subpackages.
     */
    private final boolean deploySubpackages;

    /**
     * Enable the "Pause/resume JCR Installer" feature.
     * This feature is only available for AEM 6.1+.
     * See also: https://issues.apache.org/jira/browse/SLING-3747
     */
    private final boolean pauseJcrInstaller;

    private final RetryableHttpAction.Configuration configuration;

    public DeployCommand(@NotNull Log log, @NotNull URI hostUri, @NotNull String aemAdminPassword, int deployRetries,
                         @NotNull List<File> deployFiles,
                         int deploySaveThreshold,
                         boolean deploySubpackages, boolean pauseJcrInstaller) {
        this.configuration = new RetryableHttpAction.Configuration(hostUri, aemAdminPassword, deployRetries, log);
        this.deployFiles = deployFiles;
        this.deploySaveThreshold = deploySaveThreshold;
        this.deploySubpackages = deploySubpackages;
        this.pauseJcrInstaller = pauseJcrInstaller;
    }

    public void execute() {
        // Connection shall be established within two seconds, but an installation may take up to 10 minutes,
        // for instance large content packages.
        Unirest.config().connectTimeout((int) SECONDS.toMillis(2)).socketTimeout((int) MINUTES.toMillis(10));

        if (pauseJcrInstaller) {
            new PauseJcrInstallerAction(configuration).run();
        }

        try {
            for (File file : deployFiles) {
                String packagePath = new UploadPackageAction(configuration, file).run();
                new InstallPackageAction(configuration, file, packagePath, deploySubpackages, deploySaveThreshold).run();
            }
        } catch (Exception e) {
            try {
                resumeJcrInstaller();
            } catch (Exception resumeError) {
                this.configuration.getLog().error("In addition to the failed deployment, resuming the JCR installer failed as well: " + e.getMessage(), e);
            }
            throw e;
        }

        resumeJcrInstaller();
    }

    private void resumeJcrInstaller() {
        if (pauseJcrInstaller) {
            new ResumeJcrInstallerAction(configuration).run();
        }
    }
}
