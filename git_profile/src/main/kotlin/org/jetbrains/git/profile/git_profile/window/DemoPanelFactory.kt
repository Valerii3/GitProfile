package org.jetbrains.git.profile.git_profile.window


import com.google.gson.JsonArray
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.jetbrains.git.profile.git_profile.api.GitRepoStats
import org.jetbrains.git.profile.git_profile.utils.GitUtils
import org.jetbrains.git.profile.git_profile.utils.createLanguageUsageChart
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit
import java.awt.*
import javax.swing.JPanel


class GitStatsPanelFactory : ToolWindowFactory {
    companion object {
        private val GITHUB_TOKEN = System.getenv("GITHUB_TOKEN")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(createToolWindowPanel(project), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createToolWindowPanel(project: Project): JPanel {

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val progressBar = JProgressBar().apply {
            isIndeterminate = true // You can also set this to false and update the progress explicitly if needed
            isVisible = true // Initially hidden
        }
        panel.add(progressBar)

        val editorPane = createEditorPane()
        panel.add(JBScrollPane(editorPane))

        val chartPanel = JPanel()
        panel.add(Box.createVerticalStrut(20))
        panel.add(chartPanel)
        panel.add(Box.createVerticalStrut(20))

        val contributorListLabel = JLabel("Contributors").apply {
            font = JBUI.Fonts.label(16f)
            alignmentX = JLabel.CENTER_ALIGNMENT
        }
        panel.add(contributorListLabel)

        val listModel = DefaultListModel<String>()
        val contributorList = JBList(listModel)
        panel.add(JScrollPane(contributorList))

        val backButton = JButton("Back").apply {
            isVisible = false
        }
        panel.add(backButton)

        val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        fetchData(project, coroutineScope, editorPane, listModel, chartPanel, backButton) {
            progressBar.isVisible = false
        }
        setupContributorListListener(contributorList, project, coroutineScope, editorPane, chartPanel, backButton)

        return panel
    }

    private fun createEditorPane(): JEditorPane {
        return JEditorPane().apply {
            isEditable = false
            editorKit = HTMLEditorKit()
        }
    }

    private fun fetchData(
        project: Project,
        coroutineScope: CoroutineScope,
        editorPane: JEditorPane,
        listModel: DefaultListModel<String>,
        chartPanel: JPanel,
        backButton: JButton,
        onComplete: () -> Unit
    ) {
        coroutineScope.launch {
            try {
                val projectDir = File(project.basePath ?: return@launch)
                val remoteUrl = GitUtils.getRemoteUrl(projectDir) ?: return@launch
                val (owner, repo) = GitUtils.parseGitHubRepo(remoteUrl) ?: return@launch

                val service = GitRepoStats()
                val repoName = service.getRepoName(owner, repo, GITHUB_TOKEN)
                val repoDescription = try {
                    service.getRepoDescription(owner, repo, GITHUB_TOKEN)
                } catch (e: Exception) {
                    "No description available"
                }

                val commitCount = service.getCommitCount(owner, repo, GITHUB_TOKEN)
                val branchCount = service.getBranchCount(owner, repo, GITHUB_TOKEN)
                val contributors = service.getContributors(owner, repo, GITHUB_TOKEN)
                val languageData = service.getLanguageUsage(owner, repo, GITHUB_TOKEN)

                val htmlContent =
                    repoName?.let { buildHtmlContent(it, repoDescription, commitCount, branchCount, contributors.size()) }
                if (htmlContent != null) {
                    updateUI(editorPane, listModel, contributors, htmlContent, chartPanel, languageData, backButton)
                    onComplete()
                }
            } catch (e: Exception) {
                handleError(project, e)
            }
        }
    }

    private fun buildHtmlContent(
        repoName: String,
        repoDescription: String?,
        commitCount: Int,
        branchCount: Int,
        contributorCount: Int
    ): String {
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h1>$repoName</h1>
                <p>$repoDescription</p>
                <hr/>
                <p><strong>Total commits:</strong> $commitCount</p>
                <p><strong>Number of branches:</strong> $branchCount</p>
                <p><strong>Number of contributors:</strong> $contributorCount</p>
            </body>
            </html>
        """.trimIndent()
    }

    private suspend fun updateUI(
        editorPane: JEditorPane,
        listModel: DefaultListModel<String>,
        contributors: JsonArray,
        htmlContent: String,
        chartPanel: JPanel,
        languageData: Map<String, Double>?,
        backButton: JButton
    ) {
        withContext(Dispatchers.Main) {
            editorPane.text = htmlContent
            contributors.forEach { contributor ->
                listModel.addElement(contributor.asJsonObject["login"].asString)
            }

            val languageUsageChart = createLanguageUsageChart(languageData)
            chartPanel.layout = BorderLayout()
            chartPanel.add(languageUsageChart, BorderLayout.CENTER)
            chartPanel.revalidate()

            backButton.addActionListener {
                editorPane.text = htmlContent
                chartPanel.layout = BorderLayout()
                chartPanel.add(languageUsageChart, BorderLayout.CENTER)
                chartPanel.revalidate()
                backButton.isVisible = false
            }
        }
    }

    private suspend fun handleError(project: Project, e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Messages.showErrorDialog(project, "Error fetching repository statistics: ${e.message}", "Error")
        }
    }

    private fun setupContributorListListener(
        contributorList: JBList<String>,
        project: Project,
        coroutineScope: CoroutineScope,
        editorPane: JEditorPane,
        chartPanel: JPanel,
        backButton: JButton
    ) {
        contributorList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) { // Double-click detected
                    val selectedContributor = contributorList.selectedValue
                    if (selectedContributor != null) {
                        fetchContributorData(project, coroutineScope, editorPane, chartPanel, backButton, selectedContributor)
                    }
                }
            }
        })
    }

    private fun fetchContributorData(
        project: Project,
        coroutineScope: CoroutineScope,
        editorPane: JEditorPane,
        chartPanel: JPanel,
        backButton: JButton,
        selectedContributor: String
    ) {
        coroutineScope.launch {
            try {
                val projectDir = File(project.basePath ?: return@launch)
                val remoteUrl = GitUtils.getRemoteUrl(projectDir) ?: return@launch
                val (owner, repo) = GitUtils.parseGitHubRepo(remoteUrl) ?: return@launch

                val service = GitRepoStats()
                val allCommits = service.getAllCommitsByContributor(owner, repo, selectedContributor, GITHUB_TOKEN)
                val contributorHtmlContent = buildContributorHtmlContent(selectedContributor, allCommits.size)
                updateContributorUI(editorPane, chartPanel, backButton, contributorHtmlContent)
            } catch (e: Exception) {
                handleContributorError(project, e)
            }
        }
    }

    private fun buildContributorHtmlContent(selectedContributor: String, commits: Int): String {
        return """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h1>Statistics for $selectedContributor</h1>
                <p><strong>Total commits:</strong> $commits</p>
            </body>
            </html>
        """.trimIndent()
    }

    private suspend fun updateContributorUI(
        editorPane: JEditorPane,
        chartPanel: JPanel,
        backButton: JButton,
        contributorHtmlContent: String
    ) {
        withContext(Dispatchers.Main) {
            editorPane.text = contributorHtmlContent
            chartPanel.removeAll()
            chartPanel.revalidate()
            chartPanel.repaint()
            backButton.isVisible = true
        }
    }

    private suspend fun handleContributorError(project: Project, e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Messages.showErrorDialog(project, "Error fetching contributor statistics: ${e.message}", "Error")
        }
    }
}

