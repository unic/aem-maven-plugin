# Unic Maven-AEM-plugin
A plugin for working with local and remote Adobe AEM&trade; instances. This plugin allows installing, starting, stopping and killing AEM instances,
conveniently deploying CRX packages and validating JCR content XML files prior to packaging and deploying it.

Please refer to [the project site](https://unic.github.io/aem-maven-plugin/) for the standard maven plugin goal documentation.

[![Maven Central](https://img.shields.io/maven-central/v/com.unic.maven.plugins/aem-maven-plugin.svg)](https://search.maven.org/search?q=a:aem-maven-plugin&g=com.unic.maven.plugins)
[![Travis](https://api.travis-ci.org/unic/aem-maven-plugin.svg?branch=master)](https://travis-ci.org/unic/aem-maven-plugin/) 


The plugins main design goals are:

* Speed: Both the AEM instance and the deployments are are configured for maximum performance, e.g. using generous auto save thresholds and memory settings
   or omitting the installation of sample content

* Stability: Starting, stopping and deploying AEM instances is not trivial and requires extensive handling
  of the various state transitions the system exhibits. The plugin attempts to handle all of these situations. 

* Non-verboseness: Only show meaningful and concise log messages to the user

* Simplicity: The plugin configuration shall be self-explanatory. Each configuration option must be documented, i.e. every @Parameter annotation
  is documented, as this description is included in the plugin metadata and displayed in the IDE or when executing the help MOJO.

## Usage

See [official plugin documentation](https://unic.github.io/aem-maven-plugin/).