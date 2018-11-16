package ru.fix.gradle.release.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File


class BranchGardener(private val project: Project) {

    fun createRelease() {
        val git = GitExtensionConfiguration(project).buildGitClient()
        val versionManager = VersionManager(git)

        if (git.isUncommittedChangesExist()) {
            project.logger.lifecycle("" +
                    "Could not create new release due to uncommitted changes. " +
                    "Please commit your current work before creating new release.")
            return
        }

        git.fetchTags()

        val extension = project.extensions.findByType(ReleaseExtension::class.java)
        checkNotNull(extension) { "Failed to find ReleaseExtension" }


        // by default current branch is used as release branch
        // but user can specify explicitly which branch
        if (project.hasProperty(ProjectProperties.RELEASE_BRANCH_VERSION)) {
            val releaseBranchVersion = project.property(ProjectProperties.RELEASE_BRANCH_VERSION).toString()
            project.logger.lifecycle("Using user defined branch version: $releaseBranchVersion")

            if (!versionManager.isValidBranchVersion(releaseBranchVersion)) {
                throw GradleException("Invalid release branch version: $releaseBranchVersion. Should be in x.y format")
            }

            val targetBranch = "${extension.releaseBranchPrefix}$releaseBranchVersion"
            if (git.getCurrentBranch() != targetBranch) {
                project.logger.lifecycle("Switching to release branch $targetBranch")

                if (git.isLocalBranchExists(targetBranch)) {
                    git.checkoutLocalBranch(targetBranch)
                } else {
                    git.checkoutRemoteBranch(targetBranch)
                }
            }
        }

        val branch = git.getCurrentBranch()

        checkValidBranch(extension.releaseBranchPrefix, branch)
        val baseVersion = versionManager.extractVersionFromBranch(branch)

        val version = versionManager.supposeReleaseVersion(baseVersion)

        project.logger.lifecycle("Creating release for version $version")

        val files = File("./").walkTopDown()
                .filter { it.name == "gradle.properties" }

        val fileList = files.toList();

        if (fileList.isEmpty()) {
            throw GradleException("There are no gradle.properties in project. Terminating")
        }

        val tempBranch = "temp_release_${extension.releaseBranchPrefix}$version"

        with(git) {

            if (isLocalBranchExists(tempBranch)) {
                throw GradleException("Temporary branch $tempBranch already exists. Please delete it first")
            }

            createBranch(tempBranch, true)
            fileList.forEach { versionManager.updateVersionInFile(it.absolutePath, version) }

            commitFilesInIndex("Updating version to $version")
            val tagRef = createTag(version, "Release $version")


            if (project.hasProperty(ProjectProperties.CHECKOUT_TAG) &&
                    project.property(ProjectProperties.CHECKOUT_TAG).toString().toBoolean()) {
                checkoutTag(version)
            } else {
                checkoutLocalBranch(branch)
            }

            deleteBranch(tempBranch)

            pushTag(tagRef)
        }
    }

    private fun checkValidBranch(branchPrefix: String, currentBranch: String) {
        project.logger.lifecycle("Checking branch $currentBranch matches release branch naming pattern")
        if (!Regex("$branchPrefix(\\d+)\\.(\\d+)").matches(currentBranch)) {
            throw GradleException("Invalid release branch")
        }
    }

    fun createReleaseBranch() {
        val git = GitExtensionConfiguration(project).buildGitClient()
        val versionManager = VersionManager(git)

        if (git.isUncommittedChangesExist()) {
            project.logger.lifecycle("" +
                    "Could not create new release branch due to uncommitted changes. " +
                    "Please commit your current work before creating new release branch.")
            return
        }

        val extension = project.extensions.findByType(ReleaseExtension::class.java)
        checkNotNull(extension) { "Failed to find ReleaseExtension" }


        if (git.getCurrentBranch() != extension.mainBranch) {
            throw GradleException("You are not in the main branch: ${extension.mainBranch}.\n" +
                    "Release branch can be built only from ${extension.mainBranch} branch.")
        }

        val supposedVersion = versionManager.supposeBranchVersion()
        project.logger.lifecycle("Please specify release version (Should be in x.y format) [$supposedVersion]")


        while (true) {

            var input = readLine()

            if (input == null || input.isBlank()) {
                input = supposedVersion
            }

            if (versionManager.branchVersionExists(input)) {
                project.logger.lifecycle("Version $input already exists")
                continue
            }

            if (!versionManager.isValidBranchVersion(input)) {
                project.logger.lifecycle("Please specify valid version")
                continue
            }

            val branch = "${extension.releaseBranchPrefix}$input"

            if (git.isLocalBranchExists(branch)) {
                project.logger.lifecycle("Branch with name $branch already exists")
                continue
            }

            git.createBranch(branch, true)

            project.logger.lifecycle("Branch $branch was successfully created")
            break

        }
    }
}