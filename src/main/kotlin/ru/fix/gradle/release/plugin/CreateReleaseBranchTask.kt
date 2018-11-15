package ru.fix.gradle.release.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

open class CreateReleaseBranchTask : DefaultTask() {

    @TaskAction
    fun createReleaseBranch() {

        val git = GitExtensionConfiguration(project).buildGitClient()
        val versionManager = VersionManager(git)

        val extension = project.extensions.findByType(ReleaseExtension::class.java)
        checkNotNull(extension) { "Failed to find ReleaseExtension" }


        if (git.getCurrentBranch() != extension.mainBranch) {
            throw GradleException("Release branch can be built only from ${extension.mainBranch} branch")
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

            if (git.isBranchExists(branch)) {
                project.logger.lifecycle("Branch with name $branch already exists")
                continue
            }

            git.createBranch(branch, true)

            project.logger.lifecycle("Branch $branch was successfully created")
            break

        }
    }
}