package com.otd.otd_msa_back_life.meal.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Entity
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor

@ToString
public class MealFoodDb {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long foodDbId;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(nullable = false, length = 10)
    private String flag;

    @Positive
    @Column(nullable = false)
    private Integer kcal;

    @PositiveOrZero
    @Column(nullable = false)
    private Float protein;

    @PositiveOrZero
    @Column(nullable = false)
    private Float carbohydrate;

    @PositiveOrZero
    @Column(nullable = false)
    private Float fat;

    @PositiveOrZero
    @Column(nullable = false)
    private Float sugar;

    @PositiveOrZero
    @Column(nullable = false)
    private Float natrium;


    @Column(nullable = false, length = 10)
    private int foodCapacity;

}
