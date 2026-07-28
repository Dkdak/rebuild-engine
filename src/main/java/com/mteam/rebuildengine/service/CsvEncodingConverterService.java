package com.mteam.rebuildengine.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.stream.Stream;

// 공공데이터 CSV는 대부분 CP949(EUC-KR 계열)로 내려오는데 우리 적재 파이프라인(COPY 등)은 UTF-8을 전제로
// 한다 — 지금까지는 iconv를 수동으로 돌렸지만(F-13 건축물대장 CSV), 앞으로 실거래가/공시가 등 원본 CSV가
// 계속 나올 걸로 예상되어(2026-07-26) 매번 재사용 가능하게 자바로 자동화했다.
// 매핑 불가능한 바이트(예: 손상된 문자)는 CSV 구조가 안 깨지도록 버리고 넘어간다(iconv의 //IGNORE와 동일 정책).
@Service
public class CsvEncodingConverterService {

    private static final Logger logger = LogManager.getLogger(CsvEncodingConverterService.class);
    private static final int BUFFER_SIZE = 1 << 16;

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long sourceBytes, long outputBytes) {
    }

    // rawFileName만 넘기면 관례대로 처리한다: {dataDir}/raw/{rawFileName}을 읽어
    // {dataDir}/converted/{outputFileName}에 UTF-8로 쓴다.
    public ConvertResult convertRawToUtf8(String rawFileName, String sourceCharsetName, String outputFileName) throws IOException {
        String sourcePath = dataDir + "/raw/" + rawFileName;
        String targetPath = dataDir + "/converted/" + outputFileName;
        return convertToUtf8(sourcePath, sourceCharsetName, targetPath);
    }

    // sourceCharsetName 예: "MS949"(CP949/UHC), "EUC-KR" 등. targetCharset은 항상 UTF-8로 고정.
    public ConvertResult convertToUtf8(String sourcePath, String sourceCharsetName, String targetPath) throws IOException {
        Charset sourceCharset = Charset.forName(sourceCharsetName);
        long sourceBytes = new File(sourcePath).length();

        CharsetDecoder decoder = sourceCharset.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);

        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sourcePath), decoder), BUFFER_SIZE);
             Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetPath), java.nio.charset.StandardCharsets.UTF_8), BUFFER_SIZE)) {

            char[] buffer = new char[BUFFER_SIZE];
            Stream.generate(() -> readChunk(reader, buffer))
                    .takeWhile(length -> length != -1)
                    .forEach(length -> writeChunk(writer, buffer, length));
        }

        long outputBytes = new File(targetPath).length();
        logger.info("CSV 인코딩 변환 완료: {} ({} bytes, {}) -> {} ({} bytes, UTF-8)",
                sourcePath, sourceBytes, sourceCharsetName, targetPath, outputBytes);
        return new ConvertResult(sourceBytes, outputBytes);
    }

    // Stream.generate 람다 안에서 호출되므로 checked IOException을 밖으로 던질 수 없다 — Unchecked로 감싼다.
    private static int readChunk(Reader reader, char[] buffer) {
        try {
            return reader.read(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeChunk(Writer writer, char[] buffer, int length) {
        try {
            writer.write(buffer, 0, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
