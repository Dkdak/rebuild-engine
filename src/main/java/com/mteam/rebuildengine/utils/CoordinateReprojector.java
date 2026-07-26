package com.mteam.rebuildengine.utils;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

// EPSG:5186(Korea 2000 / Korea Central Belt 2010) -> EPSG:4326(WGS84) 좌표 재투영 (F-14 GIS 데이터 이관).
// .prj 파일 확인 결과의 투영 파라미터를 그대로 사용한다 (central_meridian=127, latitude_of_origin=38, false_easting=200000, false_northing=600000).
public class CoordinateReprojector {

    private static final String EPSG_5186_PROJ4 =
            "+proj=tmerc +lat_0=38 +lon_0=127 +k=1 +x_0=200000 +y_0=600000 +ellps=GRS80 +units=m +no_defs";
    // createFromName("epsg:4326")은 proj4j가 클래스패스 리소스(proj4/nad/epsg)를 파일로 찾으려다 실패하는 경우가 있어,
    // WGS84도 EPSG 코드 조회 대신 파라미터 문자열로 직접 정의해 리소스 조회 자체를 피한다.
    private static final String WGS84_PROJ4 = "+proj=longlat +datum=WGS84 +no_defs";

    private final CoordinateTransform transform;

    public CoordinateReprojector() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem source = crsFactory.createFromParameters("EPSG5186", EPSG_5186_PROJ4);
        CoordinateReferenceSystem target = crsFactory.createFromParameters("WGS84", WGS84_PROJ4);
        this.transform = new CoordinateTransformFactory().createTransform(source, target);
    }

    // 입력 (x, y)는 EPSG:5186 미터 좌표, 반환 [lng, lat]는 WGS84 경위도.
    public double[] toWgs84(double x, double y) {
        ProjCoordinate result = new ProjCoordinate();
        transform.transform(new ProjCoordinate(x, y), result);
        return new double[]{result.x, result.y};
    }
}
