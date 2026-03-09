package com.hostlify.module.menu.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
    name = "menu_items",
    indexes = @Index(name = "idx_mi_menu_id", columnList = "menu_id")
)
@SQLRestriction("is_deleted = 0")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }
    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }
    public String getTiming() { return timing; }
    public void setTiming(String timing) { this.timing = timing; }
}
