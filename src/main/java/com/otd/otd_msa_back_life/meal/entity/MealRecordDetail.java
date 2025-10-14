package com.otd.otd_msa_back_life.meal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class MealRecordDetail {
    @EmbeddedId
    MealRecordIds mealRecordIds;

    @Column(nullable = false)
    private Long userId;

    @PositiveOrZero
    private Integer totalKcal;

    @PositiveOrZero
    private float totalProtein;

    @PositiveOrZero
    private float totalCarbohydrate;

    @PositiveOrZero
    private float totalFat;

    @PositiveOrZero
    private float totalSugar;

    @PositiveOrZero
    private float totalNatrium;


}
