package com.mteam.rebuildengine.utils;

import java.util.List;

// 폴리곤 중심점 계산·GeoJSON 직렬화 (F-14 GIS 데이터 이관).
public class GeoUtils {

    // Shoelace 공식 기반 면적 가중 중심점 (외곽 링 기준). 반환 [x, y].
    public static double[] computeCentroid(List<double[]> ring) {
        int n = ring.size();
        if (n < 3) {
            // 점/선처럼 면적이 없는 경우 단순 평균으로 대체
            return simpleAverage(ring, n);
        }

        double area = 0;
        double cx = 0;
        double cy = 0;
        for (int i = 0; i < n; i++) {
            double[] p0 = ring.get(i);
            double[] p1 = ring.get((i + 1) % n);
            double cross = p0[0] * p1[1] - p1[0] * p0[1];
            area += cross;
            cx += (p0[0] + p1[0]) * cross;
            cy += (p0[1] + p1[1]) * cross;
        }
        area *= 0.5;
        if (Math.abs(area) < 1e-9) {
            // 퇴화 폴리곤(면적이 0에 가까움)도 단순 평균으로 대체
            return simpleAverage(ring, n);
        }
        cx /= (6 * area);
        cy /= (6 * area);
        return new double[]{cx, cy};
    }

    private static double[] simpleAverage(List<double[]> ring, int n) {
        if (n == 0) {
            return new double[]{0, 0};
        }
        double sx = 0;
        double sy = 0;
        for (double[] p : ring) {
            sx += p[0];
            sy += p[1];
        }
        return new double[]{sx / n, sy / n};
    }

    // 재투영이 이미 끝난 (lng, lat) 링들을 GeoJSON Polygon 좌표 문자열로 직렬화한다.
    public static String toGeoJsonPolygon(List<List<double[]>> rings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"Polygon\",\"coordinates\":[");
        for (int i = 0; i < rings.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[');
            List<double[]> ring = rings.get(i);
            for (int j = 0; j < ring.size(); j++) {
                if (j > 0) sb.append(',');
                double[] p = ring.get(j);
                sb.append('[').append(p[0]).append(',').append(p[1]).append(']');
            }
            sb.append(']');
        }
        sb.append("]}");
        return sb.toString();
    }
}
