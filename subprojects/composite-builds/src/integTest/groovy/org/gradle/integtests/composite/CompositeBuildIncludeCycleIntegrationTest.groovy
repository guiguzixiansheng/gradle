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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildIncludeCycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        buildB = singleProjectBuild("buildB")
        includedBuilds << buildB
        buildC = singleProjectBuild("buildC")
        includedBuilds << buildC
    }

    def "two included builds can include each other"() {
        when:
        buildB.settingsFile << "includeBuild '../buildC'"
        buildC.settingsFile << "includeBuild '../buildB'"

        then:
        execute(buildA, 'help')
    }

    def "included build can include root build"() {
        when:
        buildB.settingsFile << "includeBuild '../buildA'"
        buildC.settingsFile << "includeBuild '../buildA'"

        then:
        execute(buildA, 'help')
    }

    def "nested build can include root build"() {
        when:
        buildB.settingsFile << "includeBuild '../buildC'"
        buildC.settingsFile << "includeBuild '../buildA'"

        then:
        execute(buildA, 'help')
    }

    def "included build can see included root build"() {
        when:
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.settingsFile << "includeBuild '../buildA'"
        buildB.buildFile << """
            assert gradle.includedBuilds.collect { it.name } == ['theNameOfBuildA']
        """

        then:
        execute(buildA, 'help')
    }

    def "the root build cannot be renamed"() {
        when:
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.settingsFile << "includeBuild('../buildA') { name = 'some-other-name' }"
        buildB.buildFile << """
            assert gradle.includedBuilds.collect { it.name } == ['theNameOfBuildA']
        """

        then:
        execute(buildA, 'help')
    }

    def "included root build is only configured one time"() {
        given:
        buildA.buildFile << """
            println("Configuring \$identityPath")
        """
        buildB.settingsFile << "includeBuild '../buildA'"

        when:
        execute(buildA, 'help')

        then:
        output.count("Configuring :") == 1
        outputDoesNotContain("Configuring :buildA")
    }

    def "can address root build task from included build"() {
        given:
        buildA.buildFile << """
            tasks.register('task1') {
                dependsOn gradle.includedBuild('buildB').task(':task2')
            }
            tasks.register('task3') { }
        """
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.settingsFile << "includeBuild '../buildA'"
        buildB.buildFile << """
            tasks.register('task2') {
                dependsOn gradle.includedBuild('theNameOfBuildA').task(':task3')
            }
        """

        when:
        execute(buildA, 'task1')

        then:
        result.assertTasksExecuted(':task3',':buildB:task2', ':task1')
    }

    def "can depend back on root build"() {
        given:
        def buildX = multiProjectBuild('buildX', ['x1', 'x2']) {
            buildFile << """
                subprojects { apply plugin: 'java-library'}
                project(':x1') {
                    dependencies { implementation 'org.test:theNameOfBuildA' }
                }
            """
            settingsFile << """
                includeBuild '../buildA'
            """
        }

        buildA.buildFile << """
            apply plugin: 'java-library'
            dependencies { implementation 'org.test:x2' }
        """
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """

        when:
        execute(buildX, ':x1:compileJava')

        then:
        result.assertTasksExecuted(':x2:compileJava', ':buildA:compileJava', ':x1:compileJava')
    }

    def "can depend back on root build and back on an included build"() {
        given:
        def buildX = multiProjectBuild('buildX', ['x1', 'x2', 'x3']) {
            buildFile << """
                subprojects { apply plugin: 'java-library'}
                project(':x1') {
                    dependencies { api 'org.test:theNameOfBuildA' }
                }
                project(':x2') {
                    dependencies { api 'org.test:buildB' }
                }
            """
            settingsFile << """
                includeBuild '../buildA'
                includeBuild '../buildB'
            """
        }

        buildA.buildFile << """
            apply plugin: 'java-library'
            dependencies { api 'org.test:x2' }
        """
        buildA.settingsFile << """
            rootProject.name = 'theNameOfBuildA'
        """
        buildB.buildFile << """
            apply plugin: 'java-library'
            dependencies { api 'org.test:x3' }
        """

        when:
        execute(buildX, ':x1:compileJava')

        then:
        result.assertTasksExecuted(':x3:compileJava', ':buildB:compileJava', ':x2:compileJava', ':buildA:compileJava', ':x1:compileJava')
    }
}
