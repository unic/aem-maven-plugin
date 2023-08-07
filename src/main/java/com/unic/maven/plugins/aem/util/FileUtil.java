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
import org.apache.maven.plugin.MojoExecutionException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class FileUtil {

    /**
     * Finds the name of a single .jar file in a directory.
     *
     * @param directory The directory to look for a .jar file.
     * @return The file name.
     * @throws MojoExecutionException if no file or more than one file is found.
     */
    @NotNull
    public static String getJarFileName(@NotNull File directory) throws MojoExecutionException {
        File[] jarFiles = directory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            throw new MojoExecutionException("No jar file was found in the AEM directory " + directory + ".");
        }
        if (jarFiles.length > 1) {
            throw new MojoExecutionException("Unable to determine the jar file, more than one jar file " +
                    "was found in the AEM directory " + directory + ": " + StringUtils.join(jarFiles, ", ") + ".");
        }
        return jarFiles[0].getName();
    }

    private FileUtil() {
    }
}
