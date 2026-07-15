package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.request.TestPropertySaveRequest;
import com.mteam.rebuildengine.model.response.TestPropertyResponse;

import java.util.List;

public interface TestPropertyService {

    TestPropertyResponse saveProperty(TestPropertySaveRequest requestDto);

    TestPropertyResponse getProperty(Long id);

    List<TestPropertyResponse> getFindAll();
}
