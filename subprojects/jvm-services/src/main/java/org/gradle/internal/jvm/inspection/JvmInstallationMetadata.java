/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.jvm.inspection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.api.JavaVersion;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

public interface JvmInstallationMetadata {

    enum JavaInstallationCapability {
        JAVA_COMPILER, J9_VIRTUAL_MACHINE
    }

    static DefaultJvmInstallationMetadata from(File javaHome, String implementationVersion, String vendor, String implementationName) {
        return new DefaultJvmInstallationMetadata(javaHome, implementationVersion, vendor, implementationName);
    }

    static JvmInstallationMetadata failure(File javaHome, String errorMessage) {
        return new FailureInstallationMetadata(javaHome, errorMessage);
    }

    JavaVersion getLanguageVersion();

    String getImplementationVersion();

    JvmVendor getVendor();

    Path getJavaHome();

    String getDisplayName();

    boolean hasCapability(JavaInstallationCapability capability);

    String getErrorMessage();

    boolean isValidInstallation();

    class DefaultJvmInstallationMetadata implements JvmInstallationMetadata {

        private final JavaVersion languageVersion;
        private final String vendor;
        private final String implementationName;
        private final Path javaHome;
        private final String implementationVersion;
        private final Supplier<Set<JavaInstallationCapability>> capabilities = Suppliers.memoize(this::gatherCapabilities);

        private DefaultJvmInstallationMetadata(File javaHome, String implementationVersion, String vendor, String implementationName) {
            this.javaHome = javaHome.toPath();
            this.implementationVersion = implementationVersion;
            this.languageVersion = JavaVersion.toVersion(implementationVersion);
            this.vendor = vendor;
            this.implementationName = implementationName;
        }

        @Override
        public JavaVersion getLanguageVersion() {
            return languageVersion;
        }

        @Override
        public String getImplementationVersion() {
            return implementationVersion;
        }

        @Override
        public JvmVendor getVendor() {
            return JvmVendor.fromString(vendor);
        }

        @Override
        public Path getJavaHome() {
            return javaHome;
        }

        @Override
        public String getDisplayName() {
            final String vendor = determineVendorName();
            String installationType = determineInstallationType(vendor);
            return MessageFormat.format("{0}{1}", vendor, installationType);
        }

        private String determineVendorName() {
            JvmVendor.KnownJvmVendor vendor = getVendor().getKnownVendor();
            if(vendor == JvmVendor.KnownJvmVendor.ORACLE) {
                if (implementationName != null && implementationName.contains("OpenJDK")) {
                    return "OpenJDK";
                }
            }
            return getVendor().getDisplayName();
        }

        private String determineInstallationType(String vendor) {
            if (hasCapability(JavaInstallationCapability.JAVA_COMPILER)) {
                if (!vendor.toLowerCase().contains("jdk")) {
                    return " JDK";
                }
                return "";
            }
            return " JRE";
        }

        @Override
        public boolean hasCapability(JavaInstallationCapability capability) {
            return capabilities.get().contains(capability);
        }

        private Set<JavaInstallationCapability> gatherCapabilities() {
            final Set<JavaInstallationCapability> capabilities = new HashSet<>(2);
            final File javaCompiler = new File(new File(javaHome.toFile(), "bin"), OperatingSystem.current().getExecutableName("javac"));
            if (javaCompiler.exists()) {
                capabilities.add(JavaInstallationCapability.JAVA_COMPILER);
            }
            boolean isJ9vm = implementationName.contains("J9");
            if(isJ9vm) {
                capabilities.add(JavaInstallationCapability.J9_VIRTUAL_MACHINE);
            }
            return capabilities;
        }

        @Override
        public String getErrorMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValidInstallation() {
            return true;
        }
    }

    class FailureInstallationMetadata implements JvmInstallationMetadata {

        private final File javaHome;
        private final String errorMessage;

        private FailureInstallationMetadata(File javaHome, String errorMessage) {
            this.javaHome = javaHome;
            this.errorMessage = errorMessage;
        }

        @Override
        public JavaVersion getLanguageVersion() {
            throw unsupportedOperation();
        }

        @Override
        public String getImplementationVersion() {
            throw unsupportedOperation();
        }

        @Override
        public JvmVendor getVendor() {
            throw unsupportedOperation();
        }

        @Override
        public Path getJavaHome() {
            return javaHome.toPath();
        }

        @Override
        public String getDisplayName() {
            return "Invalid installation: " + getErrorMessage();
        }

        @Override
        public boolean hasCapability(JavaInstallationCapability capability) {
            return false;
        }

        private UnsupportedOperationException unsupportedOperation() {
            return new UnsupportedOperationException("Installation is not valid. Original error message: " + getErrorMessage());
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public boolean isValidInstallation() {
            return false;
        }
    }

}
