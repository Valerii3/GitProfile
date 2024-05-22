package org.jetbrains.git.profile.git_profile.utils

import com.intellij.ui.JBColor
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.chart.plot.PiePlot
import org.jfree.data.general.DefaultPieDataset
import java.awt.Color
import java.awt.Dimension
import javax.swing.JPanel

internal fun createLanguageUsageChart(languageData: Map<String, Double>?): JPanel {
    val dataset = DefaultPieDataset<String>()

    languageData?.forEach { (language, percentage) ->
        dataset.setValue(language, percentage)
    }

    val chart: JFreeChart = ChartFactory.createPieChart(
        "Language Usage", dataset, true, true, false
    )

    val plot = chart.plot as PiePlot<*>
    plot.sectionOutlinesVisible = false
    plot.labelGenerator = null
    plot.backgroundPaint = Color.WHITE

    plot.setSectionPaint("Java", JBColor.BLUE)
    plot.setSectionPaint("Kotlin", JBColor.MAGENTA)
    plot.setSectionPaint("Python", JBColor.GREEN)

    return ChartPanel(chart).apply {
        preferredSize = Dimension(300, 200)
    }
}
