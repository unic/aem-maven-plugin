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

import org.apache.http.annotation.ThreadSafe;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.unic.maven.plugins.aem.util.ExceptionUtil.getRootCause;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathFactory.newInstance;
import static org.codehaus.plexus.util.FileUtils.getFiles;
import static org.codehaus.plexus.util.StringUtils.join;

/**
 * Performs XML validation of all XML files within a directory, by default within src/main/content (see {@link #contentDirectory}). This can be used
 * to ensure that JCR content is syntactically valid.
 *
 * @author Olaf Otto
 */
@Mojo(name = "validate-content")
@ThreadSafe
public class ValidateContent extends AbstractMojo {
    /**
     * Additional directories containing JCR XML content for import into AEM that shall be validated by the
     * validate-xml mojo.
     */
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    @Parameter
    private File[] contentDirectories = new File[]{};

    /**
     * The standard directly containing content XML files to be validated.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/content")
    private File contentDirectory = null;

    /**
     * UUIDs should not be present in XML content as importing UUIDs may conflict with existing UUIDs on the target system.
     * set this to <code>true</code> to allow UUIDs.
     */
    @Parameter(defaultValue = "false", property = "allow.uuids")
    private boolean allowUuids = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<ValidationError> validationErrors = new ArrayList<>();

        if (contentDirectory != null && contentDirectory.exists()) {
            validationErrors.addAll(validateXmlFilesIn(contentDirectory));
        }

        for (File directory : contentDirectories) {
            validationErrors.addAll(validateXmlFilesIn(directory));
        }

        if (!validationErrors.isEmpty()) {
            throw new MojoFailureException(validationErrors.size() + " invalid XML files found: \n" + join(validationErrors.iterator(), "\n"));
        }
    }

    private List<ValidationError> validateXmlFilesIn(@NotNull File directory) throws MojoFailureException, MojoExecutionException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new MojoFailureException("The configured AEM content sources directory " + directory.getAbsolutePath() + " does not exist.");
        }
        getLog().info("Validating XML files in " + directory.getAbsolutePath() + "...");

        List<ValidationError> failedValidations = new ArrayList<>();
        DocumentBuilder builder = getDocumentBuilder();

        for (File file : getXmlFiles(directory)) {
            try {
                Document document = builder.parse(file);
                if (!allowUuids) {
                    failedValidations.addAll(validateDocumentContainsNoUUIDs(file, document));
                }
            } catch (SAXParseException e) {
                failedValidations.add(new ValidationError(file, e));
            } catch (IOException | SAXException e) {
                throw new MojoExecutionException("Unable to validate the XML file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
            }
        }

        getLog().info("Validation completed, found " + failedValidations.size() + " errors.");
        return failedValidations;
    }

    private List<File> getXmlFiles(@NotNull File directory) throws MojoExecutionException {
        List<File> files;
        try {
            files = getFiles(directory, "**/*.xml", null);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to collect all XML files in " + directory.getAbsolutePath() + ": " + e.getMessage(), e);
        }
        return files;
    }

    private List<ValidationError> validateDocumentContainsNoUUIDs(File file, Document document) {
        List<ValidationError> failedValidations = new ArrayList<>();
        NodeList itemsWithUUIDs;
        try {
            itemsWithUUIDs = (NodeList) newInstance().newXPath().compile("//*[@jcr:uuid]").evaluate(document, NODESET);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Unable to test for UUIDs: The uuid search expression is malformed.", e);
        }
        for (int i = 0; i < itemsWithUUIDs.getLength(); ++i) {
            Node node = itemsWithUUIDs.item(i);
            failedValidations.add(new ValidationError(file, "A jcr:uuid attribute was found: " + node +
                    ". Deploying UUIDs can conflict with existing UUIDs on the target system."));
        }
        return failedValidations;
    }

    @NotNull
    private DocumentBuilder getDocumentBuilder() throws MojoExecutionException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        try {
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            // Allows using an offline catalog to resolve schema or DTD URIs pointing to online resources.
            // See the CatalogManager.properties and catalog.xml files.
            documentBuilder.setEntityResolver(new CatalogResolver());
            // Prevents logging to system.err, which is the default
            documentBuilder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return documentBuilder;
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException("Unable to obtain an XML document builder.", e);
        }
    }

    /**
     * @author Olaf Otto
     */
    private static class ValidationError {
        private final File file;
        private final String message;

        private ValidationError(File file, SAXParseException parsingException) {
            this(file,
                    parsingException.getLineNumber() +
                            ":" + parsingException.getColumnNumber() +
                            ": " + getRootCause(parsingException).getMessage());
        }

        private ValidationError(File file, String message) {
            this.file = file;
            this.message = message;
        }

        @Override
        public String toString() {
            return file.getPath() + ":" + message;
        }
    }
}
