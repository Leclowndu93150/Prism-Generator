package dev.prism.generator

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

/**
 * A chain step that composes multiple wizard steps into a linear chain.
 * Each step in the chain is the parent of the next, ensuring that data propagation
 * (e.g., finding [com.intellij.ide.wizard.NewProjectWizardBaseData]) works correctly.
 *
 * This is a local implementation of the chain utility pattern, similar to what
 * MinecraftDev uses to compose wizard steps.
 */
class NewProjectWizardChainStep private constructor(
    parent: NewProjectWizardStep,
    private val allSteps: List<NewProjectWizardStep>
) : AbstractNewProjectWizardStep(parent) {

    override fun setupUI(builder: Panel) {
        for (step in allSteps) {
            step.setupUI(builder)
        }
    }

    override fun setupProject(project: Project) {
        for (step in allSteps) {
            step.setupProject(project)
        }
    }

    companion object {
        /**
         * Chains multiple step providers onto [this] step. Each provider receives the
         * previously created step as its parent, building a proper ancestor chain so
         * that step data lookup (e.g., `NewProjectWizardBaseData`) works correctly.
         *
         * Usage:
         * ```
         * RootNewProjectWizardStep(context)
         *     .chain(
         *         ::NewProjectWizardBaseStep,
         *         ::GitNewProjectWizardStep,
         *         ::PrismConfigStep
         *     )
         * ```
         */
        fun <S : NewProjectWizardStep> S.chain(
            vararg providers: (NewProjectWizardStep) -> NewProjectWizardStep
        ): NewProjectWizardChainStep {
            val steps = mutableListOf<NewProjectWizardStep>()
            var current: NewProjectWizardStep = this
            for (provider in providers) {
                val next = provider(current)
                steps.add(next)
                current = next
            }
            return NewProjectWizardChainStep(this, steps)
        }
    }
}
