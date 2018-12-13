# Unic Maven-AEM-plugin
A plugin for working with local and remote Adobe AEM&trade; instances. This plugin allows installing, starting, stopping and killing AEM instances,
conveniently deploying CRX packages and validating JCR content XML files prior to packaging and deploying it.

The plugins main design goals are:

* Speed: Both the AEM instance and the deployments are are configured for maximum performance, e.g. using generous auto save thresholds and memory settings
   or omitting the installation of sample content

* Stability: Starting, stopping and deploying AEM instances is not trivial and requires extensive handling
  of the various state transitions the system exhibits. The plugin attempts to handle all of these situations. 

* Non-verboseness: Only show meaningful and concise log messages to the user

* Simplicity: The plugin configuration must be self-explanatory and each configuration option must be documented, i.e. every @Parameter annotation
  must have a javadoc description, as this description is included in the plugin metadata and displayed in the IDE or when executing the help MOJO.

## Usage
Reference the plugin in your POM:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <version>1.4.0</version>
    </plugin>
    
Start (or install) AEM using a quickstart.jar from target/aem/&lt;instanceType&gt;/ :

    mvn aem:start
    
To stop the AEM instance:

    mvn aem:stop

To restart the AEM instance:

    mvn aem:restart

To kill any AEM process running on either the configured HTTP or debug port:

    mvn aem:kill
    
To deploy an artifact, add the artifact coordinates to the plugin configuration like so:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <configuration>
            <deployArtifacts>
                <artifact><groupId>:<artifactId>[:<extension>[:<classifier>]]:<version></artifact>
            </deployArtifacts>
        </configuration>
    </plugin>
    
For example:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <configuration>
            <deployArtifacts>
                <artifact>io.neba:io.neba.neba-delivery:zip:${version.neba}</artifact>
            </deployArtifacts>
        </configuration>
    </plugin>

    
To deploy files from the local file system, add the files to the plugin configuration like so:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <configuration>
            <deployFiles>
                <file><filepath></file>
            </deployFiles>
        </configuration>
    </plugin>

You may mix artifact and file deployment. 

The configured artifacts and files are deployed using

    mvn aem:deploy
    
Note: Existing packages will be overwritten, but their contents will not be uninstalled.


### Install sample content (default: nosamplecontent)

By default the sample content is not installed. But this can be changed by setting the `installSampleContent` to true.


### Enabling or disabling remote debugging

Remote debugging may be disabled or enabled using the following configuration property. It is enabled by default. It is recommended to disable remote
debugging on integration instances.

	<plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
		<version>${plugin.version.aem}</version>
		<configuration>
			...
			<aemDebugModeEnabled>false</aemDebugModeEnabled>
			...
		</configuration>
	</plugin>


### Add additional startup VM options

Example config:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <configuration>
            <aemStartupVmOptions>
                <vmOption>${failsafeArgLine}</vmOption>
                <vmOption>-Xms512m</vmOption>
            </aemStartupVmOptions>
        </configuration>
    </plugin>


### Enable Pause/Resume JcrInstaller feature (AEM 6.1+)

For new AEM 6.1+ versions it is possible to enable the Pause/Resume feature to achieve a more stable deployment.
By default this feature is disabled.

Example config for enabling it:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <configuration>
            ...
            <pauseJcrInstaller>true</pauseJcrInstaller>
            ...
        </configuration>
    </plugin>


### Ignoring bundles during initialization check

There may be cases where you won't like to fail the build if some bundles are not in active state.

For example if you use the _AEM-Forms Addon_ there will be the `adobe-aemfd-signatures` bundle which does not needed to be started.

Example config:

    <configuration>
        ...
        <ignoreBundlesRegex>
            <bundleRegex>adobe-aemfd-signatures</bundleRegex>
            <bundlesRegex>com\.adobe\.livecycle.*</bundlesRegex>
        </ignoreBundlesRegex>
        ...
    </configuration>


### Example configuration for local publisher, on port 4507, with debug port 30201 and heap size configured

Example config:

    <plugin>
        <groupId>com.unic.maven.plugins</groupId>
        <artifactId>aem-maven-plugin</artifactId>
        <configuration>
            <aemType>publish</aemType>
            <aemPort>4507</aemPort>
            <aemDebugPort>30201</aemDebugPort>
            <aemHeapSize>2048m</aemHeapSize>
            <aemStartupVmOptions>
                <vmOption>${failsafeArgLine}</vmOption>
                <vmOption>-Xms512m</vmOption>
            </aemStartupVmOptions>
        </configuration>
    </plugin>


