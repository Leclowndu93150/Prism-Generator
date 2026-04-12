package dev.prism.generator

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import dev.prism.generator.NewProjectWizardChainStep.Companion.chain
import javax.swing.Icon

class PrismModuleBuilder : AbstractNewProjectWizardBuilder() {

    override fun getPresentableName(): String = "Prism Mod"

    override fun getDescription(): String = "Multi-version, multi-loader Minecraft mod"

    override fun getNodeIcon(): Icon = PrismIcons.PRISM

    override fun getGroupName(): String = "Minecraft"

    override fun createStep(context: WizardContext): NewProjectWizardStep {
        var baseStep: NewProjectWizardBaseStep? = null
        val providers = mutableListOf<(NewProjectWizardStep) -> NewProjectWizardStep>(
            { parent ->
                val step = NewProjectWizardBaseStep(parent)
                baseStep = step
                step
            },
            ::PrismConfigStep,
        )
        try {
            Class.forName("com.intellij.ide.wizard.GitNewProjectWizardStep")
            providers.add { _ ->
                com.intellij.ide.wizard.GitNewProjectWizardStep(baseStep!!)
            }
        } catch (_: ClassNotFoundException) {
        }
        return RootNewProjectWizardStep(context)
            .chain(*providers.toTypedArray())
    }
}
