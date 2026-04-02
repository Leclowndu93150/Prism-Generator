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

class PrismConfigStep(private val parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    var modId: String = ""
    var modName: String = ""
    var groupId: String = "com.example"
    var license: String? = "MIT"
    val versionEntries: MutableList<VersionEntry> = mutableListOf(VersionEntry())
    var enableKotlin: Boolean = false
    var enableSharedCommon: Boolean = false

    private lateinit var versionListPanel: JPanel
    private val versionPanels: MutableList<VersionEntryPanel> = mutableListOf()

    data class VersionEntry(
        var mcVersion: String = "1.21.1",
        var mode: String = "Single Loader",
        var singleLoader: String = "NeoForge",
        var fabric: Boolean = false,
        var neoforge: Boolean = false,
        var forge: Boolean = false,
        var legacyForge: Boolean = false,
        var fabricLoaderVersion: String = "",
        var fabricApiVersion: String = "",
        var neoforgeVersion: String = "",
        var forgeVersion: String = "",
    )

    companion object {
        val MC_VERSIONS = arrayOf("1.7.10", "1.12.2", "1.18.2", "1.20.1", "1.21.1", "1.21.4", "26.1")
        val LICENSES = arrayOf("MIT", "GPL-3.0", "Apache-2.0", "All Rights Reserved")
        val MODES = arrayOf("Single Loader", "Multi Loader")

        val VALID_LOADERS: Map<String, List<String>> = mapOf(
            "1.7.10" to listOf("Legacy Forge"),
            "1.12.2" to listOf("Legacy Forge"),
            "1.18.2" to listOf("Fabric", "Forge"),
            "1.20.1" to listOf("Fabric", "Forge"),
            "1.21.1" to listOf("Fabric", "NeoForge"),
            "1.21.4" to listOf("Fabric", "NeoForge"),
            "26.1" to listOf("Fabric", "NeoForge"),
        )

        val DEFAULT_VERSIONS: Map<String, Map<String, String>> = mapOf(
            "1.7.10" to mapOf("legacyforge" to "10.13.4.1614"),
            "1.12.2" to mapOf("legacyforge" to "14.23.5.2847"),
            "1.18.2" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.77.0+1.18.2", "forge" to "40.2.21"),
            "1.20.1" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.92.7+1.20.1", "forge" to "47.4.18"),
            "1.21.1" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.116.9+1.21.1", "neoforge" to "21.1.222"),
            "1.21.4" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.119.2+1.21.4", "neoforge" to "21.4.86"),
            "26.1" to mapOf("fabric" to "0.18.6", "fabricApi" to "0.145.2+26.1.1", "neoforge" to "26.1.1.0-beta"),
        )

        fun loaderKey(display: String): String = when (display) {
            "Fabric" -> "fabric"
            "NeoForge" -> "neoforge"
            "Forge" -> "forge"
            "Legacy Forge" -> "legacyforge"
            else -> "neoforge"
        }
    }

    private fun findBaseData(): NewProjectWizardBaseData? {
        var step: NewProjectWizardStep? = parent
        while (step != null) {
            if (step is NewProjectWizardBaseData) return step
            step = (step as? AbstractNewProjectWizardStep)?.let { getParent(it) }
        }
        return null
    }

    private fun getParent(step: AbstractNewProjectWizardStep): NewProjectWizardStep? {
        return try {
            val field = AbstractNewProjectWizardStep::class.java.getDeclaredField("parentStep")
            field.isAccessible = true
            field.get(step) as? NewProjectWizardStep
        } catch (_: Exception) {
            null
        }
    }

    override fun setupUI(builder: Panel) {
        val baseData = findBaseData()
        val projectName = baseData?.name ?: "mymod"
        if (modId.isEmpty()) modId = projectName.lowercase().replace(Regex("[^a-z0-9_]"), "")
        if (modName.isEmpty()) modName = projectName.replaceFirstChar { it.uppercase() }

        builder.apply {
            group("Mod Metadata") {
                row("Mod ID:") { textField().bindText(::modId).columns(30) }
                row("Mod Name:") { textField().bindText(::modName).columns(30) }
                row("Group ID:") { textField().bindText(::groupId).columns(30) }
                row("License:") { comboBox(LICENSES.toList()).bindItem(::license) }
            }

            group("Minecraft Versions") {
                row { cell(createVersionListComponent()).resizableColumn() }
            }

            group("Options") {
                row { checkBox("Enable Kotlin").bindSelected(::enableKotlin) }
                row { checkBox("Enable shared common module").bindSelected(::enableSharedCommon) }
            }
        }
    }

    private fun createVersionListComponent(): JPanel {
        val outer = JPanel(BorderLayout())
        versionListPanel = JPanel()
        versionListPanel.layout = BoxLayout(versionListPanel, BoxLayout.Y_AXIS)
        addVersionPanel()

        val scrollPane = JBScrollPane(versionListPanel)
        scrollPane.preferredSize = JBUI.size(650, 200)
        scrollPane.minimumSize = JBUI.size(650, 150)
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

    inner class VersionEntryPanel(val entry: VersionEntry, index: Int) {
        val panel: JPanel = JPanel()

        private val mcVersionCombo = ComboBox(MC_VERSIONS)
        private val modeCombo = ComboBox(MODES)
        private val singleLoaderCombo = ComboBox(arrayOf<String>())

        private val fabricCheck = JCheckBox("Fabric")
        private val neoforgeCheck = JCheckBox("NeoForge")
        private val forgeCheck = JCheckBox("Forge")
        private val legacyForgeCheck = JCheckBox("Legacy Forge")

        private val fabricLoaderField = JTextField(12)
        private val fabricApiField = JTextField(14)
        private val neoforgeVersionField = JTextField(12)
        private val forgeVersionField = JTextField(12)
        private val legacyForgeVersionField = JTextField(12)

        private val singleLoaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        private val multiLoaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        private val versionFieldsPanel = JPanel()

        init {
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Version $index"),
                JBUI.Borders.empty(4)
            )

            val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2))
            row1.add(JLabel("MC Version:"))
            mcVersionCombo.selectedItem = entry.mcVersion
            row1.add(mcVersionCombo)
            row1.add(JLabel("Mode:"))
            modeCombo.selectedItem = entry.mode
            row1.add(modeCombo)
            panel.add(row1)

            singleLoaderPanel.add(JLabel("Loader:"))
            singleLoaderPanel.add(singleLoaderCombo)

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

            versionFieldsPanel.layout = BoxLayout(versionFieldsPanel, BoxLayout.Y_AXIS)
            panel.add(versionFieldsPanel)

            updateLoadersForVersion()
            updateLoaderVisibility()
            updateVersionFields()

            mcVersionCombo.addActionListener {
                entry.mcVersion = mcVersionCombo.selectedItem as String
                updateLoadersForVersion()
                updateLoaderVisibility()
                fillDefaults()
                updateVersionFields()
                panel.revalidate()
                panel.repaint()
            }
            modeCombo.addActionListener {
                entry.mode = modeCombo.selectedItem as String
                updateLoaderVisibility()
                updateVersionFields()
                panel.revalidate()
                panel.repaint()
            }
            singleLoaderCombo.addActionListener {
                entry.singleLoader = (singleLoaderCombo.selectedItem as? String) ?: ""
                updateVersionFields()
                panel.revalidate()
                panel.repaint()
            }
            fabricCheck.addActionListener { entry.fabric = fabricCheck.isSelected; updateVersionFields(); panel.revalidate() }
            neoforgeCheck.addActionListener { entry.neoforge = neoforgeCheck.isSelected; updateVersionFields(); panel.revalidate() }
            forgeCheck.addActionListener { entry.forge = forgeCheck.isSelected; updateVersionFields(); panel.revalidate() }
            legacyForgeCheck.addActionListener { entry.legacyForge = legacyForgeCheck.isSelected; updateVersionFields(); panel.revalidate() }

            fillDefaults()
        }

        private fun fillDefaults() {
            val defaults = DEFAULT_VERSIONS[entry.mcVersion] ?: return
            fabricLoaderField.text = defaults["fabric"] ?: ""
            fabricApiField.text = defaults["fabricApi"] ?: ""
            neoforgeVersionField.text = defaults["neoforge"] ?: ""
            forgeVersionField.text = defaults["forge"] ?: ""
            legacyForgeVersionField.text = defaults["legacyforge"] ?: ""
        }

        private fun updateLoadersForVersion() {
            val valid = VALID_LOADERS[entry.mcVersion] ?: listOf("NeoForge")

            singleLoaderCombo.removeAllItems()
            for (l in valid) singleLoaderCombo.addItem(l)
            if (valid.isNotEmpty()) {
                singleLoaderCombo.selectedIndex = 0
                entry.singleLoader = valid[0]
            }

            fabricCheck.isEnabled = "Fabric" in valid
            neoforgeCheck.isEnabled = "NeoForge" in valid
            forgeCheck.isEnabled = "Forge" in valid
            legacyForgeCheck.isEnabled = "Legacy Forge" in valid

            if (!fabricCheck.isEnabled) { fabricCheck.isSelected = false; entry.fabric = false }
            if (!neoforgeCheck.isEnabled) { neoforgeCheck.isSelected = false; entry.neoforge = false }
            if (!forgeCheck.isEnabled) { forgeCheck.isSelected = false; entry.forge = false }
            if (!legacyForgeCheck.isEnabled) { legacyForgeCheck.isSelected = false; entry.legacyForge = false }

            if (valid.size <= 1) {
                modeCombo.selectedItem = "Single Loader"
                modeCombo.isEnabled = false
                entry.mode = "Single Loader"
            } else {
                modeCombo.isEnabled = true
            }
        }

        private fun updateLoaderVisibility() {
            val isMulti = entry.mode == "Multi Loader"
            singleLoaderPanel.isVisible = !isMulti
            multiLoaderPanel.isVisible = isMulti
        }

        private fun updateVersionFields() {
            versionFieldsPanel.removeAll()

            val activeLoaders = if (entry.mode == "Multi Loader") {
                buildList {
                    if (entry.fabric) add("fabric")
                    if (entry.neoforge) add("neoforge")
                    if (entry.forge) add("forge")
                    if (entry.legacyForge) add("legacyforge")
                }
            } else {
                listOf(loaderKey(entry.singleLoader))
            }

            for (loader in activeLoaders) {
                when (loader) {
                    "fabric" -> {
                        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
                        row1.add(JLabel("Fabric Loader:"))
                        row1.add(fabricLoaderField)
                        versionFieldsPanel.add(row1)
                        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
                        row2.add(JLabel("Fabric API:"))
                        row2.add(fabricApiField)
                        versionFieldsPanel.add(row2)
                    }
                    "neoforge" -> {
                        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
                        row.add(JLabel("NeoForge Version:"))
                        row.add(neoforgeVersionField)
                        versionFieldsPanel.add(row)
                    }
                    "forge" -> {
                        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
                        row.add(JLabel("Forge Version:"))
                        row.add(forgeVersionField)
                        versionFieldsPanel.add(row)
                    }
                    "legacyforge" -> {
                        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
                        row.add(JLabel("Forge Version:"))
                        row.add(legacyForgeVersionField)
                        versionFieldsPanel.add(row)
                    }
                }
            }
        }

        fun syncToEntry() {
            entry.mcVersion = mcVersionCombo.selectedItem as String
            entry.mode = modeCombo.selectedItem as String
            entry.singleLoader = (singleLoaderCombo.selectedItem as? String) ?: ""
            entry.fabric = fabricCheck.isSelected
            entry.neoforge = neoforgeCheck.isSelected
            entry.forge = forgeCheck.isSelected
            entry.legacyForge = legacyForgeCheck.isSelected
            entry.fabricLoaderVersion = fabricLoaderField.text
            entry.fabricApiVersion = fabricApiField.text
            entry.neoforgeVersion = neoforgeVersionField.text
            entry.forgeVersion = forgeVersionField.text
        }
    }

    override fun setupProject(project: Project) {
        for (vp in versionPanels) vp.syncToEntry()

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
                    listOf(loaderKey(entry.singleLoader))
                }
                PrismProjectGenerator.VersionConfig(
                    mcVersion = entry.mcVersion,
                    multiLoader = entry.mode == "Multi Loader",
                    loaders = loaders,
                    fabricLoaderVersion = entry.fabricLoaderVersion,
                    fabricApiVersion = entry.fabricApiVersion,
                    neoforgeVersion = entry.neoforgeVersion,
                    forgeVersion = entry.forgeVersion,
                )
            },
            enableKotlin = enableKotlin,
            enableSharedCommon = enableSharedCommon
        )

        PrismProjectGenerator.generate(config)
    }
}
