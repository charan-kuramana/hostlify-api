package com.hostlify.module.listing.controller;

import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.service.HostelService;
import com.hostlify.module.listing.entity.HostelEnquiry;
import com.hostlify.module.listing.entity.HostelPhoto;
import com.hostlify.module.listing.service.EnquiryService;
import com.hostlify.module.listing.service.ListingService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.HostelAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Staff: public listing switch, photo gallery, and the enquiries inbox. */
@RestController
@RequestMapping("/api/v1/listing")
@Tag(name = "Listing (staff)")
@PreAuthorize("hasRole('HOSTEL_STAFF')")
public class ListingAdminController {

    private static final String[] MANAGERS = {"OWNER", "ADMIN", "MANAGER"};

    private final ListingService listingService;
    private final EnquiryService enquiryService;
    private final HostelService hostelService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public ListingAdminController(ListingService listingService, EnquiryService enquiryService, HostelService hostelService) {
        this.listingService = listingService;
        this.enquiryService = enquiryService;
        this.hostelService = hostelService;
    }

    // ─── Listing switch ────────────────────────────────────────────────────

    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Listing status, public link and photos")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        Hostel h = hostelService.getHostel(hostelId);
        return ApiResponse.ok(view(h));
    }

    @PutMapping("/hostel/{hostelId}")
    @Operation(summary = "Owner/Admin/Manager: publish or hide the hostel; optionally change its link")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        Boolean enabled = body.containsKey("listingEnabled") ? Boolean.TRUE.equals(body.get("listingEnabled")) : null;
        Hostel h = listingService.setListing(hostelId, enabled, (String) body.get("slug"));
        return ApiResponse.ok(view(h), Boolean.TRUE.equals(enabled) ? "Your hostel is now public" : "Listing updated");
    }

    private Map<String, Object> view(Hostel h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("listingEnabled", h.isListingEnabled());
        m.put("slug", h.getSlug());
        m.put("publicUrl", frontendUrl + "/hostels/" + h.getSlug());
        m.put("photos", listingService.photos(h.getId()));
        m.put("maxPhotos", ListingService.MAX_PHOTOS);
        // What a good listing needs, so the owner sees what's missing
        m.put("checklist", Map.of(
                "photos", !listingService.photos(h.getId()).isEmpty(),
                "about", h.getAbout() != null && !h.getAbout().isBlank(),
                "location", h.getLatitude() != null && h.getLongitude() != null,
                "phone", h.getContactPhone() != null && !h.getContactPhone().isBlank()));
        return m;
    }

    // ─── Photos ────────────────────────────────────────────────────────────

    @PostMapping("/hostel/{hostelId}/photos")
    @Operation(summary = "Upload a gallery photo (JPG/PNG/WebP, max 5 MB, up to 20)")
    public ApiResponse<HostelPhoto> upload(@PathVariable Long hostelId, @RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "caption", required = false) String caption) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(listingService.addPhoto(hostelId, file, caption), "Photo added");
    }

    @PutMapping("/hostel/{hostelId}/photos/order")
    @Operation(summary = "Save gallery order")
    public ApiResponse<List<HostelPhoto>> reorder(@PathVariable Long hostelId, @RequestBody Map<String, List<Number>> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        List<Long> ids = body.getOrDefault("ids", List.of()).stream().map(Number::longValue).toList();
        listingService.reorder(hostelId, ids);
        return ApiResponse.ok(listingService.photos(hostelId), "Order saved");
    }

    @PutMapping("/photos/{photoId}/cover")
    public ApiResponse<Void> cover(@PathVariable Long photoId) {
        HostelAccess.requireStaffHostel(listingService.getPhoto(photoId).getHostelId());
        HostelAccess.requireSubRole(MANAGERS);
        listingService.setCover(photoId);
        return ApiResponse.ok(null, "Cover photo updated");
    }

    @PutMapping("/photos/{photoId}")
    public ApiResponse<HostelPhoto> caption(@PathVariable Long photoId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(listingService.getPhoto(photoId).getHostelId());
        HostelAccess.requireSubRole(MANAGERS);
        return ApiResponse.ok(listingService.updateCaption(photoId, (String) body.get("caption")), "Caption saved");
    }

    @DeleteMapping("/photos/{photoId}")
    public ApiResponse<Void> delete(@PathVariable Long photoId) {
        HostelAccess.requireStaffHostel(listingService.getPhoto(photoId).getHostelId());
        HostelAccess.requireSubRole(MANAGERS);
        listingService.deletePhoto(photoId);
        return ApiResponse.ok(null, "Photo deleted");
    }

    // ─── Enquiries inbox (all staff incl. supervisors: front-desk work) ────

    @GetMapping("/hostel/{hostelId}/enquiries")
    @Operation(summary = "Enquiries for a hostel, optionally by status")
    public ApiResponse<List<HostelEnquiry>> enquiries(@PathVariable Long hostelId, @RequestParam(required = false) String status) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(enquiryService.list(hostelId, status));
    }

    @GetMapping("/hostel/{hostelId}/enquiries/counts")
    public ApiResponse<Map<String, Long>> counts(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(enquiryService.counts(hostelId));
    }

    @PatchMapping("/enquiries/{id}")
    @Operation(summary = "Update an enquiry: status, notes, visit date/time")
    public ApiResponse<HostelEnquiry> updateEnquiry(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(enquiryService.get(id).getHostelId());
        return ApiResponse.ok(enquiryService.update(id, body), "Enquiry updated");
    }
}
