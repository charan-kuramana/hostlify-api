package com.hostlify.module.hostel.controller;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.service.HostelService;
import com.hostlify.shared.dto.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hostlify.shared.security.HostelAccess;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HostelController {

    private final HostelService hostelService;

    public HostelController(HostelService hostelService) {
        this.hostelService = hostelService;
    }

    // Owner endpoints
    @GetMapping("/owners")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<HostelOwner>> getAllOwners() {
        return ApiResponse.ok(hostelService.getAllOwners());
    }

    @GetMapping("/owners/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<HostelOwner> getOwner(@PathVariable Long id) {
        return ApiResponse.ok(hostelService.getOwner(id));
    }

    @PostMapping("/owners")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<HostelOwner> createOwner(@RequestBody HostelOwner owner) {
        return ApiResponse.ok(hostelService.createOwner(owner));
    }

    @PutMapping("/owners/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<HostelOwner> updateOwner(@PathVariable Long id, @RequestBody HostelOwner owner) {
        return ApiResponse.ok(hostelService.updateOwner(id, owner));
    }

    @DeleteMapping("/owners/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteOwner(@PathVariable Long id) {
        hostelService.deleteOwner(id);
        return ApiResponse.ok(null, "Owner deleted");
    }

    // Hostel endpoints
    @GetMapping("/hostels")
    public ApiResponse<Page<Hostel>> getHostels(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(hostelService.getHostels(PageRequest.of(page, size)));
    }

    @GetMapping("/hostels/{id}")
    public ApiResponse<Hostel> getHostel(@PathVariable Long id) {
        return ApiResponse.ok(hostelService.getHostel(id));
    }

    @GetMapping("/owners/{ownerId}/hostels")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<Hostel>> getHostelsByOwner(@PathVariable Long ownerId) {
        return ApiResponse.ok(hostelService.getHostelsByOwner(ownerId));
    }

    @PostMapping("/hostels")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Hostel> createHostel(@RequestBody Hostel hostel) {
        return ApiResponse.ok(hostelService.createHostel(hostel));
    }

    @PutMapping("/hostels/{id}")
    @PreAuthorize("hasAnyRole('HOSTEL_STAFF','SUPER_ADMIN')")
    public ApiResponse<Hostel> updateHostel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(id);
        HostelAccess.requireSubRole("OWNER", "ADMIN", "MANAGER");
        return ApiResponse.ok(hostelService.partialUpdateHostel(id, body));
    }

    @DeleteMapping("/hostels/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteHostel(@PathVariable Long id) {
        hostelService.deleteHostel(id);
        return ApiResponse.ok(null, "Hostel deleted");
    }

    @PatchMapping("/hostels/{id}/settings")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Hostel> updateHostelSettings(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(id);
        HostelAccess.requireSubRole("OWNER", "ADMIN", "MANAGER");
        Hostel hostel = hostelService.getHostel(id);
        if (body.containsKey("onlinePaymentEnabled")) {
            hostel.setOnlinePaymentEnabled(Boolean.TRUE.equals(body.get("onlinePaymentEnabled")));
        }
        return ApiResponse.ok(hostelService.updateHostel(id, hostel), "Settings updated");
    }

    // Floor endpoints
    @GetMapping("/hostels/{hostelId}/floors")
    public ApiResponse<List<Floor>> getFloors(@PathVariable Long hostelId) {
        return ApiResponse.ok(hostelService.getFloorsByHostel(hostelId));
    }

    @PostMapping("/hostels/{hostelId}/floors")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Floor> createFloor(@PathVariable Long hostelId, @RequestBody Floor floor) {
        HostelAccess.requireStaffHostel(hostelId);
        if (floor.getFloorName() == null || floor.getFloorName().isBlank()) {
            throw new com.hostlify.shared.exception.AppException("Floor name is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        floor.setFloorName(floor.getFloorName().trim());
        boolean duplicate = hostelService.getFloorsByHostel(hostelId).stream()
                .anyMatch(f -> f.getFloorName() != null && f.getFloorName().equalsIgnoreCase(floor.getFloorName()));
        if (duplicate) {
            throw new com.hostlify.shared.exception.AppException("A floor with this name already exists", org.springframework.http.HttpStatus.CONFLICT);
        }
        floor.setId(null);
        floor.setHostelId(hostelId);
        return ApiResponse.ok(hostelService.createFloor(floor));
    }

    /** Staff: rename a floor */
    @PutMapping("/floors/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Floor> renameFloor(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Floor floor = hostelService.getFloor(id);
        HostelAccess.requireStaffHostel(floor.getHostelId());
        String name = body.get("floorName") != null ? body.get("floorName").toString().trim() : "";
        if (name.isEmpty()) {
            throw new com.hostlify.shared.exception.AppException("Floor name is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        boolean duplicate = hostelService.getFloorsByHostel(floor.getHostelId()).stream()
                .anyMatch(f -> !f.getId().equals(id) && f.getFloorName() != null && f.getFloorName().equalsIgnoreCase(name));
        if (duplicate) {
            throw new com.hostlify.shared.exception.AppException("A floor with this name already exists", org.springframework.http.HttpStatus.CONFLICT);
        }
        floor.setFloorName(name);
        return ApiResponse.ok(hostelService.createFloor(floor), "Floor renamed");
    }

    @DeleteMapping("/floors/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Void> deleteFloor(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(hostelService.getFloor(id).getHostelId());
        hostelService.deleteFloor(id);
        return ApiResponse.ok(null, "Floor deleted");
    }

    // Room endpoints
    @GetMapping("/floors/{floorId}/rooms")
    public ApiResponse<List<Room>> getRooms(@PathVariable Long floorId) {
        return ApiResponse.ok(hostelService.getRoomsByFloor(floorId));
    }

    @PostMapping("/floors/{floorId}/rooms")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Room> createRoom(@PathVariable Long floorId, @RequestBody Map<String, Object> body) {
        // Get floor to find hostelId
        Floor floor = hostelService.getFloor(floorId);
        HostelAccess.requireStaffHostel(floor.getHostelId());
        if (floor.getHostelId() == null) {
            throw new com.hostlify.shared.exception.AppException("Floor has no associated hostel", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        Room room = new Room();
        room.setFloorId(floorId);
        room.setHostelId(floor.getHostelId());
        // Support roomName, roomNumber, or name as field names
        String name = body.containsKey("roomName") ? (String) body.get("roomName")
                    : body.containsKey("roomNumber") ? (String) body.get("roomNumber")
                    : body.containsKey("name") ? (String) body.get("name") : null;
        if (name == null || name.isBlank()) {
            throw new com.hostlify.shared.exception.AppException("Room name is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        room.setRoomName(name.trim());
        // Support roomType or type as field names
        String type = body.containsKey("roomType") ? (String) body.get("roomType")
                    : body.containsKey("type") ? (String) body.get("type") : "SINGLE";
        room.setRoomType(type != null ? type : "SINGLE");
        // Capacity / totalBeds
        if (body.containsKey("capacity") && body.get("capacity") != null) {
            room.setCapacity(((Number) body.get("capacity")).intValue());
        } else if (body.containsKey("totalBeds") && body.get("totalBeds") != null) {
            room.setCapacity(((Number) body.get("totalBeds")).intValue());
        }
        return ApiResponse.ok(hostelService.createRoom(room));
    }

    @DeleteMapping("/rooms/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Void> deleteRoom(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(hostelService.getRoom(id).getHostelId());
        hostelService.deleteRoom(id);
        return ApiResponse.ok(null, "Room deleted");
    }

    // Bed endpoints
    @GetMapping("/rooms/{roomId}/beds")
    public ApiResponse<List<Bed>> getBeds(@PathVariable Long roomId) {
        return ApiResponse.ok(hostelService.getBedsByRoom(roomId));
    }

    @PostMapping("/rooms/{roomId}/beds")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Bed> createBed(@PathVariable Long roomId, @RequestBody Map<String, Object> body) {
        Room room = hostelService.getRoom(roomId);
        HostelAccess.requireStaffHostel(room.getHostelId());
        Bed bed = new Bed();
        bed.setRoomId(roomId);
        bed.setHostelId(room.getHostelId());
        // Support bedLabel, bedName, or label as field names
        String bedName = body.containsKey("bedName") ? (String) body.get("bedName")
                       : body.containsKey("bedLabel") ? (String) body.get("bedLabel")
                       : body.containsKey("label") ? (String) body.get("label") : null;
        // Auto-generate a bed name if none provided
        if (bedName == null || bedName.isBlank()) {
            long existingCount = hostelService.getBedsByRoom(roomId).size();
            bedName = room.getRoomName() + "-" + (char)('A' + existingCount);
        }
        final String newBedName = bedName.trim();
        if (hostelService.getBedsByRoom(roomId).stream().anyMatch(b -> newBedName.equalsIgnoreCase(b.getBedName()))) {
            throw new com.hostlify.shared.exception.AppException("A bed named " + newBedName + " already exists in this room", org.springframework.http.HttpStatus.CONFLICT);
        }
        bedName = newBedName;
        bed.setBedName(bedName);
        bed.setStatus("AVAILABLE");
        return ApiResponse.ok(hostelService.createBed(bed));
    }

    @DeleteMapping("/beds/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Void> deleteBed(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(hostelService.getBed(id).getHostelId());
        hostelService.deleteBed(id);
        return ApiResponse.ok(null, "Bed deleted");
    }
}
