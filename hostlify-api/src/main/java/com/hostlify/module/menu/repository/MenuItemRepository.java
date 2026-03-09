package com.hostlify.module.menu.repository;

import com.hostlify.module.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByMenuIdOrderByDayOfWeekAscMealTypeAsc(Long menuId);
}
