package com.hostlify.module.menu.service;

import com.hostlify.module.menu.entity.MenuItem;
import com.hostlify.module.menu.entity.WeeklyMenu;
import com.hostlify.module.menu.repository.MenuItemRepository;
import com.hostlify.module.menu.repository.WeeklyMenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
public class MenuService {

    private final WeeklyMenuRepository menuRepo;
    private final MenuItemRepository itemRepo;

    public MenuService(WeeklyMenuRepository menuRepo, MenuItemRepository itemRepo) {
        this.menuRepo = menuRepo;
        this.itemRepo = itemRepo;
    }

    public WeeklyMenu getCurrentMenu(Long hostelId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        return menuRepo.findByHostelIdAndWeekStart(hostelId, weekStart)
                .orElse(null);
    }

    public WeeklyMenu getMenuByWeek(Long hostelId, LocalDate weekStart) {
        return menuRepo.findByHostelIdAndWeekStart(hostelId, weekStart)
                .orElse(null);
    }

    public List<MenuItem> getMenuItems(Long menuId) {
        return itemRepo.findByMenuIdOrderByDayOfWeekAscMealTypeAsc(menuId);
    }

    @Transactional
    public WeeklyMenu saveWeeklyMenu(Long hostelId, Long createdBy, LocalDate weekStart, List<MenuItem> items) {
        // Upsert — if menu already exists for this week, replace it
        WeeklyMenu menu = menuRepo.findByHostelIdAndWeekStart(hostelId, weekStart)
                .orElse(new WeeklyMenu());
        menu.setHostelId(hostelId);
        menu.setWeekStart(weekStart);
        menu.setCreatedBy(createdBy);
        menu = menuRepo.save(menu);

        // Replace items
        if (menu.getId() != null) {
            List<MenuItem> existing = itemRepo.findByMenuIdOrderByDayOfWeekAscMealTypeAsc(menu.getId());
            existing.forEach(i -> i.setDeleted(true));
            itemRepo.saveAll(existing);
        }

        Long menuId = menu.getId();
        for (MenuItem item : items) {
            item.setMenuId(menuId);
        }
        itemRepo.saveAll(items);
        return menu;
    }
}
