package com.mteam.rebuildengine.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// dBASE III/IV(.dbf) 순차 리더 — Shapefile 속성 테이블 파싱 전용 (F-14 GIS 데이터 이관).
// 대용량 파일(1GB+)을 스트리밍으로 한 레코드씩 읽는다. 랜덤 접근(.shx)은 쓰지 않는다.
public class DbfReader implements Closeable {

    public record Field(String name, char type, int length, int decimalCount) {
    }

    private final RandomAccessFile file;
    private final Charset charset;
    private final List<Field> fields = new ArrayList<>();
    private final int recordLength;
    private final int recordCount;
    private int readCount = 0;

    public DbfReader(String path, Charset charset) throws IOException {
        this.file = new RandomAccessFile(path, "r");
        this.charset = charset;

        byte[] header = new byte[32];
        file.readFully(header);
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        this.recordCount = bb.getInt(4);
        int headerSize = bb.getShort(8) & 0xFFFF;
        this.recordLength = bb.getShort(10) & 0xFFFF;

        int fieldBytes = headerSize - 32 - 1; // 32(헤더) + 필드디스크립터들 + 종료문자(0x0D) 1바이트
        byte[] fieldDescriptors = new byte[fieldBytes];
        file.readFully(fieldDescriptors);
        file.skipBytes(1); // 0x0D 종료문자

        int fieldCount = fieldBytes / 32;
        for (int i = 0; i < fieldCount; i++) {
            int offset = i * 32;
            int nameEnd = offset;
            while (nameEnd < offset + 11 && fieldDescriptors[nameEnd] != 0) {
                nameEnd++;
            }
            String name = new String(fieldDescriptors, offset, nameEnd - offset, Charset.forName("ASCII"));
            char type = (char) fieldDescriptors[offset + 11];
            int length = fieldDescriptors[offset + 16] & 0xFF;
            int decimalCount = fieldDescriptors[offset + 17] & 0xFF;
            fields.add(new Field(name, type, length, decimalCount));
        }
    }

    public List<Field> getFields() {
        return fields;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public boolean hasNext() {
        return readCount < recordCount;
    }

    // 레코드 하나를 읽어 필드명->원본 문자열(트림 전) 맵으로 반환. 삭제된 레코드는 null 반환.
    public Map<String, String> next() throws IOException {
        byte[] record = new byte[recordLength];
        file.readFully(record);
        readCount++;

        if (record[0] == '*') {
            return null; // 논리적으로 삭제된 레코드
        }

        Map<String, String> values = new LinkedHashMap<>();
        int pos = 1; // 0번 바이트는 삭제 플래그
        for (Field f : fields) {
            String raw = new String(record, pos, f.length(), charset).trim();
            values.put(f.name(), raw);
            pos += f.length();
        }
        return values;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
