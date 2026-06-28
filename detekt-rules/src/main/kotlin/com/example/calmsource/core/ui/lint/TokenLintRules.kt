package com.example.calmsource.core.ui.lint

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

// SINGLE SOURCE OF TRUTH — DO NOT FORK (Detekt custom rules for Lumen token enforcement)

class ForbiddenScreenDpLiteral(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "ForbiddenScreenDpLiteral",
        severity = Severity.CodeSmell,
        description = "Use LumenTokens instead of raw .dp in screen files.",
        debt = Debt.FIVE_MINS,
    )

    private val screenFile = Regex("""(Screen|Section)\.kt$""")

    override fun visitKtFile(file: KtFile) {
        if (file.name == "LumenTokens.generated.kt" || file.name == "GlassSurface.kt") return
        if (!screenFile.containsMatchIn(file.name)) return
        super.visitKtFile(file)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        var parent = expression.parent
        while (parent != null) {
            if (parent is org.jetbrains.kotlin.psi.KtImportDirective) return
            parent = parent.parent
        }
        if (expression.selectorExpression?.text == "dp") {
            val text = expression.text
            val allowed = listOf("0.dp", "1.dp", "0f.dp", "1f.dp", "0.0.dp", "1.0.dp", "0.0f.dp", "1.0f.dp")
            if (text !in allowed) {
                report(CodeSmell(issue, Entity.from(expression), "Replace $text with LumenTokens"))
            }
        }
    }
}

class ForbiddenScreenSpLiteral(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "ForbiddenScreenSpLiteral",
        severity = Severity.CodeSmell,
        description = "Use LumenTokens or LumenType instead of raw .sp in screen files.",
        debt = Debt.FIVE_MINS,
    )

    private val screenFile = Regex("""(Screen|Section)\.kt$""")

    override fun visitKtFile(file: KtFile) {
        if (file.name == "LumenTokens.generated.kt" || file.name == "GlassSurface.kt") return
        if (!screenFile.containsMatchIn(file.name)) return
        super.visitKtFile(file)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        var parent = expression.parent
        while (parent != null) {
            if (parent is org.jetbrains.kotlin.psi.KtImportDirective) return
            parent = parent.parent
        }
        if (expression.selectorExpression?.text == "sp") {
            report(CodeSmell(issue, Entity.from(expression), "Replace ${expression.text} with LumenTokens or LumenType"))
        }
    }
}

class ForbiddenScreenColorLiteral(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "ForbiddenScreenColorLiteral",
        severity = Severity.CodeSmell,
        description = "Use LumenTokens.Color instead of Color(0x…) in screen files.",
        debt = Debt.FIVE_MINS,
    )

    private val screenFile = Regex("""(Screen|Section)\.kt$""")

    override fun visitKtFile(file: KtFile) {
        if (file.name == "LumenTokens.generated.kt" || file.name == "GlassSurface.kt") return
        if (!screenFile.containsMatchIn(file.name)) return
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text == "Color") {
            val arg = expression.valueArguments.firstOrNull()?.getArgumentExpression()?.text.orEmpty()
            if (arg.contains("0x", ignoreCase = true)) {
                report(CodeSmell(issue, Entity.from(expression), "Use LumenTokens.Color"))
            }
        }
    }
}

class ForbiddenScreenMaterialColors(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "ForbiddenScreenMaterialColors",
        severity = Severity.CodeSmell,
        description = "Screens must use LumenTokens.Color, not MaterialTheme.colorScheme.",
        debt = Debt.FIVE_MINS,
    )

    private val screenFile = Regex("""(Screen|Section)\.kt$""")

    override fun visitKtFile(file: KtFile) {
        if (file.name == "LumenTokens.generated.kt" || file.name == "GlassSurface.kt") return
        if (!screenFile.containsMatchIn(file.name)) return
        super.visitKtFile(file)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.text.startsWith("MaterialTheme.colorScheme")) {
            report(CodeSmell(issue, Entity.from(expression), expression.text))
        }
    }
}

class ForbiddenScreenRoundedCornerShape(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "ForbiddenScreenRoundedCornerShape",
        severity = Severity.CodeSmell,
        description = "Use LumenTokens.Shape in screen files.",
        debt = Debt.FIVE_MINS,
    )

    private val screenFile = Regex("""(Screen|Section)\.kt$""")

    override fun visitKtFile(file: KtFile) {
        if (file.name == "LumenTokens.generated.kt" || file.name == "GlassSurface.kt") return
        if (!screenFile.containsMatchIn(file.name)) return
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text == "RoundedCornerShape") {
            report(CodeSmell(issue, Entity.from(expression), "Use LumenTokens.Shape"))
        }
    }
}

class LumenTokenRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "lumen-tokens"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ForbiddenScreenDpLiteral(config),
            ForbiddenScreenSpLiteral(config),
            ForbiddenScreenColorLiteral(config),
            ForbiddenScreenMaterialColors(config),
            ForbiddenScreenRoundedCornerShape(config),
        ),
    )
}
