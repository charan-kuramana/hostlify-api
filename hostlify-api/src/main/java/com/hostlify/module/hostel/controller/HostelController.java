package com.hostlify.module.hostel.controller;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.service.HostelService;
import com.hostlify.shared.dto.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class HostelController {

    private final HostelService hostelService;

    public HostelController(HostelService hostelService) {
        this.hostelService = hostelService;
    }

    // Owner endpoints
    @GetMapping("/owners")
    public ApiResponse<List<HostelOwner>> getAllOwners() {
        return ApiResponse.ok(hostelService.getAllOwners());
    }

    @GetMapping("/owners/{id}")
    public ApiResponse<HostelOwner> getOwner(@PathVariable Long id) {
        return ApiResponse.ok(hostelService.getOwner(id));
    }

    @PostMapping("/owners")
    public ApiResponse<HostelOwner> createOwner(@RequestBody HostelOwner owner) {
        return ApiResponse.ok(hostelService.createOwner(owner));
    }

    @PutMapping("/owners/{id}")
    public ApiResponse<HostelOwner> updateOwner(@PathVariable Long id, @RequestBody HostelOwner owner) {
        return ApiResponse.ok(hostelService.updateOwner(id, owner));
    }

    @DeleteMapping("/owners/{id}")
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
    public ApiResponse<List<Hostel>> getHostelsByOwner(@PathVariable Long ownerId) {
        return ApiResponse.ok(hostelService.getHostelsByOwner(ownerId));
    }

    @PostMapping("/hostels")
    public ApiResponse<Hostel> createHostel(@RequestBody Hostel hostel) {
        return ApiResponse.ok(hostelService.createHostel(hostel));
    }

    @PutMapping("/hostels/{id}")
    public ApiResponse<Hostel> updateHostel(@PathVariable Long id, @RequestBody Hostel hostel) {
        return ApiResponse.ok(hostelService.updateHostel(id, hostel));
    }

    @DeleteMapping("/hostels/{id}")
    public ApiResponse<Void> deleteHostel(@PathVariable Long id) {
        hostelService.deleteHostel(id);
        return ApiResponse.ok(null, "Hostel deleted");
    }

    // Floor endpoints
    @GetMapping("/hostels/{hostelId}/floors")
    public ApiResponse<List<Floor>> getFloors(@PathVariable Long hostelId) {
        return ApiResponse.ok(hostelService.getFloorsByHostel(hostelId));
    }

    @PostMapping("/hostels/{hostelId}/floors")
    public ApiResponse<Floor> createFloor(@PathVariable Long hostelId, @RequestBody Floor floor) {
        floor.setHostelId(hostelId);
        return ApiResponse.ok(hostelService.createFloor(floor));
    }

    @DeleteMapping("/floors/{id}")
    public ApiResponse<Void> deleteFloor(@PathVariable Long id) {
        hostelService.deleteFloor(id);
        return ApiResponse.ok(null, "Floor deleted");
    }

    // Room endpoints
    @GetMapping("/floors/{floorId}/rooms")
    public ApiResponse<List<Room>> getRooms(@PathVariable Long floorId) {
        return ApiResponse.ok(hostelService.getRoomsByFloor(floorId));
    }

    @PostMapping("/floors/{floorId}/rooms")
    public ApiResponse<Room> createRoom(@PathVariable Long floorId, @RequestBody Room room) {
        room.setFloorId(floorId);
        return ApiResponse.ok(hostelService.createRoom(room));
    }

    @DeleteMapping("/rooms/{id}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long id) {
        hostelService.deleteRoom(id);
        return ApiResponse.ok(null, "Room deleted");
    }

    // Bed endpoints
    @GetMapping("/rooms/{roomId}/beds")
    public ApiResponse<List<Bed>> getBeds(@PathVariable Long roomId) {
        return ApiResponse.ok(hostelService.getBedsByRoom(roomId));
    }

    @PostMapping("/rooms/{roomId}/beds")
    public ApiResponse<Bed> createBed(@PathVariable Long roomId, @RequestBody Bed bed) {
        bed.setRoomId(roomId);
        return ApiResponse.ok(hostelService.createBed(bed));
    }

    @DeleteMapping("/beds/{id}")
    public ApiResponse<Void> deleteBed(@PathVariable Long id) {
        hostelService.deleteBed(id);
        return ApiResponse.ok(null, "Bed deleted");
    }
}
