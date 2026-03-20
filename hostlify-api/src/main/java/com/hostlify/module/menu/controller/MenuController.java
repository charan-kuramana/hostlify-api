package com.hostlify.module.menu.controller;

import com.hostlify.module.menu.entity.MenuItem;
import com.hostlify.module.menu.entity.WeeklyMenu;
import com.hostlify.module.menu.service.MenuService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/menu")
@Tag(name = "Menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Hosteller + Staff: get current week's menu")
    public ApiResponse<WeeklyMenu> getCurrentMenu(@RequestParam Long hostelId) {
        return ApiResponse.ok(menuService.getCurrentMenu(hostelId));
    }

    @GetMapping("/week")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Get menu for a specific week")
    public ApiResponse<WeeklyMenu> getByWeek(@RequestParam Long hostelId,
                                               @RequestParam String weekStart) {
        return ApiResponse.ok(menuService.getMenuByWeek(hostelId, LocalDate.parse(weekStart)));
    }

    @GetMapping("/{menuId}/items")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    @Operation(summary = "Get menu items for a weekly menu")
    public ApiResponse<List<MenuItem>> getItems(@PathVariable Long menuId) {
        return ApiResponse.ok(menuService.getMenuItems(menuId));
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: create or update a weekly menu")
    public ApiResponse<WeeklyMenu> saveMenu(@RequestBody Map<String, Object> body) {
        UserPrincipal principal = getPrincipal();
        Long hostelId = ((Number) body.get("hostelId")).longValue();
        String weekStartStr = (String) body.get("weekStart");
        LocalDate weekStart = weekStartStr != null ? LocalDate.parse(weekStartStr)
                : LocalDate.now().with(java.time.DayOfWeek.MONDAY);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        List<MenuItem> items = itemsRaw == null ? List.of() : itemsRaw.stream().map(raw -> {
            MenuItem item = new MenuItem();
            item.setDayOfWeek((String) raw.get("dayOfWeek"));
            item.setMealType((String) raw.get("mealType"));
            item.setItems((String) raw.get("items"));
            if (raw.get("timing") != null) item.setTiming((String) raw.get("timing"));
            return item;
        }).toList();

        return ApiResponse.ok(menuService.saveWeeklyMenu(hostelId, principal.getUserId(), weekStart, items),
                "Menu saved successfully");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new com.hostlify.shared.exception.AppException("Not authenticated",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return (UserPrincipal) auth.getPrincipal();
    }
}
