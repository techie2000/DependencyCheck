/*
 * This file is part of dependency-check-ant.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.taskdefs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.NotThreadSafe;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileProvider;
import org.apache.tools.ant.types.resources.Resources;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.agent.DependencyCheckScanAgent;
import org.owasp.dependencycheck.data.nvdcve.DatabaseException;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Vulnerability;
import org.owasp.dependencycheck.dependency.naming.Identifier;
import org.owasp.dependencycheck.exception.ExceptionCollection;
import org.owasp.dependencycheck.exception.ReportException;
import org.owasp.dependencycheck.reporting.ReportGenerator.Format;
import org.owasp.dependencycheck.utils.Downloader;
import org.owasp.dependencycheck.utils.InvalidSettingException;
import org.owasp.dependencycheck.utils.Settings;
import org.owasp.dependencycheck.utils.SeverityUtil;
import org.slf4j.impl.StaticLoggerBinder;

//CSOFF: MethodCount
/**
 * An Ant task definition to execute dependency-check during an Ant build.
 *
 * @author Jeremy Long
 */
@NotThreadSafe
public class Check extends Update {

    /**
     * System specific new line character.
     */
    private static final String NEW_LINE = System.getProperty("line.separator", "\n").intern();

    /**
     * Whether the ruby gemspec analyzer should be enabled.
     */
    private Boolean rubygemsAnalyzerEnabled;
    /**
     * Whether or not the Node.js Analyzer is enabled.
     */
    private Boolean nodeAnalyzerEnabled;
    /**
     * Whether or not the Node Audit Analyzer is enabled.
     */
    private Boolean nodeAuditAnalyzerEnabled;
    /**
     * Whether or not the Yarn Audit Analyzer is enabled.
     */
    private Boolean yarnAuditAnalyzerEnabled;
    /**
     * Whether or not the Pnpm Audit Analyzer is enabled.
     */
    private Boolean pnpmAuditAnalyzerEnabled;
    /**
     * Sets whether or not the Node Audit Analyzer should use a local cache.
     */
    private Boolean nodeAuditAnalyzerUseCache;
    /**
     * Sets whether or not the Node Package Analyzer should skip dev
     * dependencies.
     */
    private Boolean nodePackageSkipDevDependencies;
    /**
     * Sets whether or not the Node Audit Analyzer should use a local cache.
     */
    private Boolean nodeAuditSkipDevDependencies;
    /**
     * The list of filters (regular expressions) used by the RetireJS Analyzer
     * to exclude files that contain matching content..
     */
    @SuppressWarnings("CanBeFinal")
    private final List<String> retirejsFilters = new ArrayList<>();
    /**
     * Whether or not the RetireJS Analyzer filters non-vulnerable JS files from
     * the report; default is false.
     */
    private Boolean retirejsFilterNonVulnerable;
    /**
     * Whether or not the Ruby Bundle Audit Analyzer is enabled.
     */
    private Boolean bundleAuditAnalyzerEnabled;
    /**
     * Whether the CMake analyzer should be enabled.
     */
    private Boolean cmakeAnalyzerEnabled;
    /**
     * Whether or not the Open SSL analyzer is enabled.
     */
    private Boolean opensslAnalyzerEnabled;
    /**
     * Whether the python package analyzer should be enabled.
     */
    private Boolean pyPackageAnalyzerEnabled;
    /**
     * Whether the python distribution analyzer should be enabled.
     */
    private Boolean pyDistributionAnalyzerEnabled;
    /**
     * Whether or not the mix audit analyzer is enabled.
     */
    private Boolean mixAuditAnalyzerEnabled;
    /**
     * Whether or not the central analyzer is enabled.
     */
    private Boolean centralAnalyzerEnabled;
    /**
     * Whether or not the Central Analyzer should use a local cache.
     */
    private Boolean centralAnalyzerUseCache;
    /**
     * Whether or not the nexus analyzer is enabled.
     */
    private Boolean nexusAnalyzerEnabled;
    /**
     * The URL of a Nexus server's REST API end point
     * (http://domain/nexus/service/local).
     */
    private String nexusUrl;
    /**
     * The username to authenticate to the Nexus Server's REST API Endpoint.
     */
    private String nexusUser;
    /**
     * The password to authenticate to the Nexus Server's REST API Endpoint.
     */
    private String nexusPassword;
    /**
     * Whether or not the defined proxy should be used when connecting to Nexus.
     */
    private Boolean nexusUsesProxy;

    /**
     * Sets whether the Golang Dependency analyzer is enabled. Default is true.
     */
    private Boolean golangDepEnabled;
    /**
     * Sets whether Golang Module Analyzer is enabled; this requires `go` to be
     * installed. Default is true.
     */
    private Boolean golangModEnabled;
    /**
     * Sets the path to `go`.
     */
    private String pathToGo;
    /**
     * Sets whether the Dart analyzer is enabled. Default is true.
     */
    private Boolean dartAnalyzerEnabled;
    /**
     * The path to `yarn`.
     */
    private String pathToYarn;
    /**
     * The path to `pnpm`.
     */
    private String pathToPnpm;
    /**
     * Additional ZIP File extensions to add analyze. This should be a
     * comma-separated list of file extensions to treat like ZIP files.
     */
    private String zipExtensions;
    /**
     * The path to dotnet core for .NET assembly analysis.
     */
    private String pathToCore;
    /**
     * The name of the project being analyzed.
     */
    private String projectName = "dependency-check";
    /**
     * Specifies the destination directory for the generated Dependency-Check
     * report.
     */
    private String reportOutputDirectory = ".";
    /**
     * If using the JUNIT report format the junitFailOnCVSS sets the CVSS score
     * threshold that is considered a failure. The default is 0.
     */
    private float junitFailOnCVSS = 0;
    /**
     * Specifies if the build should be failed if a CVSS score above a specified
     * level is identified. The default is 11 which means since the CVSS scores
     * are 0-10, by default the build will never fail and the CVSS score is set
     * to 11. The valid range for the fail build on CVSS is 0 to 11, where
     * anything above 10 will not cause the build to fail.
     */
    private float failBuildOnCVSS = 11;
    /**
     * Sets whether auto-updating of the NVD CVE/CPE data is enabled. It is not
     * recommended that this be turned to false. Default is true.
     */
    private Boolean autoUpdate;
    /**
     * The report format to be generated (HTML, XML, CSV, JSON, JUNIT, SARIF,
     * JENKINS, GITLAB, ALL). Default is HTML.
     */
    private String reportFormat = "HTML";
    /**
     * The report format to be generated (HTML, XML, CSV, JSON, JUNIT, SARIF,
     * JENKINS, GITLAB, ALL). Default is HTML.
     */
    private final List<String> reportFormats = new ArrayList<>();
    /**
     * Whether the JSON and XML reports should be pretty printed; the default is
     * false.
     */
    private Boolean prettyPrint = null;

    /**
     * Suppression file paths.
     */
    @SuppressWarnings("CanBeFinal")
    private final List<String> suppressionFiles = new ArrayList<>();

    /**
     * The path to the suppression file.
     */
    private String hintsFile;
    /**
     * flag indicating whether or not to show a summary of findings.
     */
    private boolean showSummary = true;
    /**
     * Whether experimental analyzers are enabled.
     */
    private Boolean enableExperimental;
    /**
     * Whether retired analyzers are enabled.
     */
    private Boolean enableRetired;
    /**
     * Whether or not the Jar Analyzer is enabled.
     */
    private Boolean jarAnalyzerEnabled;
    /**
     * Whether or not the Archive Analyzer is enabled.
     */
    private Boolean archiveAnalyzerEnabled;
    /**
     * Whether or not the .NET Nuspec Analyzer is enabled.
     */
    private Boolean nuspecAnalyzerEnabled;
    /**
     * Whether or not the .NET Nuget packages.config file Analyzer is enabled.
     */
    private Boolean nugetconfAnalyzerEnabled;
    /**
     * Whether or not the Libman Analyzer is enabled.
     */
    private Boolean libmanAnalyzerEnabled;
    /**
     * Whether or not the PHP Composer Analyzer is enabled.
     */
    private Boolean composerAnalyzerEnabled;
    /**
     * Whether or not the PHP Composer Analyzer will skip "packages-dev".
     */
    private Boolean composerAnalyzerSkipDev;
    /**
     * Whether or not the Perl CPAN File Analyzer is enabled.
     */
    private Boolean cpanfileAnalyzerEnabled;

    /**
     * Whether or not the .NET Assembly Analyzer is enabled.
     */
    private Boolean assemblyAnalyzerEnabled;
    /**
     * Whether or not the MS Build Assembly Analyzer is enabled.
     */
    private Boolean msbuildAnalyzerEnabled;
    /**
     * Whether the autoconf analyzer should be enabled.
     */
    private Boolean autoconfAnalyzerEnabled;
    /**
     * Whether the pip analyzer should be enabled.
     */
    private Boolean pipAnalyzerEnabled;
    /**
     * Whether the Maven install.json analyzer should be enabled.
     */
    private Boolean mavenInstallAnalyzerEnabled;
    /**
     * Whether the pipfile analyzer should be enabled.
     */
    private Boolean pipfileAnalyzerEnabled;
    /**
     * Whether the Poetry analyzer should be enabled.
     */
    private Boolean poetryAnalyzerEnabled;
    /**
     * Sets the path for the mix_audit binary.
     */
    private String mixAuditPath;
    /**
     * Sets the path for the bundle-audit binary.
     */
    private String bundleAuditPath;
    /**
     * Sets the path for the working directory that the bundle-audit binary
     * should be executed from.
     */
    private String bundleAuditWorkingDirectory;
    /**
     * Whether or not the CocoaPods Analyzer is enabled.
     */
    private Boolean cocoapodsAnalyzerEnabled;
    /**
     * Whether or not the Carthage Analyzer is enabled.
     */
    private Boolean carthageAnalyzerEnabled;

    /**
     * Whether or not the Swift package Analyzer is enabled.
     */
    private Boolean swiftPackageManagerAnalyzerEnabled;
    /**
     * Whether or not the Swift package Analyzer is enabled.
     */
    private Boolean swiftPackageResolvedAnalyzerEnabled;

    /**
     * Whether or not the Sonatype OSS Index analyzer is enabled.
     */
    private Boolean ossindexAnalyzerEnabled;
    /**
     * Whether or not the Sonatype OSS Index analyzer should cache results.
     */
    private Boolean ossindexAnalyzerUseCache;
    /**
     * URL of the Sonatype OSS Index service.
     */
    private String ossindexAnalyzerUrl;
    /**
     * The username to use for the Sonatype OSS Index service.
     */
    private String ossindexAnalyzerUsername;
    /**
     * The password to use for the Sonatype OSS Index service.
     */
    private String ossindexAnalyzerPassword;
    /**
     * Whether we should only warn about Sonatype OSS Index remote errors
     * instead of failing completely.
     */
    private Boolean ossIndexAnalyzerWarnOnlyOnRemoteErrors;

    /**
     * Whether or not the Artifactory Analyzer is enabled.
     */
    private Boolean artifactoryAnalyzerEnabled;
    /**
     * The URL to Artifactory.
     */
    private String artifactoryAnalyzerUrl;
    /**
     * Whether or not Artifactory analysis should use the proxy..
     */
    private Boolean artifactoryAnalyzerUseProxy;
    /**
     * Whether or not Artifactory analysis should be parallelized.
     */
    private Boolean artifactoryAnalyzerParallelAnalysis;
    /**
     * The Artifactory username needed to connect.
     */
    private String artifactoryAnalyzerUsername;
    /**
     * The Artifactory API token needed to connect.
     */
    private String artifactoryAnalyzerApiToken;
    /**
     * The Artifactory bearer token.
     */
    private String artifactoryAnalyzerBearerToken;
    /**
     * Whether the version check is enabled
     */
    private Boolean versionCheckEnabled;

    /**
     * whether an unsused suppression rule should get force the build to fail
     */
    private boolean failBuildOnUnusedSuppressionRule = false;

    /**
     * The username to download user-authored suppression files from an HTTP Basic auth protected location.
     */
    private String suppressionFileUser;
    /**
     * The password to download user-authored suppression files from an HTTP Basic auth protected location.
     */
    private String suppressionFilePassword;
    /**
     * The token to download user-authored suppression files from an HTTP Bearer auth protected location.
     */
    private String suppressionFileBearerToken;

    //region Code copied from org.apache.tools.ant.taskdefs.PathConvert
    //The following code was copied Apache Ant PathConvert
    /**
     * Path to be converted
     */
    private Resources path = null;
    /**
     * Reference to path/file set to convert
     */
    private Reference refId = null;

    /**
     * Add an arbitrary ResourceCollection.
     *
     * @param rc the ResourceCollection to add.
     * @since Ant 1.7
     */
    public void add(ResourceCollection rc) {
        if (isReference()) {
            throw new BuildException("Nested elements are not allowed when using the refId attribute.");
        }
        getPath().add(rc);
    }

    /**
     * Returns the path. If the path has not been initialized yet, this class is
     * synchronized, and will instantiate the path object.
     *
     * @return the path
     */
    private synchronized Resources getPath() {
        if (path == null) {
            path = new Resources(getProject());
            path.setCache(true);
        }
        return path;
    }

    /**
     * Learn whether the refId attribute of this element been set.
     *
     * @return true if refId is valid.
     */
    public boolean isReference() {
        return refId != null;
    }

    /**
     * Add a reference to a Path, FileSet, DirSet, or FileList defined
     * elsewhere.
     *
     * @param r the reference to a path, fileset, dirset or filelist.
     */
    public synchronized void setRefId(Reference r) {
        if (path != null) {
            throw new BuildException("Nested elements are not allowed when using the refId attribute.");
        }
        refId = r;
    }

    /**
     * If this is a reference, this method will add the referenced resource
     * collection to the collection of paths.
     *
     * @throws BuildException if the reference is not to a resource collection
     */
    //declaring a throw that extends runtime exception may be a bad practice
    //but seems to be an ingrained practice within Ant as even the base `Task`
    //contains an `execute() throws BuildExecption`.
    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    private void dealWithReferences() throws BuildException {
        if (isReference()) {
            final Object o = refId.getReferencedObject(getProject());
            if (!(o instanceof ResourceCollection)) {
                throw new BuildException("refId '" + refId.getRefId()
                        + "' does not refer to a resource collection.");
            }
            getPath().add((ResourceCollection) o);
        }
    }
    //endregion COPIED from org.apache.tools.ant.taskdefs

    /**
     * Construct a new DependencyCheckTask.
     */
    public Check() {
        super();
        // Call this before Dependency Check Core starts logging anything - this way, all SLF4J messages from
        // core end up coming through this tasks logger
        StaticLoggerBinder.getSingleton().setTask(this);
    }

    /**
     * Add a suppression file.
     * <p>
     * This is called by Ant with the configured {@link SuppressionFile}.
     *
     * @param suppressionFile the suppression file to add.
     */
    public void addConfiguredSuppressionFile(final SuppressionFile suppressionFile) {
        suppressionFiles.add(suppressionFile.getPath());
    }

    /**
     * Add a report format.
     * <p>
     * This is called by Ant with the configured {@link ReportFormat}.
     *
     * @param reportFormat the reportFormat to add.
     */
    public void addConfiguredReportFormat(final ReportFormat reportFormat) {
        reportFormats.add(reportFormat.getFormat());
    }

    /**
     * Sets whether the version check is enabled.
     *
     * @param versionCheckEnabled a Boolean indicating if the version check is
     * enabled.
     */
    public void setVersionCheckEnabled(Boolean versionCheckEnabled) {
        this.versionCheckEnabled = versionCheckEnabled;
    }

    /**
     * Get the value of projectName.
     *
     * @return the value of projectName
     */
    public String getProjectName() {
        if (projectName == null) {
            projectName = "";
        }
        return projectName;
    }

    /**
     * Set the value of projectName.
     *
     * @param projectName new value of projectName
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Set the value of reportOutputDirectory.
     *
     * @param reportOutputDirectory new value of reportOutputDirectory
     */
    public void setReportOutputDirectory(String reportOutputDirectory) {
        this.reportOutputDirectory = reportOutputDirectory;
    }

    /**
     * Set the value of failBuildOnCVSS.
     *
     * @param failBuildOnCVSS new value of failBuildOnCVSS
     */
    public void setFailBuildOnCVSS(float failBuildOnCVSS) {
        this.failBuildOnCVSS = failBuildOnCVSS;
    }

    /**
     * Set the value of junitFailOnCVSS.
     *
     * @param junitFailOnCVSS new value of junitFailOnCVSS
     */
    public void setJunitFailOnCVSS(float junitFailOnCVSS) {
        this.junitFailOnCVSS = junitFailOnCVSS;
    }

    /**
     * Set the value of autoUpdate.
     *
     * @param autoUpdate new value of autoUpdate
     */
    public void setAutoUpdate(Boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    /**
     * Set the value of prettyPrint.
     *
     * @param prettyPrint new value of prettyPrint
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Set the value of reportFormat.
     *
     * @param reportFormat new value of reportFormat
     */
    public void setReportFormat(ReportFormats reportFormat) {
        this.reportFormat = reportFormat.getValue();
        this.reportFormats.add(this.reportFormat);
    }

    /**
     * Get the value of reportFormats.
     *
     * @return the value of reportFormats
     */
    public List<String> getReportFormats() {
        if (reportFormats.isEmpty()) {
            this.reportFormats.add(this.reportFormat);
        }
        return this.reportFormats;
    }

    /**
     * Set the value of suppressionFile.
     *
     * @param suppressionFile new value of suppressionFile
     */
    public void setSuppressionFile(String suppressionFile) {
        suppressionFiles.add(suppressionFile);
    }

    /**
     * Sets the username to download user-authored suppression files from an HTTP Basic auth protected location.
     *
     * @param suppressionFileUser The username
     */
    public void setSuppressionFileUser(String suppressionFileUser) {
        this.suppressionFileUser = suppressionFileUser;
    }

    /**
     * Sets the password/token to download user-authored suppression files from an HTTP Basic auth protected location.
     *
     * @param suppressionFilePassword The password/token
     */
    public void setSuppressionFilePassword(String suppressionFilePassword) {
        this.suppressionFilePassword = suppressionFilePassword;
    }

    /**
     * Sets the token to download user-authored suppression files from an HTTP Bearer auth protected location.
     *
     * @param suppressionFileBearerToken The token
     */
    public void setSuppressionFileBearerToken(String suppressionFileBearerToken) {
        this.suppressionFileBearerToken = suppressionFileBearerToken;
    }

    /**
     * Set the value of hintsFile.
     *
     * @param hintsFile new value of hintsFile
     */
    public void setHintsFile(String hintsFile) {
        this.hintsFile = hintsFile;
    }

    /**
     * Set the value of showSummary.
     *
     * @param showSummary new value of showSummary
     */
    public void setShowSummary(boolean showSummary) {
        this.showSummary = showSummary;
    }

    /**
     * Set the value of enableExperimental.
     *
     * @param enableExperimental new value of enableExperimental
     */
    public void setEnableExperimental(Boolean enableExperimental) {
        this.enableExperimental = enableExperimental;
    }

    /**
     * Set the value of enableRetired.
     *
     * @param enableRetired new value of enableRetired
     */
    public void setEnableRetired(Boolean enableRetired) {
        this.enableRetired = enableRetired;
    }

    /**
     * Sets whether or not the analyzer is enabled.
     *
     * @param jarAnalyzerEnabled the value of the new setting
     */
    public void setJarAnalyzerEnabled(Boolean jarAnalyzerEnabled) {
        this.jarAnalyzerEnabled = jarAnalyzerEnabled;
    }

    /**
     * Sets whether the analyzer is enabled.
     *
     * @param archiveAnalyzerEnabled the value of the new setting
     */
    public void setArchiveAnalyzerEnabled(Boolean archiveAnalyzerEnabled) {
        this.archiveAnalyzerEnabled = archiveAnalyzerEnabled;
    }

    /**
     * Sets whether or not the analyzer is enabled.
     *
     * @param assemblyAnalyzerEnabled the value of the new setting
     */
    public void setAssemblyAnalyzerEnabled(Boolean assemblyAnalyzerEnabled) {
        this.assemblyAnalyzerEnabled = assemblyAnalyzerEnabled;
    }

    /**
     * Sets whether or not the analyzer is enabled.
     *
     * @param msbuildAnalyzerEnabled the value of the new setting
     */
    public void setMSBuildAnalyzerEnabled(Boolean msbuildAnalyzerEnabled) {
        this.msbuildAnalyzerEnabled = msbuildAnalyzerEnabled;
    }

    /**
     * Sets whether or not the analyzer is enabled.
     *
     * @param nuspecAnalyzerEnabled the value of the new setting
     */
    public void setNuspecAnalyzerEnabled(Boolean nuspecAnalyzerEnabled) {
        this.nuspecAnalyzerEnabled = nuspecAnalyzerEnabled;
    }

    /**
     * Sets whether or not the analyzer is enabled.
     *
     * @param nugetconfAnalyzerEnabled the value of the new setting
     */
    public void setNugetconfAnalyzerEnabled(Boolean nugetconfAnalyzerEnabled) {
        this.nugetconfAnalyzerEnabled = nugetconfAnalyzerEnabled;
    }

    /**
     * Sets whether or not the analyzer is enabled.
     *
     * @param libmanAnalyzerEnabled the value of the new setting
     */
    public void setLibmanAnalyzerEnabled(Boolean libmanAnalyzerEnabled) {
        this.libmanAnalyzerEnabled = libmanAnalyzerEnabled;
    }

    /**
     * Set the value of composerAnalyzerEnabled.
     *
     * @param composerAnalyzerEnabled new value of composerAnalyzerEnabled
     */
    public void setComposerAnalyzerEnabled(Boolean composerAnalyzerEnabled) {
        this.composerAnalyzerEnabled = composerAnalyzerEnabled;
    }

    /**
     * Set the value of composerAnalyzerSkipDev.
     *
     * @param composerAnalyzerSkipDev new value of composerAnalyzerSkipDev
     */
    public void setComposerAnalyzerSkipDev(Boolean composerAnalyzerSkipDev) {
        this.composerAnalyzerSkipDev = composerAnalyzerSkipDev;
    }

    /**
     * Set the value of cpanfileAnalyzerEnabled.
     *
     * @param cpanfileAnalyzerEnabled new value of cpanfileAnalyzerEnabled
     */
    public void setCpanfileAnalyzerEnabled(Boolean cpanfileAnalyzerEnabled) {
        this.cpanfileAnalyzerEnabled = cpanfileAnalyzerEnabled;
    }

    /**
     * Set the value of autoconfAnalyzerEnabled.
     *
     * @param autoconfAnalyzerEnabled new value of autoconfAnalyzerEnabled
     */
    public void setAutoconfAnalyzerEnabled(Boolean autoconfAnalyzerEnabled) {
        this.autoconfAnalyzerEnabled = autoconfAnalyzerEnabled;
    }

    /**
     * Set the value of pipAnalyzerEnabled.
     *
     * @param pipAnalyzerEnabled new value of pipAnalyzerEnabled
     */
    public void setPipAnalyzerEnabled(Boolean pipAnalyzerEnabled) {
        this.pipAnalyzerEnabled = pipAnalyzerEnabled;
    }

    /**
     * Set the value of pipfileAnalyzerEnabled.
     *
     * @param pipfileAnalyzerEnabled new value of pipfileAnalyzerEnabled
     */
    public void setPipfileAnalyzerEnabled(Boolean pipfileAnalyzerEnabled) {
        this.pipfileAnalyzerEnabled = pipfileAnalyzerEnabled;
    }

    /**
     * Set the value of poetryAnalyzerEnabled.
     *
     * @param poetryAnalyzerEnabled new value of poetryAnalyzerEnabled
     */
    public void setPoetryAnalyzerEnabled(Boolean poetryAnalyzerEnabled) {
        this.poetryAnalyzerEnabled = poetryAnalyzerEnabled;
    }

    /**
     * Sets if the Bundle Audit Analyzer is enabled.
     *
     * @param bundleAuditAnalyzerEnabled whether or not the analyzer should be
     * enabled
     */
    public void setBundleAuditAnalyzerEnabled(Boolean bundleAuditAnalyzerEnabled) {
        this.bundleAuditAnalyzerEnabled = bundleAuditAnalyzerEnabled;
    }

    /**
     * Sets the path to the bundle audit executable.
     *
     * @param bundleAuditPath the path to the bundle audit executable
     */
    public void setBundleAuditPath(String bundleAuditPath) {
        this.bundleAuditPath = bundleAuditPath;
    }

    /**
     * Sets the path to the working directory that the bundle audit executable
     * should be executed from.
     *
     * @param bundleAuditWorkingDirectory the path to the working directory that
     * the bundle audit executable should be executed from.
     */
    public void setBundleAuditWorkingDirectory(String bundleAuditWorkingDirectory) {
        this.bundleAuditWorkingDirectory = bundleAuditWorkingDirectory;
    }

    /**
     * Sets whether or not the cocoapods analyzer is enabled.
     *
     * @param cocoapodsAnalyzerEnabled the state of the cocoapods analyzer
     */
    public void setCocoapodsAnalyzerEnabled(Boolean cocoapodsAnalyzerEnabled) {
        this.cocoapodsAnalyzerEnabled = cocoapodsAnalyzerEnabled;
    }

    /**
     * Sets whether or not the Carthage analyzer is enabled.
     *
     * @param carthageAnalyzerEnabled the state of the Carthage analyzer
     */
    public void setCarthageAnalyzerEnabled(Boolean carthageAnalyzerEnabled) {
        this.carthageAnalyzerEnabled = carthageAnalyzerEnabled;
    }

    /**
     * Sets the enabled state of the swift package manager analyzer.
     *
     * @param swiftPackageManagerAnalyzerEnabled the enabled state of the swift
     * package manager
     */
    public void setSwiftPackageManagerAnalyzerEnabled(Boolean swiftPackageManagerAnalyzerEnabled) {
        this.swiftPackageManagerAnalyzerEnabled = swiftPackageManagerAnalyzerEnabled;
    }

    /**
     * Sets the enabled state of the swift package manager analyzer.
     *
     * @param swiftPackageResolvedAnalyzerEnabled the enabled state of the swift
     * package resolved analyzer
     */
    public void setSwiftPackageResolvedAnalyzerEnabled(Boolean swiftPackageResolvedAnalyzerEnabled) {
        this.swiftPackageResolvedAnalyzerEnabled = swiftPackageResolvedAnalyzerEnabled;
    }

    /**
     * Set the value of opensslAnalyzerEnabled.
     *
     * @param opensslAnalyzerEnabled new value of opensslAnalyzerEnabled
     */
    public void setOpensslAnalyzerEnabled(Boolean opensslAnalyzerEnabled) {
        this.opensslAnalyzerEnabled = opensslAnalyzerEnabled;
    }

    /**
     * Set the value of nodeAnalyzerEnabled.
     *
     * @param nodeAnalyzerEnabled new value of nodeAnalyzerEnabled
     */
    public void setNodeAnalyzerEnabled(Boolean nodeAnalyzerEnabled) {
        this.nodeAnalyzerEnabled = nodeAnalyzerEnabled;
    }

    /**
     * Set the value of nodeAuditAnalyzerEnabled.
     *
     * @param nodeAuditAnalyzerEnabled new value of nodeAuditAnalyzerEnabled
     */
    public void setNodeAuditAnalyzerEnabled(Boolean nodeAuditAnalyzerEnabled) {
        this.nodeAuditAnalyzerEnabled = nodeAuditAnalyzerEnabled;
    }

    /**
     * Set the value of yarnAuditAnalyzerEnabled.
     *
     * @param yarnAuditAnalyzerEnabled new value of yarnAuditAnalyzerEnabled
     */
    public void setYarnAuditAnalyzerEnabled(Boolean yarnAuditAnalyzerEnabled) {
        this.yarnAuditAnalyzerEnabled = yarnAuditAnalyzerEnabled;
    }

    /**
     * Set the value of pnpmAuditAnalyzerEnabled.
     *
     * @param pnpmAuditAnalyzerEnabled new value of pnpmAuditAnalyzerEnabled
     */
    public void setPnpmAuditAnalyzerEnabled(Boolean pnpmAuditAnalyzerEnabled) {
        this.pnpmAuditAnalyzerEnabled = pnpmAuditAnalyzerEnabled;
    }

    /**
     * Set the value of nodeAuditAnalyzerUseCache.
     *
     * @param nodeAuditAnalyzerUseCache new value of nodeAuditAnalyzerUseCache
     */
    public void setNodeAuditAnalyzerUseCache(Boolean nodeAuditAnalyzerUseCache) {
        this.nodeAuditAnalyzerUseCache = nodeAuditAnalyzerUseCache;
    }

    /**
     * Set the value of nodePackageSkipDevDependencies.
     *
     * @param nodePackageSkipDevDependencies new value of
     * nodePackageSkipDevDependencies
     */
    public void setNodePackageSkipDevDependencies(Boolean nodePackageSkipDevDependencies) {
        this.nodePackageSkipDevDependencies = nodePackageSkipDevDependencies;
    }

    /**
     * Set the value of nodeAuditSkipDevDependencies.
     *
     * @param nodeAuditSkipDevDependencies new value of
     * nodeAuditSkipDevDependencies
     */
    public void setNodeAuditSkipDevDependencies(Boolean nodeAuditSkipDevDependencies) {
        this.nodeAuditSkipDevDependencies = nodeAuditSkipDevDependencies;
    }

    /**
     * Set the value of retirejsFilterNonVulnerable.
     *
     * @param retirejsFilterNonVulnerable new value of
     * retirejsFilterNonVulnerable
     */
    public void setRetirejsFilterNonVulnerable(Boolean retirejsFilterNonVulnerable) {
        this.retirejsFilterNonVulnerable = retirejsFilterNonVulnerable;
    }

    /**
     * Add a regular expression to the set of retire JS content filters.
     * <p>
     * This is called by Ant.
     *
     * @param retirejsFilter the regular expression used to filter based on file
     * content
     */
    public void addConfiguredRetirejsFilter(final RetirejsFilter retirejsFilter) {
        retirejsFilters.add(retirejsFilter.getRegex());
    }

    /**
     * Set the value of rubygemsAnalyzerEnabled.
     *
     * @param rubygemsAnalyzerEnabled new value of rubygemsAnalyzerEnabled
     */
    public void setRubygemsAnalyzerEnabled(Boolean rubygemsAnalyzerEnabled) {
        this.rubygemsAnalyzerEnabled = rubygemsAnalyzerEnabled;
    }

    /**
     * Set the value of pyPackageAnalyzerEnabled.
     *
     * @param pyPackageAnalyzerEnabled new value of pyPackageAnalyzerEnabled
     */
    public void setPyPackageAnalyzerEnabled(Boolean pyPackageAnalyzerEnabled) {
        this.pyPackageAnalyzerEnabled = pyPackageAnalyzerEnabled;
    }

    /**
     * Set the value of pyDistributionAnalyzerEnabled.
     *
     * @param pyDistributionAnalyzerEnabled new value of
     * pyDistributionAnalyzerEnabled
     */
    public void setPyDistributionAnalyzerEnabled(Boolean pyDistributionAnalyzerEnabled) {
        this.pyDistributionAnalyzerEnabled = pyDistributionAnalyzerEnabled;
    }

    /**
     * Set the value of mixAuditAnalyzerEnabled.
     *
     * @param mixAuditAnalyzerEnabled new value of mixAuditAnalyzerEnabled
     */
    public void setMixAuditAnalyzerEnabled(Boolean mixAuditAnalyzerEnabled) {
        this.mixAuditAnalyzerEnabled = mixAuditAnalyzerEnabled;
    }

    /**
     * Sets the path to the mix audit executable.
     *
     * @param mixAuditPath the path to the bundle audit executable
     */
    public void setMixAuditPath(String mixAuditPath) {
        this.mixAuditPath = mixAuditPath;
    }
    /**
     * Set the value of centralAnalyzerEnabled.
     *
     * @param centralAnalyzerEnabled new value of centralAnalyzerEnabled
     */
    public void setCentralAnalyzerEnabled(Boolean centralAnalyzerEnabled) {
        this.centralAnalyzerEnabled = centralAnalyzerEnabled;
    }

    /**
     * Set the value of centralAnalyzerUseCache.
     *
     * @param centralAnalyzerUseCache new value of centralAnalyzerUseCache
     */
    public void setCentralAnalyzerUseCache(Boolean centralAnalyzerUseCache) {
        this.centralAnalyzerUseCache = centralAnalyzerUseCache;
    }

    /**
     * Set the value of nexusAnalyzerEnabled.
     *
     * @param nexusAnalyzerEnabled new value of nexusAnalyzerEnabled
     */
    public void setNexusAnalyzerEnabled(Boolean nexusAnalyzerEnabled) {
        this.nexusAnalyzerEnabled = nexusAnalyzerEnabled;
    }

    /**
     * Set the value of golangDepEnabled.
     *
     * @param golangDepEnabled new value of golangDepEnabled
     */
    public void setGolangDepEnabled(Boolean golangDepEnabled) {
        this.golangDepEnabled = golangDepEnabled;
    }

    /**
     * Set the value of golangModEnabled.
     *
     * @param golangModEnabled new value of golangModEnabled
     */
    public void setGolangModEnabled(Boolean golangModEnabled) {
        this.golangModEnabled = golangModEnabled;
    }

    /**
     * Set the value of dartAnalyzerEnabled.
     *
     * @param dartAnalyzerEnabled new value of dartAnalyzerEnabled
     */
    public void setDartAnalyzerEnabled(Boolean dartAnalyzerEnabled) {
        this.dartAnalyzerEnabled = dartAnalyzerEnabled;
    }

    /**
     * Set the value of pathToYarn.
     *
     * @param pathToYarn new value of pathToYarn
     */
    public void setPathToYarn(String pathToYarn) {
        this.pathToYarn = pathToYarn;
    }

    /**
     * Set the value of pathToPnpm.
     *
     * @param pathToPnpm new value of pathToPnpm
     */
    public void setPathToPnpm(String pathToPnpm) {
        this.pathToPnpm = pathToPnpm;
    }

    /**
     * Set the value of pathToGo.
     *
     * @param pathToGo new value of pathToGo
     */
    public void setPathToGo(String pathToGo) {
        this.pathToGo = pathToGo;
    }

    /**
     * Set the value of nexusUrl.
     *
     * @param nexusUrl new value of nexusUrl
     */
    public void setNexusUrl(String nexusUrl) {
        this.nexusUrl = nexusUrl;
    }

    /**
     * Set the value of nexusUser.
     *
     * @param nexusUser new value of nexusUser
     */
    public void setNexusUser(String nexusUser) {
        this.nexusUser = nexusUser;
    }

    /**
     * Set the value of nexusPassword.
     *
     * @param nexusPassword new value of nexusPassword
     */
    public void setNexusPassword(String nexusPassword) {
        this.nexusPassword = nexusPassword;
    }

    /**
     * Set the value of nexusUsesProxy.
     *
     * @param nexusUsesProxy new value of nexusUsesProxy
     */
    public void setNexusUsesProxy(Boolean nexusUsesProxy) {
        this.nexusUsesProxy = nexusUsesProxy;
    }

    /**
     * Set the value of zipExtensions.
     *
     * @param zipExtensions new value of zipExtensions
     */
    public void setZipExtensions(String zipExtensions) {
        this.zipExtensions = zipExtensions;
    }

    /**
     * Set the value of pathToCore.
     *
     * @param pathToCore new value of pathToCore
     */
    public void setPathToDotnetCore(String pathToCore) {
        this.pathToCore = pathToCore;
    }

    /**
     * Set value of {@link #ossindexAnalyzerEnabled}.
     *
     * @param ossindexAnalyzerEnabled new value of ossindexAnalyzerEnabled
     */
    public void setOssindexAnalyzerEnabled(Boolean ossindexAnalyzerEnabled) {
        this.ossindexAnalyzerEnabled = ossindexAnalyzerEnabled;
    }

    /**
     * Set value of {@link #ossindexAnalyzerUseCache}.
     *
     * @param ossindexAnalyzerUseCache new value of ossindexAnalyzerUseCache
     */
    public void setOssindexAnalyzerUseCache(Boolean ossindexAnalyzerUseCache) {
        this.ossindexAnalyzerUseCache = ossindexAnalyzerUseCache;
    }

    /**
     * Set value of {@link #ossindexAnalyzerUrl}.
     *
     * @param ossindexAnalyzerUrl new value of ossindexAnalyzerUrl
     */
    public void setOssindexAnalyzerUrl(String ossindexAnalyzerUrl) {
        this.ossindexAnalyzerUrl = ossindexAnalyzerUrl;
    }

    /**
     * Set value of {@link #ossindexAnalyzerUsername}.
     *
     * @param ossindexAnalyzerUsername new value of ossindexAnalyzerUsername
     */
    public void setOssindexAnalyzerUsername(String ossindexAnalyzerUsername) {
        this.ossindexAnalyzerUsername = ossindexAnalyzerUsername;
    }

    /**
     * Set value of {@link #ossindexAnalyzerPassword}.
     *
     * @param ossindexAnalyzerPassword new value of ossindexAnalyzerPassword
     */
    public void setOssindexAnalyzerPassword(String ossindexAnalyzerPassword) {
        this.ossindexAnalyzerPassword = ossindexAnalyzerPassword;
    }

    /**
     * Set value of {@link #ossIndexAnalyzerWarnOnlyOnRemoteErrors}.
     *
     * @param ossIndexWarnOnlyOnRemoteErrors the value of
     * ossIndexWarnOnlyOnRemoteErrors
     */
    public void setOssIndexWarnOnlyOnRemoteErrors(Boolean ossIndexWarnOnlyOnRemoteErrors) {
        this.ossIndexAnalyzerWarnOnlyOnRemoteErrors = ossIndexWarnOnlyOnRemoteErrors;
    }

    /**
     * Set the value of cmakeAnalyzerEnabled.
     *
     * @param cmakeAnalyzerEnabled new value of cmakeAnalyzerEnabled
     */
    public void setCmakeAnalyzerEnabled(Boolean cmakeAnalyzerEnabled) {
        this.cmakeAnalyzerEnabled = cmakeAnalyzerEnabled;
    }

    /**
     * Set the value of artifactoryAnalyzerEnabled.
     *
     * @param artifactoryAnalyzerEnabled new value of artifactoryAnalyzerEnabled
     */
    public void setArtifactoryAnalyzerEnabled(Boolean artifactoryAnalyzerEnabled) {
        this.artifactoryAnalyzerEnabled = artifactoryAnalyzerEnabled;
    }

    /**
     * Set the value of artifactoryAnalyzerUrl.
     *
     * @param artifactoryAnalyzerUrl new value of artifactoryAnalyzerUrl
     */
    public void setArtifactoryAnalyzerUrl(String artifactoryAnalyzerUrl) {
        this.artifactoryAnalyzerUrl = artifactoryAnalyzerUrl;
    }

    /**
     * Set the value of artifactoryAnalyzerUseProxy.
     *
     * @param artifactoryAnalyzerUseProxy new value of
     * artifactoryAnalyzerUseProxy
     */
    public void setArtifactoryAnalyzerUseProxy(Boolean artifactoryAnalyzerUseProxy) {
        this.artifactoryAnalyzerUseProxy = artifactoryAnalyzerUseProxy;
    }

    /**
     * Set the value of artifactoryAnalyzerParallelAnalysis.
     *
     * @param artifactoryAnalyzerParallelAnalysis new value of
     * artifactoryAnalyzerParallelAnalysis
     */
    public void setArtifactoryAnalyzerParallelAnalysis(Boolean artifactoryAnalyzerParallelAnalysis) {
        this.artifactoryAnalyzerParallelAnalysis = artifactoryAnalyzerParallelAnalysis;
    }

    /**
     * Set the value of artifactoryAnalyzerUsername.
     *
     * @param artifactoryAnalyzerUsername new value of
     * artifactoryAnalyzerUsername
     */
    public void setArtifactoryAnalyzerUsername(String artifactoryAnalyzerUsername) {
        this.artifactoryAnalyzerUsername = artifactoryAnalyzerUsername;
    }

    /**
     * Set the value of artifactoryAnalyzerApiToken.
     *
     * @param artifactoryAnalyzerApiToken new value of
     * artifactoryAnalyzerApiToken
     */
    public void setArtifactoryAnalyzerApiToken(String artifactoryAnalyzerApiToken) {
        this.artifactoryAnalyzerApiToken = artifactoryAnalyzerApiToken;
    }

    /**
     * Set the value of artifactoryAnalyzerBearerToken.
     *
     * @param artifactoryAnalyzerBearerToken new value of
     * artifactoryAnalyzerBearerToken
     */
    public void setArtifactoryAnalyzerBearerToken(String artifactoryAnalyzerBearerToken) {
        this.artifactoryAnalyzerBearerToken = artifactoryAnalyzerBearerToken;
    }

    /**
     * Set the value of failBuildOnUnusedSuppressionRule.
     *
     * @param failBuildOnUnusedSuppressionRule new value of
     * failBuildOnUnusedSuppressionRule
     */
    public void setFailBuildOnUnusedSuppressionRule(boolean failBuildOnUnusedSuppressionRule) {
        this.failBuildOnUnusedSuppressionRule = failBuildOnUnusedSuppressionRule;
    }

    //see note on `dealWithReferences()` for information on this suppression
    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    @Override
    protected void executeWithContextClassloader() throws BuildException {
        dealWithReferences();
        validateConfiguration();
        populateSettings();
        try {
            Downloader.getInstance().configure(getSettings());
        } catch (InvalidSettingException e) {
            throw new BuildException(e);
        }
        try (Engine engine = new Engine(Check.class.getClassLoader(), getSettings())) {
            for (Resource resource : getPath()) {
                final FileProvider provider = resource.as(FileProvider.class);
                if (provider != null) {
                    final File file = provider.getFile();
                    if (file != null && file.exists()) {
                        engine.scan(file);
                    }
                }
            }
            final ExceptionCollection exceptions = callExecuteAnalysis(engine);
            if (exceptions == null || !exceptions.isFatal()) {
                for (String format : getReportFormats()) {
                    engine.writeReports(getProjectName(), new File(reportOutputDirectory), format, exceptions);
                }
                if (this.failBuildOnCVSS <= 10) {
                    checkForFailure(engine.getDependencies());
                }
                if (this.showSummary) {
                    DependencyCheckScanAgent.showSummary(engine.getDependencies());
                }
            }
        } catch (DatabaseException ex) {
            final String msg = "Unable to connect to the dependency-check database; analysis has stopped";
            if (this.isFailOnError()) {
                throw new BuildException(msg, ex);
            }
            log(msg, ex, Project.MSG_ERR);
        } catch (ReportException ex) {
            final String msg = "Unable to generate the dependency-check report";
            if (this.isFailOnError()) {
                throw new BuildException(msg, ex);
            }
            log(msg, ex, Project.MSG_ERR);
        } finally {
            getSettings().cleanup();
        }
    }

    /**
     * Wraps the call to `engine.analyzeDependencies()` and correctly handles
     * any exceptions
     *
     * @param engine a reference to the engine
     * @return the collection of any exceptions that occurred; otherwise
     * <code>null</code>
     * @throws BuildException thrown if configured to fail the build on errors
     */
    //see note on `dealWithReferences()` for information on this suppression
    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    private ExceptionCollection callExecuteAnalysis(final Engine engine) throws BuildException {
        ExceptionCollection exceptions = null;
        try {
            engine.analyzeDependencies();
        } catch (ExceptionCollection ex) {
            if (this.isFailOnError()) {
                throw new BuildException(ex);
            }
            exceptions = ex;
        }
        return exceptions;
    }

    /**
     * Validate the configuration to ensure the parameters have been properly
     * configured/initialized.
     *
     * @throws BuildException if the task was not configured correctly.
     */
    //see note on `dealWithReferences()` for information on this suppression
    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    private synchronized void validateConfiguration() throws BuildException {
        if (path == null) {
            throw new BuildException("No project dependencies have been defined to analyze.");
        }
        if (failBuildOnCVSS < 0 || failBuildOnCVSS > 11) {
            throw new BuildException("Invalid configuration, failBuildOnCVSS must be between 0 and 11.");
        }
    }

    /**
     * Takes the properties supplied and updates the dependency-check settings.
     * Additionally, this sets the system properties required to change the
     * proxy server, port, and connection timeout.
     *
     * @throws BuildException thrown when an invalid setting is configured.
     */
    //see note on `dealWithReferences()` for information on this suppression
    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    @Override
    protected void populateSettings() throws BuildException {
        super.populateSettings();
        getSettings().setBooleanIfNotNull(Settings.KEYS.AUTO_UPDATE, autoUpdate);
        getSettings().setArrayIfNotEmpty(Settings.KEYS.SUPPRESSION_FILE, suppressionFiles);
        getSettings().setStringIfNotEmpty(Settings.KEYS.SUPPRESSION_FILE_USER, suppressionFileUser);
        getSettings().setStringIfNotEmpty(Settings.KEYS.SUPPRESSION_FILE_PASSWORD, suppressionFilePassword);
        getSettings().setStringIfNotEmpty(Settings.KEYS.SUPPRESSION_FILE_BEARER_TOKEN, suppressionFileBearerToken);
        getSettings().setBooleanIfNotNull(Settings.KEYS.UPDATE_VERSION_CHECK_ENABLED, versionCheckEnabled);
        getSettings().setStringIfNotEmpty(Settings.KEYS.HINTS_FILE, hintsFile);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_EXPERIMENTAL_ENABLED, enableExperimental);
        getSettings().setBooleanIfNotNull(Settings.KEYS.PRETTY_PRINT, prettyPrint);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_RETIRED_ENABLED, enableRetired);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_JAR_ENABLED, jarAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_PYTHON_DISTRIBUTION_ENABLED, pyDistributionAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_PYTHON_PACKAGE_ENABLED, pyPackageAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_RUBY_GEMSPEC_ENABLED, rubygemsAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_OPENSSL_ENABLED, opensslAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_CMAKE_ENABLED, cmakeAnalyzerEnabled);

        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_ARTIFACTORY_ENABLED, artifactoryAnalyzerEnabled);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_ARTIFACTORY_URL, artifactoryAnalyzerUrl);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_ARTIFACTORY_USES_PROXY, artifactoryAnalyzerUseProxy);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_ARTIFACTORY_PARALLEL_ANALYSIS, artifactoryAnalyzerParallelAnalysis);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_ARTIFACTORY_API_USERNAME, artifactoryAnalyzerUsername);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_ARTIFACTORY_API_TOKEN, artifactoryAnalyzerApiToken);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_ARTIFACTORY_BEARER_TOKEN, artifactoryAnalyzerBearerToken);

        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_SWIFT_PACKAGE_MANAGER_ENABLED, swiftPackageManagerAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_SWIFT_PACKAGE_RESOLVED_ENABLED, swiftPackageResolvedAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_COCOAPODS_ENABLED, cocoapodsAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_CARTHAGE_ENABLED, carthageAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_BUNDLE_AUDIT_ENABLED, bundleAuditAnalyzerEnabled);
        getSettings().setStringIfNotNull(Settings.KEYS.ANALYZER_BUNDLE_AUDIT_PATH, bundleAuditPath);
        getSettings().setStringIfNotNull(Settings.KEYS.ANALYZER_BUNDLE_AUDIT_WORKING_DIRECTORY, bundleAuditWorkingDirectory);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_AUTOCONF_ENABLED, autoconfAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_MAVEN_INSTALL_ENABLED, mavenInstallAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_PIP_ENABLED, pipAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_PIPFILE_ENABLED, pipfileAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_POETRY_ENABLED, poetryAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_COMPOSER_LOCK_ENABLED, composerAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_COMPOSER_LOCK_SKIP_DEV, composerAnalyzerSkipDev);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_CPANFILE_ENABLED, cpanfileAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NODE_PACKAGE_ENABLED, nodeAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NODE_PACKAGE_SKIPDEV, nodePackageSkipDevDependencies);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NODE_AUDIT_ENABLED, nodeAuditAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_YARN_AUDIT_ENABLED, yarnAuditAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_PNPM_AUDIT_ENABLED, pnpmAuditAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NODE_AUDIT_USE_CACHE, nodeAuditAnalyzerUseCache);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NODE_AUDIT_SKIPDEV, nodeAuditSkipDevDependencies);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_RETIREJS_FILTER_NON_VULNERABLE, retirejsFilterNonVulnerable);
        getSettings().setArrayIfNotEmpty(Settings.KEYS.ANALYZER_RETIREJS_FILTERS, retirejsFilters);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_GOLANG_DEP_ENABLED, golangDepEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_GOLANG_MOD_ENABLED, golangModEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_DART_ENABLED, dartAnalyzerEnabled);
        getSettings().setStringIfNotNull(Settings.KEYS.ANALYZER_GOLANG_PATH, pathToGo);
        getSettings().setStringIfNotNull(Settings.KEYS.ANALYZER_YARN_PATH, pathToYarn);
        getSettings().setStringIfNotNull(Settings.KEYS.ANALYZER_PNPM_PATH, pathToPnpm);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_MIX_AUDIT_ENABLED, mixAuditAnalyzerEnabled);
        getSettings().setStringIfNotNull(Settings.KEYS.ANALYZER_MIX_AUDIT_PATH, mixAuditPath);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NUSPEC_ENABLED, nuspecAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NUGETCONF_ENABLED, nugetconfAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_LIBMAN_ENABLED, libmanAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, centralAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_CENTRAL_USE_CACHE, centralAnalyzerUseCache);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NEXUS_ENABLED, nexusAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_ARCHIVE_ENABLED, archiveAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_ASSEMBLY_ENABLED, assemblyAnalyzerEnabled);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_MSBUILD_PROJECT_ENABLED, msbuildAnalyzerEnabled);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_NEXUS_URL, nexusUrl);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_NEXUS_USER, nexusUser);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_NEXUS_PASSWORD, nexusPassword);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_NEXUS_USES_PROXY, nexusUsesProxy);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ADDITIONAL_ZIP_EXTENSIONS, zipExtensions);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_ASSEMBLY_DOTNET_PATH, pathToCore);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_OSSINDEX_ENABLED, ossindexAnalyzerEnabled);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_OSSINDEX_URL, ossindexAnalyzerUrl);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_OSSINDEX_USER, ossindexAnalyzerUsername);
        getSettings().setStringIfNotEmpty(Settings.KEYS.ANALYZER_OSSINDEX_PASSWORD, ossindexAnalyzerPassword);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_OSSINDEX_USE_CACHE, ossindexAnalyzerUseCache);
        getSettings().setBooleanIfNotNull(Settings.KEYS.ANALYZER_OSSINDEX_WARN_ONLY_ON_REMOTE_ERRORS, ossIndexAnalyzerWarnOnlyOnRemoteErrors);
        getSettings().setFloat(Settings.KEYS.JUNIT_FAIL_ON_CVSS, junitFailOnCVSS);
        getSettings().setBooleanIfNotNull(Settings.KEYS.FAIL_ON_UNUSED_SUPPRESSION_RULE, failBuildOnUnusedSuppressionRule);
    }

    /**
     * Checks to see if a vulnerability has been identified with a CVSS score
     * that is above the threshold set in the configuration.
     *
     * @param dependencies the list of dependency objects
     * @throws BuildException thrown if a CVSS score is found that is higher
     * than the threshold set
     */
    //see note on `dealWithReferences()` for information on this suppression
    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    private void checkForFailure(Dependency[] dependencies) throws BuildException {
        final StringBuilder ids = new StringBuilder();
        for (Dependency d : dependencies) {
            boolean addName = true;
            for (Vulnerability v : d.getVulnerabilities()) {
                if ((v.getCvssV2() != null && v.getCvssV2().getCvssData().getBaseScore() >= failBuildOnCVSS)
                        || (v.getCvssV3() != null && v.getCvssV3().getCvssData().getBaseScore() >= failBuildOnCVSS)
                        || (v.getCvssV4() != null && v.getCvssV4().getCvssData().getBaseScore() >= failBuildOnCVSS)
                        || (v.getUnscoredSeverity() != null && SeverityUtil.estimateCvssV2(v.getUnscoredSeverity()) >= failBuildOnCVSS)
                        //safety net to fail on any if for some reason the above misses on 0
                        || (failBuildOnCVSS <= 0.0f)) {
                    if (addName) {
                        addName = false;
                        ids.append(NEW_LINE).append(d.getFileName()).append(" (")
                           .append(Stream.concat(d.getSoftwareIdentifiers().stream(), d.getVulnerableSoftwareIdentifiers().stream())
                                         .map(Identifier::getValue)
                                         .collect(Collectors.joining(", ")))
                           .append("): ")
                           .append(v.getName());
                    } else {
                        ids.append(", ").append(v.getName());
                    }
                }
            }
        }
        if (ids.length() > 0) {
            final String msg;
            if (showSummary) {
                msg = String.format("%n%nDependency-Check Failure:%n"
                        + "One or more dependencies were identified with vulnerabilities that have a CVSS score greater than or equal to '%.1f': %s%n"
                        + "See the dependency-check report for more details.%n%n", failBuildOnCVSS, ids);
            } else {
                msg = String.format("%n%nDependency-Check Failure:%n"
                        + "One or more dependencies were identified with vulnerabilities.%n%n"
                        + "See the dependency-check report for more details.%n%n");
            }
            throw new BuildException(msg);
        }
    }

    /**
     * An enumeration of supported report formats: "ALL", "HTML", "XML", "CSV",
     * "JSON", "JUNIT", "SARIF", 'JENkINS', etc..
     */
    public static class ReportFormats extends EnumeratedAttribute {

        /**
         * Returns the list of values for the report format.
         *
         * @return the list of values for the report format
         */
        @Override
        public String[] getValues() {
            int i = 0;
            final Format[] formats = Format.values();
            final String[] values = new String[formats.length];
            for (Format format : formats) {
                values[i++] = format.name();
            }
            return values;
        }
    }

    /**
     * A class for Ant to represent the
     * {@code <reportFormat format="<format>"/>} nested element to define
     * multiple report formats for the ant-task.
     */
    public static class ReportFormat {

        /**
         * The format of this ReportFormat.
         */
        private ReportFormats format;

        /**
         * Gets the format as a String.
         *
         * @return the String representing a report format
         */
        public String getFormat() {
            return this.format.getValue();
        }

        /**
         * Sets the format.
         *
         * @param format the String value for one of the {@link ReportFormats}
         * @throws BuildException When the offered String is not one of the
         * valid values of the {@link ReportFormats} EnumeratedAttribute
         */
        public void setFormat(final String format) {
            this.format = (ReportFormats) EnumeratedAttribute.getInstance(ReportFormats.class, format);
        }
    }
}
//CSON: MethodCount
