/*-
 * ============LICENSE_START===============================================
 * ONAP
 * ========================================================================
 * Copyright (C) 2024 Nordix Foundation.
 * ========================================================================
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
 * ============LICENSE_END=================================================
 */

package org.onap.policy.drools.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileSystemPersistenceTest {

    FileSystemPersistence persistence = new FileSystemPersistence();

    @Test
    void testSetConfiguration_FileIsNotDirectory() {
        persistence.configurationPath = Path.of("src/test/resources/echo.drl");
        assertThrows(IllegalStateException.class, () -> persistence.setConfigurationDir());
        assertThatThrownBy(() -> persistence.setConfigurationDir())
            .hasMessageContaining("config directory: src/test/resources/echo.drl is not a directory");
    }

    @Test
    void testSetConfiguration_InvalidDir() {
        persistence.configurationPath = Path.of("/opt/path"); // opt path needs sudo
        assertThrows(IllegalStateException.class, () -> persistence.setConfigurationDir());
        assertThatThrownBy(() -> persistence.setConfigurationDir())
            .hasMessageContaining("cannot create /opt/path");
    }

    @Test
    void testGetProperties_Exception() {
        assertThatThrownBy(() -> persistence.getProperties(""))
            .hasMessageContaining("properties name must be provided");

        String propName = null;
        assertThatThrownBy(() -> persistence.getProperties(propName)) // for code coverage
            .hasMessageContaining("properties name must be provided");
    }

    @Test
    void testGetEnvironmentProperties_Exception() {
        assertThatThrownBy(() -> persistence.getEnvironmentProperties(""))
            .hasMessageContaining("environment name must be provided");

        String propName = null;
        assertThatThrownBy(() -> persistence.getEnvironmentProperties(propName)) // for code coverage
            .hasMessageContaining("environment name must be provided");
    }

    @Test
    void testGetProperties_ByPathException() {
        assertThatThrownBy(() -> persistence.getProperties(Path.of("/path/does/not/exist.properties")))
            .hasMessageContaining("properties for /path/does/not/exist.properties are not persisted.");

        Path pathProps = null;
        assertThatThrownBy(() -> persistence.getProperties(pathProps)) // for code coverage
            .hasMessageContaining("propertiesPath is marked non-null but is null");
    }
}
