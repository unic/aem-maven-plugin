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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Olaf Otto
 */
public class ExceptionUtil {
    /**
     * @param throwable must not be <code>null</code>.
     * @return never <code>null</code>.
     */
    @NotNull
    public static Throwable getRootCause(@NotNull Throwable throwable) {
        Throwable rootCause = ExceptionUtils.getRootCause(throwable);
        return rootCause == null ? throwable : rootCause;
    }

    private ExceptionUtil() {}
}
