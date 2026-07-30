package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;

public record GradeSummaryReadModel(String grade, long count, BigDecimal avgRoi) {
}
