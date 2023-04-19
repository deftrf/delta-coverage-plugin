package io.github.surpsg.deltacoverage.gradle

import io.github.surpsg.deltacoverage.diff.git.getCrlf
import io.github.surpsg.deltacoverage.gradle.DeltaCoveragePlugin.Companion.DELTA_COVERAGE_TASK
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Paths

class DeltaCoverageSingleModuleTest : BaseDeltaCoverageTest() {

    companion object {
        const val TEST_PROJECT_RESOURCE_NAME = "single-module-test-project"

        private const val MOCK_SERVER_PORT = 8888
    }

    override fun buildTestConfiguration() = TestConfiguration(
        TEST_PROJECT_RESOURCE_NAME,
        "build.gradle",
        "test.diff.file"
    )

    @BeforeEach
    fun setup() {
        initializeGradleTest()
    }

    @ParameterizedTest
    @ValueSource(strings = ["5.0", "5.3", "6.7.1", "7.4.2", "8.0.2"])
    fun `deltaCoverage task should be completed successfully on Gradle release`(
        gradleVersion: String
    ) {
        // setup
        buildFile.appendText(
            """
            deltaCoverageReport {
                diffSource.file = '$diffFilePath'
                jacocoExecFiles = fileTree('build') { include '*/**/*.exec' }
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.withGradleVersion(gradleVersion).runTask(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(SUCCESS)
    }

    @Test
    fun `delta-coverage should validate coverage and fail without report creation`() {
        // setup
        val baseReportDir = "build/custom/reports/dir/jacoco/"
        buildFile.appendText(
            """
            deltaCoverageReport {
                diffSource.file = '$diffFilePath'
                reportConfiguration.baseReportDir = '$baseReportDir'
                violationRules {
                    failIfCoverageLessThan 1.0
                    failOnViolation = true
                }
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTaskAndFail(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(FAILED)
            .assertOutputContainsStrings("Fail on violations: true. Found violations: 3")
        assertThat(
            rootProjectDir.resolve(baseReportDir).resolve("deltaCoverage")
        ).doesNotExist()
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "html,  html,       true",
            "csv,   report.csv, false",
            "xml,   report.xml, false"
        ]
    )
    fun `delta-coverage should create single report type`(
        reportToGenerate: String,
        expectedReportFile: String,
        isDirectory: Boolean
    ) {
        // setup
        val baseReportDir = "build/custom/reports/dir/jacoco/"
        buildFile.appendText(
            """
            deltaCoverageReport {
                diffSource.file = '$diffFilePath'
                reportConfiguration.baseReportDir = '$baseReportDir'
                reportConfiguration.$reportToGenerate = true
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTask(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(SUCCESS)
            .assertOutputContainsStrings("Fail on violations: false. Found violations: 0")

        val diffReportDir: File = rootProjectDir.resolve(baseReportDir).resolve("deltaCoverage")
        assertThat(diffReportDir.list()!!.toList())
            .hasSize(1).first()
            .extracting(
                { it },
                { diffReportDir.resolve(it).isDirectory }
            )
            .containsExactly(expectedReportFile, isDirectory)
    }

    @Test
    fun `delta-coverage should fail if classes file collection is empty`() {
        // setup
        buildFile.appendText(
            """
            deltaCoverageReport {
                diffSource.file = '$diffFilePath'
                classesDirs = files()
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTaskAndFail(DELTA_COVERAGE_TASK)

        // assert
        result.assertOutputContainsStrings("'deltaCoverageReport.classesDirs' file collection is empty.")
    }

    @Test
    fun `delta-coverage should create deltaCoverage dir and full coverage with html, csv and xml reports`() {
        // setup
        val baseReportDir = "build/custom/reports/dir/jacoco/"
        buildFile.appendText(
            """
            
            deltaCoverageReport {
                diffSource {
                    file = '$diffFilePath'
                }
                jacocoExecFiles = jacocoTestReport.executionData
                classesDirs = jacocoTestReport.classDirectories
                srcDirs = jacocoTestReport.sourceDirectories
                
                reports {
                    html = true
                    xml = true
                    csv = true
                    fullCoverageReport = true
                    baseReportDir = '$baseReportDir'
                }
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTask(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(SUCCESS)
        rootProjectDir.resolve(baseReportDir).apply {
            assertAllReportsCreated(resolve("deltaCoverage"))
            assertAllReportsCreated(resolve("fullReport"))
        }
    }

    @Test
    fun `delta-coverage should use git to generate diff`() {
        // setup
        prepareTestProjectWithGit()

        buildFile.delete()
        buildFile = File(rootProjectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            import io.github.surpsg.deltacoverage.gradle.DeltaCoverageConfiguration
                
            plugins {
                java
                id("io.github.surpsg.delta-coverage")
            }
            
            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation("junit:junit:4.13.2")
            }

            configure<DeltaCoverageConfiguration> {
                diffSource.git compareWith "HEAD"
                violationRules failIfCoverageLessThan 0.7
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTaskAndFail(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(FAILED)
            .assertOutputContainsStrings(
                "instructions covered ratio is 0.5, but expected minimum is 0.7",
                "branches covered ratio is 0.5, but expected minimum is 0.7",
                "lines covered ratio is 0.6, but expected minimum is 0.7"
            )
    }

    @Test
    fun `delta-coverage should fail on violation and generate html report`() {
        // setup
        val absolutePathBaseReportDir = rootProjectDir
            .resolve("build/absolute/path/reports/jacoco/")
            .toUnixAbsolutePath()

        buildFile.appendText(
            """

            deltaCoverageReport {
                diffSource.file = '$diffFilePath'
                reports {
                    html = true
                    baseReportDir = '$absolutePathBaseReportDir'
                }
                violationRules {
                    minBranches = 0.6
                    minLines = 0.7
                    minInstructions = 0.8
                    failOnViolation = true
                }
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTaskAndFail(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(FAILED)
            .assertOutputContainsStrings(
                "instructions covered ratio is 0.5, but expected minimum is 0.8",
                "branches covered ratio is 0.5, but expected minimum is 0.6",
                "lines covered ratio is 0.6, but expected minimum is 0.7"
            )

        val deltaCoverageDir = Paths.get(absolutePathBaseReportDir, "deltaCoverage", "html").toFile()
        assertThat(deltaCoverageDir.list())
            .containsExactlyInAnyOrder(
                *expectedHtmlReportFiles("com.java.test")
            )
    }

    @Test
    fun `delta-coverage should not fail on violation when failOnViolation is false`() {
        // setup
        buildFile.appendText(
            """

            deltaCoverageReport {
                diffSource.file = '$diffFilePath'
                violationRules {
                    failIfCoverageLessThan 1.0
                    failOnViolation = false
                }
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTask(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(SUCCESS)
            .assertOutputContainsStrings("Fail on violations: false. Found violations: 3")
    }

    @Test
    fun `delta-coverage should get diff info by url`() {
        // setup
        buildFile.appendText(
            """

            deltaCoverageReport {
                diffSource.url = 'http://localhost:$MOCK_SERVER_PORT/'
                println diffSource.url
                violationRules {
                    minInstructions = 1
                    failOnViolation = true
                }
            }
        """.trimIndent()
        )

        MockHttpServer(MOCK_SERVER_PORT, File(diffFilePath).readText()).use {
            // run
            val result = gradleRunner.runTaskAndFail(DELTA_COVERAGE_TASK)

            // assert
            result.assertDeltaCoverageStatusEqualsTo(FAILED)
                .assertOutputContainsStrings("instructions covered ratio is 0.5, but expected minimum is 1")
        }
    }

    @Test
    fun `delta-coverage should fail and print available branches if provided branch not found`() {
        // setup
        val unknownBranch = "unknown-branch"
        val newBranch = "new-branch"
        buildGitRepository().use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Add all").call()
            git.branchCreate().setName(newBranch).call()
        }

        buildFile.appendText(
            """

            deltaCoverageReport {
                diffSource.git.compareWith '$unknownBranch'
            }
        """.trimIndent()
        )

        // run
        val result = gradleRunner.runTaskAndFail(DELTA_COVERAGE_TASK)

        // assert
        result.assertDeltaCoverageStatusEqualsTo(FAILED)
            .assertOutputContainsStrings(
                "Unknown revision '$unknownBranch'",
                "Available branches: refs/heads/master, refs/heads/$newBranch"
            )
    }

    private fun prepareTestProjectWithGit() {
        rootProjectDir.resolve(".gitignore").apply {
            appendText("\n*")
            appendText("\n!*.java")
            appendText("\n!gitignore")
            appendText("\n!*/")
        }
        buildGitRepository().use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Add all").call()

            val oldVersionFile = "src/main/java/com/java/test/Class1.java"
            val targetFile = rootProjectDir.resolve(oldVersionFile)
            getResourceFile<DeltaCoverageSingleModuleTest>("git-diff-source-test-files/Class1GitTest.java")
                .copyTo(targetFile, true)

            git.add().addFilepattern(oldVersionFile).call()
            git.commit().setMessage("Added old file version").call()

            getResourceFile<DeltaCoverageSingleModuleTest>("$TEST_PROJECT_RESOURCE_NAME/src").copyRecursively(
                rootProjectDir.resolve("src"),
                true
            )
            git.add().addFilepattern(".").call()
        }
    }

    private fun buildGitRepository(): Git {
        val repository: Repository = FileRepositoryBuilder.create(File(rootProjectDir, ".git")).apply {
            config.setEnum(
                ConfigConstants.CONFIG_CORE_SECTION,
                null,
                ConfigConstants.CONFIG_KEY_AUTOCRLF,
                getCrlf()
            )
            create()
        }
        return Git(repository)
    }

    private fun assertAllReportsCreated(baseReportDir: File) {
        assertThat(baseReportDir.list()).containsExactlyInAnyOrder("report.xml", "report.csv", "html")
        assertThat(baseReportDir.resolve("html").list())
            .containsExactlyInAnyOrder(
                *expectedHtmlReportFiles("com.java.test")
            )
    }
}