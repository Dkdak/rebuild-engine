package com.mteam.rebuildengine.utils;

import org.apache.logging.log4j.LogManager;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import org.apache.logging.log4j.Logger;

@Component
public class HeaderSizeFilter extends OncePerRequestFilter {

    private static final Logger logger = LogManager.getLogger(HeaderSizeFilter.class);

    private static final int MAX_HEADER_SIZE = 16 * 1024; // 최대 헤더 크기를 16KB로 설정 예시

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long requestHeaderSize = calculateHeaderSize(request);

        // 로거 사용하여 헤더 크기 출력
        if (logger.isDebugEnabled()) {
            logger.debug("요청 헤더 크기: {}", requestHeaderSize);
        }

        if (requestHeaderSize > MAX_HEADER_SIZE) {
            throw new RuntimeException("Request header size exceeds the limit.");
        }

        filterChain.doFilter(request, response);
    }

    private long calculateHeaderSize(HttpServletRequest request) {
        long headerSize = 0; // 총 헤더 크기 초기화 (long 타입 사용)
        for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements();) { // 모든 헤더 이름 반복
            String headerName = headerNames.nextElement(); // 현재 헤더 이름 추출
            for (Enumeration<String> headerValues = request.getHeaders(headerName); headerValues.hasMoreElements();) { // 현재 헤더 이름에 대한 모든 헤더 값 반복
                String headerValue = headerValues.nextElement(); // 현재 헤더 값 추출
                headerSize += (headerName.length() + headerValue.length()); // 헤더 이름과 값 길이 합산하여 총 크기 계산
            }
        }
        return headerSize; // 총 헤더 크기 반환
    }


}
