package com.dndtranslator.service.regression;

import com.dndtranslator.service.PageAnalysisData;
import com.dndtranslator.service.PageAnalyzer;
import com.dndtranslator.service.PageType;
import com.dndtranslator.service.PageTypeClassifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageClassificationRegressionTest {

    private final PageCorpusLoader loader = new PageCorpusLoader();
    private final PageAnalyzer analyzer = new PageAnalyzer();
    private final PageTypeClassifier classifier = new PageTypeClassifier();

    @Test
    void pageCorpusClassificationMatchesGoldenExpectations() {
        List<PageCorpusLoader.RegressionCase> cases = loader.load();

        for (PageCorpusLoader.RegressionCase c : cases) {
            PageAnalysisData data = analyzer.analyze(
                    c.pageNumber(),
                    c.pageMeta(),
                    c.paragraphs(),
                    c.images()
            );
            PageType actual = classifier.classify(data);
            assertEquals(c.expectedType(), actual, "Caso " + c.id() + " clasifico distinto");

            Map<String, Boolean> expectedSignals = c.expectedSignals();
            for (Map.Entry<String, Boolean> expected : expectedSignals.entrySet()) {
                boolean actualSignal = resolveSignal(data, expected.getKey());
                assertEquals(expected.getValue(), actualSignal,
                        "Caso " + c.id() + " señal " + expected.getKey() + " distinta");
            }
        }
    }

    private boolean resolveSignal(PageAnalysisData data, String signalKey) {
        Map<String, Function<PageAnalysisData, Boolean>> dispatch = Map.of(
                "hasTitleLikePatterns", PageAnalysisData::hasTitleLikePatterns,
                "hasVeryLowTextDensity", PageAnalysisData::hasVeryLowTextDensity,
                "hasLargeImage", PageAnalysisData::hasLargeImage,
                "hasMapLikeKeywords", PageAnalysisData::hasMapLikeKeywords,
                "hasIndexLikePatterns", PageAnalysisData::hasIndexLikePatterns,
                "hasTableLikePatterns", PageAnalysisData::hasTableLikePatterns,
                "hasDottedLeaderPatterns", PageAnalysisData::hasDottedLeaderPatterns,
                "hasManyNumericLines", PageAnalysisData::hasManyNumericLines
        );

        Function<PageAnalysisData, Boolean> resolver = dispatch.get(signalKey);
        if (resolver == null) {
            throw new IllegalArgumentException("Señal no soportada en regression fixture: " + signalKey);
        }
        return resolver.apply(data);
    }
}

