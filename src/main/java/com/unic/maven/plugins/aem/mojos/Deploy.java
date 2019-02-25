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

import com.unic.maven.plugins.aem.core.DeployCommand;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.addAll;

/**
 * Deploys a CRX package from a local files (e.g. residing within the build directory) or from the configured artifacts.
 *
 * @author Olaf Otto
 */
@Mojo(name = "deploy", threadSafe = true, requiresProject = false)
public class Deploy extends AemMojo {

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> projectRepos;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The artifacts to deploy, in format
     * <code>&lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;</code>
     */
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    @Parameter(property = "deploy.artifacts")
    private String[] deployArtifacts = new String[] {};

    /**
     * The packages to deploy directly from the file system.
     */
    @Parameter(property = "deploy.files")
    private File[] deployFiles = new File[] {};

    /**
     * The number of nodes after which an intermediate save is triggered during deployment.
     */
    @Parameter(defaultValue = "100000", property = "deploy.saveThreshold")
    private int deploySaveThreshold = 100000;

    /**
     * Whether to extract subpackages.
     */
    @Parameter(defaultValue = "true", property = "deploy.subPackages")
    private boolean deploySubpackages = true;

    /**
     * The number of times an file upload may be re-tried
     */
    @Parameter(defaultValue = "3", property = "deploy.retries")
    private int deployRetries = 3;

    /**
     * Enable the "Pause/resume JCR Installer" feature. By default it is disabled.
     * This feature is only available for AEM 6.1+.
     * See also: https://issues.apache.org/jira/browse/SLING-3747
     */
    @Parameter(property = "pause.jcrInstaller")
    private boolean pauseJcrInstaller = false;

    @Override
    public void runMojo() throws MojoFailureException {
        List<File> files = new LinkedList<>();
        for (Artifact artifact : resolveArtifactsToDeploy()) {
            files.add(artifact.getFile());
        }

        addAll(files, deployFiles);

        try {
            new DeployCommand(
                    new MavenLogAdapter(getLog()),
                    new URI(getAemBaseUrl()),
                    getAdminPassword(),
                    deployRetries,
                    files,
                    deploySaveThreshold,
                    deploySubpackages,
                    pauseJcrInstaller).execute();
        } catch (URISyntaxException e) {
            throw new MojoFailureException("Illegal AEM base URL", e);
        }
    }

    /**
     * Resolves the configured deployArtifacts from either the local or remote repositories.
     *
     * @return never null.
     * @throws MojoFailureException if any file resolution fails
     */
    @NotNull
    private List<Artifact> resolveArtifactsToDeploy() throws MojoFailureException {
        List<Artifact> resolvedArtifacts = new ArrayList<>();
        for (String artifact : deployArtifacts) {
            try {
                ArtifactRequest request = new ArtifactRequest().setArtifact(new DefaultArtifact(artifact));
                request.setRepositories(projectRepos);
                ArtifactResult artifactResult = this.repositorySystem.resolveArtifact(repoSession, request);
                if (!artifactResult.isResolved()) {
                    throw new MojoFailureException("Unable to resolve file " + artifact + ".");
                }
                resolvedArtifacts.add(artifactResult.getArtifact());
            } catch (ArtifactResolutionException e) {
                throw new MojoFailureException("Unable to resolve file " + artifact + ".", e);
            }
        }
        return resolvedArtifacts;
    }

}
