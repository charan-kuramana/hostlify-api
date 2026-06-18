package com.hostlify.module.menu.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(
    name = "menu_items",
    indexes = @Index(name = "idx_mi_menu_id", columnList = "menu_id")
)
@SQLRestriction("is_deleted = false")
public class MenuItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "day_of_week", nullable = false, length = 10)
    private String dayOfWeek;

    @Column(name = "meal_type", nullable = false, length = 15)
    private String mealType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String items;

    @Column(length = 50)
    private String timing;
}
