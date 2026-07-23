package com.mteam.rebuildengine.utils;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// 공공데이터 API가 숫자/날짜도 문자열로 내려주는 경우가 많아, 공백/비정상값을 안전하게 null로 처리하며 변환한다.
public class SafeParser {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    // JsonNode.asText()는 숫자 노드를 Double 포맷으로 바꾸면서 큰 정수를 과학적 표기법(예: 1.017111257E9)으로
    // 깨뜨릴 수 있다. API가 정수를 1017111257.0처럼 소수점 붙여 내려주면 Jackson이 DoubleNode로 파싱하므로
    // isIntegralNumber()(노드 타입 체크)로는 못 잡는다 — isNumber()로 전체를 잡고 BigDecimal로 안전하게 텍스트화한다.
    public static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            BigDecimal decimal = node.decimalValue();
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return decimal.toBigInteger().toString();
            }
            return decimal.toPlainString();
        }
        return node.asText(null);
    }

    public static BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer toInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate toDate(String value) {
        if (value == null || value.isBlank() || "0".equals(value.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), YYYYMMDD);
        } catch (Exception e) {
            return null;
        }
    }
}
