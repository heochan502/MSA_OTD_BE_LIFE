package com.otd.otd_msa_back_life.meal.controller;



import com.otd.otd_msa_back_life.admin.model.AdminMealDataDto;
import com.otd.otd_msa_back_life.configuration.model.UserPrincipal;
import com.otd.otd_msa_back_life.meal.entity.MealFoodDb;
import com.otd.otd_msa_back_life.meal.entity.MealRecord;
import com.otd.otd_msa_back_life.meal.entity.MealRecordDetail;
import com.otd.otd_msa_back_life.meal.entity.MealSaveResultDto;
import com.otd.otd_msa_back_life.meal.model.*;
import com.otd.otd_msa_back_life.meal.service.MealService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/OTD/meal")
@Slf4j
public class MealController {
    public final MealService mealService;

    @GetMapping("/search")
    public ResponseEntity<?> getMeal(@AuthenticationPrincipal UserPrincipal userPrincipal, @RequestParam("foodName") String foodName) {

        log.info("유저 아이디: {}", userPrincipal.getSignedUserId());
        log.info("넘어오는 값: {}", foodName);

        List<FoodSearchResultDto> res = mealService.findFood(foodName, userPrincipal.getSignedUserId());
        return ResponseEntity.ok(res);

    }

    @PostMapping("/record")
    public ResponseEntity<?> inputMealRecord(@AuthenticationPrincipal UserPrincipal userPrincipal, @RequestBody InputMealRecordReq mealRecordReq) {
        log.info("유저 아이디: {}", userPrincipal.getSignedUserId());
//        mealRecordReq.setMemberNoLogin(userPrincipal.getSignedUserId());
        log.info("넘어오는 값: {}", mealRecordReq);

        MealSaveResultDto result = mealService.inputMealData(  userPrincipal.getSignedUserId(),mealRecordReq);
        log.info("결과값 : {}", result);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<?> getMealRecord(@AuthenticationPrincipal UserPrincipal userPrincipal, @RequestParam LocalDate mealDay) {
        log.info("유저 아이디: {}", userPrincipal.getSignedUserId());
        log.info("선택 날 : {}", mealDay);

        List<MealRecord> result = mealService.mealMainListRes(userPrincipal.getSignedUserId(), mealDay);
        log.info("데이터  : {}", result.toString());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/myday")
    public ResponseEntity<?> myDay(@AuthenticationPrincipal UserPrincipal userPrincipal,  @RequestParam LocalDate mealDay) {
        log.info("아디 날짜: {} {}", userPrincipal.getSignedUserId(), mealDay);
        GetMyDayDateDto result = mealService.getToDay(userPrincipal.getSignedUserId(), mealDay);

        return ResponseEntity.ok(result);

    }

    // 일간
    @GetMapping("/summary/day")
    public  ResponseEntity<?> day(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mealDay
    ) {

        GetSummaryTotalDto result = mealService.getDailyTotal(userPrincipal.getSignedUserId(), mealDay);

        return ResponseEntity.ok(result);
    }

    // 주간 (start/end 직접 받는 경우)
    @GetMapping("/summary/week")
    public  ResponseEntity<?> week(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {

        GetSummaryTotalDto result = mealService.getWeeklyTotal(userPrincipal.getSignedUserId(), start, end);
        return ResponseEntity.ok(result);
    }

    // 월간 (YearMonth 사용)
    @GetMapping("/summary/month")
    public  ResponseEntity<?> month(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {

        GetSummaryTotalDto result = mealService.getMonthlyTotal(userPrincipal.getSignedUserId(), month);
        return ResponseEntity.ok(result);
    }
}







