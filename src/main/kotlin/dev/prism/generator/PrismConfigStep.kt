package dev.prism.generator

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * The main configuration step for the Prism Mod project wizard.
 * Provides UI fields for mod metadata, version/loader selection, and project options.
 */
class PrismConfigStep(private val parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    // -- Metadata fields --
    var modId: String = ""
    var modName: String = ""
    var groupId: String = "com.example"
    var license: String? = "MIT"

    // -- Version entries --
    val versionEntries: MutableList<VersionEntry> = mutableListOf(VersionEntry())

    // -- Options --
    var enableKotlin: Boolean = false
    var enableSharedCommon: Boolean = false

    // -- UI references for dynamic version list --
    private lateinit var versionListPanel: JPanel
    private val versionPanels: MutableList<VersionEntryPanel> = mutableListOf()

    data class VersionEntry(
        var mcVersion: String = "1.21.1",
        var mode: String = "Single Loader",
        var singleLoader: String = "NeoForge",
        var fabric: Boolean = false,
        var neoforge: Boolean = false,
        var forge: Boolean = false,
        var legacyForge: Boolean = false
    )

    companion object {
        val MC_VERSIONS = arrayOf(
            "1.7.10", "1.12.2", "1.16.5", "1.18.2", "1.19.4",
            "1.20.1", "1.20.4", "1.21.1", "1.21.4", "26.1"
        )
        val LICENSES = arrayOf("MIT", "GPL-3.0", "Apache-2.0", "All Rights Reserved")
        val LOADERS = arrayOf("Fabric", "NeoForge", "Forge", "Legacy Forge")
        val MODES = arrayOf("Single Loader", "Multi Loader")
    }

    /**
     * Walk the parent step chain to find an instance of [NewProjectWizardBaseData].
     */
    private fun findBaseData(): NewProjectWizardBaseData? {
        var step: NewProjectWizardStep? = parent
        while (step != null) {
            if (step is NewProjectWizardBaseData) return step
            step = (step as? AbstractNewProjectWizardStep)?.let { getParent(it) }
        }
        return null
    }

    /**
     * Access the parent step from an [AbstractNewProjectWizardStep] via reflection.
     * The parentStep constructor parameter is stored as a field but may not always
     * be directly accessible as a Kotlin property depending on the IntelliJ version.
     */
    private fun getParent(step: AbstractNewProjectWizardStep): NewProjectWizardStep? {
        return try {
            // In IntelliJ 2024.3, AbstractNewProjectWizardStep stores parent as 'parentStep'
            val field = AbstractNewProjectWizardStep::class.java.getDeclaredField("parentStep")
            field.isAccessible = true
            field.get(step) as? NewProjectWizardStep
        } catch (_: Exception) {
            null
        }
    }

    override fun setupUI(builder: Panel) {
        // Derive defaults from the project name entered in the base step
        val baseData = findBaseData()
        val projectName = baseData?.name ?: "mymod"
        if (modId.isEmpty()) modId = projectName.lowercase().replace(Regex("[^a-z0-9_]"), "")
        if (modName.isEmpty()) modName = projectName.replaceFirstChar { it.uppercase() }

        builder.apply {
            group("Mod Metadata") {
                row("Mod ID:") {
                    textField()
                        .bindText(::modId)
                        .columns(30)
                }
                row("Mod Name:") {
                    textField()
                        .bindText(::modName)
                        .columns(30)
                }
                row("Group ID:") {
                    textField()
                        .bindText(::groupId)
                        .columns(30)
                }
                row("License:") {
                    comboBox(LICENSES.toList())
                        .bindItem(::license)
                }
            }

            group("Minecraft Versions") {
                row {
                    cell(createVersionListComponent())
                        .resizableColumn()
                }
            }

            group("Options") {
                row {
                    checkBox("Enable Kotlin")
                        .bindSelected(::enableKotlin)
                }
                row {
                    checkBox("Enable shared common module")
                        .bindSelected(::enableSharedCommon)
                }
            }
        }
    }

    private fun createVersionListComponent(): JPanel {
        val outer = JPanel(BorderLayout())
        outer.border = JBUI.Borders.empty()

        versionListPanel = JPanel()
        versionListPanel.layout = BoxLayout(versionListPanel, BoxLayout.Y_AXIS)

        // Add the initial version entry panel
        addVersionPanel()

        val scrollPane = JBScrollPane(versionListPanel)
        scrollPane.preferredSize = JBUI.size(600, 200)
        scrollPane.border = JBUI.Borders.empty()

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("Add Version")
        val removeButton = JButton("Remove Last")

        addButton.addActionListener {
            addVersionPanel()
            versionListPanel.revalidate()
            versionListPanel.repaint()
        }

        removeButton.addActionListener {
            if (versionPanels.size > 1) {
                val last = versionPanels.removeAt(versionPanels.size - 1)
                versionEntries.removeAt(versionEntries.size - 1)
                versionListPanel.remove(last.panel)
                versionListPanel.revalidate()
                versionListPanel.repaint()
            }
        }

        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)

        outer.add(scrollPane, BorderLayout.CENTER)
        outer.add(buttonPanel, BorderLayout.SOUTH)
        return outer
    }

    private fun addVersionPanel() {
        val entry = if (versionEntries.size <= versionPanels.size) {
            val e = VersionEntry()
            versionEntries.add(e)
            e
        } else {
            versionEntries[versionPanels.size]
        }
        val vp = VersionEntryPanel(entry, versionPanels.size + 1)
        versionPanels.add(vp)
        versionListPanel.add(vp.panel)
    }

    /**
     * Swing panel for a single version entry with MC version, mode, and loader controls.
     */
    inner class VersionEntryPanel(val entry: VersionEntry, index: Int) {
        val panel: JPanel = JPanel()

        private val mcVersionCombo = ComboBox(MC_VERSIONS)
        private val modeCombo = ComboBox(MODES)
        private val singleLoaderCombo = ComboBox(LOADERS)
        private val fabricCheck = JCheckBox("Fabric")
        private val neoforgeCheck = JCheckBox("NeoForge")
        private val forgeCheck = JCheckBox("Forge")
        private val legacyForgeCheck = JCheckBox("Legacy Forge")
        private val singleLoaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        private val multiLoaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

        init {
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Version $index"),
                JBUI.Borders.empty(4)
            )

            // Row 1: MC Version + Mode
            val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2))
            row1.add(JLabel("MC Version:"))
            mcVersionCombo.selectedItem = entry.mcVersion
            row1.add(mcVersionCombo)
            row1.add(JLabel("Mode:"))
            modeCombo.selectedItem = entry.mode
            row1.add(modeCombo)
            panel.add(row1)

            // Single loader row
            singleLoaderPanel.add(JLabel("Loader:"))
            singleLoaderCombo.selectedItem = entry.singleLoader
            singleLoaderPanel.add(singleLoaderCombo)

            // Multi loader row
            fabricCheck.isSelected = entry.fabric
            neoforgeCheck.isSelected = entry.neoforge
            forgeCheck.isSelected = entry.forge
            legacyForgeCheck.isSelected = entry.legacyForge
            multiLoaderPanel.add(JLabel("Loaders:"))
            multiLoaderPanel.add(fabricCheck)
            multiLoaderPanel.add(neoforgeCheck)
            multiLoaderPanel.add(forgeCheck)
            multiLoaderPanel.add(legacyForgeCheck)

            panel.add(singleLoaderPanel)
            panel.add(multiLoaderPanel)
            updateLoaderVisibility()

            // Wire up listeners to sync UI state to the entry
            mcVersionCombo.addActionListener {
                entry.mcVersion = mcVersionCombo.selectedItem as String
            }
            modeCombo.addActionListener {
                entry.mode = modeCombo.selectedItem as String
                updateLoaderVisibility()
                panel.revalidate()
                panel.repaint()
            }
            singleLoaderCombo.addActionListener {
                entry.singleLoader = singleLoaderCombo.selectedItem as String
            }
            fabricCheck.addActionListener { entry.fabric = fabricCheck.isSelected }
            neoforgeCheck.addActionListener { entry.neoforge = neoforgeCheck.isSelected }
            forgeCheck.addActionListener { entry.forge = forgeCheck.isSelected }
            legacyForgeCheck.addActionListener { entry.legacyForge = legacyForgeCheck.isSelected }
        }

        private fun updateLoaderVisibility() {
            val isMulti = entry.mode == "Multi Loader"
            singleLoaderPanel.isVisible = !isMulti
            multiLoaderPanel.isVisible = isMulti
        }

        /** Force-sync all combo/checkbox state back into the entry model. */
        fun syncToEntry() {
            entry.mcVersion = mcVersionCombo.selectedItem as String
            entry.mode = modeCombo.selectedItem as String
            entry.singleLoader = singleLoaderCombo.selectedItem as String
            entry.fabric = fabricCheck.isSelected
            entry.neoforge = neoforgeCheck.isSelected
            entry.forge = forgeCheck.isSelected
            entry.legacyForge = legacyForgeCheck.isSelected
        }
    }

    override fun setupProject(project: Project) {
        // Sync all version panel state before generating
        for (vp in versionPanels) {
            vp.syncToEntry()
        }

        val baseData = findBaseData()
        val projectPath = baseData?.path ?: project.basePath ?: return
        val projectName = baseData?.name ?: project.name

        val config = PrismProjectGenerator.PrismConfig(
            projectPath = "$projectPath/$projectName",
            projectName = projectName,
            modId = modId.ifBlank { projectName.lowercase().replace(Regex("[^a-z0-9_]"), "") },
            modName = modName.ifBlank { projectName },
            groupId = groupId.ifBlank { "com.example" },
            license = license ?: "MIT",
            versions = versionEntries.map { entry ->
                val loaders = if (entry.mode == "Multi Loader") {
                    buildList {
                        if (entry.fabric) add("fabric")
                        if (entry.neoforge) add("neoforge")
                        if (entry.forge) add("forge")
                        if (entry.legacyForge) add("legacyforge")
                    }
                } else {
                    listOf(
                        when (entry.singleLoader) {
                            "Fabric" -> "fabric"
                            "NeoForge" -> "neoforge"
                            "Forge" -> "forge"
                            "Legacy Forge" -> "legacyforge"
                            else -> "neoforge"
                        }
                    )
                }
                PrismProjectGenerator.VersionConfig(
                    mcVersion = entry.mcVersion,
                    multiLoader = entry.mode == "Multi Loader",
                    loaders = loaders
                )
            },
            enableKotlin = enableKotlin,
            enableSharedCommon = enableSharedCommon
        )

        PrismProjectGenerator.generate(config)
    }
}
