package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.request.TestPropertySaveRequest;
import com.mteam.rebuildengine.model.response.TestPropertyResponse;
import com.mteam.rebuildengine.service.TestPropertyService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/properties")
public class TestPropertyController {

    private static final Logger logger = LogManager.getLogger(TestPropertyController.class);

    private  final TestPropertyService testPropertyService;

    // 저장 API
    @PostMapping("save")
    public ResponseEntity<TestPropertyResponse> save(@RequestBody TestPropertySaveRequest request) {
        TestPropertyResponse response = testPropertyService.saveProperty(request);
        return ResponseEntity.ok(response);
    }

    // 조회 API
    @GetMapping("/{id}")
    public ResponseEntity<TestPropertyResponse> get(@PathVariable Long id) {
        TestPropertyResponse response = testPropertyService.getProperty(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/findAll")
    public ResponseEntity<List<TestPropertyResponse>> findAll() {
        List<TestPropertyResponse> response = testPropertyService.getFindAll();

        System.out.println("total size = " + response.size());

        return ResponseEntity.ok(response);
    }

}
