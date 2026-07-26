package com.mteam.rebuildengine.utils;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

// ESRI Shapefile(.shp) 순차 리더 — Polygon(shape type 5)만 지원 (F-14 GIS 데이터 이관 대상 데이터에 한정).
// .dbf와 같은 순서로 레코드가 저장되어 있다는 전제로, 레코드 번호가 아니라 파일 순서대로 순차 읽는다.
public class ShpReader implements Closeable {

    public record Polygon(List<List<double[]>> rings) {
    }

    private final RandomAccessFile file;
    private final long fileLength;

    public ShpReader(String path) throws IOException {
        this.file = new RandomAccessFile(path, "r");
        this.fileLength = file.length();
        file.seek(100); // 100바이트 메인 헤더 스킵
    }

    public boolean hasNext() throws IOException {
        return file.getFilePointer() < fileLength;
    }

    // 다음 레코드의 폴리곤을 읽는다. Null shape(빈 도형)이면 rings가 빈 리스트로 반환된다.
    public Polygon next() throws IOException {
        byte[] recordHeader = new byte[8];
        file.readFully(recordHeader);
        int contentLengthWords = ByteBuffer.wrap(recordHeader, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        int contentLengthBytes = contentLengthWords * 2;

        byte[] content = new byte[contentLengthBytes];
        file.readFully(content);
        ByteBuffer bb = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);

        int shapeType = bb.getInt();
        if (shapeType == 0) {
            return new Polygon(new ArrayList<>());
        }
        if (shapeType != 5) {
            throw new IOException("지원하지 않는 shape type: " + shapeType + " (Polygon(5)만 지원)");
        }

        bb.position(bb.position() + 32); // Box(4 double) 스킵
        int numParts = bb.getInt();
        int numPoints = bb.getInt();

        int[] partStarts = new int[numParts];
        for (int i = 0; i < numParts; i++) {
            partStarts[i] = bb.getInt();
        }

        double[][] points = new double[numPoints][2];
        for (int i = 0; i < numPoints; i++) {
            points[i][0] = bb.getDouble(); // x
            points[i][1] = bb.getDouble(); // y
        }

        List<List<double[]>> rings = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            int start = partStarts[i];
            int end = (i + 1 < numParts) ? partStarts[i + 1] : numPoints;
            List<double[]> ring = new ArrayList<>();
            for (int j = start; j < end; j++) {
                ring.add(points[j]);
            }
            rings.add(ring);
        }
        return new Polygon(rings);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
