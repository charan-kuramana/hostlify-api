package com.hostlify.module.listing.repository;

import com.hostlify.module.listing.entity.HostelPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HostelPhotoRepository extends JpaRepository<HostelPhoto, Long> {
    List<HostelPhoto> findByHostelIdOrderBySortOrderAscIdAsc(Long hostelId);
    Optional<HostelPhoto> findFirstByHostelIdAndCoverTrue(Long hostelId);
    long countByHostelId(Long hostelId);
}
