package com.hostlify.module.hostel.service;

import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.repository.*;
import com.hostlify.shared.exception.AppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HostelService {

    private final HostelRepository hostelRepo;
    private final HostelOwnerRepository ownerRepo;
    private final FloorRepository floorRepo;
    private final RoomRepository roomRepo;
    private final BedRepository bedRepo;

    public HostelService(HostelRepository hostelRepo, HostelOwnerRepository ownerRepo,
                         FloorRepository floorRepo, RoomRepository roomRepo, BedRepository bedRepo) {
        this.hostelRepo = hostelRepo;
        this.ownerRepo = ownerRepo;
        this.floorRepo = floorRepo;
        this.roomRepo = roomRepo;
        this.bedRepo = bedRepo;
    }

    // ===== Hostel Owner =====
    public List<HostelOwner> getAllOwners() { return ownerRepo.findAll(); }
    public HostelOwner getOwner(Long id) {
        return ownerRepo.findById(id).orElseThrow(() -> new AppException("Owner not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public HostelOwner createOwner(HostelOwner owner) { return ownerRepo.save(owner); }
    @Transactional
    public HostelOwner updateOwner(Long id, HostelOwner req) {
        HostelOwner existing = getOwner(id);
        existing.setOwnerName(req.getOwnerName());
        existing.setMobile(req.getMobile());
        existing.setBusinessName(req.getBusinessName());
        existing.setStatus(req.getStatus());
        return ownerRepo.save(existing);
    }
    @Transactional
    public void deleteOwner(Long id) {
        HostelOwner owner = getOwner(id);
        owner.setDeleted(true);
        ownerRepo.save(owner);
    }

    // ===== Hostel =====
    public Page<Hostel> getHostels(Pageable pageable) { return hostelRepo.findAll(pageable); }
    public Hostel getHostel(Long id) {
        return hostelRepo.findById(id).orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));
    }
    public List<Hostel> getHostelsByOwner(Long ownerId) { return hostelRepo.findByOwnerId(ownerId); }
    @Transactional
    public Hostel createHostel(Hostel hostel) { return hostelRepo.save(hostel); }
    @Transactional
    public Hostel updateHostel(Long id, Hostel req) {
        Hostel existing = getHostel(id);
        existing.setName(req.getName());
        existing.setHostelType(req.getHostelType());
        existing.setCity(req.getCity());
        existing.setPincode(req.getPincode());
        existing.setAddress(req.getAddress());
        existing.setLatitude(req.getLatitude());
        existing.setLongitude(req.getLongitude());
        existing.setDefaultRent(req.getDefaultRent());
        return hostelRepo.save(existing);
    }
    @Transactional
    public void deleteHostel(Long id) {
        Hostel hostel = getHostel(id);
        hostel.setDeleted(true);
        hostelRepo.save(hostel);
    }

    // ===== Floor =====
    public List<Floor> getFloorsByHostel(Long hostelId) { return floorRepo.findByHostelIdOrderByFloorNumberAsc(hostelId); }
    public Floor getFloor(Long id) {
        return floorRepo.findById(id).orElseThrow(() -> new AppException("Floor not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public Floor createFloor(Floor floor) { return floorRepo.save(floor); }
    @Transactional
    public void deleteFloor(Long id) {
        Floor floor = getFloor(id);
        floor.setDeleted(true);
        floorRepo.save(floor);
    }

    // ===== Room =====
    public List<Room> getRoomsByFloor(Long floorId) { return roomRepo.findByFloorIdOrderByRoomNameAsc(floorId); }
    public Room getRoom(Long id) {
        return roomRepo.findById(id).orElseThrow(() -> new AppException("Room not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public Room createRoom(Room room) { return roomRepo.save(room); }
    @Transactional
    public void deleteRoom(Long id) {
        Room room = getRoom(id);
        room.setDeleted(true);
        roomRepo.save(room);
    }

    // ===== Bed =====
    public List<Bed> getBedsByRoom(Long roomId) { return bedRepo.findByRoomIdOrderByBedNameAsc(roomId); }
    public Bed getBed(Long id) {
        return bedRepo.findById(id).orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));
    }
    @Transactional
    public Bed createBed(Bed bed) { return bedRepo.save(bed); }
    @Transactional
    public void deleteBed(Long id) {
        Bed bed = getBed(id);
        bed.setDeleted(true);
        bedRepo.save(bed);
    }
}
