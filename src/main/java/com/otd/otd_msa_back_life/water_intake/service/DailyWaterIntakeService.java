package com.otd.otd_msa_back_life.water_intake.service;


import com.otd.otd_msa_back_life.feign.ChallengeFeignClient;
import com.otd.otd_msa_back_life.feign.model.MealDataReq;
import com.otd.otd_msa_back_life.water_intake.entity.DailyWaterIntake;
import com.otd.otd_msa_back_life.water_intake.model.DailyWaterIntakeGetRes;
import com.otd.otd_msa_back_life.water_intake.model.DailyWaterIntakePostReq;
import com.otd.otd_msa_back_life.water_intake.model.DailyWaterIntakePutReq;
import com.otd.otd_msa_back_life.water_intake.repository.DailyWaterIntakeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyWaterIntakeService {
    private final DailyWaterIntakeRepository dailyWaterIntakeRepository;
    private final ChallengeFeignClient challengeFeignClient;
    private static final String CHALLENGE_NAME = "물마시기";

    //    음수량 기록
    @Transactional
    public Long saveDailyWaterIntake(Long userId, DailyWaterIntakePostReq req) {

        DailyWaterIntake dailyWaterIntake = DailyWaterIntake.builder()
                .userId(userId)
                .intakeDate(req.getIntakeDate())
                .amountLiter(req.getAmountLiter())
                .build();
        dailyWaterIntakeRepository.save(dailyWaterIntake);

        checkAndUpdateChallenge(userId, req.getIntakeDate(), req.getAmountLiter());

        return dailyWaterIntake.getDailyWaterIntakeId();
    }

//    하루 총 음수량 조회
    public DailyWaterIntakeGetRes getDailyWaterIntake(Long userId, LocalDate intakeDate) {
        DailyWaterIntake dailyWaterIntake = dailyWaterIntakeRepository.findByUserIdAndIntakeDate(userId, intakeDate);
        if (dailyWaterIntake == null) {
            return new DailyWaterIntakeGetRes(0L ,intakeDate, 0.0);
        }
        return new DailyWaterIntakeGetRes(dailyWaterIntake);

    }

    //    음수량 수정
    @Transactional
    public void updateDailyWaterIntake( DailyWaterIntakePutReq req) {

        DailyWaterIntake dailyWaterIntake = dailyWaterIntakeRepository.findById(req.getDailyWaterIntakeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 id 입니다."));

        checkAndUpdateChallenge(dailyWaterIntake.getUserId(), req.getIntakeDate() ,req.getAmountLiter());

        dailyWaterIntake.updateAmountLiter(req.getAmountLiter());
    }

    // challenge
    private void checkAndUpdateChallenge(Long userId, LocalDate intakeDate, double amountLiter) {
        if (amountLiter < 2.0) return;

        ResponseEntity<List<String>> myChallenges = challengeFeignClient
                .getActiveChallengeNames(userId, intakeDate);
        List<String> activeChallenges = myChallenges.getBody();

        if (activeChallenges != null && !activeChallenges.isEmpty()) {
            // 조건: "물마시기" 챌린지를 하고 있을 때만 진행 업데이트
            if (activeChallenges.contains(CHALLENGE_NAME)) {
                MealDataReq feign = MealDataReq.builder()
                        .userId(userId)
                        .name(CHALLENGE_NAME)
                        .today(LocalDate.now())
                        .value(amountLiter)
                        .recDate(intakeDate)
                        .build();

                try {
                    ResponseEntity<Integer> response = challengeFeignClient.updateProgressByMeal(feign);
                    log.debug("챌린지 업데이트 결과: {}", response.getStatusCode());

                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("물마시기 챌린지 진행 상황이 정상적으로 업데이트되었습니다.");
                    } else {
                        log.warn("물마시기 챌린지 업데이트 실패: status={}", response.getStatusCode());
                    }
                } catch (Exception e) {
                    // FeignException, HttpClientErrorException, ResourceAccessException 등 처리 가능
                    log.error("물마시기 챌린지 진행 업데이트 중 예외 발생: {}", e.getMessage(), e);
                }
            }
        }
    }
}
