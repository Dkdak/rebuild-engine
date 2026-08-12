package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.GisShapefileParserService;
import com.mteam.rebuildengine.service.datapipeline.SiteBoundaryShapefileParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// GIS Shapefile 파싱 수동 트리거 (F-14 데이터 이관, 일별 재수집 대비 재사용 가능한 배치)
@RestController
@RequestMapping("/api/v1/admin/gis")
@RequiredArgsConstructor
public class AdminGisController {

    private final GisShapefileParserService gisShapefileParserService;
    private final SiteBoundaryShapefileParserService siteBoundaryShapefileParserService;

    // shpBasePath 예: C:/rebuild-project/rebuild-infra/postgres/data/raw/AL_D010_11_20260719/AL_D010_11_20260719
    @PostMapping("/parse")
    public ResponseEntity<GisShapefileParserService.ParseResult> parse(
            @RequestParam String shpBasePath,
            @RequestParam String outputDir,
            @RequestParam(defaultValue = "0") int maxRows
    ) throws IOException {
        return ResponseEntity.ok(gisShapefileParserService.parseAndExport(shpBasePath, outputDir, maxRows));
    }

    // zipPath 예: C:/rebuild-project/rebuild-infra/postgres/data/raw/AL_D010_11_20260719.zip
    // extractDir 예: C:/rebuild-project/rebuild-infra/postgres/data/raw/AL_D010_11_20260719
    @PostMapping("/parse-zip")
    public ResponseEntity<GisShapefileParserService.ParseResult> parseZip(
            @RequestParam String zipPath,
            @RequestParam String extractDir,
            @RequestParam String outputDir,
            @RequestParam(defaultValue = "0") int maxRows
    ) throws IOException {
        return ResponseEntity.ok(gisShapefileParserService.extractZipAndParse(zipPath, extractDir, outputDir, maxRows));
    }

    // 관례 경로만 쓰는 단순 버전 — postgres/data/raw/{zipFileName}만 넘기면 된다 (예: AL_D010_11_20260719.zip)
    @PostMapping("/parse-zip-simple")
    public ResponseEntity<GisShapefileParserService.ParseResult> parseZipSimple(
            @RequestParam String zipFileName,
            @RequestParam(defaultValue = "0") int maxRows
    ) throws IOException {
        return ResponseEntity.ok(gisShapefileParserService.extractZipAndParseByFileName(zipFileName, maxRows));
    }

    // 연속지적도(FEATURE_05_PROPERTY_INFO.md §5.1 site_boundary) — 이미 압축 해제된 상태로 받아서
    // zip 경로는 없다. shpBasePath 예: C:/rebuild-project/rebuild-infra/postgres/data/raw/site_boundary/AL_D002_11_20260719
    @PostMapping("/site-boundary/parse")
    public ResponseEntity<SiteBoundaryShapefileParserService.ParseResult> parseSiteBoundary(
            @RequestParam String shpBasePath,
            @RequestParam String outputDir,
            @RequestParam(defaultValue = "0") int maxRows
    ) throws IOException {
        return ResponseEntity.ok(siteBoundaryShapefileParserService.parseAndExport(shpBasePath, outputDir, maxRows));
    }
}
