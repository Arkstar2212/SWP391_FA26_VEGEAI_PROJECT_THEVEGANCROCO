package com.example.swp391_fa26_vegeai_project.modules.nutrition.controller;

import com.example.swp391_fa26_vegeai_project.common.dto.ApiResponse;
import com.example.swp391_fa26_vegeai_project.modules.nutrition.dto.BmiRequest;
import com.example.swp391_fa26_vegeai_project.modules.nutrition.dto.BmiResponse;
import com.example.swp391_fa26_vegeai_project.modules.nutrition.service.BmiCalculatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final BmiCalculatorService bmiCalculatorService;

    @PostMapping("/bmi/calculate")
    public ResponseEntity<ApiResponse<BmiResponse>> calculateBmi(@Valid @RequestBody BmiRequest request) {
        BmiResponse response = bmiCalculatorService.calculateBmi(request);
        return ResponseEntity.ok(ApiResponse.ok("BMI calculated successfully", response));
    }
}
